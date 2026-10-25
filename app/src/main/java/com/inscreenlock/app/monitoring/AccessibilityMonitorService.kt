package com.inscreenlock.app.monitoring

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.inscreenlock.app.UnlockActivity
import com.inscreenlock.app.R
import com.inscreenlock.app.data.SettingsRepository
import com.inscreenlock.app.data.UserSettings
import com.inscreenlock.app.domain.BudgetEvaluator
import com.inscreenlock.app.domain.BudgetLevel
import com.inscreenlock.app.domain.DailyTimeCalculator
import com.inscreenlock.app.domain.MonitoringGate
import com.inscreenlock.app.domain.ReactionMode
import com.inscreenlock.app.domain.LockPolicy
import com.inscreenlock.app.monitoring.UsageStatsReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class AccessibilityMonitorService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var usageStatsReader: UsageStatsReader
    private lateinit var windowManager: WindowManager
    private var currentSettings = UserSettings()
    private var currentTargetPackage: String? = null
    private var overlay: View? = null
    private var overlayIsLock = false
    private var refreshJob: kotlinx.coroutines.Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                refreshJob?.cancel()
                removeOverlay()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsRepository = SettingsRepository(this)
        usageStatsReader = UsageStatsReader(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        serviceScope.launch {
            settingsRepository.settings.collect { settings ->
                currentSettings = settings
                if (!settings.accessibilityDisclosureAccepted || !settings.monitoringEnabled) {
                    currentTargetPackage = null
                    refreshJob?.cancel()
                    removeOverlay()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!MonitoringGate.canInspectEvents(
                consent = currentSettings.accessibilityDisclosureAccepted,
                enabled = currentSettings.monitoringEnabled,
                usageAccess = usageStatsReader.hasUsageAccess(),
            )
        ) {
            currentTargetPackage = null
            removeOverlay()
            return
        }
        val packageName = event?.packageName?.toString()
        val targetPackage = packageName?.takeIf(::isSupportedPackage)
        if (targetPackage == currentTargetPackage) {
            if (targetPackage != null && refreshJob?.isActive != true) startRefreshLoop()
            return
        }
        currentTargetPackage = targetPackage
        if (currentTargetPackage == null) {
            refreshJob?.cancel()
            removeOverlay()
        } else {
            startRefreshLoop()
        }
    }

    private fun startRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = serviceScope.launch {
            while (isActive && currentTargetPackage != null) {
                refreshOverlay()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    override fun onInterrupt() {
        currentTargetPackage = null
        refreshJob?.cancel()
        removeOverlay()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        currentTargetPackage = null
        refreshJob?.cancel()
        removeOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun refreshOverlay() {
        val targetPackage = currentTargetPackage ?: return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        if (!powerManager.isInteractive || keyguardManager.isKeyguardLocked) {
            removeOverlay()
            return
        }

        val settings = currentSettings
        if (!MonitoringGate.canInspectEvents(
                consent = settings.accessibilityDisclosureAccepted,
                enabled = settings.monitoringEnabled,
                usageAccess = usageStatsReader.hasUsageAccess(),
            )
        ) {
            removeOverlay()
            return
        }

        val totals = withContext(Dispatchers.IO) { usageStatsReader.todayTotals() }
        if (currentTargetPackage != targetPackage) return
        settingsRepository.clearExpiredUnlock(System.currentTimeMillis(), LocalDate.now())
        if (totals == null) {
            showBadge("Contagem indisponível")
            return
        }

        val limitMs = settings.dailyLimitMinutes * MINUTE_MS
        val budget = BudgetEvaluator.evaluate(totals.totalMs, limitMs, dataAvailable = true)
        if (budget.level == BudgetLevel.EXCEEDED) {
            val warningClaimed = settingsRepository.claimThresholdWarning(
                LocalDate.now(),
                settings.dailyLimitMinutes,
            )
            if (warningClaimed) Toast.makeText(this, R.string.threshold_warning, Toast.LENGTH_LONG).show()
        }

        val credentialAvailable = keyguardManager.isDeviceSecure || settings.hasPin
        val shouldLock = LockPolicy.shouldBlock(
            mode = settings.reactionMode,
            dataAvailable = true,
            credentialAvailable = credentialAvailable,
            monitoringEnabled = settings.monitoringEnabled,
            usedMs = totals.totalMs,
            limitMs = limitMs,
            unlockedUntilMs = settings.unlockedUntilMs,
            nowMs = System.currentTimeMillis(),
            unlockedDay = settings.unlockedDay,
            currentDay = LocalDate.now().toString(),
        )
        if (shouldLock) {
            showLock(totals.totalMs, settings.dailyLimitMinutes, settings.hasPin)
        } else {
            val timeLabel = "${formatMinutes(totals.totalMs)} / ${settings.dailyLimitMinutes} min"
            val lockUnavailable = settings.reactionMode == ReactionMode.LOCK && !credentialAvailable && budget.level == BudgetLevel.EXCEEDED
            showBadge(if (lockUnavailable) "Bloqueio indisponível · Aviso" else if (budget.level == BudgetLevel.EXCEEDED) "Limite · $timeLabel" else timeLabel)
        }
    }

    private fun showBadge(text: String) {
        val existing = overlay
        if (existing is TextView && !overlayIsLock) {
            existing.text = text
            return
        }
        removeOverlay()
        val badge = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.rgb(242, 245, 247))
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = roundedBackground(Color.argb(235, 27, 32, 41))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        addOverlay(badge, pause = false)
    }

    private fun showLock(totalMs: Long, limitMinutes: Int, hasPin: Boolean) {
        val existing = overlay
        if (existing is LinearLayout && overlayIsLock) {
            existing.findViewWithTag<TextView>(PAUSE_SUMMARY_TAG).text =
                getString(R.string.lock_summary, formatMinutes(totalMs), limitMinutes)
            return
        }
        removeOverlay()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(36), dp(28), dp(36))
            setBackgroundColor(Color.rgb(16, 19, 25))
        }
        panel.addView(TextView(this).apply {
            text = getString(R.string.lock_title)
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(242, 245, 247))
            gravity = Gravity.CENTER
        }, matchWidthWrapHeight())
        panel.addView(TextView(this).apply {
            tag = PAUSE_SUMMARY_TAG
            text = getString(R.string.lock_summary, formatMinutes(totalMs), limitMinutes)
            textSize = 16f
            setTextColor(Color.rgb(165, 175, 188))
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(24))
        }, matchWidthWrapHeight())
        panel.addView(pauseButton(getString(R.string.lock_home)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }, matchWidthWrapHeight())
        panel.addView(pauseButton(getString(R.string.lock_unlock)) {
            startActivity(UnlockActivity.intent(this, confirmOnly = false, hasPin = hasPin).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, matchWidthWrapHeight())
        addOverlay(panel, pause = true)
    }

    private fun pauseButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        minHeight = dp(52)
        setOnClickListener { action() }
    }

    private fun addOverlay(view: View, pause: Boolean) {
        val params = WindowManager.LayoutParams(
            if (pause) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            if (pause) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                if (!pause) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = if (pause) Gravity.CENTER else Gravity.TOP or Gravity.END
            if (!pause) {
                x = dp(12)
                y = dp(44)
            }
        }
        runCatching { windowManager.addView(view, params) }
            .onSuccess {
                overlay = view
                overlayIsLock = pause
            }
    }

    private fun removeOverlay() {
        overlay?.let { view -> runCatching { windowManager.removeView(view) } }
        overlay = null
        overlayIsLock = false
    }

    private fun roundedBackground(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(22).toFloat()
        setStroke(dp(1), Color.rgb(48, 56, 69))
    }

    private fun matchWidthWrapHeight() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatMinutes(milliseconds: Long): String = "${milliseconds / MINUTE_MS} min"

    private fun isSupportedPackage(packageName: String): Boolean =
        packageName == DailyTimeCalculator.INSTAGRAM_PACKAGE ||
            packageName == DailyTimeCalculator.TIKTOK_PACKAGE

    private companion object {
        const val REFRESH_INTERVAL_MS = 10_000L
        const val MINUTE_MS = 60_000L
        const val PAUSE_SUMMARY_TAG = "pause_summary"
    }
}

package com.inscreenlock.app.monitoring

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.inputmethod.InputMethodManager
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.inscreenlock.app.MainActivity
import com.inscreenlock.app.UnlockActivity
import com.inscreenlock.app.R
import com.inscreenlock.app.data.SettingsRepository
import com.inscreenlock.app.data.UserSettings
import com.inscreenlock.app.domain.BudgetEvaluator
import com.inscreenlock.app.domain.BudgetLevel
import com.inscreenlock.app.domain.BadgeStatus
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
import kotlinx.coroutines.flow.first
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
    private var sessionController: OverlaySessionController? = null
    private val currentTargetPackage: String? get() = sessionController?.targetPackage
    private var overlay: View? = null
    private var overlayIsLock = false
    private var refreshJob: kotlinx.coroutines.Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                sessionController?.reset()
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
        sessionController = OverlaySessionController(object : OverlaySessionHost {
            override fun show() = Unit
            override fun remove() = removeOverlayView()
        })
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        serviceScope.launch {
            var initialSettingsHandled = false
            settingsRepository.settings.collect { settings ->
                currentSettings = settings
                if (!settings.accessibilityDisclosureAccepted || !settings.monitoringEnabled) {
                    sessionController?.reset()
                    refreshJob?.cancel()
                    removeOverlay()
                }
                if (!initialSettingsHandled) {
                    initialSettingsHandled = true
                    val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                    val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                    val monitoringAllowed = MonitoringGate.canInspectEvents(
                        consent = settings.accessibilityDisclosureAccepted,
                        enabled = settings.monitoringEnabled,
                        usageAccess = usageStatsReader.hasUsageAccess(),
                    )
                    val canQueryForeground = monitoringAllowed && powerManager.isInteractive &&
                        !keyguardManager.isKeyguardLocked && sessionController?.targetPackage == null
                    val observedForeground = if (canQueryForeground) {
                        withContext(Dispatchers.IO) { usageStatsReader.foregroundPackageForReconnection() }
                    } else null
                    val resumed = sessionController?.resumeFromReconnection(
                        observedForegroundPackage = observedForeground,
                        monitoringAllowed = monitoringAllowed,
                        screenInteractive = powerManager.isInteractive,
                        keyguardVisible = keyguardManager.isKeyguardLocked,
                    ) == true
                    if (resumed) startRefreshLoop()
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
            sessionController?.reset()
            removeOverlay()
            return
        }
        val packageName = event?.packageName?.toString()
        val ownPackageName = packageName?.takeIf { it == this.packageName }
        val eventClassName = if (ownPackageName != null) event?.className?.toString() else null
        val previousTarget = currentTargetPackage
        val resolution = sessionController?.onWindowEvent(
            eventPackage = packageName,
            transientPackages = transientPackages(),
            ownPackageName = ownPackageName,
            eventClassName = eventClassName,
            ownActivityClassNames = if (ownPackageName != null) OWN_ACTIVITY_CLASS_NAMES else emptySet(),
        ) ?: return
        val targetPackage = resolution.targetPackage
        if (resolution.transientWindow) {
            removeOverlay()
            startRefreshLoop(TRANSIENT_RECONFIRM_DELAY_MS)
            return
        }
        if (targetPackage == previousTarget) {
            if (targetPackage != null && refreshJob?.isActive != true) startRefreshLoop()
            return
        }
        if (targetPackage == null) {
            refreshJob?.cancel()
            removeOverlay()
        } else {
            startRefreshLoop()
        }
    }

    private fun startRefreshLoop(initialDelayMs: Long = 0L) {
        refreshJob?.cancel()
        refreshJob = serviceScope.launch {
            if (initialDelayMs > 0) delay(initialDelayMs)
            while (isActive && currentTargetPackage != null) {
                refreshOverlay()
                if (currentTargetPackage != null) delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun transientPackages(): Set<String> = buildSet {
        add(SYSTEM_UI_PACKAGE)
        runCatching {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .enabledInputMethodList
                .mapTo(this) { it.packageName }
        }
    }

    override fun onInterrupt() {
        sessionController?.reset()
        refreshJob?.cancel()
        removeOverlay()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        sessionController?.reset()
        refreshJob?.cancel()
        removeOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun refreshOverlay() {
        val targetPackage = currentTargetPackage ?: return
        val transientPackages = transientPackages()
        val usageForeground = withContext(Dispatchers.IO) { usageStatsReader.recentForegroundPackage() }
        if (currentTargetPackage != targetPackage) return
        when (sessionController?.reconfirmUsage(usageForeground, transientPackages)) {
            false -> {
                removeOverlay()
                return
            }
            true -> Unit
            null -> if (usageForeground in transientPackages) {
                removeOverlay()
                return
            }
        }

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
        val clockReading = settingsRepository.clockReading()
        settingsRepository.clearExpiredUnlock(LocalDate.now(), clockReading)
        if (totals == null) {
            showBadge("Contagem indisponível")
            return
        }
        val lockSettings = settingsRepository.settings.first()
        currentSettings = lockSettings
        if (currentTargetPackage != targetPackage || !MonitoringGate.canInspectEvents(
                consent = lockSettings.accessibilityDisclosureAccepted,
                enabled = lockSettings.monitoringEnabled,
                usageAccess = usageStatsReader.hasUsageAccess(),
            )
        ) {
            removeOverlay()
            return
        }

        val limitMs = lockSettings.dailyLimitMinutes * MINUTE_MS
        val budget = BudgetEvaluator.evaluate(totals.totalMs, limitMs, dataAvailable = true)
        if (budget.level == BudgetLevel.EXCEEDED) {
            val warningClaimed = settingsRepository.claimThresholdWarning(
                LocalDate.now(),
                lockSettings.dailyLimitMinutes,
            )
            if (warningClaimed) Toast.makeText(this, R.string.threshold_warning, Toast.LENGTH_LONG).show()
        }

        val credentialAvailable = keyguardManager.isDeviceSecure || lockSettings.hasPin
        val shouldLock = LockPolicy.shouldBlock(
            mode = lockSettings.reactionMode,
            dataAvailable = true,
            credentialAvailable = credentialAvailable,
            monitoringEnabled = lockSettings.monitoringEnabled,
            usedMs = totals.totalMs,
            limitMs = limitMs,
            unlockedUntilMs = lockSettings.unlockedUntilMs,
            nowMs = System.currentTimeMillis(),
            unlockedDay = lockSettings.unlockedDay,
            currentDay = LocalDate.now().toString(),
            unlockedElapsedUntilMs = lockSettings.unlockedElapsedUntilMs,
            unlockedBootCount = lockSettings.unlockedBootCount,
            clockReading = clockReading,
            unlockedDurationMs = lockSettings.unlockedDurationMs,
        )
        if (shouldLock) {
            showLock(totals.totalMs, lockSettings.dailyLimitMinutes, lockSettings.hasPin)
        } else {
            val timeLabel = "${formatMinutes(totals.totalMs)} / ${lockSettings.dailyLimitMinutes} min"
            val lockUnavailable = lockSettings.reactionMode == ReactionMode.LOCK && !credentialAvailable && budget.level == BudgetLevel.EXCEEDED
            showBadge(BadgeStatus.format(budget.level, timeLabel, lockUnavailable))
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
        presentOverlay { addOverlay(badge, pause = false) }
    }

    private fun showLock(totalMs: Long, limitMinutes: Int, hasPin: Boolean) {
        val existing = overlay
        if (existing is LinearLayout && overlayIsLock) {
            existing.findViewWithTag<TextView>(LOCK_SUMMARY_TAG).text =
                getString(R.string.lock_summary, formatMinutes(totalMs), limitMinutes)
            return
        }
        removeOverlay()

        val panel = LockOverlayContent.create(
            context = this,
            title = getString(R.string.lock_title),
            summary = getString(R.string.lock_summary, formatMinutes(totalMs), limitMinutes),
            homeLabel = getString(R.string.lock_home),
            unlockLabel = getString(R.string.lock_unlock),
            onHome = { performGlobalAction(GLOBAL_ACTION_HOME) },
            onUnlock = {
                startActivity(UnlockActivity.intent(this, confirmOnly = false, hasPin = hasPin).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
        )
        presentOverlay { addOverlay(panel, pause = true) }
    }

    private fun presentOverlay(show: () -> Boolean) {
        sessionController?.presentOverlay(show) ?: show()
    }

    private fun addOverlay(view: View, pause: Boolean): Boolean {
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
        return overlay === view
    }

    private fun removeOverlay() {
        sessionController?.hideOverlay() ?: removeOverlayView()
    }

    private fun removeOverlayView() {
        overlay?.let { view -> runCatching { windowManager.removeView(view) } }
        overlay = null
        overlayIsLock = false
    }

    private fun roundedBackground(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(22).toFloat()
        setStroke(dp(1), Color.rgb(48, 56, 69))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatMinutes(milliseconds: Long): String = "${milliseconds / MINUTE_MS} min"

    private companion object {
        const val REFRESH_INTERVAL_MS = 10_000L
        const val TRANSIENT_RECONFIRM_DELAY_MS = 1_500L
        const val MINUTE_MS = 60_000L
        val OWN_ACTIVITY_CLASS_NAMES = setOf(MainActivity::class.java.name, UnlockActivity::class.java.name)
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}

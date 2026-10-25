package com.inscreenlock.app.monitoring

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import androidx.core.graphics.createBitmap
import com.inscreenlock.app.domain.DailyTimeCalculator
import com.inscreenlock.app.domain.UsageSignal
import com.inscreenlock.app.domain.UsageTotals
import com.inscreenlock.app.domain.UsageQuerySnapshot
import com.inscreenlock.app.domain.UsageWindow
import com.inscreenlock.app.domain.rankUsageByPackage
import com.inscreenlock.app.domain.recentForegroundPackage
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

data class AppUsageEntry(
    val packageName: String,
    val label: String,
    val durationMs: Long,
    val icon: Bitmap?,
)

data class TodayUsageSnapshot(
    val totals: UsageTotals?,
    val apps: List<AppUsageEntry>,
)

class UsageStatsReader(private val context: Context) {
    private val presentationCache = ConcurrentHashMap<String, AppPresentation>()

    fun hasUsageAccess(): Boolean = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun todayTotals(nowMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): UsageTotals? =
        todaySnapshot(nowMs, zoneId)?.totals

    fun todaySnapshot(
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): TodayUsageSnapshot? {
        if (!hasUsageAccess()) return null
        val startOfDayMs = UsageWindow.forInstant(nowMs, zoneId).startMs
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        return runCatching {
            val usageEvents = manager.queryEvents(UsageWindow.queryStartMs(startOfDayMs), nowMs) ?: return null
            val signals = mutableListOf<UsageSignal>()
            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                signalFor(event)?.let(signals::add)
            }
            val excludedPackages = excludedPackages()
            val durations = DailyTimeCalculator.calculateByPackage(
                signals,
                startOfDayMs,
                nowMs,
                excludedPackages,
            )
            val totals = UsageQuerySnapshot.totals(true, signals, startOfDayMs, nowMs)
                ?: return TodayUsageSnapshot(totals = null, apps = emptyList())
            val apps = rankUsageByPackage(durations, limit = Int.MAX_VALUE)
                .map { (packageName, duration) -> appUsageEntry(packageName, duration) }
            TodayUsageSnapshot(totals, apps)
        }.getOrNull()
    }

    fun recentForegroundPackage(nowMs: Long = System.currentTimeMillis()): String? {
        if (!hasUsageAccess()) return null
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        return runCatching {
            val usageEvents = manager.queryEvents(nowMs - FOREGROUND_LOOKBACK_MS, nowMs) ?: return null
            val signals = mutableListOf<UsageSignal>()
            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                signalFor(event)?.let(signals::add)
            }
            recentForegroundPackage(signals)
        }.getOrNull()
    }

    private fun excludedPackages(): Set<String> = buildSet {
        add(context.packageName)
        add(SYSTEM_UI_PACKAGE)
        runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.resolveActivity(homeIntent, 0)?.activityInfo?.packageName
        }.getOrNull()?.let(::add)
    }

    private fun appUsageEntry(packageName: String, durationMs: Long): AppUsageEntry {
        val presentation = presentationCache.getOrPut(packageName) { appPresentation(packageName) }
        return AppUsageEntry(packageName, presentation.label, durationMs, presentation.icon)
    }

    private fun appPresentation(packageName: String): AppPresentation {
        val packageManager = context.packageManager
        return runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            AppPresentation(
                label = packageManager.getApplicationLabel(info).toString().ifBlank { packageName },
                icon = packageManager.getApplicationIcon(info).toBoundedBitmap(),
            )
        }.getOrElse {
            AppPresentation(packageName, null)
        }
    }

    private data class AppPresentation(val label: String, val icon: Bitmap?)

    private fun Drawable.toBoundedBitmap(): Bitmap = createBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
        .also { bitmap ->
            val previousBounds = copyBounds()
            setBounds(0, 0, ICON_SIZE_PX, ICON_SIZE_PX)
            draw(Canvas(bitmap))
            bounds = previousBounds
        }

    @Suppress("DEPRECATION")
    private fun signalFor(event: UsageEvents.Event): UsageSignal? {
        val kind = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageSignal.Kind.APP_FOREGROUND
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageSignal.Kind.APP_BACKGROUND
                else -> systemSignalKind(event.eventType)
            }
        } else {
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> UsageSignal.Kind.APP_FOREGROUND
                UsageEvents.Event.MOVE_TO_BACKGROUND -> UsageSignal.Kind.APP_BACKGROUND
                else -> systemSignalKind(event.eventType)
            }
        } ?: return null

        val packageName = when (kind) {
            UsageSignal.Kind.APP_FOREGROUND, UsageSignal.Kind.APP_BACKGROUND -> event.packageName
            else -> null
        }
        return UsageSignal(event.timeStamp, kind, packageName, event.className)
    }

    @Suppress("DEPRECATION")
    private fun systemSignalKind(eventType: Int): UsageSignal.Kind? = when (eventType) {
        UsageEvents.Event.SCREEN_INTERACTIVE -> UsageSignal.Kind.SCREEN_INTERACTIVE
        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageSignal.Kind.SCREEN_NON_INTERACTIVE
        UsageEvents.Event.KEYGUARD_SHOWN -> UsageSignal.Kind.KEYGUARD_SHOWN
        UsageEvents.Event.KEYGUARD_HIDDEN -> UsageSignal.Kind.KEYGUARD_HIDDEN
        else -> null
    }

    private companion object {
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val ICON_SIZE_PX = 96
        const val FOREGROUND_LOOKBACK_MS = 15 * 60 * 1000L
    }
}

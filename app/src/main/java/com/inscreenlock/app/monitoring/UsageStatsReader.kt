package com.inscreenlock.app.monitoring

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.inscreenlock.app.domain.DailyTimeCalculator
import com.inscreenlock.app.domain.UsageSignal
import com.inscreenlock.app.domain.UsageTotals
import java.time.ZoneId
import com.inscreenlock.app.domain.UsageWindow

class UsageStatsReader(private val context: Context) {
    fun hasUsageAccess(): Boolean = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun todayTotals(nowMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): UsageTotals? {
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
            if (signals.isEmpty()) return null
            DailyTimeCalculator.calculate(signals, startOfDayMs, nowMs)
        }.getOrNull()
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
}

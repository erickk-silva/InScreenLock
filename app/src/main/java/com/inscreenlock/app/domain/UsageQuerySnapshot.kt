package com.inscreenlock.app.domain

object UsageQuerySnapshot {
    fun totals(
        querySucceeded: Boolean,
        signals: List<UsageSignal>,
        windowStartMs: Long,
        nowMs: Long,
    ): UsageTotals? {
        if (!querySucceeded) return null
        return DailyTimeCalculator.calculate(signals, windowStartMs, nowMs)
    }
}

fun recentForegroundPackage(signals: List<UsageSignal>): String? {
    var foregroundPackage: String? = null
    val ordered = signals.withIndex()
        .sortedWith(compareBy<IndexedValue<UsageSignal>>({ it.value.timestampMs }, { it.index }))
        .map { it.value }
    for (signal in ordered) {
        when (signal.kind) {
            UsageSignal.Kind.APP_FOREGROUND -> foregroundPackage = signal.packageName
            UsageSignal.Kind.APP_BACKGROUND -> {
                if (foregroundPackage == signal.packageName) foregroundPackage = null
            }
            else -> Unit
        }
    }
    return foregroundPackage
}

private const val RECENT_FOREGROUND_LOOKBACK_MS = 15 * 60 * 1000L

/**
 * Cheap path: one query over the last 15 minutes. With [expandLookback] the window grows until a foreground
 * package resolves, so a long session without recent activity transitions is still recognised.
 */
fun currentForegroundPackage(
    nowMs: Long,
    expandLookback: Boolean,
    query: (startMs: Long, endMs: Long) -> List<UsageSignal>,
): String? {
    val signals = if (expandLookback) {
        UsageWindow.querySignalsUntilForegroundResolved(nowMs, query)
    } else {
        query((nowMs - RECENT_FOREGROUND_LOOKBACK_MS).coerceAtLeast(0L), nowMs)
    }
    return recentForegroundPackage(signals)
}

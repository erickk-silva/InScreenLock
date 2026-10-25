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

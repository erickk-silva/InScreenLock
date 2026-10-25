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

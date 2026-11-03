package com.inscreenlock.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class UsageWindow(val date: LocalDate, val startMs: Long, val endMs: Long) {
    companion object {
        private const val HOUR_MS = 60 * 60 * 1000L
        private const val DAY_MS = 24 * HOUR_MS

        fun querySignalsWithLookback(
            dayStartMs: Long,
            endMs: Long,
            query: (startMs: Long, endMs: Long) -> List<UsageSignal>,
        ): List<UsageSignal> {
            var lookbackMs = INITIAL_LOOKBACK_MS
            while (true) {
                val startMs = (dayStartMs - lookbackMs).coerceAtLeast(0L)
                val signals = query(startMs, endMs)
                if (hasResolvedDayStartState(signals, dayStartMs) || startMs == 0L || lookbackMs >= MAX_LOOKBACK_MS) {
                    return signals
                }
                lookbackMs = (lookbackMs + lookbackMs).coerceAtMost(MAX_LOOKBACK_MS)
            }
        }

        fun querySignalsUntilForegroundResolved(
            endMs: Long,
            query: (startMs: Long, endMs: Long) -> List<UsageSignal>,
        ): List<UsageSignal> {
            var lookbackMs = INITIAL_LOOKBACK_MS
            while (true) {
                val startMs = (endMs - lookbackMs).coerceAtLeast(0L)
                val signals = query(startMs, endMs)
                if (recentForegroundPackage(signals) != null || startMs == 0L || lookbackMs >= MAX_LOOKBACK_MS) {
                    return signals
                }
                lookbackMs = (lookbackMs + lookbackMs).coerceAtMost(MAX_LOOKBACK_MS)
            }
        }

        private fun hasResolvedDayStartState(signals: List<UsageSignal>, dayStartMs: Long): Boolean {
            val priorEvents = signals.asSequence().filter { it.timestampMs < dayStartMs }
            var foregroundResolved = false
            var lastInteractive: Boolean? = null
            var keyguardResolved = false
            priorEvents.forEach { signal ->
                when (signal.kind) {
                    UsageSignal.Kind.APP_FOREGROUND -> foregroundResolved = true
                    UsageSignal.Kind.APP_BACKGROUND -> Unit
                    UsageSignal.Kind.SCREEN_INTERACTIVE -> lastInteractive = true
                    UsageSignal.Kind.SCREEN_NON_INTERACTIVE -> lastInteractive = false
                    UsageSignal.Kind.KEYGUARD_SHOWN, UsageSignal.Kind.KEYGUARD_HIDDEN -> keyguardResolved = true
                }
            }
            return foregroundResolved && lastInteractive != null && (!lastInteractive || keyguardResolved)
        }

        fun forInstant(instantMs: Long, zoneId: ZoneId): UsageWindow {
            val date = Instant.ofEpochMilli(instantMs).atZone(zoneId).toLocalDate()
            val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            return UsageWindow(date, start, end)
        }

        private const val INITIAL_LOOKBACK_MS = 6 * HOUR_MS
        private const val MAX_LOOKBACK_MS = 7 * DAY_MS
    }
}

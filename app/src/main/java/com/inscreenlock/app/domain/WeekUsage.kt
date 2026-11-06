package com.inscreenlock.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayUsage(
    val date: LocalDate,
    val available: Boolean,
    val totalMs: Long,
    val byPackage: Map<String, Long>,
)

data class WeekUsage(val days: List<DayUsage>)

object WeekUsageCalculator {
    /**
     * An available day has a successful UsageEvents query and at least one recognized event
     * inside that local day. This is a coverage heuristic, not proof that Android retained every
     * event for the whole day; an empty event stream is therefore unavailable, never zero usage.
     */
    fun calculate(
        nowMs: Long,
        zone: ZoneId,
        query: (startMs: Long, endMs: Long) -> List<UsageSignal>?,
        excludedPackages: Set<String> = emptySet(),
    ): WeekUsage {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val days = (6 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), nowMs)
            val signals = runCatching {
                UsageWindow.querySignalsWithLookback(startMs, endMs) { queryStartMs, queryEndMs ->
                    query(queryStartMs, queryEndMs) ?: error("Usage events unavailable")
                }
            }.getOrNull()
            if (signals == null) {
                DayUsage(date, available = false, totalMs = 0L, byPackage = emptyMap())
            } else {
                val durations = DailyTimeCalculator.calculateByPackage(signals, startMs, endMs, excludedPackages)
                val hasDayEvent = signals.any { it.timestampMs >= startMs && it.timestampMs < endMs }
                DayUsage(
                    date = date,
                    available = hasDayEvent,
                    totalMs = durations.values.sum(),
                    byPackage = durations.toMap(),
                )
            }
        }
        return WeekUsage(days.toList())
    }

    fun unavailable(nowMs: Long, zone: ZoneId): WeekUsage {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return WeekUsage((6 downTo 0).map { daysAgo ->
            DayUsage(today.minusDays(daysAgo.toLong()), available = false, totalMs = 0L, byPackage = emptyMap())
        })
    }
}

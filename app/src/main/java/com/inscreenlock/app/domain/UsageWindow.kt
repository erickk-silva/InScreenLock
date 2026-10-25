package com.inscreenlock.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class UsageWindow(val date: LocalDate, val startMs: Long, val endMs: Long) {
    companion object {
        fun queryStartMs(dayStartMs: Long): Long =
            (dayStartMs - EVENT_LOOKBACK_MS).coerceAtLeast(0L)

        fun forInstant(instantMs: Long, zoneId: ZoneId): UsageWindow {
            val date = Instant.ofEpochMilli(instantMs).atZone(zoneId).toLocalDate()
            val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            return UsageWindow(date, start, end)
        }

        private const val EVENT_LOOKBACK_MS = 6 * 60 * 60 * 1000L
    }
}

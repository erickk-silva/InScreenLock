package com.inscreenlock.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class UsageWindow(val date: LocalDate, val startMs: Long, val endMs: Long) {
    companion object {
        fun forInstant(instantMs: Long, zoneId: ZoneId): UsageWindow {
            val date = Instant.ofEpochMilli(instantMs).atZone(zoneId).toLocalDate()
            val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            return UsageWindow(date, start, end)
        }
    }
}

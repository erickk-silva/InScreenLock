package com.inscreenlock.app.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageWindowTest {
    @Test
    fun localDayUsesCalendarBoundariesAcrossDaylightSavingTime() {
        val zone = ZoneId.of("America/New_York")
        val now = Instant.parse("2026-03-08T16:00:00Z").toEpochMilli()

        val window = UsageWindow.forInstant(now, zone)

        assertEquals(Instant.parse("2026-03-08T05:00:00Z").toEpochMilli(), window.startMs)
        assertEquals(Instant.parse("2026-03-09T04:00:00Z").toEpochMilli(), window.endMs)
        assertEquals(23 * 60 * 60 * 1000L, window.endMs - window.startMs)
    }
}

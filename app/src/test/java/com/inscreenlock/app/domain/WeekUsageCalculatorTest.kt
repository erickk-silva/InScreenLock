package com.inscreenlock.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekUsageCalculatorTest {
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun calculatesSevenLocalDaysIncludingPartialTodayAndPerPackageTotals() {
        val now = LocalDate.of(2026, 11, 6).atTime(1, 30).atZone(zone).toInstant().toEpochMilli()
        val result = WeekUsageCalculator.calculate(now, zone, query = { _, end ->
            val dayStart = Instant.ofEpochMilli(end - 1).atZone(zone).toLocalDate().atStartOfDay(zone)
                .toInstant().toEpochMilli()
            when (Instant.ofEpochMilli(dayStart).atZone(zone).toLocalDate()) {
                LocalDate.of(2026, 11, 6) -> listOf(
                    signal(dayStart - 1_000, UsageSignal.Kind.SCREEN_INTERACTIVE),
                    signal(dayStart, UsageSignal.Kind.APP_FOREGROUND, "social.app"),
                    signal(dayStart + 30 * MINUTE_MS, UsageSignal.Kind.APP_BACKGROUND, "social.app"),
                    signal(dayStart + 30 * MINUTE_MS, UsageSignal.Kind.APP_FOREGROUND, "video.app"),
                    signal(end, UsageSignal.Kind.APP_BACKGROUND, "video.app"),
                )
                LocalDate.of(2026, 11, 5) -> listOf(
                    signal(dayStart, UsageSignal.Kind.SCREEN_INTERACTIVE),
                    signal(dayStart, UsageSignal.Kind.APP_FOREGROUND, "social.app"),
                    signal(end - 10 * MINUTE_MS, UsageSignal.Kind.APP_BACKGROUND, "social.app"),
                )
                else -> emptyList()
            }
        })

        assertEquals((6 downTo 0).map { LocalDate.of(2026, 11, 6).minusDays(it.toLong()) }, result.days.map { it.date })
        assertEquals(7, result.days.size)
        assertFalse(result.days[0].available)
        assertEquals(0L, result.days[0].totalMs)
        assertEquals(24 * HOUR_MS - 10 * MINUTE_MS, result.days[5].totalMs)
        assertTrue(result.days[6].available)
        assertEquals(90 * MINUTE_MS, result.days[6].totalMs)
        assertEquals(mapOf("social.app" to 30 * MINUTE_MS, "video.app" to 60 * MINUTE_MS), result.days[6].byPackage)
    }

    @Test
    fun priorDayLookbackEventsDoNotMakeAnEmptyDayAvailable() {
        val day = LocalDate.of(2026, 11, 6)
        val now = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val result = WeekUsageCalculator.calculate(now, zone, query = { start, _ ->
            listOf(signal(start - 1, UsageSignal.Kind.SCREEN_INTERACTIVE))
        })

        assertFalse(result.days.last().available)
    }

    @Test
    fun usesLocalCalendarBoundariesAcrossShortAndLongDstDays() {
        val springZone = ZoneId.of("America/New_York")
        val springDate = LocalDate.of(2024, 3, 10)
        val springStart = springDate.atStartOfDay(springZone).toInstant().toEpochMilli()
        val springEnd = springDate.plusDays(1).atStartOfDay(springZone).toInstant().toEpochMilli()
        val fallDate = LocalDate.of(2024, 11, 3)
        val fallStart = fallDate.atStartOfDay(springZone).toInstant().toEpochMilli()
        val fallEnd = fallDate.plusDays(1).atStartOfDay(springZone).toInstant().toEpochMilli()

        assertEquals(23 * 60 * MINUTE_MS, springEnd - springStart)
        assertEquals(25 * 60 * MINUTE_MS, fallEnd - fallStart)
        assertEquals(23 * 60 * MINUTE_MS, usageForDay(springDate, springZone))
        assertEquals(25 * 60 * MINUTE_MS, usageForDay(fallDate, springZone))
    }

    private fun usageForDay(date: LocalDate, zone: ZoneId): Long {
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = date.plusDays(1).atTime(1, 0).atZone(zone).toInstant().toEpochMilli()
        val week = WeekUsageCalculator.calculate(now, zone, query = { _, end ->
            val queriedDate = Instant.ofEpochMilli(end - 1).atZone(zone).toLocalDate()
            if (queriedDate == date) {
                listOf(
                    signal(dayStart, UsageSignal.Kind.SCREEN_INTERACTIVE),
                    signal(dayStart, UsageSignal.Kind.APP_FOREGROUND, "social.app"),
                    signal(dayEnd, UsageSignal.Kind.APP_BACKGROUND, "social.app"),
                )
            } else {
                emptyList()
            }
        })
        return week.days.single { it.date == date }.totalMs
    }

    private fun signal(
        timestamp: Long,
        kind: UsageSignal.Kind,
        packageName: String? = null,
    ) = UsageSignal(timestamp, kind, packageName)

    private companion object {
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60 * MINUTE_MS
    }
}

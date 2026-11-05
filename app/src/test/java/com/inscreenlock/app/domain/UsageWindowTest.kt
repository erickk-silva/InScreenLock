package com.inscreenlock.app.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HOUR_MS = 60 * 60 * 1000L
private const val MINUTE_MS = 60 * 1000L
private const val DAY_MS = 24 * HOUR_MS

class UsageWindowTest {
    @Test
    fun expandsToFindEightHourForegroundAndCountsOnlyPostMidnightTime() {
        val midnight = 10L * HOUR_MS
        val starts = mutableListOf<Long>()

        val signals = UsageWindow.querySignalsWithLookback(midnight, midnight + 2 * HOUR_MS) { start, _ ->
            starts += start
            if (start <= midnight - 8 * HOUR_MS) listOf(
                signal(midnight - 8 * HOUR_MS, UsageSignal.Kind.SCREEN_INTERACTIVE),
                signal(midnight - 8 * HOUR_MS, UsageSignal.Kind.KEYGUARD_HIDDEN),
                signal(midnight - 8 * HOUR_MS, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
            ) else emptyList()
        }

        assertEquals(2, starts.size)
        assertEquals(2 * HOUR_MS, DailyTimeCalculator.calculate(signals, midnight, midnight + 2 * HOUR_MS).instagramMs)
    }

    @Test
    fun expandsWhenScreenBecameNonInteractiveBeforeInitialCutoff() {
        val midnight = 10L * HOUR_MS
        val starts = mutableListOf<Long>()

        val signals = UsageWindow.querySignalsWithLookback(midnight, midnight + HOUR_MS) { start, _ ->
            starts += start
            if (start <= midnight - 8 * HOUR_MS) listOf(
                signal(midnight - 8 * HOUR_MS, UsageSignal.Kind.SCREEN_INTERACTIVE),
                signal(midnight - 8 * HOUR_MS, UsageSignal.Kind.KEYGUARD_HIDDEN),
                signal(midnight - 8 * HOUR_MS, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.TIKTOK_PACKAGE),
                signal(midnight - 7 * HOUR_MS, UsageSignal.Kind.SCREEN_NON_INTERACTIVE),
                signal(midnight + 30 * MINUTE_MS, UsageSignal.Kind.SCREEN_INTERACTIVE),
            ) else listOf(signal(midnight + 30 * MINUTE_MS, UsageSignal.Kind.SCREEN_INTERACTIVE))
        }

        assertEquals(2, starts.size)
        assertEquals(30 * MINUTE_MS, DailyTimeCalculator.calculate(signals, midnight, midnight + HOUR_MS).tiktokMs)
    }

    @Test
    fun backgroundWithoutForegroundDoesNotEstablishForegroundAtMidnight() {
        val midnight = 10L * DAY_MS
        val starts = mutableListOf<Long>()
        val signals = UsageWindow.querySignalsWithLookback(midnight, midnight + HOUR_MS) { start, _ ->
            starts += start
            if (start <= midnight - 7 * HOUR_MS) listOf(
                signal(midnight - 7 * HOUR_MS, UsageSignal.Kind.SCREEN_INTERACTIVE),
                signal(midnight - 7 * HOUR_MS, UsageSignal.Kind.KEYGUARD_HIDDEN),
                signal(midnight - 7 * HOUR_MS, UsageSignal.Kind.APP_BACKGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
            ) else emptyList()
        }

        assertEquals(midnight - 7 * DAY_MS, starts.last())
        assertEquals(UsageTotals(), DailyTimeCalculator.calculate(signals, midnight, midnight + HOUR_MS))
    }

    @Test
    fun stopsExpansionAtSevenDayLimitWhenSignalsCannotResolveState() {
        val midnight = 10L * DAY_MS
        val starts = mutableListOf<Long>()

        UsageWindow.querySignalsWithLookback(midnight, midnight + HOUR_MS) { start, _ ->
            starts += start
            emptyList()
        }

        assertEquals(midnight - 7 * DAY_MS, starts.last())
        assertEquals(6, starts.size)
    }

    @Test
    fun queryLookbackClampsToEpochWhenDayStartIsBeforeInitialLookback() {
        val dayStart = 2L * HOUR_MS
        val starts = mutableListOf<Long>()

        UsageWindow.querySignalsWithLookback(dayStart, dayStart + HOUR_MS) { start, _ ->
            starts += start
            emptyList()
        }

        assertEquals(0L, starts.first())
        assertTrue(starts.all { it >= 0L })
        assertTrue(starts.all { it >= (dayStart - 7 * DAY_MS).coerceAtLeast(0L) })
    }

    @Test
    fun doesNotExpandWhenForegroundAndScreenStatesAreResolvedBeforeMidnight() {
        val midnight = 10L * HOUR_MS
        val starts = mutableListOf<Long>()

        UsageWindow.querySignalsWithLookback(midnight, midnight + HOUR_MS) { start, _ ->
            starts += start
            listOf(
                signal(midnight - HOUR_MS, UsageSignal.Kind.SCREEN_INTERACTIVE),
                signal(midnight - HOUR_MS, UsageSignal.Kind.KEYGUARD_HIDDEN),
                signal(midnight - HOUR_MS, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
            )
        }

        assertEquals(1, starts.size)
        assertTrue(starts.single() < midnight)
    }

    @Test
    fun screenOffAtDayStartDoesNotExpandWhenKeyguardSignalIsAbsent() {
        val midnight = 10L * HOUR_MS
        var queryCount = 0

        UsageWindow.querySignalsWithLookback(midnight, midnight + HOUR_MS) { _, _ ->
            queryCount++
            listOf(
                signal(midnight - HOUR_MS, UsageSignal.Kind.SCREEN_NON_INTERACTIVE),
                signal(midnight - HOUR_MS, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
            )
        }

        assertEquals(1, queryCount)
    }

    @Test
    fun reconnectionLookbackStopsWhenForegroundIsResolvedWithoutScreenSignals() {
        val now = 10L * DAY_MS
        val starts = mutableListOf<Long>()

        val signals = UsageWindow.querySignalsUntilForegroundResolved(now) { start, _ ->
            starts += start
            if (start <= now - 8 * HOUR_MS) {
                listOf(signal(now - 8 * HOUR_MS, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.TIKTOK_PACKAGE))
            } else emptyList()
        }

        assertEquals(2, starts.size)
        assertEquals(DailyTimeCalculator.TIKTOK_PACKAGE, recentForegroundPackage(signals))
    }

    @Test
    fun localDayUsesCalendarBoundariesAcrossDaylightSavingTime() {
        val zone = ZoneId.of("America/New_York")
        val now = Instant.parse("2026-03-08T16:00:00Z").toEpochMilli()

        val window = UsageWindow.forInstant(now, zone)

        assertEquals(Instant.parse("2026-03-08T05:00:00Z").toEpochMilli(), window.startMs)
        assertEquals(Instant.parse("2026-03-09T04:00:00Z").toEpochMilli(), window.endMs)
        assertEquals(23 * 60 * 60 * 1000L, window.endMs - window.startMs)
    }

    private fun signal(timestamp: Long, kind: UsageSignal.Kind, packageName: String? = null) =
        UsageSignal(timestamp, kind, packageName)
}

package com.inscreenlock.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DailyTimeCalculatorScenariosTest {
    @Test
    fun activitySwitchInsideSameAppDoesNotResetOrDoubleCount() {
        val result = calculate(
            signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
            signal(0, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
            signal(40, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
        )
        assertEquals(100L, result.instagramMs)
    }

    @Test
    fun latePauseForPreviousActivityDoesNotEndCurrentActivityInSamePackage() {
        val result = calculate(
            signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
            signal(0, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE, "Feed"),
            signal(40, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE, "Login"),
            signal(60, UsageSignal.Kind.APP_BACKGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE, "Feed"),
            signal(100, UsageSignal.Kind.APP_BACKGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE, "Login"),
        )
        assertEquals(100L, result.instagramMs)
    }

    @Test
    fun alternatingAppsAccumulatesOnlyEachForegroundInterval() {
        val result = calculate(
            signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
            signal(0, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
            signal(20, UsageSignal.Kind.APP_BACKGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
            signal(20, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.TIKTOK_PACKAGE),
            signal(50, UsageSignal.Kind.APP_BACKGROUND, DailyTimeCalculator.TIKTOK_PACKAGE),
            signal(50, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
        )
        assertEquals(70L, result.instagramMs)
        assertEquals(30L, result.tiktokMs)
    }

    @Test
    fun twoAppsUsedSequentiallyAccumulateExactDurations() {
        val result = calculate(
            signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
            signal(0, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE, "Feed"),
            signal(35, UsageSignal.Kind.APP_BACKGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE, "Feed"),
            signal(35, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.TIKTOK_PACKAGE, "Feed"),
            signal(80, UsageSignal.Kind.APP_BACKGROUND, DailyTimeCalculator.TIKTOK_PACKAGE, "Feed"),
        )
        assertEquals(35L, result.instagramMs)
        assertEquals(45L, result.tiktokMs)
    }

    @Test
    fun appSwitchWithoutBackgroundClosesPreviousForegroundSpan() {
        val result = calculate(
            signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
            signal(0, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
            signal(50, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.TIKTOK_PACKAGE),
        )
        assertEquals(50L, result.instagramMs)
        assertEquals(50L, result.tiktokMs)
    }

    @Test
    fun duplicateForegroundAndInteractiveEventsAreIdempotent() {
        val result = calculate(
            signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
            signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
            signal(0, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.TIKTOK_PACKAGE),
            signal(0, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.TIKTOK_PACKAGE),
        )
        assertEquals(100L, result.tiktokMs)
    }

    @Test
    fun inputEventsOutOfOrderAreProcessedChronologically() {
        val result = calculate(
            signal(80, UsageSignal.Kind.APP_BACKGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
            signal(50, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
            signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
        )
        assertEquals(30L, result.instagramMs)
    }

    @Test
    fun staleBackgroundFromPreviousAppDoesNotStopCurrentApp() {
        val result = calculate(
            signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
            signal(0, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
            signal(40, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.TIKTOK_PACKAGE),
            signal(60, UsageSignal.Kind.APP_BACKGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
        )
        assertEquals(40L, result.instagramMs)
        assertEquals(60L, result.tiktokMs)
    }

    @Test
    fun screenOffStopsTimeUntilNextInteractiveEvent() {
        val result = calculate(
            signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
            signal(0, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
            signal(30, UsageSignal.Kind.SCREEN_NON_INTERACTIVE),
            signal(70, UsageSignal.Kind.SCREEN_INTERACTIVE),
        )
        assertEquals(60L, result.instagramMs)
    }

    @Test
    fun keyguardTimeIsExcludedEvenWhenScreenRemainsInteractive() {
        val result = calculate(
            signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
            signal(0, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.TIKTOK_PACKAGE),
            signal(20, UsageSignal.Kind.KEYGUARD_SHOWN),
            signal(80, UsageSignal.Kind.KEYGUARD_HIDDEN),
        )
        assertEquals(40L, result.tiktokMs)
    }

    @Test
    fun unrelatedForegroundAppIsNotCounted() {
        val result = calculate(
            signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
            signal(0, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
            signal(25, UsageSignal.Kind.APP_FOREGROUND, "com.example.other"),
        )
        assertEquals(25L, result.instagramMs)
        assertEquals(0L, result.tiktokMs)
    }

    @Test
    fun requestedWindowClipsAnAlreadyOpenForegroundSpan() {
        val result = DailyTimeCalculator.calculate(
            events = listOf(
                signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
                signal(0, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
            ),
            windowStartMs = 40,
            nowMs = 100,
        )
        assertEquals(60L, result.instagramMs)
    }

    @Test
    fun backgroundAtNowClosesSpanWithoutCountingAfterBoundary() {
        val result = calculate(
            signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
            signal(0, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.TIKTOK_PACKAGE),
            signal(100, UsageSignal.Kind.APP_BACKGROUND, DailyTimeCalculator.TIKTOK_PACKAGE),
        )
        assertEquals(100L, result.tiktokMs)
    }

    @Test
    fun repeatedCalculationAfterProcessRestartIsDeterministic() {
        val events = listOf(
            signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
            signal(0, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
            signal(50, UsageSignal.Kind.APP_BACKGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
            signal(50, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.TIKTOK_PACKAGE),
        )
        assertEquals(
            UsageTotals(instagramMs = 50, tiktokMs = 50),
            DailyTimeCalculator.calculate(events, 0, 100),
        )
        assertEquals(UsageTotals(instagramMs = 50, tiktokMs = 50), DailyTimeCalculator.calculate(events, 0, 100))
    }

    @Test
    fun activeAppBeforeMidnightIsClippedToDayStartAndCountedOnce() {
        val dayStart = 86_400_000L
        val result = DailyTimeCalculator.calculate(
            events = listOf(
                signal(dayStart - 5_000, UsageSignal.Kind.SCREEN_INTERACTIVE),
                signal(dayStart - 5_000, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
            ),
            windowStartMs = dayStart,
            nowMs = dayStart + 12_000,
        )
        assertEquals(12_000L, result.instagramMs)
    }

    @Test
    fun appOpenAt235959CountsOnlyThePostMidnightSecond() {
        val midnight = 86_400_000L
        val result = DailyTimeCalculator.calculate(
            events = listOf(
                signal(midnight - 1_000, UsageSignal.Kind.SCREEN_INTERACTIVE),
                signal(midnight - 1_000, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE, "Feed"),
            ),
            windowStartMs = midnight,
            nowMs = midnight + 1_000,
        )
        assertEquals(1_000L, result.instagramMs)
        assertEquals(0L, result.tiktokMs)
    }

    @Test
    fun duplicateAndOutOfOrderEventsKeepExactDuration() {
        val result = calculate(
            signal(70, UsageSignal.Kind.APP_BACKGROUND, DailyTimeCalculator.TIKTOK_PACKAGE),
            signal(30, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.TIKTOK_PACKAGE),
            signal(30, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.TIKTOK_PACKAGE),
            signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
            signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
        )
        assertEquals(40L, result.tiktokMs)
    }

    @Test
    fun elapsedTimeUsesActualDayBoundaryAcrossDaylightSavingChange() {
        val zone = java.time.ZoneId.of("America/Sao_Paulo")
        val start = java.time.LocalDate.of(2018, 11, 4).atStartOfDay(zone).toInstant().toEpochMilli()
        val next = java.time.LocalDate.of(2018, 11, 5).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(23L * 60 * 60 * 1000, next - start)
        val result = DailyTimeCalculator.calculate(
            listOf(
                signal(start, UsageSignal.Kind.SCREEN_INTERACTIVE),
                signal(start, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.TIKTOK_PACKAGE),
            ),
            start,
            next,
        )
        assertEquals(next - start, result.tiktokMs)
    }

    @Test
    fun unavailableUsageDoesNotBecomeZeroOrAllowPause() {
        val budget = BudgetEvaluator.evaluate(0, 60, dataAvailable = false)
        assertEquals(BudgetLevel.UNAVAILABLE, budget.level)
        assertEquals(false, budget.pauseAllowed)
    }

    @Test
    fun revokedUsagePermissionProducesUnavailableStateAndCannotPause() {
        assertFalse(MonitoringGate.canInspectEvents(consent = true, enabled = true, usageAccess = false))
        val budget = BudgetEvaluator.evaluate(0, 60, dataAvailable = false)
        assertEquals(BudgetLevel.UNAVAILABLE, budget.level)
        assertFalse(budget.pauseAllowed)
    }

    @Test
    fun queryLookbackStartsBeforeLocalDayButNeverBeforeEpoch() {
        val dayStart = 100_000_000L
        assertEquals(dayStart - 6 * 60 * 60 * 1000L, UsageWindow.queryStartMs(dayStart))
        assertEquals(0L, UsageWindow.queryStartMs(1_000L))
    }

    @Test
    fun invalidAndEmptyTimeWindowsReturnZeroWithoutInventingAnActiveApp() {
        assertEquals(UsageTotals(), DailyTimeCalculator.calculate(emptyList(), 0, 100))
        assertEquals(
            UsageTotals(),
            DailyTimeCalculator.calculate(
                listOf(signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE)),
                windowStartMs = 100,
                nowMs = 100,
            ),
        )
    }

    private fun calculate(vararg events: UsageSignal): UsageTotals =
        DailyTimeCalculator.calculate(events.toList(), windowStartMs = 0, nowMs = 100)

    private fun signal(
        timestamp: Long,
        kind: UsageSignal.Kind,
        packageName: String? = null,
        activityName: String? = null,
    ) = UsageSignal(timestamp, kind, packageName, activityName)
}

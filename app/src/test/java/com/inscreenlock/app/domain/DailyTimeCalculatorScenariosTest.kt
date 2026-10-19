package com.inscreenlock.app.domain

import org.junit.Assert.assertEquals
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
            DailyTimeCalculator.calculate(events, 0, 100),
            DailyTimeCalculator.calculate(events, 0, 100),
        )
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
    ) = UsageSignal(timestamp, kind, packageName)
}

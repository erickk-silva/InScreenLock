package com.inscreenlock.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyTimeCalculatorTest {
    @Test
    fun countsOnlyInteractiveTargetForegroundAndSplitsByApp() {
        val totals = DailyTimeCalculator.calculate(
            events = listOf(
                UsageSignal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
                UsageSignal(0, UsageSignal.Kind.APP_FOREGROUND, "com.instagram.android"),
                UsageSignal(10_000, UsageSignal.Kind.APP_BACKGROUND, "com.instagram.android"),
                UsageSignal(10_000, UsageSignal.Kind.APP_FOREGROUND, "com.zhiliaoapp.musically"),
                UsageSignal(15_000, UsageSignal.Kind.SCREEN_NON_INTERACTIVE),
                UsageSignal(20_000, UsageSignal.Kind.SCREEN_INTERACTIVE),
                UsageSignal(20_000, UsageSignal.Kind.APP_FOREGROUND, "com.zhiliaoapp.musically"),
                UsageSignal(30_000, UsageSignal.Kind.APP_BACKGROUND, "com.zhiliaoapp.musically"),
            ),
            windowStartMs = 0,
            nowMs = 40_000,
        )

        assertEquals(10_000L, totals.instagramMs)
        assertEquals(15_000L, totals.tiktokMs)
        assertEquals(25_000L, totals.totalMs)
    }
}

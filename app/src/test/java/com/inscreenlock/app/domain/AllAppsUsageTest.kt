package com.inscreenlock.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AllAppsUsageTest {
    @Test
    fun accumulatesAlternatingPackagesWithoutDoubleCountingAndExcludesSystemPackages() {
        val instagram = DailyTimeCalculator.INSTAGRAM_PACKAGE
        val tiktok = DailyTimeCalculator.TIKTOK_PACKAGE
        val events = listOf(
            signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
            signal(0, UsageSignal.Kind.APP_FOREGROUND, instagram, "Feed"),
            signal(20, UsageSignal.Kind.APP_FOREGROUND, tiktok, "Feed"),
            signal(45, UsageSignal.Kind.APP_FOREGROUND, "com.example.reader", "Main"),
            signal(60, UsageSignal.Kind.APP_FOREGROUND, "com.android.systemui", "Shade"),
            signal(75, UsageSignal.Kind.APP_FOREGROUND, "com.example.launcher", "Home"),
            signal(85, UsageSignal.Kind.APP_FOREGROUND, "com.inscreenlock.app", "Main"),
        )

        assertEquals(
            mapOf(instagram to 20L, tiktok to 25L, "com.example.reader" to 15L),
            DailyTimeCalculator.calculateByPackage(
                events,
                windowStartMs = 0,
                nowMs = 100,
                excludedPackages = setOf("com.inscreenlock.app", "com.android.systemui", "com.example.launcher"),
            ),
        )
    }

    @Test
    fun instagramAndTikTokMatchTheBudgetCalculatorExactly() {
        val events = listOf(
            signal(0, UsageSignal.Kind.SCREEN_INTERACTIVE),
            signal(0, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.INSTAGRAM_PACKAGE),
            signal(25, UsageSignal.Kind.APP_FOREGROUND, "com.example.mail"),
            signal(40, UsageSignal.Kind.APP_FOREGROUND, DailyTimeCalculator.TIKTOK_PACKAGE),
        )
        val budgetTotals = DailyTimeCalculator.calculate(events, 0, 100)
        val allPackages = DailyTimeCalculator.calculateByPackage(events, 0, 100)

        assertEquals(budgetTotals.instagramMs, allPackages[DailyTimeCalculator.INSTAGRAM_PACKAGE])
        assertEquals(budgetTotals.tiktokMs, allPackages[DailyTimeCalculator.TIKTOK_PACKAGE])
    }

    @Test
    fun ranksPositiveUsageByDurationAndAppliesLimitAfterExclusions() {
        val usage = mapOf(
            "com.example.a" to 120L,
            "com.example.b" to 500L,
            "com.example.c" to 300L,
            "com.android.systemui" to 900L,
            "com.example.zero" to 0L,
        )

        assertEquals(
            listOf("com.example.b" to 500L, "com.example.c" to 300L),
            rankUsageByPackage(usage, excludedPackages = setOf("com.android.systemui"), limit = 2),
        )
    }

    @Test
    fun formatsMinutesAndHoursForAccessibleRows() {
        assertEquals("0 min", formatUsageDuration(0))
        assertEquals("12 min", formatUsageDuration(12 * 60_000L))
        assertEquals("1 h 12 min", formatUsageDuration(72 * 60_000L))
    }

    private fun signal(timestamp: Long, kind: UsageSignal.Kind, packageName: String? = null, activity: String? = null) =
        UsageSignal(timestamp, kind, packageName, activity)
}

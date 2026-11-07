package com.inscreenlock.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentForegroundPackageTest {
    private val now = 10 * 60 * 60 * 1000L
    private val target = DailyTimeCalculator.INSTAGRAM_PACKAGE

    private fun store(vararg signals: UsageSignal): (Long, Long) -> List<UsageSignal> =
        { start, end -> signals.filter { it.timestampMs in start..end } }

    @Test fun shortLookbackMissesTargetResumedMoreThanFifteenMinutesAgo() {
        val query = store(UsageSignal(now - 40 * 60_000L, UsageSignal.Kind.APP_FOREGROUND, target))
        assertNull(currentForegroundPackage(now, expandLookback = false, query = query))
    }

    @Test fun expandedLookbackFindsTargetOfLongSessionWithoutRecentTransitions() {
        val query = store(
            UsageSignal(now - 3 * 60 * 60_000L, UsageSignal.Kind.APP_FOREGROUND, "com.example.other"),
            UsageSignal(now - 2 * 60 * 60_000L, UsageSignal.Kind.APP_BACKGROUND, "com.example.other"),
            UsageSignal(now - 40 * 60_000L, UsageSignal.Kind.APP_FOREGROUND, target),
        )
        assertEquals(target, currentForegroundPackage(now, expandLookback = true, query = query))
    }

    @Test fun shortLookbackStillResolvesRecentTransition() {
        val query = store(UsageSignal(now - 60_000L, UsageSignal.Kind.APP_FOREGROUND, target))
        assertEquals(target, currentForegroundPackage(now, expandLookback = false, query = query))
    }

    @Test fun expandedLookbackWithNoEventsAtAllIsUnresolved() {
        assertNull(currentForegroundPackage(now, expandLookback = true, query = store()))
    }
}

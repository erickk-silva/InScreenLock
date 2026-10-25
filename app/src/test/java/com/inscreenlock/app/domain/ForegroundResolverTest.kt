package com.inscreenlock.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundResolverTest {
    private val instagram = DailyTimeCalculator.INSTAGRAM_PACKAGE

    @Test fun imeWindowKeepsActiveTargetAndMarksWindowTransient() {
        assertEquals(
            WindowForegroundResolution(instagram, transientWindow = true),
            ForegroundResolver.resolveWindowEvent("com.example.ime", setOf("com.example.ime"), instagram),
        )
    }

    @Test fun systemUiWindowKeepsActiveTargetAndMarksWindowTransient() {
        assertEquals(
            WindowForegroundResolution(instagram, transientWindow = true),
            ForegroundResolver.resolveWindowEvent("com.android.systemui", setOf("com.android.systemui"), instagram),
        )
    }

    @Test fun anotherAppClearsTargetImmediately() {
        assertEquals(
            WindowForegroundResolution(null, transientWindow = false),
            ForegroundResolver.resolveWindowEvent("com.example.other", setOf("com.android.systemui"), instagram),
        )
    }

    @Test fun ownAppWindowClearsTargetSoUnlockActivityIsVisible() {
        assertEquals(
            WindowForegroundResolution(null, transientWindow = false),
            ForegroundResolver.resolveWindowEvent("com.inscreenlock.app", emptySet(), instagram),
        )
    }

    @Test fun usageStatsTargetRequestsOverlayReconfirmation() {
        assertTrue(ForegroundResolver.resolveUsageStats(instagram, instagram, emptySet())!!)
    }

    @Test fun usageStatsOtherAppRequestsTargetExit() {
        assertFalse(ForegroundResolver.resolveUsageStats(instagram, "com.example.other", emptySet())!!)
    }

    @Test fun usageStatsTransientOrMissingDoesNotInventAnExit() {
        assertNull(ForegroundResolver.resolveUsageStats(instagram, "com.example.ime", setOf("com.example.ime")))
        assertNull(ForegroundResolver.resolveUsageStats(instagram, null, emptySet()))
    }
}

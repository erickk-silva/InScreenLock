package com.inscreenlock.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentForegroundPackageTest {
    @Test fun latestForegroundIsClearedByBackgroundOfSamePackage() {
        val signals = listOf(
            UsageSignal(1, UsageSignal.Kind.APP_FOREGROUND, "com.example.app"),
            UsageSignal(2, UsageSignal.Kind.APP_BACKGROUND, "com.example.app"),
        )
        assertNull(recentForegroundPackage(signals))
    }

    @Test fun latestForegroundPackageIsSelected() {
        val signals = listOf(
            UsageSignal(1, UsageSignal.Kind.APP_FOREGROUND, "com.example.one"),
            UsageSignal(2, UsageSignal.Kind.APP_BACKGROUND, "com.example.one"),
            UsageSignal(3, UsageSignal.Kind.APP_FOREGROUND, "com.example.two"),
        )
        assertEquals("com.example.two", recentForegroundPackage(signals))
    }

    @Test fun emptySignalsHaveNoKnownForegroundPackage() {
        assertNull(recentForegroundPackage(emptyList()))
    }
}

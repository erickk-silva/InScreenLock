package com.inscreenlock.app.monitoring

import com.inscreenlock.app.domain.DailyTimeCalculator
import com.inscreenlock.app.domain.UsageSignal
import com.inscreenlock.app.domain.currentForegroundPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlaySessionControllerTest {
    private val host = FakeOverlayHost()
    private val controller = OverlaySessionController(host)
    private val instagram = DailyTimeCalculator.INSTAGRAM_PACKAGE

    private val other = "com.example.other"
    private val systemUi = setOf("com.android.systemui")

    @Test fun foreignWindowHidesOverlayButKeepsSessionUntilUsageReconfirmation() {
        controller.onWindowEvent(instagram, emptySet())
        controller.presentOverlay { true }
        val resolution = controller.onWindowEvent(other, systemUi)
        assertFalse(host.visible)
        assertEquals(instagram, controller.targetPackage)
        assertTrue(controller.loopRunning)
        assertEquals(true, resolution?.transientWindow)
        controller.presentOverlay { true }
        assertFalse(host.visible)
    }

    @Test fun foreignWindowFollowedByUsageStatsOnTargetResumesOverlay() {
        controller.onWindowEvent(instagram, emptySet())
        controller.presentOverlay { true }
        controller.onWindowEvent(other, systemUi)
        assertEquals(true, controller.reconfirmUsage(instagram, systemUi))
        assertEquals(instagram, controller.targetPackage)
        assertTrue(controller.loopRunning)
        controller.presentOverlay { true }
        assertTrue(host.visible)
    }

    @Test fun foreignReconfirmationIsPendingOnlyBetweenForeignEventAndReconfirmation() {
        assertFalse(controller.foreignReconfirmationPending)
        controller.onWindowEvent(instagram, emptySet())
        assertFalse(controller.foreignReconfirmationPending)
        controller.onWindowEvent(other, systemUi)
        assertTrue(controller.foreignReconfirmationPending)
        controller.reconfirmUsage(instagram, systemUi)
        assertFalse(controller.foreignReconfirmationPending)
    }

    @Test fun longSessionSurvivesForeignEventWhenExpandedLookbackResolvesTarget() {
        controller.onWindowEvent(instagram, emptySet())
        controller.onWindowEvent(other, systemUi)
        assertTrue(controller.foreignReconfirmationPending)
        val now = 10 * 60 * 60 * 1000L
        val query = { start: Long, end: Long ->
            listOf(UsageSignal(now - 40 * 60_000L, UsageSignal.Kind.APP_FOREGROUND, instagram))
                .filter { it.timestampMs in start..end }
        }
        val observed = currentForegroundPackage(now, expandLookback = controller.foreignReconfirmationPending, query = query)
        assertEquals(true, controller.reconfirmUsage(observed, systemUi))
        assertEquals(instagram, controller.targetPackage)
        assertTrue(controller.loopRunning)
    }

    @Test fun foreignWindowFollowedByUsageStatsOnAnotherAppEndsSession() {
        controller.onWindowEvent(instagram, emptySet())
        controller.presentOverlay { true }
        controller.onWindowEvent(other, systemUi)
        assertEquals(false, controller.reconfirmUsage("com.example.third", systemUi))
        assertFalse(host.visible)
        assertFalse(controller.loopRunning)
        assertNull(controller.targetPackage)
    }

    @Test fun foreignWindowFollowedByMissingUsageStatsEndsSessionWithoutShowingLock() {
        controller.onWindowEvent(instagram, emptySet())
        controller.presentOverlay { true }
        controller.onWindowEvent(other, systemUi)
        assertEquals(false, controller.reconfirmUsage(null, systemUi))
        assertFalse(controller.loopRunning)
        assertNull(controller.targetPackage)
        controller.presentOverlay { true }
        assertFalse(host.visible)
    }

    @Test fun foreignWindowFollowedByTransientUsageStatsEndsSession() {
        controller.onWindowEvent(instagram, emptySet())
        controller.onWindowEvent(other, systemUi)
        assertEquals(false, controller.reconfirmUsage("com.android.systemui", systemUi))
        assertNull(controller.targetPackage)
    }

    @Test fun targetEventAfterForeignWindowConfirmsSessionAndMissingUsageStatsKeepsTransientSemantics() {
        controller.onWindowEvent(instagram, emptySet())
        controller.onWindowEvent(other, systemUi)
        controller.onWindowEvent(instagram, systemUi)
        assertEquals(instagram, controller.targetPackage)
        assertNull(controller.reconfirmUsage(null, systemUi))
        assertEquals(instagram, controller.targetPackage)
    }

    @Test fun foreignWindowWithoutActiveSessionStaysIdle() {
        val resolution = controller.onWindowEvent(other, systemUi)
        assertNull(resolution?.targetPackage)
        assertNull(controller.targetPackage)
        assertFalse(controller.loopRunning)
    }

    @Test fun transientWindowHidesOverlayAndKeepsTargetUntilReconfirmation() {
        controller.onWindowEvent(instagram, emptySet())
        controller.presentOverlay { true }
        controller.onWindowEvent("com.android.systemui", setOf("com.android.systemui"))
        assertFalse(host.visible)
        assertEquals(instagram, controller.targetPackage)
        assertTrue(controller.loopRunning)
    }

    @Test fun ownActivityWindowRemovesOverlayAndStopsRefreshLoop() {
        controller.onWindowEvent(instagram, emptySet())
        controller.presentOverlay { true }
        controller.onWindowEvent(
            eventPackage = "com.inscreenlock.app",
            transientPackages = emptySet(),
            ownPackageName = "com.inscreenlock.app",
            eventClassName = "com.inscreenlock.app.MainActivity",
            ownActivityClassNames = setOf("com.inscreenlock.app.MainActivity", "com.inscreenlock.app.UnlockActivity"),
        )
        assertFalse(host.visible)
        assertFalse(controller.loopRunning)
        assertNull(controller.targetPackage)
    }

    @Test fun ownViewWindowEventKeepsOverlayAndTargetSession() {
        controller.onWindowEvent(instagram, emptySet())
        controller.presentOverlay { true }

        val ignoredResolution = controller.onWindowEvent(
            eventPackage = "com.inscreenlock.app",
            transientPackages = emptySet(),
            ownPackageName = "com.inscreenlock.app",
            eventClassName = "android.widget.TextView",
            ownActivityClassNames = setOf("com.inscreenlock.app.MainActivity", "com.inscreenlock.app.UnlockActivity"),
        )

        assertNull(ignoredResolution)
        assertEquals(instagram, controller.targetPackage)
        assertTrue(controller.loopRunning)
        assertTrue(host.visible)
    }

    @Test fun usageStatsReconfirmationWithOtherAppEndsTargetSession() {
        controller.onWindowEvent(instagram, emptySet())
        controller.presentOverlay { true }
        controller.reconfirmUsage("com.example.other", emptySet())
        assertFalse(host.visible)
        assertFalse(controller.loopRunning)
        assertNull(controller.targetPackage)
    }

    @Test fun reconnectResumesTargetOnlyWithConsentUsageAndInteractiveUnlockedScreen() {
        assertTrue(controller.resumeFromReconnection(instagram, monitoringAllowed = true, screenInteractive = true, keyguardVisible = false))
        assertEquals(instagram, controller.targetPackage)
        assertTrue(controller.loopRunning)
        controller.presentOverlay { true }
        assertTrue(host.visible)

        val blockedScenarios = listOf(
            Triple(false, true, false),
            Triple(true, false, false),
            Triple(true, true, true),
        )
        blockedScenarios.forEach { (allowed, interactive, keyguard) ->
            val blocked = OverlaySessionController(FakeOverlayHost())
            assertFalse(blocked.resumeFromReconnection(instagram, allowed, interactive, keyguard))
            assertNull(blocked.targetPackage)
            assertFalse(blocked.loopRunning)
        }
    }

    @Test fun reconnectIgnoresTransientUnknownAndNullPackagesAndRunsOnce() {
        for (packageName in listOf("com.android.systemui", "com.example.other", null)) {
            val fresh = OverlaySessionController(FakeOverlayHost())
            assertFalse(fresh.resumeFromReconnection(packageName, monitoringAllowed = true, screenInteractive = true, keyguardVisible = false))
            assertNull(fresh.targetPackage)
            assertFalse(fresh.loopRunning)
        }

        val fresh = OverlaySessionController(FakeOverlayHost())
        assertTrue(fresh.resumeFromReconnection(instagram, true, true, false))
        assertFalse(fresh.resumeFromReconnection(DailyTimeCalculator.TIKTOK_PACKAGE, true, true, false))
        assertEquals(instagram, fresh.targetPackage)
    }

    @Test fun reconnectDoesNotOverwriteSessionStartedByWindowEvent() {
        controller.onWindowEvent(DailyTimeCalculator.TIKTOK_PACKAGE, emptySet())

        assertFalse(controller.resumeFromReconnection(instagram, monitoringAllowed = true, screenInteractive = true, keyguardVisible = false))
        assertEquals(DailyTimeCalculator.TIKTOK_PACKAGE, controller.targetPackage)
        assertTrue(controller.loopRunning)
    }

    private class FakeOverlayHost : OverlaySessionHost {
        var visible = false
        override fun show() { visible = true }
        override fun remove() { visible = false }
    }
}

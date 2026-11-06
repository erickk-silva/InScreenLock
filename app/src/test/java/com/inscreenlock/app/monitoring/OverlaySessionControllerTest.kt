package com.inscreenlock.app.monitoring

import com.inscreenlock.app.domain.DailyTimeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlaySessionControllerTest {
    private val host = FakeOverlayHost()
    private val controller = OverlaySessionController(host)
    private val instagram = DailyTimeCalculator.INSTAGRAM_PACKAGE

    @Test fun unrelatedWindowRemovesOverlayAndStopsRefreshLoop() {
        controller.onWindowEvent(instagram, emptySet())
        controller.presentOverlay { true }
        controller.onWindowEvent("com.example.other", emptySet())
        assertFalse(host.visible)
        assertFalse(controller.loopRunning)
        assertNull(controller.targetPackage)
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

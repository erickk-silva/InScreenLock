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

    @Test fun ownAppWindowRemovesOverlayAndStopsRefreshLoop() {
        controller.onWindowEvent(instagram, emptySet())
        controller.presentOverlay { true }
        controller.onWindowEvent("com.inscreenlock.app", emptySet())
        assertFalse(host.visible)
        assertFalse(controller.loopRunning)
        assertNull(controller.targetPackage)
    }

    @Test fun usageStatsReconfirmationWithOtherAppEndsTargetSession() {
        controller.onWindowEvent(instagram, emptySet())
        controller.presentOverlay { true }
        controller.reconfirmUsage("com.example.other", emptySet())
        assertFalse(host.visible)
        assertFalse(controller.loopRunning)
        assertNull(controller.targetPackage)
    }

    private class FakeOverlayHost : OverlaySessionHost {
        var visible = false
        override fun show() { visible = true }
        override fun remove() { visible = false }
    }
}

package com.inscreenlock.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockDeadlineTest {
    @Test fun sameBootUsesMonotonicDeadlineDespiteWallClockChanges() {
        val deadline = StoredDeadline(wallDeadlineMs = 10_000, elapsedDeadlineMs = 10_000, bootCount = 4)
        assertEquals(5_000L, deadline.remainingMs(ClockReading(20_000, 5_000, 4)))
        assertEquals(5_000L, deadline.remainingMs(ClockReading(1_000, 5_000, 4)))
    }

    @Test fun afterRebootUsesRemainingCivilDeadline() {
        val deadline = StoredDeadline(wallDeadlineMs = 10_000, elapsedDeadlineMs = 10_000, bootCount = 4)
        assertEquals(10_000L, deadline.remainingMs(ClockReading(7_000, 200, 5)))
    }

    @Test fun unknownBootCountFallsBackToCivilDeadline() {
        val deadline = StoredDeadline(wallDeadlineMs = 10_000, elapsedDeadlineMs = 10_000, bootCount = -1)
        assertEquals(3_000L, deadline.remainingMs(ClockReading(7_000, 5_000, -1)))
        assertEquals(0L, deadline.remainingMs(ClockReading(10_000, 8_000, -1)))
    }

    @Test fun rebootUsesMaximumThrottleAndConservativeUnlockRemainder() {
        val deadline = StoredDeadline(
            wallDeadlineMs = 10_000,
            elapsedDeadlineMs = 10_000,
            bootCount = 4,
            durationMs = 10_000,
        )
        val afterForwardClockChangeAndReboot = ClockReading(20_000, 200, 5)
        assertEquals(10_000L, deadline.remainingMs(afterForwardClockChangeAndReboot))
        assertEquals(0L, deadline.remainingUnlockMs(afterForwardClockChangeAndReboot))
        val afterBackwardClockChangeAndReboot = ClockReading(1_000, 200, 5)
        assertEquals(9_000L, deadline.remainingUnlockMs(afterBackwardClockChangeAndReboot))
    }
}

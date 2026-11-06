package com.inscreenlock.app.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefreshDisplayPolicyTest {
    @Test fun blockedDecisionUsesShortRefreshIntervalAndOtherStatesKeepNormalInterval() {
        assertEquals(2_000L, RefreshDisplayPolicy.refreshIntervalMs(lastDecisionShouldLock = true))
        assertEquals(10_000L, RefreshDisplayPolicy.refreshIntervalMs(lastDecisionShouldLock = false))
    }

    @Test fun activeUnlockBadgeShowsLocalDeadlineAndRejectsExpiredGrant() {
        val clock = ClockReading(
            wallTimeMs = Instant.parse("2026-11-06T03:35:50Z").toEpochMilli(),
            elapsedRealtimeMs = 100_000L,
            bootCount = 7L,
        )
        val deadline = StoredDeadline.after(15 * 60_000L, clock)

        assertEquals(
            "Desbloqueado até 00:50",
            RefreshDisplayPolicy.activeUnlockBadgeText(
                deadline = deadline,
                unlockedDay = "2026-11-06",
                currentDay = "2026-11-06",
                clockReading = clock,
                zoneId = ZoneId.of("America/Sao_Paulo"),
            ),
        )
        val expiredClock = clock.copy(
            wallTimeMs = clock.wallTimeMs + 15 * 60_000L,
            elapsedRealtimeMs = clock.elapsedRealtimeMs + 15 * 60_000L,
        )
        assertNull(
            RefreshDisplayPolicy.activeUnlockBadgeText(
                deadline = deadline,
                unlockedDay = "2026-11-06",
                currentDay = "2026-11-06",
                clockReading = expiredClock,
                zoneId = ZoneId.of("America/Sao_Paulo"),
            ),
        )
    }
}

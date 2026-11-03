package com.inscreenlock.app.data

import android.content.Context
import android.content.ContextWrapper
import com.inscreenlock.app.domain.ClockReading
import com.inscreenlock.app.domain.LockPolicy
import com.inscreenlock.app.domain.ReactionMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryClockTest {
    @Test fun timezoneChangeClearsUnlockGrantEvenWhenLocalDateIsUnchanged() = runBlocking {
        val day = LocalDate.of(2026, 11, 2)
        val now = ClockReading(
            wallTimeMs = Instant.parse("2026-11-02T18:00:00Z").toEpochMilli(),
            elapsedRealtimeMs = 10_000,
            bootCount = 3,
        )
        val directory = Files.createTempDirectory("inscreenlock-zone-clock-test").toFile()
        val repository = SettingsRepository(TestContext(directory)) { now }
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            repository.grantUnlock(day, now)
            val grant = repository.settings.first()
            assertTrue(grant.unlockedUntilMs > now.wallTimeMs)
            assertFalse(repository.isUnlockGrantCurrent(day.toString(), null, day, "America/New_York"))

            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            repository.clearExpiredUnlock(day, now)

            val cleared = repository.settings.first()
            assertEquals(0L, cleared.unlockedUntilMs)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test fun pinThrottleAndUnlockUseInjectedMonotonicClock() = runBlocking {
        var now = ClockReading(wallTimeMs = 1_000_000, elapsedRealtimeMs = 10_000, bootCount = 3)
        val directory = Files.createTempDirectory("inscreenlock-clock-test").toFile()
        val repository = SettingsRepository(TestContext(directory)) { now }
        repository.setPin("1234")

        repeat(4) { assertEquals(PinAttempt.Rejected, repository.verifyPin("0000")) }
        val retry = repository.verifyPin("0000")
        assertTrue(retry is PinAttempt.Locked)
        now = now.copy(wallTimeMs = now.wallTimeMs + 60_000, elapsedRealtimeMs = now.elapsedRealtimeMs + 1_000)
        assertTrue(repository.verifyPin("1234") is PinAttempt.Locked)
        now = now.copy(wallTimeMs = now.wallTimeMs - 120_000, elapsedRealtimeMs = now.elapsedRealtimeMs + 30_000)
        assertEquals(PinAttempt.Accepted, repository.verifyPin("1234"))

        repeat(5) { repository.verifyPin("0000") }
        val oldBootRetry = repository.verifyPin("1234") as PinAttempt.Locked
        now = now.copy(bootCount = 4, wallTimeMs = oldBootRetry.retryAtMs + 60_000, elapsedRealtimeMs = 100)
        assertTrue(repository.verifyPin("1234") is PinAttempt.Locked)
        now = now.copy(elapsedRealtimeMs = now.elapsedRealtimeMs + 30_000)
        assertEquals(PinAttempt.Accepted, repository.verifyPin("1234"))

        val day = LocalDate.of(2026, 10, 25)
        repository.grantUnlock(day)
        val grant = repository.settings.first()
        assertFalse(shouldBlock(grant, day, now))
        now = now.copy(
            wallTimeMs = now.wallTimeMs + 60 * 60_000,
            elapsedRealtimeMs = now.elapsedRealtimeMs + 60_000,
        )
        assertFalse(shouldBlock(grant, day, now))
        now = now.copy(bootCount = 5, wallTimeMs = grant.unlockedUntilMs)
        assertTrue(shouldBlock(grant, day, now))
    }

    private fun shouldBlock(settings: UserSettings, day: LocalDate, now: ClockReading) = LockPolicy.shouldBlock(
        mode = ReactionMode.LOCK,
        dataAvailable = true,
        credentialAvailable = true,
        monitoringEnabled = true,
        usedMs = 31 * 60_000L,
        limitMs = 30 * 60_000L,
        unlockedUntilMs = settings.unlockedUntilMs,
        nowMs = now.wallTimeMs,
        unlockedDay = settings.unlockedDay,
        currentDay = day.toString(),
        unlockedElapsedUntilMs = settings.unlockedElapsedUntilMs,
        unlockedBootCount = settings.unlockedBootCount,
        clockReading = now,
        unlockedDurationMs = settings.unlockedDurationMs,
    )

    private class TestContext(private val directory: java.io.File) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): java.io.File = directory
    }
}

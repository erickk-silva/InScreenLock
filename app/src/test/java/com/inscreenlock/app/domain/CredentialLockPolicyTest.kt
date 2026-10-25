package com.inscreenlock.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialLockPolicyTest {
    @Test fun lockRequiresExceededAvailableDataAndCredentialOutsideUnlockWindow() {
        val today = "2026-10-25"
        val block = { mode: ReactionMode, data: Boolean, credential: Boolean, used: Long, until: Long ->
            LockPolicy.shouldBlock(mode, data, credential, true, used, 100L, until, 100L, today, today)
        }
        assertTrue(block(ReactionMode.LOCK, true, true, 101L, 0L))
        assertTrue(block(ReactionMode.LOCK, true, true, 100L, 0L))
        assertFalse(block(ReactionMode.LOCK, true, true, 101L, 101L))
        assertFalse(block(ReactionMode.WARNING, true, true, 101L, 0L))
        assertFalse(block(ReactionMode.LOCK, false, true, 101L, 0L))
        assertFalse(block(ReactionMode.LOCK, true, false, 101L, 0L))
    }

    @Test fun unlockWindowHasExclusiveExpirationAndEndsAtLocalDayBoundary() {
        val start = 10_000L
        val expiresAt = UnlockWindow.expiresAt(start)
        assertTrue(UnlockWindow.isActive(expiresAt, expiresAt - 1, "2026-10-25", "2026-10-25"))
        assertFalse(UnlockWindow.isActive(expiresAt, expiresAt, "2026-10-25", "2026-10-25"))
        assertFalse(UnlockWindow.isActive(expiresAt, expiresAt - 1, "2026-10-25", "2026-10-26"))
    }

    @Test fun protectedChangesRequireAuthenticationOnlyWhenLessRestrictive() {
        assertTrue(ProtectedChange.requiresAuthentication(ProtectedChange.DisableMonitoring))
        assertTrue(ProtectedChange.requiresAuthentication(ProtectedChange.IncreaseLimit))
        assertTrue(ProtectedChange.requiresAuthentication(ProtectedChange.LockToWarning))
        assertTrue(ProtectedChange.requiresAuthentication(ProtectedChange.ClearData))
        assertTrue(ProtectedChange.requiresAuthentication(ProtectedChange.ChangeOrRemovePin))
        assertFalse(ProtectedChange.requiresAuthentication(ProtectedChange.ReduceLimit))
        assertFalse(ProtectedChange.requiresAuthentication(ProtectedChange.WarningToLock))
        assertFalse(ProtectedChange.requiresAuthentication(ProtectedChange.EnableMonitoring))
        assertFalse(ProtectedChange.requiresAuthentication(ProtectedChange.SetFirstPin))
    }

    @Test fun protectedChangesRequireAuthenticationOnlyWhenCredentialIsAvailable() {
        assertFalse(ProtectedChange.requiresAuthentication(ProtectedChange.DisableMonitoring, credentialAvailable = false))
        assertFalse(ProtectedChange.requiresAuthentication(ProtectedChange.IncreaseLimit, credentialAvailable = false))
        assertFalse(ProtectedChange.requiresAuthentication(ProtectedChange.LockToWarning, credentialAvailable = false))
        assertFalse(ProtectedChange.requiresAuthentication(ProtectedChange.ClearData, credentialAvailable = false))
        assertFalse(ProtectedChange.requiresAuthentication(ProtectedChange.ChangeOrRemovePin, credentialAvailable = false))
        assertTrue(ProtectedChange.requiresAuthentication(ProtectedChange.DisableMonitoring, credentialAvailable = true))
        assertTrue(ProtectedChange.requiresAuthentication(ProtectedChange.IncreaseLimit, credentialAvailable = true))
        assertTrue(ProtectedChange.requiresAuthentication(ProtectedChange.LockToWarning, credentialAvailable = true))
        assertTrue(ProtectedChange.requiresAuthentication(ProtectedChange.ClearData, credentialAvailable = true))
        assertTrue(ProtectedChange.requiresAuthentication(ProtectedChange.ChangeOrRemovePin, credentialAvailable = true))
    }

    @Test fun pinDerivationAndVerificationAreSaltedAndConstantTimeChecked() {
        val salt = ByteArray(16) { it.toByte() }
        val first = PinCredential.derive("1234", salt)
        val second = PinCredential.derive("1234", salt)
        assertTrue(java.security.MessageDigest.isEqual(first, second))
        assertTrue(PinCredential.verify("1234", salt, first))
        assertFalse(PinCredential.verify("1235", salt, first))
        assertFalse(PinCredential.isValidFormat("123"))
        assertTrue(PinCredential.isValidFormat("1234"))
        assertTrue(PinCredential.isValidFormat("12345678"))
        assertFalse(PinCredential.isValidFormat("123456789"))
    }

    @Test fun pinThrottleStartsAtFifthFailureDoublesAndCapsAtFiveMinutes() {
        assertEquals(0L, PinThrottle.delayAfterFailures(4))
        assertEquals(30_000L, PinThrottle.delayAfterFailures(5))
        assertEquals(60_000L, PinThrottle.delayAfterFailures(6))
        assertEquals(300_000L, PinThrottle.delayAfterFailures(20))
        assertEquals(0L, PinThrottle.delayAfterFailures(0))
    }

    @Test fun legacyPausePreferenceMigratesToLock() {
        assertEquals(ReactionMode.LOCK, ReactionMode.fromStoredValue("PAUSE"))
        assertEquals(ReactionMode.LOCK, ReactionMode.fromStoredValue(null))
        assertEquals(ReactionMode.WARNING, ReactionMode.fromStoredValue("WARNING"))
    }
}

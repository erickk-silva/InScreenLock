package com.inscreenlock.app.domain

import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object LockPolicy {
    fun shouldBlock(
        mode: ReactionMode,
        dataAvailable: Boolean,
        credentialAvailable: Boolean,
        monitoringEnabled: Boolean,
        usedMs: Long,
        limitMs: Long,
        unlockedUntilMs: Long,
        nowMs: Long,
        unlockedDay: String,
        currentDay: String,
    ): Boolean = mode == ReactionMode.LOCK && dataAvailable && credentialAvailable && monitoringEnabled &&
        usedMs >= limitMs && limitMs > 0 && !UnlockWindow.isActive(unlockedUntilMs, nowMs, unlockedDay, currentDay)
}

object UnlockWindow {
    const val DURATION_MS = 15 * 60 * 1000L

    fun isActive(
        unlockedUntilMs: Long,
        nowMs: Long,
        unlockedDay: String,
        currentDay: String,
    ): Boolean = unlockedUntilMs > nowMs && unlockedDay == currentDay

    fun expiresAt(nowMs: Long): Long = nowMs + DURATION_MS
}

enum class ProtectedChange {
    DisableMonitoring,
    IncreaseLimit,
    LockToWarning,
    ClearData,
    ChangeOrRemovePin,
    ReduceLimit,
    WarningToLock,
    EnableMonitoring,
    SetFirstPin,
    NoChange;

    companion object {
        fun requiresAuthentication(change: ProtectedChange): Boolean = change in setOf(
            DisableMonitoring, IncreaseLimit, LockToWarning, ClearData, ChangeOrRemovePin,
        )
    }
}

object PinCredential {
    const val ITERATIONS = 120_000
    const val KEY_BITS = 256
    const val SALT_BYTES = 16

    fun isValidFormat(pin: String): Boolean = pin.length in 4..8 && pin.all { it in '0'..'9' }

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(java.security.SecureRandom()::nextBytes)

    fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(pin: String, salt: ByteArray, expected: ByteArray): Boolean =
        MessageDigest.isEqual(derive(pin, salt), expected)
}

object PinThrottle {
    const val MIN_DELAY_MS = 30_000L
    const val MAX_DELAY_MS = 300_000L

    fun delayAfterFailures(failures: Int): Long = when {
        failures < 5 -> 0L
        else -> (MIN_DELAY_MS * (1L shl (failures - 5).coerceAtMost(30))).coerceAtMost(MAX_DELAY_MS)
    }
}

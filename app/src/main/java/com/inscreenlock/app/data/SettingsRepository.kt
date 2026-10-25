package com.inscreenlock.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.inscreenlock.app.domain.ReactionMode
import com.inscreenlock.app.domain.PinCredential
import com.inscreenlock.app.domain.PinThrottle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.time.LocalDate

private val Context.settingsStore by preferencesDataStore(name = "settings")

data class UserSettings(
    val accessibilityDisclosureAccepted: Boolean = false,
    val monitoringEnabled: Boolean = false,
    val dailyLimitMinutes: Int = 30,
    val reactionMode: ReactionMode = ReactionMode.LOCK,
    val hasPin: Boolean = false,
    val unlockedUntilMs: Long = 0L,
    val unlockedDay: String = "",
)

sealed interface PinAttempt {
    data object Accepted : PinAttempt
    data object Rejected : PinAttempt
    data class Locked(val retryAtMs: Long) : PinAttempt
}

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<UserSettings> = store.data.onEach { values ->
        if (values[REACTION_MODE] == "PAUSE") store.edit { it[REACTION_MODE] = ReactionMode.LOCK.name }
    }.map { values ->
        UserSettings(
            accessibilityDisclosureAccepted = values[ACCESSIBILITY_CONSENT] ?: false,
            monitoringEnabled = values[MONITORING_ENABLED] ?: false,
            dailyLimitMinutes = (values[DAILY_LIMIT_MINUTES] ?: 30).coerceAtLeast(1),
            reactionMode = ReactionMode.fromStoredValue(values[REACTION_MODE]),
            hasPin = values[PIN_SALT] != null && values[PIN_HASH] != null,
            unlockedUntilMs = values[UNLOCKED_UNTIL] ?: 0L,
            unlockedDay = values[UNLOCKED_DAY] ?: "",
        )
    }

    suspend fun acceptAccessibilityDisclosure() {
        store.edit { it[ACCESSIBILITY_CONSENT] = true }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        store.edit { it[MONITORING_ENABLED] = enabled }
    }

    suspend fun updateBudget(minutes: Int, reactionMode: ReactionMode) {
        require(minutes >= 1)
        store.edit {
            it[DAILY_LIMIT_MINUTES] = minutes
            it[REACTION_MODE] = reactionMode.name
        }
    }

    suspend fun setPin(pin: String) {
        require(PinCredential.isValidFormat(pin))
        val salt = PinCredential.newSalt()
        val hash = withContext(Dispatchers.Default) { PinCredential.derive(pin, salt) }
        store.edit {
            it[PIN_SALT] = Base64.getEncoder().encodeToString(salt)
            it[PIN_HASH] = Base64.getEncoder().encodeToString(hash)
            it[PIN_FAILURES] = 0
            it.remove(PIN_RETRY_AT)
        }
    }

    suspend fun removePin(secureDeviceCredential: Boolean) {
        store.edit {
            it.remove(PIN_SALT)
            it.remove(PIN_HASH)
            if (!secureDeviceCredential && it[REACTION_MODE] == ReactionMode.LOCK.name) {
                it[REACTION_MODE] = ReactionMode.WARNING.name
            }
        }
    }

    suspend fun verifyPin(pin: String, nowMs: Long = System.currentTimeMillis()): PinAttempt {
        val snapshot = store.data.first()
        val retryAt = snapshot[PIN_RETRY_AT] ?: 0L
        if (retryAt > nowMs) return PinAttempt.Locked(retryAt)
        val salt = snapshot[PIN_SALT]?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
        val expected = snapshot[PIN_HASH]?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
        val matches = if (PinCredential.isValidFormat(pin) && salt != null && expected != null) {
            withContext(Dispatchers.Default) { PinCredential.verify(pin, salt, expected) }
        } else false
        if (matches) {
            store.edit {
                it[PIN_FAILURES] = 0
                it.remove(PIN_RETRY_AT)
            }
            return PinAttempt.Accepted
        }
        var nextRetry = 0L
        store.edit {
            val failures = (it[PIN_FAILURES] ?: 0) + 1
            it[PIN_FAILURES] = failures
            val delay = PinThrottle.delayAfterFailures(failures)
            if (delay > 0) {
                nextRetry = nowMs + delay
                it[PIN_RETRY_AT] = nextRetry
            }
        }
        return if (nextRetry > nowMs) PinAttempt.Locked(nextRetry) else PinAttempt.Rejected
    }

    suspend fun grantUnlock(untilMs: Long, day: LocalDate) {
        store.edit {
            it[UNLOCKED_UNTIL] = untilMs
            it[UNLOCKED_DAY] = day.toString()
        }
    }

    suspend fun clearExpiredUnlock(nowMs: Long, day: LocalDate) {
        val snapshot = store.data.first()
        if ((snapshot[UNLOCKED_UNTIL] ?: 0L) > nowMs && snapshot[UNLOCKED_DAY] == day.toString()) return
        store.edit { values ->
            if ((values[UNLOCKED_UNTIL] ?: 0L) <= nowMs || values[UNLOCKED_DAY] != day.toString()) {
                values.remove(UNLOCKED_UNTIL)
                values.remove(UNLOCKED_DAY)
            }
        }
    }

    suspend fun claimThresholdWarning(day: LocalDate, limitMinutes: Int): Boolean {
        var claimed = false
        store.edit { values ->
            val sameDay = values[WARNING_DAY] == day.toString()
            val previousMaximum = if (sameDay) {
                maxOf(
                    values[WARNING_MAX_LIMIT_MINUTES] ?: 0,
                    values[WARNING_LIMIT_MINUTES] ?: 0,
                )
            } else {
                0
            }
            if (limitMinutes > previousMaximum) {
                values[WARNING_DAY] = day.toString()
                values[WARNING_MAX_LIMIT_MINUTES] = limitMinutes
                values[WARNING_LIMIT_MINUTES] = limitMinutes
                claimed = true
            }
        }
        return claimed
    }

    suspend fun clearLocalSettings() {
        store.edit { it.clear() }
    }

    private companion object {
        val ACCESSIBILITY_CONSENT = booleanPreferencesKey("accessibility_disclosure_accepted")
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val DAILY_LIMIT_MINUTES = intPreferencesKey("daily_limit_minutes")
        val REACTION_MODE = stringPreferencesKey("reaction_mode")
        val WARNING_DAY = stringPreferencesKey("warning_day")
        val WARNING_LIMIT_MINUTES = intPreferencesKey("warning_limit_minutes")
        val WARNING_MAX_LIMIT_MINUTES = intPreferencesKey("warning_max_limit_minutes")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_FAILURES = intPreferencesKey("pin_failures")
        val PIN_RETRY_AT = longPreferencesKey("pin_retry_at")
        val UNLOCKED_UNTIL = longPreferencesKey("unlocked_until")
        val UNLOCKED_DAY = stringPreferencesKey("unlocked_day")
    }
}

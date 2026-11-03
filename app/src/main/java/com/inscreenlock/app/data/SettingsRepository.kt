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
import com.inscreenlock.app.domain.ClockReading
import com.inscreenlock.app.domain.StoredDeadline
import com.inscreenlock.app.domain.readClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.time.LocalDate
import java.time.ZoneId

private val Context.settingsStore by preferencesDataStore(name = "settings")

data class UserSettings(
    val accessibilityDisclosureAccepted: Boolean = false,
    val monitoringEnabled: Boolean = false,
    val dailyLimitMinutes: Int = 30,
    val reactionMode: ReactionMode = ReactionMode.LOCK,
    val hasPin: Boolean = false,
    val unlockedUntilMs: Long = 0L,
    val unlockedDay: String = "",
    val unlockedElapsedUntilMs: Long = 0L,
    val unlockedBootCount: Long = -1L,
    val unlockedDurationMs: Long = 0L,
)

sealed interface PinAttempt {
    data object Accepted : PinAttempt
    data object Rejected : PinAttempt
    data class Locked(val retryAtMs: Long) : PinAttempt
}

class SettingsRepository(
    context: Context,
    private val clock: (() -> ClockReading)? = null,
) {
    private val appContext = context.applicationContext
    private val store = appContext.settingsStore

    fun clockReading(): ClockReading = clock?.invoke() ?: readClock(appContext)

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
            unlockedElapsedUntilMs = values[UNLOCKED_ELAPSED_UNTIL] ?: 0L,
            unlockedBootCount = values[UNLOCKED_BOOT_COUNT] ?: -1L,
            unlockedDurationMs = values[UNLOCKED_DURATION] ?: 0L,
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
            it.remove(PIN_RETRY_ELAPSED_AT)
            it.remove(PIN_RETRY_BOOT_COUNT)
            it.remove(PIN_RETRY_DURATION)
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

    suspend fun verifyPin(pin: String, now: ClockReading = clockReading()): PinAttempt {
        val snapshot = store.data.first()
        val retryAt = snapshot[PIN_RETRY_AT] ?: 0L
        val retryElapsedAt = snapshot[PIN_RETRY_ELAPSED_AT]
        val retryBootCount = snapshot[PIN_RETRY_BOOT_COUNT]
        val retryDeadline = if (retryElapsedAt != null && retryBootCount != null) {
            StoredDeadline(retryAt, retryElapsedAt, retryBootCount, snapshot[PIN_RETRY_DURATION] ?: 0L)
        } else null
        val retryRemaining = retryDeadline?.remainingMs(now) ?: (retryAt - now.wallTimeMs).coerceAtLeast(0L)
        if (retryRemaining > 0L) {
            if (retryBootCount != null && retryBootCount >= 0L && now.bootCount >= 0L && retryBootCount != now.bootCount) {
                persistRetryDeadline(StoredDeadline.after(retryRemaining, now))
            }
            return PinAttempt.Locked(now.wallTimeMs + retryRemaining)
        }
        val salt = snapshot[PIN_SALT]?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
        val expected = snapshot[PIN_HASH]?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
        val matches = if (PinCredential.isValidFormat(pin) && salt != null && expected != null) {
            withContext(Dispatchers.Default) { PinCredential.verify(pin, salt, expected) }
        } else false
        if (matches) {
            store.edit {
                it[PIN_FAILURES] = 0
                it.remove(PIN_RETRY_AT)
                it.remove(PIN_RETRY_ELAPSED_AT)
                it.remove(PIN_RETRY_BOOT_COUNT)
                it.remove(PIN_RETRY_DURATION)
            }
            return PinAttempt.Accepted
        }
        var nextRetry = 0L
        store.edit {
            val failures = (it[PIN_FAILURES] ?: 0) + 1
            it[PIN_FAILURES] = failures
            val delay = PinThrottle.delayAfterFailures(failures)
            if (delay > 0) {
                val deadline = StoredDeadline.after(delay, now)
                nextRetry = deadline.wallDeadlineMs
                it[PIN_RETRY_AT] = deadline.wallDeadlineMs
                it[PIN_RETRY_ELAPSED_AT] = deadline.elapsedDeadlineMs
                it[PIN_RETRY_BOOT_COUNT] = deadline.bootCount
                it[PIN_RETRY_DURATION] = deadline.durationMs
            } else {
                it.remove(PIN_RETRY_AT)
                it.remove(PIN_RETRY_ELAPSED_AT)
                it.remove(PIN_RETRY_BOOT_COUNT)
                it.remove(PIN_RETRY_DURATION)
            }
        }
        return if (nextRetry > now.wallTimeMs) PinAttempt.Locked(nextRetry) else PinAttempt.Rejected
    }

    suspend fun grantUnlock(day: LocalDate, now: ClockReading = clockReading()) {
        val deadline = StoredDeadline.after(com.inscreenlock.app.domain.UnlockWindow.DURATION_MS, now)
        val zoneId = ZoneId.systemDefault().id
        store.edit {
            it[UNLOCKED_UNTIL] = deadline.wallDeadlineMs
            it[UNLOCKED_ELAPSED_UNTIL] = deadline.elapsedDeadlineMs
            it[UNLOCKED_BOOT_COUNT] = deadline.bootCount
            it[UNLOCKED_DURATION] = deadline.durationMs
            it[UNLOCKED_DAY] = day.toString()
            it[UNLOCKED_ZONE_ID] = zoneId
        }
    }

    suspend fun clearExpiredUnlock(day: LocalDate, now: ClockReading = clockReading()) {
        val snapshot = store.data.first()
        val until = snapshot[UNLOCKED_UNTIL] ?: 0L
        val elapsedUntil = snapshot[UNLOCKED_ELAPSED_UNTIL]
        val bootCount = snapshot[UNLOCKED_BOOT_COUNT]
        val currentZoneId = ZoneId.systemDefault().id
        val grantIsCurrent = isUnlockGrantCurrent(snapshot[UNLOCKED_DAY], snapshot[UNLOCKED_ZONE_ID], day, currentZoneId)
        val remaining = if (elapsedUntil != null && bootCount != null) {
            StoredDeadline(until, elapsedUntil, bootCount, snapshot[UNLOCKED_DURATION] ?: 0L).remainingUnlockMs(now)
        } else (until - now.wallTimeMs).coerceAtLeast(0L)
        if (remaining > 0L && grantIsCurrent) return
        store.edit { values ->
            if (remaining == 0L || !isUnlockGrantCurrent(values[UNLOCKED_DAY], values[UNLOCKED_ZONE_ID], day, currentZoneId)) {
                values.remove(UNLOCKED_UNTIL)
                values.remove(UNLOCKED_ELAPSED_UNTIL)
                values.remove(UNLOCKED_BOOT_COUNT)
                values.remove(UNLOCKED_DURATION)
                values.remove(UNLOCKED_DAY)
                values.remove(UNLOCKED_ZONE_ID)
            }
        }
    }

    internal fun isUnlockGrantCurrent(
        unlockedDay: String?,
        unlockedZoneId: String?,
        day: LocalDate,
        zoneId: String,
    ): Boolean = unlockedDay == day.toString() && unlockedZoneId == zoneId

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
        val PIN_RETRY_ELAPSED_AT = longPreferencesKey("pin_retry_elapsed_at")
        val PIN_RETRY_BOOT_COUNT = longPreferencesKey("pin_retry_boot_count")
        val PIN_RETRY_DURATION = longPreferencesKey("pin_retry_duration")
        val UNLOCKED_UNTIL = longPreferencesKey("unlocked_until")
        val UNLOCKED_ELAPSED_UNTIL = longPreferencesKey("unlocked_elapsed_until")
        val UNLOCKED_BOOT_COUNT = longPreferencesKey("unlocked_boot_count")
        val UNLOCKED_DURATION = longPreferencesKey("unlocked_duration")
        val UNLOCKED_DAY = stringPreferencesKey("unlocked_day")
        val UNLOCKED_ZONE_ID = stringPreferencesKey("unlocked_zone_id")
    }

    private suspend fun persistRetryDeadline(deadline: StoredDeadline) {
        store.edit {
            it[PIN_RETRY_AT] = deadline.wallDeadlineMs
            it[PIN_RETRY_ELAPSED_AT] = deadline.elapsedDeadlineMs
            it[PIN_RETRY_BOOT_COUNT] = deadline.bootCount
            it[PIN_RETRY_DURATION] = deadline.durationMs
        }
    }
}

package com.inscreenlock.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.inscreenlock.app.domain.ReactionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.settingsStore by preferencesDataStore(name = "settings")

data class UserSettings(
    val accessibilityDisclosureAccepted: Boolean = false,
    val monitoringEnabled: Boolean = false,
    val dailyLimitMinutes: Int = 30,
    val reactionMode: ReactionMode = ReactionMode.WARNING,
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<UserSettings> = store.data.map { values ->
        UserSettings(
            accessibilityDisclosureAccepted = values[ACCESSIBILITY_CONSENT] ?: false,
            monitoringEnabled = values[MONITORING_ENABLED] ?: false,
            dailyLimitMinutes = (values[DAILY_LIMIT_MINUTES] ?: 30).coerceAtLeast(1),
            reactionMode = runCatching {
                ReactionMode.valueOf(values[REACTION_MODE] ?: ReactionMode.WARNING.name)
            }.getOrDefault(ReactionMode.WARNING),
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
    }
}

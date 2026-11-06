package com.inscreenlock.app.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object RefreshDisplayPolicy {
    const val NORMAL_REFRESH_INTERVAL_MS = 10_000L
    const val LOCKED_REFRESH_INTERVAL_MS = 2_000L

    fun refreshIntervalMs(lastDecisionShouldLock: Boolean): Long =
        if (lastDecisionShouldLock) LOCKED_REFRESH_INTERVAL_MS else NORMAL_REFRESH_INTERVAL_MS

    fun activeUnlockBadgeText(
        deadline: StoredDeadline,
        unlockedDay: String,
        currentDay: String,
        clockReading: ClockReading,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String? {
        if (unlockedDay != currentDay) return null
        val remainingMs = deadline.remainingUnlockMs(clockReading)
        if (remainingMs <= 0L) return null
        val localDeadline = Instant.ofEpochMilli(clockReading.wallTimeMs + remainingMs).atZone(zoneId)
        return "Desbloqueado até ${timeFormatter.format(localDeadline)}"
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
}

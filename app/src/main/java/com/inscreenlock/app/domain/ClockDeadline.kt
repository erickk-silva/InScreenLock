package com.inscreenlock.app.domain

import android.content.Context
import android.provider.Settings
import android.os.SystemClock

data class ClockReading(val wallTimeMs: Long, val elapsedRealtimeMs: Long, val bootCount: Long)

data class StoredDeadline(
    val wallDeadlineMs: Long,
    val elapsedDeadlineMs: Long,
    val bootCount: Long,
    val durationMs: Long = elapsedDeadlineMs,
) {
    fun remainingMs(now: ClockReading): Long = if (bootCount >= 0L && now.bootCount == bootCount) {
        (elapsedDeadlineMs - now.elapsedRealtimeMs).coerceAtLeast(0L)
    } else if (bootCount >= 0L && now.bootCount >= 0L) {
        maxOf((wallDeadlineMs - now.wallTimeMs).coerceAtLeast(0L), durationMs)
    } else {
        (wallDeadlineMs - now.wallTimeMs).coerceAtLeast(0L)
    }

    fun remainingUnlockMs(now: ClockReading): Long = if (bootCount >= 0L && now.bootCount == bootCount) {
        (elapsedDeadlineMs - now.elapsedRealtimeMs).coerceAtLeast(0L)
    } else {
        minOf((wallDeadlineMs - now.wallTimeMs).coerceAtLeast(0L), durationMs)
    }

    companion object {
        fun after(durationMs: Long, now: ClockReading): StoredDeadline = StoredDeadline(
            wallDeadlineMs = now.wallTimeMs + durationMs,
            elapsedDeadlineMs = now.elapsedRealtimeMs + durationMs,
            bootCount = now.bootCount,
            durationMs = durationMs,
        )
    }
}

fun readClock(context: Context): ClockReading = ClockReading(
    wallTimeMs = System.currentTimeMillis(),
    elapsedRealtimeMs = SystemClock.elapsedRealtime(),
    bootCount = Settings.Global.getLong(context.contentResolver, Settings.Global.BOOT_COUNT, -1L),
)

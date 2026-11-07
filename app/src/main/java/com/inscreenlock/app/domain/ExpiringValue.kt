package com.inscreenlock.app.domain

/** In-memory single value reused for [ttlMs]; a clock that moves backwards never counts as fresh. */
class ExpiringValue<T : Any>(private val ttlMs: Long) {
    private var value: T? = null
    private var storedAtMs = 0L

    fun get(nowMs: Long): T? = value?.takeIf { nowMs >= storedAtMs && nowMs - storedAtMs < ttlMs }

    fun put(newValue: T, nowMs: Long) {
        value = newValue
        storedAtMs = nowMs
    }

    fun clear() {
        value = null
    }
}

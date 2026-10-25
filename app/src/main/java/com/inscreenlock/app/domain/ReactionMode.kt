package com.inscreenlock.app.domain

enum class ReactionMode {
    WARNING,
    LOCK;

    companion object {
        fun fromStoredValue(value: String?): ReactionMode = when (value) {
            "WARNING" -> WARNING
            null, "PAUSE", "LOCK" -> LOCK
            else -> LOCK
        }
    }
}

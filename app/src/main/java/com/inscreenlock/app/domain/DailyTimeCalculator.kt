package com.inscreenlock.app.domain

data class UsageSignal(
    val timestampMs: Long,
    val kind: Kind,
    val packageName: String? = null,
) {
    enum class Kind {
        APP_FOREGROUND,
        APP_BACKGROUND,
        SCREEN_INTERACTIVE,
        SCREEN_NON_INTERACTIVE,
        KEYGUARD_SHOWN,
        KEYGUARD_HIDDEN,
    }
}

data class UsageTotals(
    val instagramMs: Long = 0,
    val tiktokMs: Long = 0,
) {
    val totalMs: Long get() = instagramMs + tiktokMs
}

object DailyTimeCalculator {
    const val INSTAGRAM_PACKAGE = "com.instagram.android"
    const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"

    fun calculate(
        events: List<UsageSignal>,
        windowStartMs: Long,
        nowMs: Long,
    ): UsageTotals {
        if (nowMs <= windowStartMs) return UsageTotals()

        var foregroundPackage: String? = null
        var interactive = false
        var keyguardVisible = false
        var cursorMs = windowStartMs
        var instagramMs = 0L
        var tiktokMs = 0L

        for (event in events.withIndex().sortedWith(compareBy({ it.value.timestampMs }, { it.index })).map { it.value }) {
            if (event.timestampMs > nowMs) break

            val boundaryMs = event.timestampMs.coerceAtLeast(windowStartMs)
            if (boundaryMs > cursorMs && interactive && !keyguardVisible) {
                val elapsedMs = boundaryMs - cursorMs
                when (foregroundPackage) {
                    INSTAGRAM_PACKAGE -> instagramMs += elapsedMs
                    TIKTOK_PACKAGE -> tiktokMs += elapsedMs
                }
            }
            cursorMs = maxOf(cursorMs, boundaryMs)

            when (event.kind) {
                UsageSignal.Kind.APP_FOREGROUND -> {
                    foregroundPackage = event.packageName?.takeIf(::isTargetPackage)
                }
                UsageSignal.Kind.APP_BACKGROUND -> {
                    if (event.packageName == foregroundPackage) foregroundPackage = null
                }
                UsageSignal.Kind.SCREEN_INTERACTIVE -> interactive = true
                UsageSignal.Kind.SCREEN_NON_INTERACTIVE -> interactive = false
                UsageSignal.Kind.KEYGUARD_SHOWN -> keyguardVisible = true
                UsageSignal.Kind.KEYGUARD_HIDDEN -> keyguardVisible = false
            }
        }

        if (nowMs > cursorMs && interactive && !keyguardVisible) {
            val elapsedMs = nowMs - cursorMs
            when (foregroundPackage) {
                INSTAGRAM_PACKAGE -> instagramMs += elapsedMs
                TIKTOK_PACKAGE -> tiktokMs += elapsedMs
            }
        }

        return UsageTotals(instagramMs, tiktokMs)
    }

    private fun isTargetPackage(packageName: String): Boolean =
        packageName == INSTAGRAM_PACKAGE || packageName == TIKTOK_PACKAGE
}

package com.inscreenlock.app.domain

data class UsageSignal(
    val timestampMs: Long,
    val kind: Kind,
    val packageName: String? = null,
    val activityName: String? = null,
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
        val totals = calculateByPackage(events, windowStartMs, nowMs)
        return UsageTotals(
            instagramMs = totals[INSTAGRAM_PACKAGE] ?: 0L,
            tiktokMs = totals[TIKTOK_PACKAGE] ?: 0L,
        )
    }

    fun calculateByPackage(
        events: List<UsageSignal>,
        windowStartMs: Long,
        nowMs: Long,
        excludedPackages: Set<String> = emptySet(),
    ): Map<String, Long> {
        if (nowMs <= windowStartMs) return emptyMap()

        var foregroundPackage: String? = null
        val activeActivities = mutableMapOf<String, MutableSet<String>>()
        var interactive = false
        var keyguardVisible = false
        var cursorMs = windowStartMs
        val usageByPackage = mutableMapOf<String, Long>()

        for (event in events.withIndex().sortedWith(compareBy({ it.value.timestampMs }, { it.index })).map { it.value }) {
            if (event.timestampMs > nowMs) break

            val boundaryMs = event.timestampMs.coerceAtLeast(windowStartMs)
            if (boundaryMs > cursorMs && interactive && !keyguardVisible) {
                val elapsedMs = boundaryMs - cursorMs
                foregroundPackage?.let { packageName ->
                    usageByPackage[packageName] = (usageByPackage[packageName] ?: 0L) + elapsedMs
                }
            }
            cursorMs = maxOf(cursorMs, boundaryMs)

            when (event.kind) {
                UsageSignal.Kind.APP_FOREGROUND -> {
                    val packageName = event.packageName
                    if (packageName == null) {
                        foregroundPackage = null
                    } else {
                        activeActivities.getOrPut(packageName) { mutableSetOf() }
                            .add(event.activityName ?: packageName)
                        foregroundPackage = packageName
                    }
                }
                UsageSignal.Kind.APP_BACKGROUND -> {
                    val packageName = event.packageName
                    if (packageName != null) {
                        val activities = activeActivities[packageName]
                        activities?.remove(event.activityName ?: packageName)
                        if (activities.isNullOrEmpty()) activeActivities.remove(packageName)
                        if (packageName == foregroundPackage && activities.isNullOrEmpty()) {
                            foregroundPackage = null
                        }
                    }
                }
                UsageSignal.Kind.SCREEN_INTERACTIVE -> interactive = true
                UsageSignal.Kind.SCREEN_NON_INTERACTIVE -> interactive = false
                UsageSignal.Kind.KEYGUARD_SHOWN -> keyguardVisible = true
                UsageSignal.Kind.KEYGUARD_HIDDEN -> keyguardVisible = false
            }
        }

        if (nowMs > cursorMs && interactive && !keyguardVisible) {
            val elapsedMs = nowMs - cursorMs
            foregroundPackage?.let { packageName ->
                usageByPackage[packageName] = (usageByPackage[packageName] ?: 0L) + elapsedMs
            }
        }

        return usageByPackage.filter { (packageName, duration) ->
            packageName !in excludedPackages && duration > 0L
        }.toSortedMap()
    }
}

fun rankUsageByPackage(
    usageByPackage: Map<String, Long>,
    excludedPackages: Set<String> = emptySet(),
    limit: Int = 10,
): List<Pair<String, Long>> = usageByPackage.entries
    .asSequence()
    .filter { (packageName, duration) -> packageName !in excludedPackages && duration > 0L }
    .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
    .take(limit.coerceAtLeast(0))
    .map { it.key to it.value }
    .toList()

fun formatUsageDuration(milliseconds: Long): String {
    val totalMinutes = milliseconds.coerceAtLeast(0L) / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}

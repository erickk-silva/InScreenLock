package com.inscreenlock.app.domain

data class WindowForegroundResolution(
    val targetPackage: String?,
    val transientWindow: Boolean,
)

object ForegroundResolver {
    fun resolveWindowEvent(
        eventPackage: String?,
        transientPackages: Set<String>,
        currentTargetPackage: String?,
    ): WindowForegroundResolution {
        val target = eventPackage?.takeIf(::isTargetPackage)
        if (target != null) return WindowForegroundResolution(target, transientWindow = false)
        if (eventPackage in transientPackages && currentTargetPackage != null) {
            return WindowForegroundResolution(currentTargetPackage, transientWindow = true)
        }
        return WindowForegroundResolution(null, transientWindow = false)
    }

    fun resolveUsageStats(
        currentTargetPackage: String,
        observedForegroundPackage: String?,
        transientPackages: Set<String>,
    ): Boolean? = when {
        observedForegroundPackage == currentTargetPackage -> true
        observedForegroundPackage == null || observedForegroundPackage in transientPackages -> null
        else -> false
    }

    private fun isTargetPackage(packageName: String): Boolean =
        packageName == DailyTimeCalculator.INSTAGRAM_PACKAGE ||
            packageName == DailyTimeCalculator.TIKTOK_PACKAGE
}

package com.inscreenlock.app.monitoring

import com.inscreenlock.app.domain.ForegroundResolver
import com.inscreenlock.app.domain.WindowForegroundResolution

interface OverlaySessionHost {
    fun show()
    fun remove()
}

/** Keeps target and overlay lifetime decisions separate from Android window APIs. */
class OverlaySessionController(private val host: OverlaySessionHost) {
    var targetPackage: String? = null
        private set
    var loopRunning: Boolean = false
        private set
    private var transientWindow = false
    private var overlayVisible = false
    private var foreignWindowPending = false
    private var reconnectionAttempted = false

    fun resumeFromReconnection(
        observedForegroundPackage: String?,
        monitoringAllowed: Boolean,
        screenInteractive: Boolean,
        keyguardVisible: Boolean,
    ): Boolean {
        if (reconnectionAttempted) return false
        reconnectionAttempted = true
        if (targetPackage != null || !monitoringAllowed || !screenInteractive || keyguardVisible) return false
        val target = ForegroundResolver.resolveWindowEvent(
            eventPackage = observedForegroundPackage,
            transientPackages = emptySet(),
            currentTargetPackage = null,
        ).targetPackage ?: return false
        targetPackage = target
        transientWindow = false
        loopRunning = true
        return true
    }

    fun onWindowEvent(
        eventPackage: String?,
        transientPackages: Set<String>,
        ownPackageName: String? = null,
        eventClassName: String? = null,
        ownActivityClassNames: Set<String> = emptySet(),
    ): WindowForegroundResolution? {
        if (eventPackage == ownPackageName && eventPackage != null && eventClassName !in ownActivityClassNames) {
            return null
        }
        val activeTarget = targetPackage
        val resolution = ForegroundResolver.resolveWindowEvent(eventPackage, transientPackages, activeTarget)
        val ownActivityEvent = eventPackage != null && eventPackage == ownPackageName
        if (resolution.targetPackage == null && activeTarget != null && !ownActivityEvent) {
            // Another window (e.g. back gesture or shade) reported state while the target may still be in
            // the foreground: hide now, let the UsageStats reconfirmation decide whether the session ends.
            foreignWindowPending = true
            transientWindow = true
            loopRunning = true
            hideOverlay()
            return WindowForegroundResolution(activeTarget, transientWindow = true)
        }
        if (!resolution.transientWindow) foreignWindowPending = false
        targetPackage = resolution.targetPackage
        transientWindow = resolution.transientWindow
        if (targetPackage == null) {
            loopRunning = false
            hideOverlay()
        } else {
            loopRunning = true
            if (transientWindow) hideOverlay()
        }
        return resolution
    }

    fun reconfirmUsage(observedForegroundPackage: String?, transientPackages: Set<String>): Boolean? {
        val target = targetPackage ?: return false
        val active = ForegroundResolver.resolveUsageStats(target, observedForegroundPackage, transientPackages)
        if (foreignWindowPending) {
            foreignWindowPending = false
            if (active != true) {
                endSession()
                return false
            }
        }
        when (active) {
            false -> endSession()
            true -> transientWindow = false
            null -> {
                transientWindow = observedForegroundPackage in transientPackages
                if (transientWindow) hideOverlay()
            }
        }
        return active
    }

    fun presentOverlay(show: () -> Boolean) {
        if (targetPackage != null && !transientWindow && show()) {
            overlayVisible = true
            host.show()
        }
    }

    fun hideOverlay() {
        if (overlayVisible) host.remove()
        overlayVisible = false
    }

    fun reset() = endSession()

    private fun endSession() {
        targetPackage = null
        transientWindow = false
        foreignWindowPending = false
        loopRunning = false
        hideOverlay()
    }
}

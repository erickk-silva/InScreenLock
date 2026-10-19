package com.inscreenlock.app.domain

object MonitoringGate {
    fun canInspectEvents(consent: Boolean, enabled: Boolean, usageAccess: Boolean): Boolean =
        consent && enabled && usageAccess
}

package com.inscreenlock.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringGateTest {
    @Test
    fun serviceReadsForegroundPackageOnlyAfterConsentAndBothOsAccesses() {
        assertTrue(MonitoringGate.canInspectEvents(consent = true, enabled = true, usageAccess = true))
        assertFalse(MonitoringGate.canInspectEvents(consent = false, enabled = true, usageAccess = true))
        assertFalse(MonitoringGate.canInspectEvents(consent = true, enabled = false, usageAccess = true))
        assertFalse(MonitoringGate.canInspectEvents(consent = true, enabled = true, usageAccess = false))
    }
}

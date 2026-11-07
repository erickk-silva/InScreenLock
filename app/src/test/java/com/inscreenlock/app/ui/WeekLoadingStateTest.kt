package com.inscreenlock.app.ui

import com.inscreenlock.app.domain.WeekUsage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekLoadingStateTest {
    @Test fun unqueriedWeekIsLoadingUntilFailureIsReported() {
        assertTrue(shouldShowWeekLoading(week = null, loading = false, failed = false))
        assertTrue(shouldShowWeekLoading(week = null, loading = true, failed = false))
        assertFalse(shouldShowWeekLoading(week = null, loading = false, failed = true))
        assertFalse(shouldShowWeekLoading(week = WeekUsage(emptyList()), loading = false, failed = false))
    }
}

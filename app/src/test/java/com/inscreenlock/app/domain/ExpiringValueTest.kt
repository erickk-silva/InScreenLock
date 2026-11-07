package com.inscreenlock.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpiringValueTest {
    @Test fun valueIsReusedUntilTtlThenExpires() {
        val cache = ExpiringValue<Int>(ttlMs = 10_000L)
        assertNull(cache.get(0L))
        cache.put(7, 1_000L)
        assertEquals(7, cache.get(10_999L))
        assertNull(cache.get(11_000L))
    }

    @Test fun clearDropsValueAndBackwardsClockIsNotFresh() {
        val cache = ExpiringValue<Int>(ttlMs = 10_000L)
        cache.put(7, 5_000L)
        assertNull(cache.get(4_000L))
        cache.put(8, 5_000L)
        cache.clear()
        assertNull(cache.get(5_001L))
    }
}

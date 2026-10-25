package com.inscreenlock.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageAndBadgeTest {
    @Test fun successfulEmptyUsageQueryIsZeroAndUnavailableQueryIsNull() {
        assertEquals(UsageTotals(), UsageQuerySnapshot.totals(true, emptyList(), 0, 100))
        assertNull(UsageQuerySnapshot.totals(false, emptyList(), 0, 100))
    }

    @Test fun badgeTextNamesAttentionAndExceededStates() {
        assertEquals("Atenção", BadgeStatus.text(BudgetLevel.ATTENTION))
        assertEquals("Limite excedido", BadgeStatus.text(BudgetLevel.EXCEEDED))
        assertEquals("", BadgeStatus.text(BudgetLevel.NORMAL))
        assertEquals("Atenção · 24 / 30 min", BadgeStatus.format(BudgetLevel.ATTENTION, "24 / 30 min"))
        assertEquals("Limite excedido · 31 / 30 min", BadgeStatus.format(BudgetLevel.EXCEEDED, "31 / 30 min"))
    }
}

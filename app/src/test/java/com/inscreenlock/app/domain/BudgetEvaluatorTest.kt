package com.inscreenlock.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetEvaluatorTest {
    @Test
    fun marksAttentionAtEightyPercentAndExceededAtOneHundredWithoutClamping() {
        assertEquals(BudgetLevel.NORMAL, BudgetEvaluator.evaluate(79, 100, true).level)
        assertEquals(BudgetLevel.ATTENTION, BudgetEvaluator.evaluate(80, 100, true).level)
        assertEquals(BudgetLevel.EXCEEDED, BudgetEvaluator.evaluate(100, 100, true).level)
        val exceeded = BudgetEvaluator.evaluate(125, 100, true)
        assertEquals(BudgetLevel.EXCEEDED, exceeded.level)
        assertEquals(125L, exceeded.usedMs)
        assertEquals(-25L, exceeded.remainingMs)
    }

    @Test
    fun unavailableDataNeverBecomesZeroUsageOrAnExceededPause() {
        val state = BudgetEvaluator.evaluate(0, 100, false)
        assertEquals(BudgetLevel.UNAVAILABLE, state.level)
        assertFalse(state.lockEligible)
    }

    @Test
    fun warningFiresOnlyOnFirstThresholdCrossing() {
        assertTrue(BudgetEvaluator.crossedThreshold(99, 100, 100, alreadyWarned = false))
        assertFalse(BudgetEvaluator.crossedThreshold(100, 101, 100, alreadyWarned = false))
        assertFalse(BudgetEvaluator.crossedThreshold(99, 100, 100, alreadyWarned = true))
        assertFalse(BudgetEvaluator.crossedThreshold(101, 99, 100, alreadyWarned = false))
    }
}

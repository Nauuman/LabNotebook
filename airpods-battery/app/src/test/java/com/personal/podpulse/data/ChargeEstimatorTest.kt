package com.personal.podpulse.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChargeEstimatorTest {
    @Test
    fun fullIsZero() {
        assertEquals(0, ChargeEstimator.estimateMinutes(100, 6.0))
    }

    @Test
    fun slowsDownAbove80() {
        // 50->80: 3 steps x 6 min, 80->100: 2 steps x 9 min
        assertEquals(36, ChargeEstimator.estimateMinutes(50, 6.0))
    }

    @Test
    fun formatting() {
        assertEquals("Fully charged", ChargeEstimator.format(0))
        assertEquals("Full in ~36 min", ChargeEstimator.format(36))
        assertEquals("Full in ~1 h 30 min", ChargeEstimator.format(90))
    }
}

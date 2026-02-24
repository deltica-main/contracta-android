package com.example.businesscardscanner.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test

class MotionSmootherTest {

    @Test
    fun medianWindow_reduces_singleFrameOutlierImpact() {
        val smoother = MotionSmoother(windowSize = 5)
        val samples = listOf(0.020, 0.024, 0.028, 0.300, 0.026)
        var smoothed = 0.0
        samples.forEach { sample ->
            smoothed = smoother.push(sample)
        }

        assertTrue(smoothed < 0.05)
    }

    @Test
    fun reset_clearsPreviousHistory() {
        val smoother = MotionSmoother(windowSize = 5)
        smoother.push(0.40)
        smoother.push(0.35)
        smoother.reset()

        val smoothed = smoother.push(0.02)
        assertTrue(smoothed <= 0.03)
    }
}

package com.example.businesscardscanner.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCaptureMathTest {

    @Test
    fun downsampleGrid_usesNearestCenterSample() {
        val source = IntArray(16) { index -> index }
        val downsampled = downsampleLumaGrid(
            source = source,
            sourceWidth = 4,
            sourceHeight = 4,
            targetWidth = 2,
            targetHeight = 2
        )

        assertEquals(4, downsampled.size)
        assertEquals(5, downsampled[0])
        assertEquals(7, downsampled[1])
        assertEquals(13, downsampled[2])
        assertEquals(15, downsampled[3])
    }

    @Test
    fun laplacianVariance_flatGrid_returnsZero() {
        val grid = IntArray(25) { 120 }
        val variance = laplacianVariance(grid, width = 5, height = 5)
        assertEquals(0.0, variance, 0.00001)
    }

    @Test
    fun motionScore_usesFloatingPointDivision() {
        val previous = IntArray(16) { 100 }
        val current = IntArray(16) { 101 }
        val score = motionScore(current = current, previous = previous)

        assertTrue(score > 0.0)
        assertTrue(score < 0.01)
        assertEquals(1.0 / 255.0, score, 0.00001)
    }

    @Test
    fun highlightPercentage_reportsFractionNotInteger() {
        val grid = intArrayOf(240, 246, 250, 200, 100, 255, 180, 247)
        val pct = highlightPercentage(grid = grid, threshold = 245)

        assertEquals(4.0 / 8.0, pct, 0.00001)
    }
}

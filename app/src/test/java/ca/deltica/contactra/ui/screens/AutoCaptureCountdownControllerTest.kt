package ca.deltica.contactra.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCaptureCountdownControllerTest {

    @Test
    fun doesNotCaptureBeforeStableHoldAndCountdownComplete() {
        val controller = AutoCaptureCountdownController(
            stableHoldMs = 700L,
            countdownMs = 1200L
        )

        val t0 = controller.onReadiness(nowMs = 0L, ready = true)
        assertEquals(AutoCaptureCountdownPhase.HOLDING, t0.phase)
        assertFalse(t0.shouldCapture)

        val t600 = controller.onReadiness(nowMs = 600L, ready = true)
        assertEquals(AutoCaptureCountdownPhase.HOLDING, t600.phase)
        assertFalse(t600.shouldCapture)

        val t700 = controller.onReadiness(nowMs = 700L, ready = true)
        assertEquals(AutoCaptureCountdownPhase.COUNTDOWN, t700.phase)
        assertFalse(t700.shouldCapture)
        assertTrue(t700.countdownRemainingMs >= 1100L)

        val t1800 = controller.onReadiness(nowMs = 1800L, ready = true)
        assertEquals(AutoCaptureCountdownPhase.COUNTDOWN, t1800.phase)
        assertFalse(t1800.shouldCapture)

        val t1900 = controller.onReadiness(nowMs = 1900L, ready = true)
        assertTrue(t1900.shouldCapture)

        val t2000 = controller.onReadiness(nowMs = 2000L, ready = true)
        assertFalse(t2000.shouldCapture)
    }

    @Test
    fun instabilityCancelsAndRequiresFreshHold() {
        val controller = AutoCaptureCountdownController(
            stableHoldMs = 700L,
            countdownMs = 1200L
        )

        controller.onReadiness(nowMs = 0L, ready = true)
        controller.onReadiness(nowMs = 450L, ready = true)

        val dropped = controller.onReadiness(nowMs = 500L, ready = false)
        assertEquals(AutoCaptureCountdownPhase.IDLE, dropped.phase)
        assertFalse(dropped.shouldCapture)

        val restarted = controller.onReadiness(nowMs = 600L, ready = true)
        assertEquals(AutoCaptureCountdownPhase.HOLDING, restarted.phase)
        assertFalse(restarted.shouldCapture)

        val beforeHoldComplete = controller.onReadiness(nowMs = 1200L, ready = true)
        assertEquals(AutoCaptureCountdownPhase.HOLDING, beforeHoldComplete.phase)
        assertFalse(beforeHoldComplete.shouldCapture)

        val afterHoldComplete = controller.onReadiness(nowMs = 1300L, ready = true)
        assertEquals(AutoCaptureCountdownPhase.COUNTDOWN, afterHoldComplete.phase)
        assertFalse(afterHoldComplete.shouldCapture)
    }
}

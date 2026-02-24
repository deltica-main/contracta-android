package com.example.businesscardscanner.ui.screens

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoCaptureStateMachineInstrumentationTest {

    @Test
    fun ocrChecks_areThrottledToTwoPerSecond() {
        val stateMachine = AutoCaptureStateMachine(
            stableFramesRequired = 1,
            readyChecksRequired = 2,
            ocrIntervalMs = 500L,
            minCaptureIntervalMs = 1200L,
            refocusAfterMs = 2000L,
            refocusMinIntervalMs = 2000L
        )
        stateMachine.reset(nowMs = 0L)

        val stageAPass = stageA(passed = true, reason = "stage_a_pass")
        var ocrRequests = 0
        for (nowMs in 0L..900L step 100L) {
            val tick = stateMachine.onStageA(nowMs = nowMs, evaluation = stageAPass)
            if (tick.shouldRunOcr) {
                ocrRequests += 1
                stateMachine.onOcrReadiness(ready = false)
            }
        }

        assertEquals(2, ocrRequests)
    }

    @Test
    fun ocrChecks_areBlockedWhenStageAFails() {
        val stateMachine = AutoCaptureStateMachine(
            stableFramesRequired = 1,
            readyChecksRequired = 2,
            ocrIntervalMs = 500L,
            minCaptureIntervalMs = 1200L,
            refocusAfterMs = 2000L,
            refocusMinIntervalMs = 2000L
        )
        stateMachine.reset(nowMs = 0L)

        val stageAFail = stageA(passed = false, reason = "motion_high")
        for (nowMs in 0L..900L step 100L) {
            val tick = stateMachine.onStageA(nowMs = nowMs, evaluation = stageAFail)
            assertFalse(tick.shouldRunOcr)
        }
    }

    private fun stageA(passed: Boolean, reason: String): StageAEvaluation {
        return StageAEvaluation(
            passed = passed,
            reason = reason,
            metrics = StageAMetrics(
                sharpness = 50.0,
                motion = 0.01,
                lumaMean = 120.0,
                highlightPct = 0.02,
                centerEdgeDensity = 0.11,
                outerEdgeDensity = 0.04
            )
        )
    }
}

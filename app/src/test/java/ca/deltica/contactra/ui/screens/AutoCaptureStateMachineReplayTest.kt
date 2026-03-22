package ca.deltica.contactra.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCaptureStateMachineReplayTest {

    @Test
    fun stableSurfaceScenario_capturesQuickly() {
        val stateMachine = buildStateMachine()
        stateMachine.reset(nowMs = 0L)

        var captureAt: Long? = null
        for (nowMs in 0L..800L step 50L) {
            val tick = stateMachine.onStageA(nowMs = nowMs, evaluation = strictPass())
            if (tick.shouldRunOcr) {
                stateMachine.onOcrReadiness(ready = true)
            }
            if (tick.shouldTriggerCapture) {
                captureAt = nowMs
                break
            }
        }

        assertNotNull(captureAt)
        assertTrue(captureAt!! <= 650L)
    }

    @Test
    fun handheldMicroJitterScenario_runsOcrAndCaptures() {
        val stateMachine = buildStateMachine()
        stateMachine.reset(nowMs = 0L)

        val replay = listOf(
            0L to strictPass(),
            100L to strictPass(),
            200L to strictPass(),
            320L to relaxedOnly(),
            460L to relaxedOnly(),
            620L to relaxedOnly(),
            780L to relaxedOnly(),
            940L to relaxedOnly(),
            1100L to relaxedOnly()
        )

        var ocrChecks = 0
        var captureAt: Long? = null
        replay.forEach { (nowMs, evaluation) ->
            val tick = stateMachine.onStageA(nowMs = nowMs, evaluation = evaluation)
            if (tick.shouldRunOcr) {
                ocrChecks += 1
                stateMachine.onOcrReadiness(ready = true)
            }
            if (tick.shouldTriggerCapture && captureAt == null) {
                captureAt = nowMs
            }
        }

        assertTrue(ocrChecks >= 1)
        assertNotNull(captureAt)
    }

    @Test
    fun highMotionScenario_neverCaptures() {
        val stateMachine = buildStateMachine()
        stateMachine.reset(nowMs = 0L)

        var captured = false
        var ocrWasScheduled = false
        for (nowMs in 0L..1800L step 100L) {
            val tick = stateMachine.onStageA(nowMs = nowMs, evaluation = motionBlocked())
            if (tick.shouldRunOcr) {
                ocrWasScheduled = true
                stateMachine.onOcrReadiness(ready = false)
            }
            if (tick.shouldTriggerCapture) {
                captured = true
            }
        }

        assertFalse(ocrWasScheduled)
        assertFalse(captured)
    }

    @Test
    fun hysteresisGrace_holdsBriefMotionSpikes_thenExpires() {
        val stateMachine = buildStateMachine()
        stateMachine.reset(nowMs = 0L)

        stateMachine.onStageA(nowMs = 0L, evaluation = strictPass())
        stateMachine.onStageA(nowMs = 100L, evaluation = strictPass())
        val stable = stateMachine.onStageA(nowMs = 200L, evaluation = strictPass())
        assertTrue(stable.stageAStable)

        val withinGrace1 = stateMachine.onStageA(nowMs = 180L, evaluation = motionBlocked())
        val withinGrace2 = stateMachine.onStageA(nowMs = 320L, evaluation = motionBlocked())
        val afterGrace = stateMachine.onStageA(nowMs = 520L, evaluation = motionBlocked())

        assertTrue(withinGrace1.graceActive)
        assertTrue(withinGrace1.stageAStable)
        assertTrue(withinGrace2.stageAStable)
        assertFalse(afterGrace.stageAStable)
    }

    private fun buildStateMachine(): AutoCaptureStateMachine {
        return AutoCaptureStateMachine(
            stableFramesRequired = 3,
            ocrStableFramesRequired = 1,
            readyChecksRequired = 2,
            ocrIntervalMs = 250L,
            fastOcrIntervalMs = 150L,
            minCaptureIntervalMs = 900L,
            firstCaptureMinIntervalMs = 400L,
            stageAStabilityGraceMs = 300L,
            refocusAfterMs = Long.MAX_VALUE,
            refocusMinIntervalMs = Long.MAX_VALUE
        )
    }

    private fun strictPass(): StageAEvaluation {
        return StageAEvaluation(
            passed = true,
            reason = "stage_a_pass",
            metrics = metrics(sharpness = 36.0, motionSmoothed = 0.020, motionRaw = 0.020),
            strictPassed = true,
            relaxedPassed = true,
            band = StageABand.STRICT,
            isSharpnessHardBlock = false
        )
    }

    private fun relaxedOnly(): StageAEvaluation {
        return StageAEvaluation(
            passed = false,
            reason = "motion_high_capture",
            metrics = metrics(sharpness = 32.0, motionSmoothed = 0.070, motionRaw = 0.085),
            strictPassed = false,
            relaxedPassed = true,
            band = StageABand.RELAXED,
            isSharpnessHardBlock = false
        )
    }

    private fun motionBlocked(): StageAEvaluation {
        return StageAEvaluation(
            passed = false,
            reason = "motion_high_relaxed",
            metrics = metrics(sharpness = 30.0, motionSmoothed = 0.160, motionRaw = 0.180),
            strictPassed = false,
            relaxedPassed = false,
            band = StageABand.BLOCKED,
            isSharpnessHardBlock = false
        )
    }

    private fun metrics(
        sharpness: Double,
        motionSmoothed: Double,
        motionRaw: Double
    ): StageAMetrics {
        return StageAMetrics(
            sharpness = sharpness,
            motion = motionSmoothed,
            motionRaw = motionRaw,
            lumaMean = 120.0,
            highlightPct = 0.05,
            centerEdgeDensity = 0.12,
            outerEdgeDensity = 0.05
        )
    }
}

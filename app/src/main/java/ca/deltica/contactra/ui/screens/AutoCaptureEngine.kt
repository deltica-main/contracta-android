package ca.deltica.contactra.ui.screens

import kotlin.math.abs
import kotlin.math.min

internal data class LumaGateThresholds(
    val minSharpnessVarCapture: Double,
    val minSharpnessVarOcr: Double,
    val maxMotionScoreCapture: Double,
    val maxMotionScoreOcr: Double,
    val minLumaMean: Double,
    val maxLumaMean: Double,
    val maxHighlightPct: Double,
    val minCenterEdgeDensity: Double,
    val maxOuterToCenterEdgeRatio: Double,
    val edgeThreshold: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val centerWindowRatio: Double,
    val motionWindowSize: Int = 5
)

internal enum class StageABand {
    STRICT,
    RELAXED,
    BLOCKED
}

internal enum class FramingOutcome {
    FRAMING_OK,
    FRAMING_SOFT_FAIL,
    FRAMING_HARD_FAIL
}

internal data class StageAMetrics(
    val sharpness: Double,
    val motion: Double,
    val motionRaw: Double = 0.0,
    val lumaMean: Double,
    val highlightPct: Double,
    val centerEdgeDensity: Double,
    val outerEdgeDensity: Double
)

internal data class StageAEvaluation(
    val passed: Boolean,
    val reason: String,
    val metrics: StageAMetrics,
    val strictPassed: Boolean = passed,
    val relaxedPassed: Boolean = passed,
    val band: StageABand = if (passed) StageABand.STRICT else StageABand.BLOCKED,
    val isSharpnessHardBlock: Boolean = false,
    val framingOutcome: FramingOutcome = FramingOutcome.FRAMING_OK
) {
    val isFramingSoftFail: Boolean
        get() = framingOutcome == FramingOutcome.FRAMING_SOFT_FAIL

    val isFramingHardFail: Boolean
        get() = framingOutcome == FramingOutcome.FRAMING_HARD_FAIL
}

internal data class AutoCaptureTick(
    val stageAStable: Boolean,
    val stageAOcrStable: Boolean = stageAStable,
    val stageBReady: Boolean,
    val shouldRunOcr: Boolean,
    val shouldTriggerCapture: Boolean,
    val shouldRefocus: Boolean,
    val ocrChecks: Int,
    val blockReason: String,
    val stageABand: StageABand = StageABand.BLOCKED,
    val graceActive: Boolean = false,
    val cooldownRemainingMs: Long = 0L
)

internal data class AutoCaptureDiagnostics(
    val sharpness: Double = 0.0,
    val motion: Double = 1.0,
    val lumaMean: Double = 0.0,
    val highlightPct: Double = 0.0,
    val stageAStable: Boolean = false,
    val ocrReadyScore: Double = 0.0,
    val ocrBlocks: Int = 0,
    val ocrChecks: Int = 0,
    val stageBReady: Boolean = false,
    val reason: String = "init"
)

internal data class OcrReadiness(
    val ready: Boolean,
    val score: Double,
    val blocks: Int,
    val reason: String
)

internal data class AutoCaptureDecisionPolicy(
    val minOcrReadyScore: Double,
    val allowSoftFailRelaxedCapture: Boolean = true,
    val softFailMinOcrReadyScore: Double = 0.85,
    val softFailReadyHitsRequired: Int = 2,
    val softFailReadyHitWindowMs: Long = 1200L,
    val relaxedNearReadyScoreMargin: Double = 0.0,
    val relaxedNearReadyStrikesRequired: Int = Int.MAX_VALUE,
    val relaxedNearReadyStrikeWindowMs: Long = 1200L,
    val maxRelaxedCaptureMotionScore: Double = Double.MAX_VALUE,
    val minCaptureSharpness: Double = 0.0
)

internal data class AutoCaptureDecisionResult(
    val shouldCapture: Boolean,
    val usedRelaxedNearReadyPath: Boolean,
    val usedRelaxedGraceReadyPath: Boolean,
    val relaxedNearReadyStrikes: Int,
    val blockReason: String
)

internal class AutoCaptureDecisionEngine(
    private val policy: AutoCaptureDecisionPolicy
) {
    private val relaxedNearReadyStrikeTimes = ArrayDeque<Long>()
    private val softFailOcrReadyHitTimes = ArrayDeque<Long>()

    fun reset() {
        relaxedNearReadyStrikeTimes.clear()
        softFailOcrReadyHitTimes.clear()
    }

    fun evaluate(
        nowMs: Long,
        tick: AutoCaptureTick,
        stageAEvaluation: StageAEvaluation,
        ocrReadiness: OcrReadiness
    ): AutoCaptureDecisionResult {
        val captureSharpnessOk = stageAEvaluation.metrics.sharpness >= policy.minCaptureSharpness
        val relaxedMotionOk = stageAEvaluation.metrics.motion <= policy.maxRelaxedCaptureMotionScore ||
            stageAEvaluation.relaxedPassed
        val softFailFraming = stageAEvaluation.isFramingSoftFail
        val relaxedBandEligible =
            !tick.shouldTriggerCapture &&
                !stageAEvaluation.isSharpnessHardBlock &&
                captureSharpnessOk &&
                tick.stageABand == StageABand.RELAXED &&
                tick.stageAOcrStable &&
                tick.cooldownRemainingMs <= 0L &&
                relaxedMotionOk

        val nearReadyThreshold = (policy.minOcrReadyScore - policy.relaxedNearReadyScoreMargin)
            .coerceIn(0.0, 1.0)
        val nearReadySignal = relaxedBandEligible && ocrReadiness.score >= nearReadyThreshold
        val windowStart = nowMs - policy.relaxedNearReadyStrikeWindowMs.coerceAtLeast(0L)
        while (relaxedNearReadyStrikeTimes.isNotEmpty() && relaxedNearReadyStrikeTimes.first() < windowStart) {
            relaxedNearReadyStrikeTimes.removeFirst()
        }
        if (nearReadySignal) {
            relaxedNearReadyStrikeTimes.addLast(nowMs)
        }
        if (stageAEvaluation.isSharpnessHardBlock || stageAEvaluation.isFramingHardFail) {
            relaxedNearReadyStrikeTimes.clear()
        }

        val ocrReadyWindowStart = nowMs - policy.softFailReadyHitWindowMs.coerceAtLeast(0L)
        while (softFailOcrReadyHitTimes.isNotEmpty() && softFailOcrReadyHitTimes.first() < ocrReadyWindowStart) {
            softFailOcrReadyHitTimes.removeFirst()
        }
        if (softFailFraming && ocrReadiness.ready) {
            softFailOcrReadyHitTimes.addLast(nowMs)
        }
        if (!softFailFraming || stageAEvaluation.isSharpnessHardBlock || stageAEvaluation.isFramingHardFail) {
            softFailOcrReadyHitTimes.clear()
        }

        val relaxedReadyByGrace =
            relaxedBandEligible &&
                tick.graceActive &&
                ocrReadiness.ready &&
                ocrReadiness.score >= policy.minOcrReadyScore
        val relaxedReadyByNearReady =
            relaxedBandEligible &&
                relaxedNearReadyStrikeTimes.size >= policy.relaxedNearReadyStrikesRequired
        val relaxedReadyByOcrReady = relaxedBandEligible && ocrReadiness.ready
        val softFailGuardPassed = !softFailFraming ||
            ocrReadiness.score >= policy.softFailMinOcrReadyScore ||
            softFailOcrReadyHitTimes.size >= policy.softFailReadyHitsRequired

        val relaxedSoftFailCapture =
            policy.allowSoftFailRelaxedCapture &&
                softFailFraming &&
                (relaxedReadyByOcrReady || relaxedReadyByNearReady || relaxedReadyByGrace) &&
                softFailGuardPassed
        val relaxedDefaultCapture = !softFailFraming && (relaxedReadyByGrace || relaxedReadyByNearReady)
        val shouldCapture = tick.shouldTriggerCapture || relaxedDefaultCapture || relaxedSoftFailCapture
        if (shouldCapture) {
            relaxedNearReadyStrikeTimes.clear()
            softFailOcrReadyHitTimes.clear()
        }

        val blockReason = when {
            shouldCapture -> "ready"
            softFailFraming && policy.allowSoftFailRelaxedCapture && !softFailGuardPassed -> "soft_fail_ocr_guard"
            !captureSharpnessOk -> "sharpness_below_capture_min"
            !relaxedMotionOk && tick.stageABand == StageABand.RELAXED -> "motion_too_high_relaxed_capture"
            else -> tick.blockReason
        }

        return AutoCaptureDecisionResult(
            shouldCapture = shouldCapture,
            usedRelaxedNearReadyPath = relaxedReadyByNearReady,
            usedRelaxedGraceReadyPath = relaxedReadyByGrace,
            relaxedNearReadyStrikes = relaxedNearReadyStrikeTimes.size,
            blockReason = blockReason
        )
    }
}

internal class AutoCaptureStateMachine(
    private val stableFramesRequired: Int,
    private val readyChecksRequired: Int,
    private val ocrIntervalMs: Long,
    private val minCaptureIntervalMs: Long,
    private val refocusAfterMs: Long,
    private val refocusMinIntervalMs: Long,
    private val ocrStableFramesRequired: Int = stableFramesRequired,
    private val fastOcrIntervalMs: Long = ocrIntervalMs,
    private val firstCaptureMinIntervalMs: Long = minCaptureIntervalMs,
    private val stageAStabilityGraceMs: Long = 300L
) {
    private val captureFramesRequired = stableFramesRequired.coerceAtLeast(1)
    private val ocrFramesRequired = ocrStableFramesRequired.coerceIn(1, captureFramesRequired)

    private var stageACaptureConsecutive = 0
    private var stageAOcrConsecutive = 0
    private var stageBOcrConsecutive = 0
    private var stageACaptureStable = false
    private var stageAOcrStable = false
    private var stageBReady = false
    private var ocrChecks = 0
    private var lastOcrCheckAt = Long.MIN_VALUE / 4
    private var lastCaptureAt = 0L
    private var lastStageAPassAt = 0L
    private var lastRefocusAt = Long.MIN_VALUE / 4
    private var graceUntilMs = Long.MIN_VALUE / 4
    private var graceConsumedSinceStable = false
    private var captureCount = 0

    @Synchronized
    fun reset(nowMs: Long = 0L) {
        stageACaptureConsecutive = 0
        stageAOcrConsecutive = 0
        stageBOcrConsecutive = 0
        stageACaptureStable = false
        stageAOcrStable = false
        stageBReady = false
        ocrChecks = 0
        lastOcrCheckAt = Long.MIN_VALUE / 4
        lastCaptureAt = nowMs
        lastStageAPassAt = nowMs
        lastRefocusAt = Long.MIN_VALUE / 4
        graceUntilMs = Long.MIN_VALUE / 4
        graceConsumedSinceStable = false
        captureCount = 0
    }

    @Synchronized
    fun clearTransientState() {
        stageACaptureConsecutive = 0
        stageAOcrConsecutive = 0
        stageBOcrConsecutive = 0
        stageACaptureStable = false
        stageAOcrStable = false
        stageBReady = false
        graceUntilMs = Long.MIN_VALUE / 4
        graceConsumedSinceStable = false
    }

    @Synchronized
    fun onStageA(nowMs: Long, evaluation: StageAEvaluation): AutoCaptureTick {
        val wasCaptureStable = stageACaptureStable
        val wasOcrStable = stageAOcrStable
        val motionFailure = evaluation.reason.startsWith("motion_")
        val eligibleForGrace = motionFailure && (wasCaptureStable || wasOcrStable)
        val graceActive = when {
            !eligibleForGrace -> false
            !graceConsumedSinceStable -> {
                graceUntilMs = nowMs + stageAStabilityGraceMs.coerceAtLeast(0L)
                graceConsumedSinceStable = true
                true
            }
            else -> nowMs <= graceUntilMs
        }

        if (evaluation.strictPassed) {
            stageACaptureConsecutive = (stageACaptureConsecutive + 1).coerceAtMost(captureFramesRequired)
        } else if (motionFailure && wasCaptureStable && graceActive) {
            stageACaptureConsecutive = captureFramesRequired
        } else {
            stageACaptureConsecutive = 0
        }

        if (evaluation.relaxedPassed) {
            stageAOcrConsecutive = (stageAOcrConsecutive + 1).coerceAtMost(captureFramesRequired)
        } else if (motionFailure && wasOcrStable && graceActive) {
            stageAOcrConsecutive = ocrFramesRequired
        } else {
            stageAOcrConsecutive = 0
            stageBOcrConsecutive = 0
            stageBReady = false
        }

        stageACaptureStable = stageACaptureConsecutive >= captureFramesRequired
        stageAOcrStable = stageAOcrConsecutive >= ocrFramesRequired

        if (evaluation.relaxedPassed) {
            lastStageAPassAt = nowMs
            graceUntilMs = Long.MIN_VALUE / 4
            graceConsumedSinceStable = false
        }

        val closeToCaptureStable = stageACaptureConsecutive >= (captureFramesRequired - 1).coerceAtLeast(1)
        val activeOcrInterval = if (closeToCaptureStable || (stageAOcrStable && !stageACaptureStable)) {
            fastOcrIntervalMs
        } else {
            ocrIntervalMs
        }
        val shouldRunOcr = stageAOcrStable && (nowMs - lastOcrCheckAt) >= activeOcrInterval
        if (shouldRunOcr) {
            lastOcrCheckAt = nowMs
            ocrChecks += 1
        }

        val shouldRefocus = !evaluation.relaxedPassed &&
            (nowMs - lastStageAPassAt) >= refocusAfterMs &&
            (nowMs - lastRefocusAt) >= refocusMinIntervalMs
        if (shouldRefocus) {
            lastRefocusAt = nowMs
        }

        val requiredCaptureCooldownMs = if (captureCount == 0) {
            firstCaptureMinIntervalMs
        } else {
            minCaptureIntervalMs
        }
        val cooldownRemainingMs = (requiredCaptureCooldownMs - (nowMs - lastCaptureAt)).coerceAtLeast(0L)

        val canCapture = stageACaptureStable &&
            stageBReady &&
            cooldownRemainingMs <= 0L &&
            !evaluation.isSharpnessHardBlock

        val stageABand = when {
            evaluation.strictPassed -> StageABand.STRICT
            evaluation.relaxedPassed || (motionFailure && graceActive && stageAOcrStable) -> StageABand.RELAXED
            else -> StageABand.BLOCKED
        }

        if (canCapture) {
            lastCaptureAt = nowMs
            captureCount += 1
            stageACaptureConsecutive = 0
            stageAOcrConsecutive = 0
            stageBOcrConsecutive = 0
            stageACaptureStable = false
            stageAOcrStable = false
            stageBReady = false
            graceUntilMs = Long.MIN_VALUE / 4
            graceConsumedSinceStable = false
        }

        val blockReason = when {
            evaluation.isSharpnessHardBlock -> "sharpness_hard_block"
            !evaluation.relaxedPassed && !(motionFailure && graceActive) -> evaluation.reason
            !stageAOcrStable -> "stage_a_ocr_unstable"
            !stageACaptureStable -> "stage_a_capture_unstable"
            !stageBReady -> "stage_b_not_ready"
            cooldownRemainingMs > 0L -> "capture_cooldown"
            else -> "ready"
        }

        return AutoCaptureTick(
            stageAStable = stageACaptureStable,
            stageAOcrStable = stageAOcrStable,
            stageBReady = stageBReady,
            shouldRunOcr = shouldRunOcr,
            shouldTriggerCapture = canCapture,
            shouldRefocus = shouldRefocus,
            ocrChecks = ocrChecks,
            blockReason = blockReason,
            stageABand = stageABand,
            graceActive = graceActive,
            cooldownRemainingMs = cooldownRemainingMs
        )
    }

    @Synchronized
    fun onOcrReadiness(ready: Boolean): Boolean {
        val previous = stageBReady
        if (!stageAOcrStable) {
            stageBOcrConsecutive = 0
            stageBReady = false
            return previous != stageBReady
        }

        stageBOcrConsecutive = if (ready) stageBOcrConsecutive + 1 else 0
        stageBReady = stageBOcrConsecutive >= readyChecksRequired
        return previous != stageBReady
    }

    @Synchronized
    fun isStageAStable(): Boolean = stageACaptureStable

    @Synchronized
    fun isStageAOcrStable(): Boolean = stageAOcrStable

    @Synchronized
    fun isStageBReady(): Boolean = stageBReady
}

internal class MotionSmoother(private val windowSize: Int) {
    private val window = DoubleArray(windowSize.coerceAtLeast(1))
    private val scratch = DoubleArray(window.size)
    private var count = 0
    private var index = 0

    fun reset() {
        count = 0
        index = 0
        window.fill(0.0)
    }

    fun push(value: Double): Double {
        window[index] = value
        index = (index + 1) % window.size
        if (count < window.size) {
            count += 1
        }
        for (i in 0 until count) {
            scratch[i] = window[i]
        }
        java.util.Arrays.sort(scratch, 0, count)
        val middle = count / 2
        return if (count % 2 == 0) {
            (scratch[middle - 1] + scratch[middle]) / 2.0
        } else {
            scratch[middle]
        }
    }
}

internal class LumaFrameAnalyzer(
    private val thresholds: LumaGateThresholds
) {
    private var sampleWidth = 0
    private var sampleHeight = 0
    private var current = IntArray(0)
    private var previous = IntArray(0)
    private var hasPreviousFrame = false
    private val motionSmoother = MotionSmoother(thresholds.motionWindowSize)

    fun reset() {
        hasPreviousFrame = false
        motionSmoother.reset()
    }

    fun evaluate(
        frameWidth: Int,
        frameHeight: Int,
        readLuma: (x: Int, y: Int) -> Int?
    ): StageAEvaluation {
        if (frameWidth <= 0 || frameHeight <= 0) {
            return StageAEvaluation(
                passed = false,
                reason = "invalid_frame_dimensions",
                metrics = StageAMetrics(
                    sharpness = 0.0,
                    motion = 1.0,
                    motionRaw = 1.0,
                    lumaMean = 0.0,
                    highlightPct = 0.0,
                    centerEdgeDensity = 0.0,
                    outerEdgeDensity = 0.0
                ),
                strictPassed = false,
                relaxedPassed = false,
                band = StageABand.BLOCKED,
                isSharpnessHardBlock = false,
                framingOutcome = FramingOutcome.FRAMING_HARD_FAIL
            )
        }

        val targetWidth = min(thresholds.gridWidth, frameWidth).coerceAtLeast(12)
        val targetHeight = min(thresholds.gridHeight, frameHeight).coerceAtLeast(12)
        ensureBuffers(targetWidth, targetHeight)

        var sum = 0L
        val total = sampleWidth * sampleHeight
        var index = 0
        for (y in 0 until sampleHeight) {
            val srcY = ((y + 0.5) * frameHeight / sampleHeight).toInt().coerceIn(0, frameHeight - 1)
            for (x in 0 until sampleWidth) {
                val srcX = ((x + 0.5) * frameWidth / sampleWidth).toInt().coerceIn(0, frameWidth - 1)
                val luma = readLuma(srcX, srcY)
                    ?: return StageAEvaluation(
                        passed = false,
                        reason = "luma_read_failed",
                        metrics = StageAMetrics(
                            sharpness = 0.0,
                            motion = 1.0,
                            motionRaw = 1.0,
                            lumaMean = 0.0,
                            highlightPct = 0.0,
                            centerEdgeDensity = 0.0,
                            outerEdgeDensity = 0.0
                        ),
                        strictPassed = false,
                        relaxedPassed = false,
                        band = StageABand.BLOCKED,
                        isSharpnessHardBlock = false,
                        framingOutcome = FramingOutcome.FRAMING_HARD_FAIL
                    )
                current[index] = luma
                sum += luma.toLong()
                index += 1
            }
        }

        val lumaMean = if (total > 0) sum.toDouble() / total.toDouble() else 0.0
        val highlightPct = highlightPercentage(current, 245)
        val sharpness = laplacianVariance(current, sampleWidth, sampleHeight)
        val motionRaw = if (hasPreviousFrame) motionScore(current, previous) else 0.0
        val motionSmoothed = motionSmoother.push(motionRaw)

        val edgeWindow = computeEdgeWindowDensity(
            grid = current,
            width = sampleWidth,
            height = sampleHeight,
            edgeThreshold = thresholds.edgeThreshold,
            centerWindowRatio = thresholds.centerWindowRatio
        )

        val sharpnessHardBlock = sharpness < thresholds.minSharpnessVarOcr
        val relaxedMotionFail = motionSmoothed > thresholds.maxMotionScoreOcr
        val strictMotionFail = motionSmoothed > thresholds.maxMotionScoreCapture
        val strictSharpnessFail = sharpness < thresholds.minSharpnessVarCapture
        val underexposed = lumaMean < thresholds.minLumaMean
        val overexposed = lumaMean > thresholds.maxLumaMean
        val glare = highlightPct > thresholds.maxHighlightPct
        val centerEdgeFail = edgeWindow.centerDensity < thresholds.minCenterEdgeDensity
        val outerEdgeFail =
            edgeWindow.outerDensity > edgeWindow.centerDensity * thresholds.maxOuterToCenterEdgeRatio

        val framingOutcome = when {
            centerEdgeFail -> FramingOutcome.FRAMING_HARD_FAIL
            outerEdgeFail -> FramingOutcome.FRAMING_SOFT_FAIL
            else -> FramingOutcome.FRAMING_OK
        }
        val relaxedPassed = !sharpnessHardBlock &&
            !underexposed &&
            !overexposed &&
            !glare &&
            framingOutcome != FramingOutcome.FRAMING_HARD_FAIL &&
            !relaxedMotionFail
        val strictPassed =
            relaxedPassed &&
                framingOutcome == FramingOutcome.FRAMING_OK &&
                !strictSharpnessFail &&
                !strictMotionFail

        val reason = when {
            sharpnessHardBlock -> "sharpness_low_hard"
            lumaMean < thresholds.minLumaMean -> "underexposed"
            lumaMean > thresholds.maxLumaMean -> "overexposed"
            highlightPct > thresholds.maxHighlightPct -> "glare_highlights"
            edgeWindow.centerDensity < thresholds.minCenterEdgeDensity -> "card_not_centered_or_too_small"
            edgeWindow.outerDensity > edgeWindow.centerDensity * thresholds.maxOuterToCenterEdgeRatio ->
                "card_clipped_or_background_dominant"
            relaxedMotionFail -> "motion_high_relaxed"
            strictSharpnessFail -> "sharpness_low_capture"
            strictMotionFail -> "motion_high_capture"
            strictPassed -> "stage_a_pass"
            relaxedPassed -> "stage_a_relaxed_pass"
            else -> "stage_a_blocked"
        }

        val band = when {
            strictPassed -> StageABand.STRICT
            relaxedPassed -> StageABand.RELAXED
            else -> StageABand.BLOCKED
        }
        val metrics = StageAMetrics(
            sharpness = sharpness,
            motion = motionSmoothed,
            motionRaw = motionRaw,
            lumaMean = lumaMean,
            highlightPct = highlightPct,
            centerEdgeDensity = edgeWindow.centerDensity,
            outerEdgeDensity = edgeWindow.outerDensity
        )

        swapBuffers()
        hasPreviousFrame = true
        return StageAEvaluation(
            passed = strictPassed,
            reason = reason,
            metrics = metrics,
            strictPassed = strictPassed,
            relaxedPassed = relaxedPassed,
            band = band,
            isSharpnessHardBlock = sharpnessHardBlock,
            framingOutcome = framingOutcome
        )
    }

    private fun ensureBuffers(targetWidth: Int, targetHeight: Int) {
        if (sampleWidth == targetWidth && sampleHeight == targetHeight) {
            return
        }
        sampleWidth = targetWidth
        sampleHeight = targetHeight
        val size = sampleWidth * sampleHeight
        current = IntArray(size)
        previous = IntArray(size)
        hasPreviousFrame = false
    }

    private fun swapBuffers() {
        val tmp = previous
        previous = current
        current = tmp
    }
}

internal data class EdgeWindowDensity(
    val centerDensity: Double,
    val outerDensity: Double
)

private val SOFT_FAIL_FRAMING_REASONS = setOf(
    "card_clipped_or_background_dominant"
)

private val HARD_FAIL_FRAMING_REASONS = setOf(
    "card_not_centered_or_too_small",
    "center_edges_too_low",
    "card_area_too_small",
    "extreme_clip_detected"
)

internal fun classifyFramingOutcomeByReason(reason: String): FramingOutcome {
    return when (reason) {
        in SOFT_FAIL_FRAMING_REASONS -> FramingOutcome.FRAMING_SOFT_FAIL
        in HARD_FAIL_FRAMING_REASONS -> FramingOutcome.FRAMING_HARD_FAIL
        else -> FramingOutcome.FRAMING_OK
    }
}

internal fun downsampleLumaGrid(
    source: IntArray,
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int
): IntArray {
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
        return IntArray(0)
    }
    if (source.size < sourceWidth * sourceHeight) {
        return IntArray(0)
    }
    val output = IntArray(targetWidth * targetHeight)
    var idx = 0
    for (y in 0 until targetHeight) {
        val srcY = ((y + 0.5) * sourceHeight / targetHeight).toInt().coerceIn(0, sourceHeight - 1)
        for (x in 0 until targetWidth) {
            val srcX = ((x + 0.5) * sourceWidth / targetWidth).toInt().coerceIn(0, sourceWidth - 1)
            output[idx] = source[srcY * sourceWidth + srcX]
            idx += 1
        }
    }
    return output
}

internal fun laplacianVariance(grid: IntArray, width: Int, height: Int): Double {
    if (width < 3 || height < 3 || grid.size < width * height) return 0.0
    var lapSum = 0.0
    var lapSumSq = 0.0
    var count = 0
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val idx = y * width + x
            val center = grid[idx]
            val left = grid[idx - 1]
            val right = grid[idx + 1]
            val up = grid[idx - width]
            val down = grid[idx + width]
            val lap = (-4.0 * center) + left + right + up + down
            lapSum += lap
            lapSumSq += lap * lap
            count += 1
        }
    }
    if (count == 0) return 0.0
    val mean = lapSum / count.toDouble()
    return (lapSumSq / count.toDouble()) - (mean * mean)
}

internal fun motionScore(current: IntArray, previous: IntArray): Double {
    if (current.isEmpty() || previous.isEmpty() || current.size != previous.size) return 1.0
    var diff = 0L
    for (index in current.indices) {
        diff += abs(current[index] - previous[index]).toLong()
    }
    return diff.toDouble() / (current.size.toDouble() * 255.0)
}

internal fun highlightPercentage(grid: IntArray, threshold: Int): Double {
    if (grid.isEmpty()) return 0.0
    var highlights = 0
    for (value in grid) {
        if (value >= threshold) {
            highlights += 1
        }
    }
    return highlights.toDouble() / grid.size.toDouble()
}

internal fun computeEdgeWindowDensity(
    grid: IntArray,
    width: Int,
    height: Int,
    edgeThreshold: Int,
    centerWindowRatio: Double
): EdgeWindowDensity {
    if (width < 3 || height < 3 || grid.size < width * height) {
        return EdgeWindowDensity(centerDensity = 0.0, outerDensity = 0.0)
    }

    val centerRatio = centerWindowRatio.coerceIn(0.3, 0.9)
    val centerMarginX = ((1.0 - centerRatio) * width / 2.0).toInt().coerceAtLeast(1)
    val centerMarginY = ((1.0 - centerRatio) * height / 2.0).toInt().coerceAtLeast(1)
    val centerStartX = centerMarginX
    val centerEndX = (width - centerMarginX).coerceAtLeast(centerStartX + 1)
    val centerStartY = centerMarginY
    val centerEndY = (height - centerMarginY).coerceAtLeast(centerStartY + 1)

    var centerEdges = 0
    var centerSamples = 0
    var outerEdges = 0
    var outerSamples = 0

    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val idx = y * width + x
            val center = grid[idx]
            val right = grid[idx + 1]
            val down = grid[idx + width]
            val gradient = abs(center - right) + abs(center - down)
            val isEdge = gradient >= edgeThreshold
            val inCenter = x in centerStartX until centerEndX && y in centerStartY until centerEndY
            if (inCenter) {
                centerSamples += 1
                if (isEdge) {
                    centerEdges += 1
                }
            } else {
                outerSamples += 1
                if (isEdge) {
                    outerEdges += 1
                }
            }
        }
    }

    val centerDensity = if (centerSamples > 0) {
        centerEdges.toDouble() / centerSamples.toDouble()
    } else {
        0.0
    }
    val outerDensity = if (outerSamples > 0) {
        outerEdges.toDouble() / outerSamples.toDouble()
    } else {
        0.0
    }

    return EdgeWindowDensity(
        centerDensity = centerDensity,
        outerDensity = outerDensity
    )
}

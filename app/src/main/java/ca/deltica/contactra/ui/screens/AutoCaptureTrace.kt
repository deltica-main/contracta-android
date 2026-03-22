package ca.deltica.contactra.ui.screens

import java.io.File
import java.util.Locale

internal const val AUTO_CAPTURE_TRACE_SCHEMA_VERSION = 2
internal const val AUTO_CAPTURE_DECISION_PARSER_VERSION = "auto_capture_decision_v3"

internal data class AutoCaptureTraceConstantsSnapshot(
    val analysisIntervalMs: Long = 100L,
    val stableFramesRequired: Int = 2,
    val ocrStableFramesRequired: Int = 1,
    val readyChecksRequired: Int = 1,
    val minCaptureIntervalMs: Long = 900L,
    val firstCaptureMinIntervalMs: Long = 400L,
    val ocrIntervalMs: Long = 250L,
    val fastOcrIntervalMs: Long = 150L,
    val stageAStabilityGraceMs: Long = 300L,
    val refocusAfterMs: Long = Long.MAX_VALUE,
    val refocusMinIntervalMs: Long = Long.MAX_VALUE,
    val minOcrReadyScore: Double = 0.65,
    val allowSoftFailRelaxedCapture: Boolean = true,
    val softFailMinOcrReadyScore: Double = 0.85,
    val softFailReadyHitsRequired: Int = 2,
    val softFailReadyHitWindowMs: Long = 1200L,
    val relaxedNearReadyScoreMargin: Double = 0.05,
    val relaxedNearReadyStrikesRequired: Int = 2,
    val relaxedNearReadyStrikeWindowMs: Long = 1500L,
    val maxRelaxedCaptureMotionScore: Double = 0.075,
    val minCaptureSharpness: Double = 24.0,
    val minOcrBlocks: Int = 2,
    val minLumaMean: Double = 48.0,
    val maxLumaMean: Double = 220.0,
    val maxHighlightPct: Double = 0.20,
    val minCenterEdgeDensity: Double = 0.055,
    val maxOuterToCenterEdgeRatio: Double = 0.92
)

internal data class AutoCaptureTraceManifest(
    val schemaVersion: Int = AUTO_CAPTURE_TRACE_SCHEMA_VERSION,
    val appVersionName: String,
    val appVersionCode: Long,
    val parserVersion: String = AUTO_CAPTURE_DECISION_PARSER_VERSION,
    val deviceModel: String,
    val sessionId: String,
    val createdAtEpochMs: Long,
    val constantsSnapshot: AutoCaptureTraceConstantsSnapshot
)

internal data class AutoCaptureTraceRecord(
    val timestampMs: Long,
    val stageAPass: Boolean,
    val stageABand: StageABand,
    val stageAFailReason: String,
    val motionRaw: Double,
    val motionSmoothed: Double,
    val sharpness: Double,
    val exposureOk: Boolean,
    val highlightOk: Boolean,
    val edgeCenterScore: Double,
    val edgeOuterScore: Double,
    val framingOk: Boolean,
    val ocrAttempted: Boolean,
    val ocrReadyScore: Double,
    val ocrReady: Boolean,
    val ocrInFlight: Boolean,
    val cooldownRemainingMs: Long,
    val captureFired: Boolean
)

internal interface AutoCaptureTraceRecorder {
    val isEnabled: Boolean
    fun record(record: AutoCaptureTraceRecord)
    fun close()
}

internal object AutoCaptureTraceJsonCodec {
    fun encodeRecord(record: AutoCaptureTraceRecord): String {
        return buildString(512) {
            append('{')
            appendJsonLong("timestampMs", record.timestampMs)
            appendJsonBoolean("stageA_pass_boolean", record.stageAPass)
            appendJsonString("stageA_band_enum", record.stageABand.name.lowercase(Locale.US))
            appendJsonString("stageA_fail_reason_enum", record.stageAFailReason)
            appendJsonDouble("motion_raw", record.motionRaw)
            appendJsonDouble("motion_smoothed", record.motionSmoothed)
            appendJsonDouble("sharpness", record.sharpness)
            appendJsonBoolean("exposure_ok", record.exposureOk)
            appendJsonBoolean("highlight_ok", record.highlightOk)
            appendJsonDouble("edge_center_score", record.edgeCenterScore)
            appendJsonDouble("edge_outer_score", record.edgeOuterScore)
            appendJsonBoolean("framing_ok_boolean", record.framingOk)
            appendJsonBoolean("ocr_attempted_boolean", record.ocrAttempted)
            appendJsonDouble("ocr_ready_score", record.ocrReadyScore)
            appendJsonBoolean("ocr_ready_boolean", record.ocrReady)
            appendJsonBoolean("ocr_in_flight_boolean", record.ocrInFlight)
            appendJsonLong("cooldown_remaining_ms", record.cooldownRemainingMs)
            appendJsonBoolean("capture_fired_boolean", record.captureFired, withComma = false)
            append('}')
        }
    }

    fun decodeRecord(line: String): AutoCaptureTraceRecord {
        if (line.isBlank()) {
            throw IllegalArgumentException("Trace line is blank")
        }
        return AutoCaptureTraceRecord(
            timestampMs = readLong(line, "timestampMs"),
            stageAPass = readBoolean(line, "stageA_pass_boolean"),
            stageABand = readBand(line, "stageA_band_enum"),
            stageAFailReason = readString(line, "stageA_fail_reason_enum"),
            motionRaw = readDouble(line, "motion_raw"),
            motionSmoothed = readDouble(line, "motion_smoothed"),
            sharpness = readDouble(line, "sharpness"),
            exposureOk = readBoolean(line, "exposure_ok"),
            highlightOk = readBoolean(line, "highlight_ok"),
            edgeCenterScore = readDouble(line, "edge_center_score"),
            edgeOuterScore = readDouble(line, "edge_outer_score"),
            framingOk = readBoolean(line, "framing_ok_boolean"),
            ocrAttempted = readBoolean(line, "ocr_attempted_boolean"),
            ocrReadyScore = readDouble(line, "ocr_ready_score"),
            ocrReady = readBoolean(line, "ocr_ready_boolean"),
            ocrInFlight = readBoolean(line, "ocr_in_flight_boolean"),
            cooldownRemainingMs = readLong(line, "cooldown_remaining_ms"),
            captureFired = readBoolean(line, "capture_fired_boolean")
        )
    }

    fun encodeManifest(manifest: AutoCaptureTraceManifest): String {
        val c = manifest.constantsSnapshot
        return buildString(2048) {
            append('{')
            appendJsonLong("schemaVersion", manifest.schemaVersion.toLong())
            appendJsonString("appVersionName", manifest.appVersionName)
            appendJsonLong("appVersionCode", manifest.appVersionCode)
            appendJsonString("parserVersion", manifest.parserVersion)
            appendJsonString("deviceModel", manifest.deviceModel)
            appendJsonString("sessionId", manifest.sessionId)
            appendJsonLong("createdAtEpochMs", manifest.createdAtEpochMs)
            append("\"constantsSnapshot\":{")
            appendJsonLong("analysisIntervalMs", c.analysisIntervalMs)
            appendJsonLong("stableFramesRequired", c.stableFramesRequired.toLong())
            appendJsonLong("ocrStableFramesRequired", c.ocrStableFramesRequired.toLong())
            appendJsonLong("readyChecksRequired", c.readyChecksRequired.toLong())
            appendJsonLong("minCaptureIntervalMs", c.minCaptureIntervalMs)
            appendJsonLong("firstCaptureMinIntervalMs", c.firstCaptureMinIntervalMs)
            appendJsonLong("ocrIntervalMs", c.ocrIntervalMs)
            appendJsonLong("fastOcrIntervalMs", c.fastOcrIntervalMs)
            appendJsonLong("stageAStabilityGraceMs", c.stageAStabilityGraceMs)
            appendJsonLong("refocusAfterMs", c.refocusAfterMs)
            appendJsonLong("refocusMinIntervalMs", c.refocusMinIntervalMs)
            appendJsonDouble("minOcrReadyScore", c.minOcrReadyScore)
            appendJsonBoolean("allowSoftFailRelaxedCapture", c.allowSoftFailRelaxedCapture)
            appendJsonDouble("softFailMinOcrReadyScore", c.softFailMinOcrReadyScore)
            appendJsonLong("softFailReadyHitsRequired", c.softFailReadyHitsRequired.toLong())
            appendJsonLong("softFailReadyHitWindowMs", c.softFailReadyHitWindowMs)
            appendJsonDouble("relaxedNearReadyScoreMargin", c.relaxedNearReadyScoreMargin)
            appendJsonLong(
                "relaxedNearReadyStrikesRequired",
                c.relaxedNearReadyStrikesRequired.toLong()
            )
            appendJsonLong("relaxedNearReadyStrikeWindowMs", c.relaxedNearReadyStrikeWindowMs)
            appendJsonDouble("maxRelaxedCaptureMotionScore", c.maxRelaxedCaptureMotionScore)
            appendJsonDouble("minCaptureSharpness", c.minCaptureSharpness)
            appendJsonLong("minOcrBlocks", c.minOcrBlocks.toLong())
            appendJsonDouble("minLumaMean", c.minLumaMean)
            appendJsonDouble("maxLumaMean", c.maxLumaMean)
            appendJsonDouble("maxHighlightPct", c.maxHighlightPct)
            appendJsonDouble("minCenterEdgeDensity", c.minCenterEdgeDensity)
            appendJsonDouble(
                "maxOuterToCenterEdgeRatio",
                c.maxOuterToCenterEdgeRatio,
                withComma = false
            )
            append('}')
            append('}')
        }
    }

    fun decodeManifest(json: String): AutoCaptureTraceManifest {
        return AutoCaptureTraceManifest(
            schemaVersion = readLong(json, "schemaVersion").toInt(),
            appVersionName = readString(json, "appVersionName"),
            appVersionCode = readLong(json, "appVersionCode"),
            parserVersion = readString(json, "parserVersion"),
            deviceModel = readString(json, "deviceModel"),
            sessionId = readString(json, "sessionId"),
            createdAtEpochMs = readLong(json, "createdAtEpochMs"),
            constantsSnapshot = AutoCaptureTraceConstantsSnapshot(
                analysisIntervalMs = readLong(json, "analysisIntervalMs"),
                stableFramesRequired = readLong(json, "stableFramesRequired").toInt(),
                ocrStableFramesRequired = readLong(json, "ocrStableFramesRequired").toInt(),
                readyChecksRequired = readLong(json, "readyChecksRequired").toInt(),
                minCaptureIntervalMs = readLong(json, "minCaptureIntervalMs"),
                firstCaptureMinIntervalMs = readLong(json, "firstCaptureMinIntervalMs"),
                ocrIntervalMs = readLong(json, "ocrIntervalMs"),
                fastOcrIntervalMs = readLong(json, "fastOcrIntervalMs"),
                stageAStabilityGraceMs = readLong(json, "stageAStabilityGraceMs"),
                refocusAfterMs = readLong(json, "refocusAfterMs"),
                refocusMinIntervalMs = readLong(json, "refocusMinIntervalMs"),
                minOcrReadyScore = readDouble(json, "minOcrReadyScore"),
                allowSoftFailRelaxedCapture = readBooleanOrDefault(
                    json,
                    "allowSoftFailRelaxedCapture",
                    defaultValue = true
                ),
                softFailMinOcrReadyScore = readDoubleOrDefault(
                    json,
                    "softFailMinOcrReadyScore",
                    defaultValue = 0.85
                ),
                softFailReadyHitsRequired = readLongOrDefault(
                    json,
                    "softFailReadyHitsRequired",
                    defaultValue = 2L
                ).toInt(),
                softFailReadyHitWindowMs = readLongOrDefault(
                    json,
                    "softFailReadyHitWindowMs",
                    defaultValue = 1200L
                ),
                relaxedNearReadyScoreMargin = readDoubleOrDefault(
                    json,
                    "relaxedNearReadyScoreMargin",
                    defaultValue = 0.0
                ),
                relaxedNearReadyStrikesRequired = readLongOrDefault(
                    json,
                    "relaxedNearReadyStrikesRequired",
                    defaultValue = Int.MAX_VALUE.toLong()
                ).toInt(),
                relaxedNearReadyStrikeWindowMs = readLongOrDefault(
                    json,
                    "relaxedNearReadyStrikeWindowMs",
                    defaultValue = 1200L
                ),
                maxRelaxedCaptureMotionScore = readDoubleOrDefault(
                    json,
                    "maxRelaxedCaptureMotionScore",
                    defaultValue = 0.075
                ),
                minCaptureSharpness = readDoubleOrDefault(
                    json,
                    "minCaptureSharpness",
                    defaultValue = 24.0
                ),
                minOcrBlocks = readLong(json, "minOcrBlocks").toInt(),
                minLumaMean = readDouble(json, "minLumaMean"),
                maxLumaMean = readDouble(json, "maxLumaMean"),
                maxHighlightPct = readDouble(json, "maxHighlightPct"),
                minCenterEdgeDensity = readDouble(json, "minCenterEdgeDensity"),
                maxOuterToCenterEdgeRatio = readDouble(json, "maxOuterToCenterEdgeRatio")
            )
        )
    }

    private fun StringBuilder.appendJsonString(key: String, value: String, withComma: Boolean = true) {
        append('"').append(key).append("\":\"").append(escape(value)).append('"')
        if (withComma) {
            append(',')
        }
    }

    private fun StringBuilder.appendJsonLong(key: String, value: Long, withComma: Boolean = true) {
        append('"').append(key).append("\":").append(value)
        if (withComma) {
            append(',')
        }
    }

    private fun StringBuilder.appendJsonBoolean(key: String, value: Boolean, withComma: Boolean = true) {
        append('"').append(key).append("\":").append(value)
        if (withComma) {
            append(',')
        }
    }

    private fun StringBuilder.appendJsonDouble(key: String, value: Double, withComma: Boolean = true) {
        val asText = String.format(Locale.US, "%.6f", value)
        append('"').append(key).append("\":").append(asText)
        if (withComma) {
            append(',')
        }
    }

    private fun escape(input: String): String {
        return buildString(input.length + 8) {
            input.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    }

    private fun unescape(input: String): String {
        if (!input.contains('\\')) {
            return input
        }
        val out = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            if (ch == '\\' && i + 1 < input.length) {
                when (val next = input[i + 1]) {
                    '\\' -> out.append('\\')
                    '"' -> out.append('"')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    else -> {
                        out.append(next)
                    }
                }
                i += 2
            } else {
                out.append(ch)
                i += 1
            }
        }
        return out.toString()
    }

    private fun readString(json: String, key: String): String {
        val match = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").find(json)
            ?: error("Missing string key: $key")
        return unescape(match.groupValues[1])
    }

    private fun readLong(json: String, key: String): Long {
        val match = Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(json)
            ?: error("Missing long key: $key")
        return match.groupValues[1].toLong()
    }

    private fun readLongOrDefault(json: String, key: String, defaultValue: Long): Long {
        val match = Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(json) ?: return defaultValue
        return match.groupValues[1].toLong()
    }

    private fun readBoolean(json: String, key: String): Boolean {
        val match = Regex("\"$key\"\\s*:\\s*(true|false)").find(json)
            ?: error("Missing boolean key: $key")
        return match.groupValues[1].toBooleanStrict()
    }

    private fun readBooleanOrDefault(json: String, key: String, defaultValue: Boolean): Boolean {
        val match = Regex("\"$key\"\\s*:\\s*(true|false)").find(json) ?: return defaultValue
        return match.groupValues[1].toBooleanStrict()
    }

    private fun readDouble(json: String, key: String): Double {
        val match = Regex("\"$key\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").find(json)
            ?: error("Missing double key: $key")
        return match.groupValues[1].toDouble()
    }

    private fun readDoubleOrDefault(json: String, key: String, defaultValue: Double): Double {
        val match = Regex("\"$key\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").find(json) ?: return defaultValue
        return match.groupValues[1].toDouble()
    }

    private fun readBand(json: String, key: String): StageABand {
        return when (readString(json, key).lowercase(Locale.US)) {
            "strict" -> StageABand.STRICT
            "relaxed" -> StageABand.RELAXED
            else -> StageABand.BLOCKED
        }
    }
}

internal data class AutoCaptureTraceReplayReport(
    val cycleCount: Int,
    val timeToFirstCaptureMs: Long?,
    val captureFiredCount: Int,
    val softFailCaptureCount: Int,
    val softFailCapturePercent: Double,
    val blockedByStageAFailReasonPercent: Map<String, Double>,
    val ocrAttemptedPercent: Double,
    val ocrReadyPercent: Double,
    val readyButNotCapturedPercent: Double,
    val ocrReadyButNoCapturePercent: Double,
    val ocrReadyButNoCaptureReasonPercent: Map<String, Double>,
    val dominantBlockerReason: String?
)

internal class AutoCaptureTraceReplayRunner {
    fun replaySession(traceDir: File): AutoCaptureTraceReplayReport {
        val manifestFile = File(traceDir, "manifest.json")
        val traceFile = File(traceDir, "trace.jsonl")
        val manifest = if (manifestFile.exists()) {
            AutoCaptureTraceJsonCodec.decodeManifest(manifestFile.readText())
        } else {
            null
        }
        val records = readTraceRecords(traceFile)
        return replayRecords(records, manifest)
    }

    fun replayRecords(
        records: List<AutoCaptureTraceRecord>,
        manifest: AutoCaptureTraceManifest? = null,
        decisionPolicyOverride: AutoCaptureDecisionPolicy? = null
    ): AutoCaptureTraceReplayReport {
        if (records.isEmpty()) {
            return AutoCaptureTraceReplayReport(
                cycleCount = 0,
                timeToFirstCaptureMs = null,
                captureFiredCount = 0,
                softFailCaptureCount = 0,
                softFailCapturePercent = 0.0,
                blockedByStageAFailReasonPercent = emptyMap(),
                ocrAttemptedPercent = 0.0,
                ocrReadyPercent = 0.0,
                readyButNotCapturedPercent = 0.0,
                ocrReadyButNoCapturePercent = 0.0,
                ocrReadyButNoCaptureReasonPercent = emptyMap(),
                dominantBlockerReason = null
            )
        }

        val constants = manifest?.constantsSnapshot ?: AutoCaptureTraceConstantsSnapshot()
        val decisionPolicy = decisionPolicyOverride ?: AutoCaptureDecisionPolicy(
            minOcrReadyScore = constants.minOcrReadyScore,
            allowSoftFailRelaxedCapture = constants.allowSoftFailRelaxedCapture,
            softFailMinOcrReadyScore = constants.softFailMinOcrReadyScore,
            softFailReadyHitsRequired = constants.softFailReadyHitsRequired,
            softFailReadyHitWindowMs = constants.softFailReadyHitWindowMs,
            relaxedNearReadyScoreMargin = constants.relaxedNearReadyScoreMargin,
            relaxedNearReadyStrikesRequired = constants.relaxedNearReadyStrikesRequired,
            relaxedNearReadyStrikeWindowMs = constants.relaxedNearReadyStrikeWindowMs,
            maxRelaxedCaptureMotionScore = constants.maxRelaxedCaptureMotionScore,
            minCaptureSharpness = constants.minCaptureSharpness
        )
        val decisionEngine = AutoCaptureDecisionEngine(decisionPolicy)
        val stateMachine = AutoCaptureStateMachine(
            stableFramesRequired = constants.stableFramesRequired,
            ocrStableFramesRequired = constants.ocrStableFramesRequired,
            readyChecksRequired = constants.readyChecksRequired,
            ocrIntervalMs = constants.ocrIntervalMs,
            fastOcrIntervalMs = constants.fastOcrIntervalMs,
            minCaptureIntervalMs = constants.minCaptureIntervalMs,
            firstCaptureMinIntervalMs = constants.firstCaptureMinIntervalMs,
            stageAStabilityGraceMs = constants.stageAStabilityGraceMs,
            refocusAfterMs = constants.refocusAfterMs,
            refocusMinIntervalMs = constants.refocusMinIntervalMs
        )
        val ordered = records.sortedBy { it.timestampMs }
        stateMachine.reset(nowMs = ordered.first().timestampMs)
        decisionEngine.reset()

        var firstCaptureAt: Long? = null
        var ocrAttemptedCount = 0
        var ocrReadyCount = 0
        var captureFiredCount = 0
        var softFailCaptureCount = 0
        var ocrReadyNoCaptureCount = 0
        val failReasonCounts = LinkedHashMap<String, Int>()
        val ocrReadyNoCaptureReasonCounts = LinkedHashMap<String, Int>()
        val blockerReasonCounts = LinkedHashMap<String, Int>()

        ordered.forEach { record ->
            val eval = toStageAEvaluation(record, constants)
            val tick = stateMachine.onStageA(record.timestampMs, eval)
            if (record.ocrAttempted) {
                ocrAttemptedCount += 1
                stateMachine.onOcrReadiness(record.ocrReady)
            }
            if (record.ocrReady) {
                ocrReadyCount += 1
            }

            val decision = decisionEngine.evaluate(
                nowMs = record.timestampMs,
                tick = tick,
                stageAEvaluation = eval,
                ocrReadiness = OcrReadiness(
                    ready = record.ocrReady,
                    score = record.ocrReadyScore,
                    blocks = 0,
                    reason = if (record.ocrReady) "ocr_ready" else "ocr_not_ready"
                )
            )
            val shouldCaptureNow = decision.shouldCapture
            if (shouldCaptureNow && firstCaptureAt == null) {
                firstCaptureAt = record.timestampMs
            }
            if (shouldCaptureNow) {
                captureFiredCount += 1
                stateMachine.onCaptureTriggered(record.timestampMs)
                if (eval.isFramingSoftFail) {
                    softFailCaptureCount += 1
                }
            } else {
                val blocker = resolveBlockerReason(record, tick, eval, decision)
                blockerReasonCounts[blocker] = (blockerReasonCounts[blocker] ?: 0) + 1
            }

            if (record.stageAFailReason.isNotBlank() && record.stageAFailReason != "none") {
                failReasonCounts[record.stageAFailReason] =
                    (failReasonCounts[record.stageAFailReason] ?: 0) + 1
            }

            if (record.ocrReady && !shouldCaptureNow) {
                ocrReadyNoCaptureCount += 1
                val reason = when {
                    eval.isSharpnessHardBlock -> "sharpness_hard_block"
                    tick.cooldownRemainingMs > 0L -> "capture_cooldown"
                    !tick.stageAOcrStable -> "stage_a_ocr_unstable"
                    !tick.stageAStable -> "stage_a_capture_unstable"
                    !tick.stageBReady -> "stage_b_not_ready"
                    else -> decision.blockReason
                }
                ocrReadyNoCaptureReasonCounts[reason] = (ocrReadyNoCaptureReasonCounts[reason] ?: 0) + 1
            }
        }

        val cycleCount = ordered.size
        val startTs = ordered.first().timestampMs
        val dominantBlocker = blockerReasonCounts.entries
            .maxByOrNull { it.value }
            ?.key
        return AutoCaptureTraceReplayReport(
            cycleCount = cycleCount,
            timeToFirstCaptureMs = firstCaptureAt?.minus(startTs),
            captureFiredCount = captureFiredCount,
            softFailCaptureCount = softFailCaptureCount,
            softFailCapturePercent = toPercent(softFailCaptureCount, captureFiredCount),
            blockedByStageAFailReasonPercent = toPercentMap(failReasonCounts, cycleCount),
            ocrAttemptedPercent = toPercent(ocrAttemptedCount, cycleCount),
            ocrReadyPercent = toPercent(ocrReadyCount, cycleCount),
            readyButNotCapturedPercent = toPercent(ocrReadyNoCaptureCount, cycleCount),
            ocrReadyButNoCapturePercent = toPercent(ocrReadyNoCaptureCount, cycleCount),
            ocrReadyButNoCaptureReasonPercent = toPercentMap(
                ocrReadyNoCaptureReasonCounts,
                ocrReadyNoCaptureCount
            ),
            dominantBlockerReason = dominantBlocker
        )
    }

    fun readTraceRecords(traceJsonlFile: File): List<AutoCaptureTraceRecord> {
        if (!traceJsonlFile.exists()) {
            return emptyList()
        }
        return traceJsonlFile.useLines { lines ->
            lines
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { AutoCaptureTraceJsonCodec.decodeRecord(it) }
                .toList()
        }
    }

    private fun toStageAEvaluation(
        record: AutoCaptureTraceRecord,
        constants: AutoCaptureTraceConstantsSnapshot
    ): StageAEvaluation {
        val rawReason = record.stageAFailReason.ifBlank { "stage_a_blocked" }
        val reason = if (rawReason == "none") "stage_a_pass" else rawReason
        val framingOutcome = deriveFramingOutcome(record, constants)
        val sharpnessHardBlock = reason == "sharpness_low_hard"
        val exposureFail = !record.exposureOk || reason == "underexposed" || reason == "overexposed"
        val highlightFail = !record.highlightOk || reason == "glare_highlights"
        val relaxedMotionFail =
            reason == "motion_high_relaxed" ||
                record.motionSmoothed > constants.maxRelaxedCaptureMotionScore
        val strictSharpnessFail =
            reason == "sharpness_low_capture" || record.sharpness < constants.minCaptureSharpness
        val strictMotionFail =
            reason == "motion_high_capture" ||
                record.motionSmoothed > REPLAY_STRICT_CAPTURE_MOTION_THRESHOLD
        val relaxedPassed = !sharpnessHardBlock &&
            !exposureFail &&
            !highlightFail &&
            !relaxedMotionFail &&
            framingOutcome != FramingOutcome.FRAMING_HARD_FAIL
        val strictPassed =
            relaxedPassed &&
                framingOutcome == FramingOutcome.FRAMING_OK &&
                !strictSharpnessFail &&
                !strictMotionFail
        val band = when {
            strictPassed -> StageABand.STRICT
            relaxedPassed -> StageABand.RELAXED
            else -> StageABand.BLOCKED
        }
        val resolvedReason = when {
            strictPassed -> "stage_a_pass"
            rawReason != "none" -> rawReason
            relaxedPassed -> "stage_a_relaxed_pass"
            else -> "stage_a_blocked"
        }
        return StageAEvaluation(
            passed = strictPassed,
            reason = resolvedReason,
            metrics = StageAMetrics(
                sharpness = record.sharpness,
                motion = record.motionSmoothed,
                motionRaw = record.motionRaw,
                lumaMean = if (record.exposureOk) 120.0 else 0.0,
                highlightPct = if (record.highlightOk) 0.0 else 1.0,
                centerEdgeDensity = record.edgeCenterScore,
                outerEdgeDensity = record.edgeOuterScore
            ),
            strictPassed = strictPassed,
            relaxedPassed = relaxedPassed,
            band = band,
            isSharpnessHardBlock = sharpnessHardBlock,
            framingOutcome = framingOutcome
        )
    }

    private fun deriveFramingOutcome(
        record: AutoCaptureTraceRecord,
        constants: AutoCaptureTraceConstantsSnapshot
    ): FramingOutcome {
        val byReason = classifyFramingOutcomeByReason(record.stageAFailReason)
        if (byReason != FramingOutcome.FRAMING_OK) {
            return byReason
        }
        val centerHardFail = record.edgeCenterScore < constants.minCenterEdgeDensity
        if (centerHardFail) {
            return FramingOutcome.FRAMING_HARD_FAIL
        }
        val outerSoftFail =
            record.edgeOuterScore > record.edgeCenterScore * constants.maxOuterToCenterEdgeRatio
        return if (outerSoftFail) {
            FramingOutcome.FRAMING_SOFT_FAIL
        } else {
            FramingOutcome.FRAMING_OK
        }
    }

    private fun resolveBlockerReason(
        record: AutoCaptureTraceRecord,
        tick: AutoCaptureTick,
        eval: StageAEvaluation,
        decision: AutoCaptureDecisionResult
    ): String {
        if (record.stageAFailReason.isNotBlank() && record.stageAFailReason != "none") {
            return "stage_a:${record.stageAFailReason}"
        }
        return when {
            eval.isSharpnessHardBlock -> "stage_a:sharpness_hard_block"
            !tick.stageAOcrStable -> "stage_a:stage_a_ocr_unstable"
            !tick.stageAStable -> "stage_a:stage_a_capture_unstable"
            tick.cooldownRemainingMs > 0L -> "capture_cooldown"
            !tick.stageBReady -> "stage_b_not_ready"
            else -> decision.blockReason
        }
    }

    private fun toPercentMap(counts: Map<String, Int>, denominator: Int): Map<String, Double> {
        if (denominator <= 0 || counts.isEmpty()) {
            return emptyMap()
        }
        return counts.entries
            .sortedByDescending { it.value }
            .associate { (key, value) -> key to toPercent(value, denominator) }
    }

    private fun toPercent(count: Int, denominator: Int): Double {
        if (denominator <= 0) {
            return 0.0
        }
        return (count.toDouble() / denominator.toDouble()) * 100.0
    }

    companion object {
        fun legacyDecisionPolicy(constants: AutoCaptureTraceConstantsSnapshot): AutoCaptureDecisionPolicy {
            return AutoCaptureDecisionPolicy(
                minOcrReadyScore = constants.minOcrReadyScore,
                allowSoftFailRelaxedCapture = false,
                softFailMinOcrReadyScore = constants.softFailMinOcrReadyScore,
                softFailReadyHitsRequired = constants.softFailReadyHitsRequired,
                softFailReadyHitWindowMs = constants.softFailReadyHitWindowMs,
                relaxedNearReadyScoreMargin = 0.0,
                relaxedNearReadyStrikesRequired = Int.MAX_VALUE,
                relaxedNearReadyStrikeWindowMs = constants.relaxedNearReadyStrikeWindowMs,
                maxRelaxedCaptureMotionScore = constants.maxRelaxedCaptureMotionScore,
                minCaptureSharpness = constants.minCaptureSharpness
            )
        }
    }
}

private const val REPLAY_STRICT_CAPTURE_MOTION_THRESHOLD = 0.055

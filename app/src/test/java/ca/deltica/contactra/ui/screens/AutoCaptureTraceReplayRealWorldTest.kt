package ca.deltica.contactra.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AutoCaptureTraceReplayRealWorldTest {

    private val replayRunner = AutoCaptureTraceReplayRunner()

    private val legacyPolicy = AutoCaptureDecisionPolicy(
        minOcrReadyScore = 0.65,
        allowSoftFailRelaxedCapture = false,
        softFailMinOcrReadyScore = 0.85,
        softFailReadyHitsRequired = 2,
        softFailReadyHitWindowMs = 1200L,
        relaxedNearReadyScoreMargin = 0.0,
        relaxedNearReadyStrikesRequired = Int.MAX_VALUE,
        relaxedNearReadyStrikeWindowMs = 1500L,
        maxRelaxedCaptureMotionScore = 0.075,
        minCaptureSharpness = 24.0
    )

    private val improvedPolicy = AutoCaptureDecisionPolicy(
        minOcrReadyScore = 0.72,
        allowSoftFailRelaxedCapture = true,
        softFailMinOcrReadyScore = 0.90,
        softFailReadyHitsRequired = 3,
        softFailReadyHitWindowMs = 1500L,
        relaxedNearReadyScoreMargin = 0.03,
        relaxedNearReadyStrikesRequired = 3,
        relaxedNearReadyStrikeWindowMs = 1800L,
        maxRelaxedCaptureMotionScore = 0.075,
        minCaptureSharpness = 24.0
    )

    @Test
    fun realSessionTrace_softFramingCapturePolicy_recoversCaptureWhileLegacyStalls() {
        val records = loadRecords("auto_capture_traces/session_1771251929465_988857328.jsonl")

        val baseline = replayRunner.replayRecords(records, decisionPolicyOverride = legacyPolicy)
        val improved = replayRunner.replayRecords(records, decisionPolicyOverride = improvedPolicy)

        printReport("baseline_session_1771251929465_988857328", baseline)
        printReport("improved_session_1771251929465_988857328", improved)

        assertEquals(0, baseline.captureFiredCount)
        assertEquals(null, baseline.timeToFirstCaptureMs)
        assertTrue(
            (baseline.blockedByStageAFailReasonPercent["card_clipped_or_background_dominant"] ?: 0.0) > 70.0
        )
        assertTrue(baseline.ocrReadyPercent > 50.0)

        assertNotNull(improved.timeToFirstCaptureMs)
        assertTrue((improved.timeToFirstCaptureMs ?: Long.MAX_VALUE) <= 3_000L)
        assertTrue(improved.captureFiredCount > baseline.captureFiredCount)
        assertTrue(improved.softFailCapturePercent > 0.0)
        assertNotNull(improved.dominantBlockerReason)
    }

    @Test
    fun tableTrace_doesNotAutocaptureWhenStabilityWindowIsTooShort() {
        val tableRecords = loadRecords("auto_capture_traces/table_success_case.jsonl")

        val baseline = replayRunner.replayRecords(tableRecords, decisionPolicyOverride = legacyPolicy)
        val improved = replayRunner.replayRecords(tableRecords, decisionPolicyOverride = improvedPolicy)

        printReport("baseline_table", baseline)
        printReport("improved_table", improved)

        assertEquals(0, baseline.captureFiredCount)
        assertEquals(0, improved.captureFiredCount)
    }

    @Test
    fun highMotionTrace_neverCaptures() {
        val highMotionRecords = loadRecords("auto_capture_traces/high_motion_case.jsonl")
        val baseline = replayRunner.replayRecords(highMotionRecords, decisionPolicyOverride = legacyPolicy)
        val improved = replayRunner.replayRecords(highMotionRecords, decisionPolicyOverride = improvedPolicy)

        printReport("baseline_high_motion", baseline)
        printReport("improved_high_motion", improved)

        assertEquals(0, baseline.captureFiredCount)
        assertEquals(0, improved.captureFiredCount)
    }

    private fun loadRecords(resourcePath: String): List<AutoCaptureTraceRecord> {
        val classLoader = checkNotNull(javaClass.classLoader) { "ClassLoader unavailable" }
        val stream = classLoader.getResourceAsStream(resourcePath)
            ?: error("Missing fixture: $resourcePath")
        return stream.bufferedReader().useLines { lines ->
            lines
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { AutoCaptureTraceJsonCodec.decodeRecord(it) }
                .toList()
        }
    }

    private fun printReport(label: String, report: AutoCaptureTraceReplayReport) {
        println(
            buildString {
                append("[$label] ")
                append("time_to_first_capture_ms=${report.timeToFirstCaptureMs ?: -1} ")
                append("capture_fired_count=${report.captureFiredCount} ")
                append("percent_ocr_attempted=${String.format(Locale.US, "%.2f", report.ocrAttemptedPercent)} ")
                append("percent_ocr_ready=${String.format(Locale.US, "%.2f", report.ocrReadyPercent)} ")
                append(
                    "percent_captures_fired_under_soft_fail_framing=${
                        String.format(Locale.US, "%.2f", report.softFailCapturePercent)
                    } "
                )
                append("percent_ready_but_not_captured=${String.format(Locale.US, "%.2f", report.readyButNotCapturedPercent)} ")
                append("dominant_blocker_reason=${report.dominantBlockerReason ?: "none"} ")
                append("stage_a_blockers=${report.blockedByStageAFailReasonPercent} ")
                append("ready_not_captured_reasons=${report.ocrReadyButNoCaptureReasonPercent}")
            }
        )
    }
}

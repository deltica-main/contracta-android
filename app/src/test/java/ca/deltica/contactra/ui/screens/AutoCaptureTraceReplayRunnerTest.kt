package ca.deltica.contactra.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCaptureTraceReplayRunnerTest {

    private val replayRunner = AutoCaptureTraceReplayRunner()

    @Test
    fun tableSuccessCase_doesNotCaptureOnBriefUnstableWindow() {
        val records = loadRecords("auto_capture_traces/table_success_case.jsonl")
        val report = replayRunner.replayRecords(records)

        assertEquals(0, report.captureFiredCount)
        assertEquals(null, report.timeToFirstCaptureMs)
        assertTrue(report.ocrAttemptedPercent > 0.0)
    }

    @Test
    fun handheldHesitantCase_blocksCaptureWhenStabilityIsInsufficient() {
        val records = loadRecords("auto_capture_traces/handheld_hesitant_case.jsonl")
        val report = replayRunner.replayRecords(records)

        assertEquals(0, report.captureFiredCount)
        assertEquals(null, report.timeToFirstCaptureMs)
        assertTrue(report.ocrAttemptedPercent > 0.0)
        assertTrue((report.blockedByStageAFailReasonPercent["sharpness_low_hard"] ?: 0.0) > 0.0)
    }

    @Test
    fun highMotionCase_neverCaptures() {
        val records = loadRecords("auto_capture_traces/high_motion_case.jsonl")
        val report = replayRunner.replayRecords(records)

        assertEquals(0, report.captureFiredCount)
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
}

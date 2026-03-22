package ca.deltica.contactra.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessCardReadinessEvaluatorTest {

    @Test
    fun cardLikeSignalsAreAccepted() {
        val readiness = BusinessCardReadinessEvaluator.evaluate(
            input = BusinessCardEvidenceInput(
                frameWidth = 420,
                frameHeight = 260,
                blockCount = 3,
                lineCount = 6,
                text = "Alex Morgan\nAccount Manager\nNorth Peak Solutions\nalex@northpeak.com\n+1 (416) 555-1200\nwww.northpeak.com",
                lines = listOf(
                    line("Alex Morgan", 40f, 52f, 250f, 80f),
                    line("Account Manager", 40f, 84f, 260f, 108f),
                    line("North Peak Solutions", 40f, 112f, 330f, 136f),
                    line("alex@northpeak.com", 40f, 142f, 320f, 168f),
                    line("+1 (416) 555-1200", 40f, 172f, 280f, 196f),
                    line("www.northpeak.com", 40f, 202f, 250f, 226f)
                ),
                stageAEvaluation = stageEvaluation(
                    centerEdge = 0.13,
                    outerEdge = 0.05,
                    framingOutcome = FramingOutcome.FRAMING_OK
                )
            )
        )

        assertTrue(readiness.ready)
        assertTrue(readiness.score >= 0.70)
    }

    @Test
    fun randomPosterTextIsRejected() {
        val readiness = BusinessCardReadinessEvaluator.evaluate(
            input = BusinessCardEvidenceInput(
                frameWidth = 420,
                frameHeight = 260,
                blockCount = 3,
                lineCount = 4,
                text = "SUMMER CLEARANCE\nUP TO 50% OFF\nLIMITED TIME\nSHOP TODAY",
                lines = listOf(
                    line("SUMMER CLEARANCE", 20f, 40f, 390f, 88f),
                    line("UP TO 50% OFF", 24f, 96f, 380f, 142f),
                    line("LIMITED TIME", 24f, 148f, 340f, 192f),
                    line("SHOP TODAY", 24f, 198f, 300f, 236f)
                ),
                stageAEvaluation = stageEvaluation(
                    centerEdge = 0.12,
                    outerEdge = 0.05,
                    framingOutcome = FramingOutcome.FRAMING_OK
                )
            )
        )

        assertFalse(readiness.ready)
        assertTrue(readiness.reason.contains("signal", ignoreCase = true))
    }

    @Test
    fun weakCardRegionBlocksEvenWhenTextLooksContactLike() {
        val readiness = BusinessCardReadinessEvaluator.evaluate(
            input = BusinessCardEvidenceInput(
                frameWidth = 420,
                frameHeight = 260,
                blockCount = 3,
                lineCount = 5,
                text = "Jamie Lee\nFounder\nOrbit Labs\njamie@orbitlabs.io\n+1 647 555 9920",
                lines = listOf(
                    line("Jamie Lee", 55f, 60f, 230f, 88f),
                    line("Founder", 55f, 90f, 170f, 116f),
                    line("Orbit Labs", 55f, 120f, 200f, 146f),
                    line("jamie@orbitlabs.io", 55f, 150f, 290f, 176f),
                    line("+1 647 555 9920", 55f, 182f, 260f, 208f)
                ),
                stageAEvaluation = stageEvaluation(
                    centerEdge = 0.03,
                    outerEdge = 0.06,
                    framingOutcome = FramingOutcome.FRAMING_HARD_FAIL
                )
            )
        )

        assertFalse(readiness.ready)
        assertTrue(readiness.reason.startsWith("card_region"))
    }

    private fun line(text: String, left: Float, top: Float, right: Float, bottom: Float): BusinessCardTextLine {
        return BusinessCardTextLine(text = text, left = left, top = top, right = right, bottom = bottom)
    }

    private fun stageEvaluation(
        centerEdge: Double,
        outerEdge: Double,
        framingOutcome: FramingOutcome
    ): StageAEvaluation {
        return StageAEvaluation(
            passed = framingOutcome == FramingOutcome.FRAMING_OK,
            reason = if (framingOutcome == FramingOutcome.FRAMING_OK) "stage_a_pass" else "card_not_centered_or_too_small",
            metrics = StageAMetrics(
                sharpness = 30.0,
                motion = 0.03,
                motionRaw = 0.03,
                lumaMean = 124.0,
                highlightPct = 0.08,
                centerEdgeDensity = centerEdge,
                outerEdgeDensity = outerEdge
            ),
            strictPassed = framingOutcome == FramingOutcome.FRAMING_OK,
            relaxedPassed = framingOutcome != FramingOutcome.FRAMING_HARD_FAIL,
            band = if (framingOutcome == FramingOutcome.FRAMING_OK) StageABand.STRICT else StageABand.BLOCKED,
            isSharpnessHardBlock = false,
            framingOutcome = framingOutcome
        )
    }
}

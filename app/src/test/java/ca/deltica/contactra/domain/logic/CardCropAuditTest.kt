package ca.deltica.contactra.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardCropAuditTest {

    @Test
    fun convexity_check_rejects_concave_quad() {
        val concave = CropQuad(
            topLeft = CropPoint(80f, 100f),
            topRight = CropPoint(700f, 140f),
            bottomRight = CropPoint(300f, 280f),
            bottomLeft = CropPoint(120f, 520f)
        )

        assertFalse(CardCropAudit.isConvex(concave))
    }

    @Test
    fun point_ordering_orders_expected_corners() {
        val points = listOf(
            CropPoint(615f, 120f),
            CropPoint(620f, 420f),
            CropPoint(110f, 130f),
            CropPoint(120f, 410f)
        )

        val ordered = CardCropAudit.orderCorners(points)

        assertNotNull(ordered)
        assertEquals(CropPoint(110f, 130f), ordered?.topLeft)
        assertEquals(CropPoint(615f, 120f), ordered?.topRight)
        assertEquals(CropPoint(620f, 420f), ordered?.bottomRight)
        assertEquals(CropPoint(120f, 410f), ordered?.bottomLeft)
        assertTrue(CardCropAudit.hasConsistentCornerOrder(ordered!!))
    }

    @Test
    fun warp_aggressiveness_flags_extreme_ratios() {
        val aggressive = CropQuad(
            topLeft = CropPoint(60f, 150f),
            topRight = CropPoint(760f, 170f),
            bottomRight = CropPoint(500f, 420f),
            bottomLeft = CropPoint(240f, 410f)
        )

        val evaluation = CardCropAudit.evaluate(
            quad = aggressive,
            imageWidth = 820,
            imageHeight = 620,
            outputWidth = 620,
            outputHeight = 320
        )

        assertTrue(evaluation.warpAggressive)
    }

    @Test
    fun transform_selection_picks_expected_mode() {
        val perspectiveCandidate = CropQuad(
            topLeft = CropPoint(110f, 140f),
            topRight = CropPoint(700f, 120f),
            bottomRight = CropPoint(620f, 460f),
            bottomLeft = CropPoint(120f, 430f)
        )
        val perspectiveEval = CardCropAudit.evaluate(
            quad = perspectiveCandidate,
            imageWidth = 820,
            imageHeight = 620,
            outputWidth = 600,
            outputHeight = 340
        )
        assertEquals(CardCropTransformType.PERSPECTIVE_WARP, CardCropAudit.chooseTransform(perspectiveEval))

        val nearFlatCandidate = CropQuad(
            topLeft = CropPoint(130f, 160f),
            topRight = CropPoint(700f, 165f),
            bottomRight = CropPoint(695f, 470f),
            bottomLeft = CropPoint(125f, 465f)
        )
        val nearFlatEval = CardCropAudit.evaluate(
            quad = nearFlatCandidate,
            imageWidth = 860,
            imageHeight = 640,
            outputWidth = 570,
            outputHeight = 305
        )
        assertEquals(CardCropTransformType.BOUNDING_BOX, CardCropAudit.chooseTransform(nearFlatEval))

        val invalid = CropQuad(
            topLeft = CropPoint(-20f, 100f),
            topRight = CropPoint(600f, 120f),
            bottomRight = CropPoint(640f, 420f),
            bottomLeft = CropPoint(80f, 410f)
        )
        val invalidEval = CardCropAudit.evaluate(
            quad = invalid,
            imageWidth = 820,
            imageHeight = 620,
            outputWidth = 580,
            outputHeight = 330
        )
        assertEquals(CardCropTransformType.CENTER_FALLBACK, CardCropAudit.chooseTransform(invalidEval))
    }
}

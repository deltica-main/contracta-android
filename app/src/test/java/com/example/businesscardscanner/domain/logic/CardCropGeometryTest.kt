package com.example.businesscardscanner.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardCropGeometryTest {

    @Test
    fun quadFromExtremes_orders_points_into_expected_corners() {
        val points = listOf(
            CropPoint(620f, 420f),
            CropPoint(120f, 410f),
            CropPoint(110f, 130f),
            CropPoint(615f, 120f)
        )

        val quad = CardCropGeometry.quadFromExtremes(points)

        assertEquals(CropPoint(110f, 130f), quad?.topLeft)
        assertEquals(CropPoint(615f, 120f), quad?.topRight)
        assertEquals(CropPoint(620f, 420f), quad?.bottomRight)
        assertEquals(CropPoint(120f, 410f), quad?.bottomLeft)
    }

    @Test
    fun isValidCardBounds_accepts_typical_card_shape() {
        val quad = CropQuad(
            topLeft = CropPoint(120f, 140f),
            topRight = CropPoint(680f, 160f),
            bottomRight = CropPoint(660f, 450f),
            bottomLeft = CropPoint(100f, 430f)
        )

        assertTrue(
            CardCropGeometry.isValidCardBounds(
                quad = quad,
                imageWidth = 800,
                imageHeight = 600
            )
        )
    }

    @Test
    fun isValidCardBounds_rejects_bounds_when_area_is_too_small() {
        val quad = CropQuad(
            topLeft = CropPoint(20f, 30f),
            topRight = CropPoint(90f, 32f),
            bottomRight = CropPoint(88f, 68f),
            bottomLeft = CropPoint(18f, 65f)
        )

        assertFalse(
            CardCropGeometry.isValidCardBounds(
                quad = quad,
                imageWidth = 800,
                imageHeight = 600
            )
        )
    }

    @Test
    fun expandAndClamp_keeps_points_inside_image() {
        val quad = CropQuad(
            topLeft = CropPoint(8f, 8f),
            topRight = CropPoint(300f, 12f),
            bottomRight = CropPoint(302f, 190f),
            bottomLeft = CropPoint(10f, 188f)
        )

        val expanded = CardCropGeometry.expandAndClamp(
            quad = quad,
            marginRatio = 0.2f,
            maxWidth = 320,
            maxHeight = 200
        )

        expanded.asList().forEach { point ->
            assertTrue(point.x in 0f..319f)
            assertTrue(point.y in 0f..199f)
        }
        assertTrue(CardCropGeometry.areaRatio(expanded, 320, 200) >= CardCropGeometry.areaRatio(quad, 320, 200))
    }

    @Test
    fun expandAndClamp_zeroMargin_preserves_quad() {
        val quad = CropQuad(
            topLeft = CropPoint(40f, 32f),
            topRight = CropPoint(260f, 34f),
            bottomRight = CropPoint(258f, 160f),
            bottomLeft = CropPoint(42f, 158f)
        )

        val expanded = CardCropGeometry.expandAndClamp(
            quad = quad,
            marginRatio = 0f,
            maxWidth = 300,
            maxHeight = 200
        )

        assertEquals(quad, expanded)
    }

    @Test
    fun isValidCardBounds_rejects_extreme_aspect_ratio() {
        val quad = CropQuad(
            topLeft = CropPoint(80f, 180f),
            topRight = CropPoint(740f, 180f),
            bottomRight = CropPoint(740f, 260f),
            bottomLeft = CropPoint(80f, 260f)
        )

        assertFalse(
            CardCropGeometry.isValidCardBounds(
                quad = quad,
                imageWidth = 800,
                imageHeight = 600
            )
        )
    }
}

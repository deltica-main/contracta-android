package com.example.businesscardscanner.domain.logic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardImageCropperAndroidTest {

    @Test
    fun cropCard_detects_clean_card_on_table_fixture() {
        val source = createCleanTableFixture()

        val cropped = CardImageCropper.cropCard(source)

        assertNotNull(cropped)
        val aspect = maxOf(cropped!!.width, cropped.height).toFloat() /
            minOf(cropped.width, cropped.height).toFloat()
        assertTrue(aspect in 1.2f..2.4f)
    }

    @Test
    fun cropCard_detects_handheld_background_fixture() {
        val source = createHandheldBackgroundFixture()

        val cropped = CardImageCropper.cropCard(source)

        assertNotNull(cropped)
        val aspect = maxOf(cropped!!.width, cropped.height).toFloat() /
            minOf(cropped.width, cropped.height).toFloat()
        assertTrue(aspect in 1.2f..2.4f)
    }

    @Test
    fun cropCard_detects_angled_perspective_fixture() {
        val source = createAngledCardFixture()

        val cropped = CardImageCropper.cropCard(source)

        assertNotNull(cropped)
        val aspect = maxOf(cropped!!.width, cropped.height).toFloat() /
            minOf(cropped.width, cropped.height).toFloat()
        assertTrue(aspect in 1.2f..2.4f)
    }

    @Test
    fun cropCard_skewed_fixture_chooses_safe_fallback_mode() {
        val source = createExtremeSkewFixture()

        val attempt = CardImageCropper.cropCardAttempt(source)

        assertTrue(attempt.auditRecord.transformType != CardCropTransformType.PERSPECTIVE_WARP)
        assertTrue(
            attempt.result != null || attempt.auditRecord.transformType == CardCropTransformType.CENTER_FALLBACK
        )
    }

    private fun createCleanTableFixture(): Bitmap {
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(218, 224, 230))

        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(70, 80, 96)
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        canvas.drawRect(180f, 170f, 1030f, 650f, cardFill)
        canvas.drawRect(180f, 170f, 1030f, 650f, cardBorder)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 44, 61)
            textSize = 44f
            style = Paint.Style.FILL
        }
        canvas.drawText("Taylor Quinn", 250f, 320f, textPaint)
        textPaint.textSize = 34f
        canvas.drawText("Sales Director", 250f, 380f, textPaint)
        canvas.drawText("taylor@example.com", 250f, 450f, textPaint)
        return bitmap
    }

    private fun createHandheldBackgroundFixture(): Bitmap {
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(195, 182, 170))

        val cardPath = Path().apply {
            moveTo(160f, 230f)
            lineTo(970f, 160f)
            lineTo(1060f, 560f)
            lineTo(240f, 650f)
            close()
        }
        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(252, 252, 248)
            style = Paint.Style.FILL
        }
        val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(63, 72, 88)
            style = Paint.Style.STROKE
            strokeWidth = 9f
        }
        canvas.drawPath(cardPath, cardFill)
        canvas.drawPath(cardPath, cardBorder)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(39, 47, 63)
            textSize = 43f
            style = Paint.Style.FILL
        }
        canvas.drawText("Morgan Hale", 320f, 350f, textPaint)
        textPaint.textSize = 32f
        canvas.drawText("Operations Lead", 320f, 408f, textPaint)
        canvas.drawText("morgan@sample.io", 320f, 470f, textPaint)
        return bitmap
    }

    private fun createAngledCardFixture(): Bitmap {
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(233, 238, 242))

        val cardPath = Path().apply {
            moveTo(250f, 160f)
            lineTo(1010f, 230f)
            lineTo(930f, 610f)
            lineTo(190f, 550f)
            close()
        }
        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(45, 57, 73)
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        canvas.drawPath(cardPath, cardFill)
        canvas.drawPath(cardPath, cardBorder)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 44, 61)
            textSize = 46f
            style = Paint.Style.FILL
        }
        canvas.drawText("Jordan Lee", 330f, 320f, textPaint)
        textPaint.textSize = 34f
        canvas.drawText("Product Manager", 330f, 380f, textPaint)
        canvas.drawText("jordan@example.com", 330f, 450f, textPaint)
        canvas.drawText("example.com", 330f, 510f, textPaint)
        return bitmap
    }

    private fun createExtremeSkewFixture(): Bitmap {
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(224, 210, 190))

        val cardPath = Path().apply {
            moveTo(120f, 180f)
            lineTo(1100f, 250f)
            lineTo(640f, 610f)
            lineTo(420f, 590f)
            close()
        }
        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(58, 66, 78)
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        canvas.drawPath(cardPath, cardFill)
        canvas.drawPath(cardPath, cardBorder)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(38, 47, 63)
            textSize = 42f
            style = Paint.Style.FILL
        }
        canvas.drawText("Skewed Card", 450f, 380f, textPaint)
        textPaint.textSize = 30f
        canvas.drawText("skew@example.com", 450f, 432f, textPaint)
        return bitmap
    }
}

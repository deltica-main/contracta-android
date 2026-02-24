package com.example.businesscardscanner.data.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Wrapper around ML Kit's text recognition API. Provides a suspend function to
 * extract text from a bitmap. Errors are propagated via exceptions.
 */
object TextRecognitionManager {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    data class RecognizedLine(
        val text: String,
        val left: Float? = null,
        val top: Float? = null,
        val right: Float? = null,
        val bottom: Float? = null
    )

    data class StructuredRecognitionResult(
        val text: String,
        val lines: List<RecognizedLine>,
        val blockCount: Int,
        val lineCount: Int
    )

    data class QuickReadinessResult(
        val text: String,
        val blockCount: Int,
        val lineCount: Int
    )

    suspend fun recognizeTextStructured(
        bitmap: Bitmap,
        rotationDegrees: Int = 0
    ): StructuredRecognitionResult {
        val normalizedRotation = when (rotationDegrees) {
            0, 90, 180, 270 -> rotationDegrees
            else -> 0
        }
        val image = InputImage.fromBitmap(bitmap, normalizedRotation)
        val result = recognizer.process(image).await()
        val extractedLines = buildList {
            result.textBlocks.forEach { block ->
                block.lines.forEach { line ->
                    val text = line.text.trim()
                    if (text.isNotBlank()) {
                        add(
                            RecognizedLine(
                                text = text,
                                left = line.boundingBox?.left?.toFloat(),
                                top = line.boundingBox?.top?.toFloat(),
                                right = line.boundingBox?.right?.toFloat(),
                                bottom = line.boundingBox?.bottom?.toFloat()
                            )
                        )
                    }
                }
            }
        }
        val lines = sortLinesByPosition(extractedLines)
        return StructuredRecognitionResult(
            text = result.text,
            blockCount = result.textBlocks.size,
            lineCount = lines.size,
            lines = lines
        )
    }

    suspend fun recognizeTextQuick(bitmap: Bitmap, rotationDegrees: Int = 0): QuickReadinessResult {
        val result = recognizeTextStructured(bitmap, rotationDegrees)
        return QuickReadinessResult(
            text = result.text,
            blockCount = result.blockCount,
            lineCount = result.lineCount
        )
    }

    suspend fun recognizeText(bitmap: Bitmap, rotationDegrees: Int = 0): String {
        return recognizeTextQuick(bitmap, rotationDegrees).text
    }

    private fun sortLinesByPosition(lines: List<RecognizedLine>): List<RecognizedLine> {
        return lines.withIndex()
            .sortedWith(
                compareBy<IndexedValue<RecognizedLine>>(
                    { it.value.top ?: Float.MAX_VALUE },
                    { it.value.left ?: Float.MAX_VALUE },
                    { it.index }
                )
            )
            .map { it.value }
    }
}

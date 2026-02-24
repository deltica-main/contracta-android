package com.example.businesscardscanner.domain.logic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class CardCropResult(
    val bitmap: Bitmap,
    val detectedQuad: CropQuad,
    val expandedQuad: CropQuad,
    val method: String,
    val transformType: CardCropTransformType,
    val auditRecord: CardCropAuditRecord
)

data class CardCropAttempt(
    val result: CardCropResult?,
    val auditRecord: CardCropAuditRecord
)

/**
 * Detects a business-card quadrilateral from an image and applies a transform selected by crop audit.
 */
object CardImageCropper {
    private const val DETECTION_MAX_SIDE = 960
    private const val EDGE_PERCENTILE = 0.82
    private const val EDGE_MIN_THRESHOLD = 28
    private const val MIN_COMPONENT_RATIO = 0.0035
    private const val MIN_OUTPUT_SIDE = 140
    private const val MAX_SUPPORTED_AREA = 38_000_000L
    const val DEFAULT_MARGIN_RATIO = 0.008f

    fun cropCard(bitmap: Bitmap): Bitmap? {
        return cropCardAttempt(bitmap).result?.bitmap
    }

    fun cropCardDetailed(bitmap: Bitmap, marginRatio: Float = DEFAULT_MARGIN_RATIO): CardCropResult? {
        return cropCardAttempt(bitmap, marginRatio).result
    }

    fun cropCardFromQuad(
        bitmap: Bitmap,
        quad: CropQuad,
        marginRatio: Float = DEFAULT_MARGIN_RATIO,
        method: String = "persisted_quadrilateral"
    ): CardCropResult? {
        return cropCardAttemptFromQuad(bitmap, quad, marginRatio, method).result
    }

    fun cropCardAttempt(bitmap: Bitmap, marginRatio: Float = DEFAULT_MARGIN_RATIO): CardCropAttempt {
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            return CardCropAttempt(
                result = null,
                auditRecord = createFallbackRecord(
                    inputWidth = bitmap.width,
                    inputHeight = bitmap.height,
                    reason = "invalid_input_dimensions"
                )
            )
        }
        if (!isBitmapSizeSupported(bitmap)) {
            return CardCropAttempt(
                result = null,
                auditRecord = createFallbackRecord(
                    inputWidth = bitmap.width,
                    inputHeight = bitmap.height,
                    reason = "unsupported_input_size"
                )
            )
        }

        val detected = detectCardQuadrilateral(bitmap)
        if (detected == null) {
            return CardCropAttempt(
                result = null,
                auditRecord = createFallbackRecord(
                    inputWidth = bitmap.width,
                    inputHeight = bitmap.height,
                    reason = "no_quad_detected"
                )
            )
        }
        return cropCardAttemptFromQuad(
            bitmap = bitmap,
            quad = detected,
            marginRatio = marginRatio,
            method = "edge_quadrilateral"
        )
    }

    fun cropCardAttemptFromQuad(
        bitmap: Bitmap,
        quad: CropQuad,
        marginRatio: Float = DEFAULT_MARGIN_RATIO,
        method: String = "persisted_quadrilateral"
    ): CardCropAttempt {
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            return CardCropAttempt(
                result = null,
                auditRecord = createFallbackRecord(
                    inputWidth = bitmap.width,
                    inputHeight = bitmap.height,
                    detectedCorners = quad.asList(),
                    orderedCorners = quad.asList(),
                    reason = "invalid_input_dimensions"
                )
            )
        }
        if (!isBitmapSizeSupported(bitmap)) {
            return CardCropAttempt(
                result = null,
                auditRecord = createFallbackRecord(
                    inputWidth = bitmap.width,
                    inputHeight = bitmap.height,
                    detectedCorners = quad.asList(),
                    orderedCorners = quad.asList(),
                    reason = "unsupported_input_size"
                )
            )
        }

        val detectedCorners = quad.asList()
        val orderedQuad = CardCropAudit.orderCorners(detectedCorners) ?: quad
        val orderedCorners = orderedQuad.asList()

        val expandedQuad = CardCropGeometry.expandAndClamp(
            quad = orderedQuad,
            marginRatio = marginRatio.coerceIn(0f, 0.04f),
            maxWidth = bitmap.width,
            maxHeight = bitmap.height
        )
        val (targetWidth, targetHeight) = CardCropGeometry.destinationSize(expandedQuad)
        val evaluation = CardCropAudit.evaluate(
            quad = orderedQuad,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            outputWidth = targetWidth,
            outputHeight = targetHeight
        )

        val initialTransform = CardCropAudit.chooseTransform(evaluation)
        if (initialTransform == CardCropTransformType.PERSPECTIVE_WARP) {
            val warpOutput = runCatching {
                warpPerspective(bitmap, expandedQuad, targetWidth, targetHeight)
            }.getOrNull()
            if (warpOutput != null) {
                val normalizedWarp = normalizeOrientationIfNeeded(warpOutput)
                if (isOutputShapePlausible(normalizedWarp.width, normalizedWarp.height, bitmap.width, bitmap.height)) {
                    val auditRecord = CardCropAudit.createRecord(
                        inputWidth = bitmap.width,
                        inputHeight = bitmap.height,
                        detectedCorners = detectedCorners,
                        orderedCorners = orderedCorners,
                        evaluation = evaluation,
                        transformType = CardCropTransformType.PERSPECTIVE_WARP
                    )
                    return CardCropAttempt(
                        result = CardCropResult(
                            bitmap = normalizedWarp,
                            detectedQuad = orderedQuad,
                            expandedQuad = expandedQuad,
                            method = method,
                            transformType = CardCropTransformType.PERSPECTIVE_WARP,
                            auditRecord = auditRecord
                        ),
                        auditRecord = auditRecord
                    )
                }
            }
        }

        val boundingRect = computeBoundingRect(expandedQuad, bitmap.width, bitmap.height)
        if (boundingRect != null) {
            val bboxBitmap = runCatching {
                Bitmap.createBitmap(bitmap, boundingRect.left, boundingRect.top, boundingRect.width, boundingRect.height)
            }.getOrNull()
            if (bboxBitmap != null) {
                val normalizedBbox = normalizeOrientationIfNeeded(bboxBitmap)
                if (isOutputShapePlausible(normalizedBbox.width, normalizedBbox.height, bitmap.width, bitmap.height)) {
                    val extraReason = if (initialTransform == CardCropTransformType.PERSPECTIVE_WARP) {
                        "warp_rejected_or_failed"
                    } else {
                        "audit_selected_bbox"
                    }
                    val auditRecord = CardCropAudit.createRecord(
                        inputWidth = bitmap.width,
                        inputHeight = bitmap.height,
                        detectedCorners = detectedCorners,
                        orderedCorners = orderedCorners,
                        evaluation = evaluation,
                        transformType = CardCropTransformType.BOUNDING_BOX,
                        extraReason = extraReason
                    )
                    return CardCropAttempt(
                        result = CardCropResult(
                            bitmap = normalizedBbox,
                            detectedQuad = orderedQuad,
                            expandedQuad = expandedQuad,
                            method = method,
                            transformType = CardCropTransformType.BOUNDING_BOX,
                            auditRecord = auditRecord
                        ),
                        auditRecord = auditRecord
                    )
                }
            }
        }

        val fallbackReason = when {
            boundingRect == null -> "bbox_rect_invalid"
            else -> "bbox_output_invalid"
        }
        val fallbackRecord = CardCropAudit.createRecord(
            inputWidth = bitmap.width,
            inputHeight = bitmap.height,
            detectedCorners = detectedCorners,
            orderedCorners = orderedCorners,
            evaluation = evaluation,
            transformType = CardCropTransformType.CENTER_FALLBACK,
            extraReason = fallbackReason
        )
        return CardCropAttempt(result = null, auditRecord = fallbackRecord)
    }

    internal fun detectCardQuadrilateral(bitmap: Bitmap): CropQuad? {
        if (bitmap.width < 64 || bitmap.height < 64) return null
        val largestSide = max(bitmap.width, bitmap.height)
        val scale = if (largestSide > DETECTION_MAX_SIDE) {
            DETECTION_MAX_SIDE.toFloat() / largestSide.toFloat()
        } else {
            1f
        }
        val detectionBitmap = runCatching {
            if (scale < 0.999f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                    (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
        }.getOrElse { bitmap }

        val width = detectionBitmap.width
        val height = detectionBitmap.height
        if (width < 32 || height < 32) return null
        val pixels = IntArray(width * height)
        runCatching {
            detectionBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        }.getOrElse { return null }

        val grayscale = toGrayscale(pixels)
        val blurred = boxBlur3x3(grayscale, width, height)
        val gradient = sobelMagnitude(blurred, width, height)
        val threshold = computeThreshold(gradient)
        if (threshold == Int.MAX_VALUE) return null

        val edgeMask = BooleanArray(width * height) { index ->
            gradient[index] >= threshold
        }
        val detectedOnScaled = findBestQuad(edgeMask, width, height) ?: return null
        if (scale >= 0.999f) return detectedOnScaled

        val scaleX = bitmap.width.toFloat() / width.toFloat()
        val scaleY = bitmap.height.toFloat() / height.toFloat()
        return CropQuad(
            topLeft = CropPoint(detectedOnScaled.topLeft.x * scaleX, detectedOnScaled.topLeft.y * scaleY),
            topRight = CropPoint(detectedOnScaled.topRight.x * scaleX, detectedOnScaled.topRight.y * scaleY),
            bottomRight = CropPoint(
                detectedOnScaled.bottomRight.x * scaleX,
                detectedOnScaled.bottomRight.y * scaleY
            ),
            bottomLeft = CropPoint(detectedOnScaled.bottomLeft.x * scaleX, detectedOnScaled.bottomLeft.y * scaleY)
        )
    }

    private fun findBestQuad(edgeMask: BooleanArray, width: Int, height: Int): CropQuad? {
        if (edgeMask.size < width * height) return null
        val visited = BooleanArray(edgeMask.size)
        val queue = IntArray(edgeMask.size)
        val minComponentSize = (width * height * MIN_COMPONENT_RATIO).roundToInt().coerceAtLeast(220)
        var bestQuad: CropQuad? = null
        var bestScore = Float.NEGATIVE_INFINITY
        val imageCenterX = width / 2f
        val imageCenterY = height / 2f

        for (index in edgeMask.indices) {
            if (!edgeMask[index] || visited[index]) continue
            var head = 0
            var tail = 0
            queue[tail++] = index
            visited[index] = true

            var componentSize = 0
            var minSumPoint = CropPoint(0f, 0f)
            var maxSumPoint = CropPoint(0f, 0f)
            var minDiffPoint = CropPoint(0f, 0f)
            var maxDiffPoint = CropPoint(0f, 0f)
            var minSum = Float.MAX_VALUE
            var maxSum = Float.NEGATIVE_INFINITY
            var minDiff = Float.MAX_VALUE
            var maxDiff = Float.NEGATIVE_INFINITY

            while (head < tail) {
                val current = queue[head++]
                val x = current % width
                val y = current / width
                val xF = x.toFloat()
                val yF = y.toFloat()
                componentSize += 1

                val sum = xF + yF
                val diff = xF - yF
                if (sum < minSum) {
                    minSum = sum
                    minSumPoint = CropPoint(xF, yF)
                }
                if (sum > maxSum) {
                    maxSum = sum
                    maxSumPoint = CropPoint(xF, yF)
                }
                if (diff < minDiff) {
                    minDiff = diff
                    minDiffPoint = CropPoint(xF, yF)
                }
                if (diff > maxDiff) {
                    maxDiff = diff
                    maxDiffPoint = CropPoint(xF, yF)
                }

                for (offsetY in -1..1) {
                    for (offsetX in -1..1) {
                        if (offsetX == 0 && offsetY == 0) continue
                        val nextX = x + offsetX
                        val nextY = y + offsetY
                        if (nextX !in 0 until width || nextY !in 0 until height) continue
                        val nextIndex = nextY * width + nextX
                        if (visited[nextIndex] || !edgeMask[nextIndex]) continue
                        visited[nextIndex] = true
                        queue[tail++] = nextIndex
                    }
                }
            }

            if (componentSize < minComponentSize) continue
            val candidate = CardCropGeometry.quadFromExtremes(
                listOf(minSumPoint, maxDiffPoint, maxSumPoint, minDiffPoint)
            ) ?: continue
            if (!CardCropGeometry.isValidCardBounds(candidate, width, height, minAreaRatio = 0.1f)) continue

            val areaRatio = CardCropGeometry.areaRatio(candidate, width, height)
            val centerX =
                (candidate.topLeft.x + candidate.topRight.x + candidate.bottomRight.x + candidate.bottomLeft.x) / 4f
            val centerY =
                (candidate.topLeft.y + candidate.topRight.y + candidate.bottomRight.y + candidate.bottomLeft.y) / 4f
            val centerPenalty = (
                abs(centerX - imageCenterX) / imageCenterX.coerceAtLeast(1f) +
                    abs(centerY - imageCenterY) / imageCenterY.coerceAtLeast(1f)
                ) * 0.5f
            val coverageBoost = componentSize.toFloat() / edgeMask.size.toFloat()
            val score = areaRatio + (coverageBoost * 0.15f) - (centerPenalty * 0.18f)
            if (score > bestScore) {
                bestScore = score
                bestQuad = candidate
            }
        }
        return bestQuad
    }

    private fun toGrayscale(pixels: IntArray): IntArray {
        val output = IntArray(pixels.size)
        for (index in pixels.indices) {
            val color = pixels[index]
            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)
            output[index] = ((red * 299) + (green * 587) + (blue * 114)) / 1000
        }
        return output
    }

    private fun boxBlur3x3(input: IntArray, width: Int, height: Int): IntArray {
        if (input.size < width * height) return input
        val output = IntArray(input.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0
                var count = 0
                for (offsetY in -1..1) {
                    for (offsetX in -1..1) {
                        val sx = x + offsetX
                        val sy = y + offsetY
                        if (sx !in 0 until width || sy !in 0 until height) continue
                        sum += input[sy * width + sx]
                        count += 1
                    }
                }
                output[y * width + x] = if (count > 0) sum / count else input[y * width + x]
            }
        }
        return output
    }

    private fun sobelMagnitude(input: IntArray, width: Int, height: Int): IntArray {
        if (input.size < width * height || width < 3 || height < 3) return IntArray(width * height)
        val output = IntArray(width * height)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val topLeft = input[(y - 1) * width + (x - 1)]
                val top = input[(y - 1) * width + x]
                val topRight = input[(y - 1) * width + (x + 1)]
                val left = input[y * width + (x - 1)]
                val right = input[y * width + (x + 1)]
                val bottomLeft = input[(y + 1) * width + (x - 1)]
                val bottom = input[(y + 1) * width + x]
                val bottomRight = input[(y + 1) * width + (x + 1)]
                val gx = -topLeft - (2 * left) - bottomLeft + topRight + (2 * right) + bottomRight
                val gy = -topLeft - (2 * top) - topRight + bottomLeft + (2 * bottom) + bottomRight
                output[y * width + x] = abs(gx) + abs(gy)
            }
        }
        return output
    }

    private fun computeThreshold(magnitude: IntArray): Int {
        var maxMagnitude = 0
        var nonZeroCount = 0
        for (value in magnitude) {
            if (value > maxMagnitude) {
                maxMagnitude = value
            }
            if (value > 0) {
                nonZeroCount += 1
            }
        }
        if (maxMagnitude <= 0 || nonZeroCount <= 0) return Int.MAX_VALUE

        val bins = 256
        val histogram = IntArray(bins)
        for (value in magnitude) {
            if (value <= 0) continue
            val bin = ((value.toFloat() / maxMagnitude.toFloat()) * (bins - 1))
                .roundToInt()
                .coerceIn(0, bins - 1)
            histogram[bin] += 1
        }

        val target = (nonZeroCount * EDGE_PERCENTILE).roundToInt().coerceIn(1, nonZeroCount)
        var cumulative = 0
        var selectedBin = bins - 1
        for (index in histogram.indices) {
            cumulative += histogram[index]
            if (cumulative >= target) {
                selectedBin = index
                break
            }
        }
        val normalized = selectedBin.toFloat() / (bins - 1).toFloat()
        return max(EDGE_MIN_THRESHOLD, (normalized * maxMagnitude).roundToInt())
    }

    private fun isBitmapSizeSupported(bitmap: Bitmap): Boolean {
        if (bitmap.width <= 0 || bitmap.height <= 0) return false
        val area = bitmap.width.toLong() * bitmap.height.toLong()
        return area in 1..MAX_SUPPORTED_AREA
    }

    private fun isOutputShapePlausible(
        width: Int,
        height: Int,
        inputWidth: Int,
        inputHeight: Int
    ): Boolean {
        if (width <= 0 || height <= 0 || inputWidth <= 0 || inputHeight <= 0) return false
        if (min(width, height) < MIN_OUTPUT_SIDE) return false
        val normalizedAspect = max(width, height).toFloat() / min(width, height).toFloat()
        if (normalizedAspect !in CardCropAudit.OUTPUT_MIN_ASPECT..CardCropAudit.OUTPUT_MAX_ASPECT) return false
        val outputAreaRatio = (width.toFloat() * height.toFloat()) / (inputWidth.toFloat() * inputHeight.toFloat())
        return outputAreaRatio >= CardCropAudit.MIN_OUTPUT_AREA_RATIO
    }

    private fun normalizeOrientationIfNeeded(bitmap: Bitmap): Bitmap {
        if (bitmap.width >= bitmap.height) return bitmap
        val normalizedAspect = max(bitmap.width, bitmap.height).toFloat() / min(bitmap.width, bitmap.height).toFloat()
        if (normalizedAspect !in CardCropAudit.OUTPUT_MIN_ASPECT..CardCropAudit.OUTPUT_MAX_ASPECT) return bitmap
        val matrix = Matrix().apply { postRotate(90f) }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun computeBoundingRect(quad: CropQuad, maxWidth: Int, maxHeight: Int): CropRect? {
        if (maxWidth <= 0 || maxHeight <= 0) return null
        val minX = floor(quad.asList().minOf { it.x }.toDouble()).toInt().coerceIn(0, maxWidth - 1)
        val maxX = ceil(quad.asList().maxOf { it.x }.toDouble()).toInt().coerceIn(1, maxWidth)
        val minY = floor(quad.asList().minOf { it.y }.toDouble()).toInt().coerceIn(0, maxHeight - 1)
        val maxY = ceil(quad.asList().maxOf { it.y }.toDouble()).toInt().coerceIn(1, maxHeight)
        val width = (maxX - minX).coerceAtLeast(1)
        val height = (maxY - minY).coerceAtLeast(1)
        if (minX + width > maxWidth || minY + height > maxHeight) return null
        return CropRect(left = minX, top = minY, width = width, height = height)
    }

    private fun createFallbackRecord(
        inputWidth: Int,
        inputHeight: Int,
        detectedCorners: List<CropPoint> = emptyList(),
        orderedCorners: List<CropPoint> = emptyList(),
        reason: String
    ): CardCropAuditRecord {
        return CardCropAuditRecord(
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            detectedCorners = detectedCorners,
            orderedCorners = orderedCorners,
            preAspectRatio = 0f,
            postAspectRatio = 0f,
            transformType = CardCropTransformType.CENTER_FALLBACK,
            auditPassed = false,
            reason = reason,
            areaRatio = 0f,
            edgeRatioWidth = 0f,
            edgeRatioHeight = 0f,
            diagonalRatio = 0f,
            minCornerAngleDeg = 0f
        )
    }

    private fun warpPerspective(bitmap: Bitmap, quad: CropQuad, width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val source = floatArrayOf(
            quad.topLeft.x, quad.topLeft.y,
            quad.topRight.x, quad.topRight.y,
            quad.bottomRight.x, quad.bottomRight.y,
            quad.bottomLeft.x, quad.bottomLeft.y
        )
        val destination = floatArrayOf(
            0f, 0f,
            width.toFloat(), 0f,
            width.toFloat(), height.toFloat(),
            0f, height.toFloat()
        )
        val matrix = Matrix()
        val mapped = matrix.setPolyToPoly(source, 0, destination, 0, 4)
        if (!mapped) return null

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)
        return output
    }
}

private data class CropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

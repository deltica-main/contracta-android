package ca.deltica.contactra.domain.logic

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class CropPoint(val x: Float, val y: Float)

data class CropQuad(
    val topLeft: CropPoint,
    val topRight: CropPoint,
    val bottomRight: CropPoint,
    val bottomLeft: CropPoint
) {
    fun asList(): List<CropPoint> = listOf(topLeft, topRight, bottomRight, bottomLeft)
}

object CardCropGeometry {
    fun quadFromExtremes(points: List<CropPoint>): CropQuad? {
        if (points.size < 4) return null
        val topLeft = points.minByOrNull { it.x + it.y } ?: return null
        val bottomRight = points.maxByOrNull { it.x + it.y } ?: return null
        val topRight = points.maxByOrNull { it.x - it.y } ?: return null
        val bottomLeft = points.minByOrNull { it.x - it.y } ?: return null
        val quad = CropQuad(
            topLeft = topLeft,
            topRight = topRight,
            bottomRight = bottomRight,
            bottomLeft = bottomLeft
        )
        return if (hasDistinctCorners(quad)) quad else null
    }

    fun expandAndClamp(
        quad: CropQuad,
        marginRatio: Float,
        maxWidth: Int,
        maxHeight: Int
    ): CropQuad {
        if (maxWidth <= 0 || maxHeight <= 0) return quad
        val margin = marginRatio.coerceAtLeast(0f)
        if (margin == 0f) return quad
        val centerX = (quad.topLeft.x + quad.topRight.x + quad.bottomRight.x + quad.bottomLeft.x) / 4f
        val centerY = (quad.topLeft.y + quad.topRight.y + quad.bottomRight.y + quad.bottomLeft.y) / 4f
        fun expandPoint(point: CropPoint): CropPoint {
            val expandedX = centerX + (point.x - centerX) * (1f + margin)
            val expandedY = centerY + (point.y - centerY) * (1f + margin)
            return CropPoint(
                x = expandedX.coerceIn(0f, maxWidth.toFloat() - 1f),
                y = expandedY.coerceIn(0f, maxHeight.toFloat() - 1f)
            )
        }
        return CropQuad(
            topLeft = expandPoint(quad.topLeft),
            topRight = expandPoint(quad.topRight),
            bottomRight = expandPoint(quad.bottomRight),
            bottomLeft = expandPoint(quad.bottomLeft)
        )
    }

    fun normalizedAspectRatio(quad: CropQuad): Float {
        val width = averageWidth(quad)
        val height = averageHeight(quad)
        if (width <= 0f || height <= 0f) return 0f
        val ratio = width / height
        return if (ratio >= 1f) ratio else 1f / ratio
    }

    fun areaRatio(quad: CropQuad, imageWidth: Int, imageHeight: Int): Float {
        if (imageWidth <= 0 || imageHeight <= 0) return 0f
        val area = polygonArea(quad).coerceAtLeast(0f)
        val imageArea = imageWidth.toFloat() * imageHeight.toFloat()
        if (imageArea <= 0f) return 0f
        return (area / imageArea).coerceIn(0f, 1f)
    }

    fun isValidCardBounds(
        quad: CropQuad,
        imageWidth: Int,
        imageHeight: Int,
        minAspect: Float = 1.2f,
        maxAspect: Float = 2.35f,
        minAreaRatio: Float = 0.12f,
        maxAreaRatio: Float = 0.97f
    ): Boolean {
        val aspect = normalizedAspectRatio(quad)
        if (aspect !in minAspect..maxAspect) return false
        val areaRatio = areaRatio(quad, imageWidth, imageHeight)
        if (areaRatio !in minAreaRatio..maxAreaRatio) return false
        return hasDistinctCorners(quad)
    }

    fun destinationSize(
        quad: CropQuad,
        minSide: Int = 160,
        maxSide: Int = 2200
    ): Pair<Int, Int> {
        val width = averageWidth(quad)
        val height = averageHeight(quad)
        val resolvedWidth = width.roundToInt().coerceIn(minSide, maxSide)
        val resolvedHeight = height.roundToInt().coerceIn(minSide, maxSide)
        return resolvedWidth to resolvedHeight
    }

    private fun averageWidth(quad: CropQuad): Float {
        val top = distance(quad.topLeft, quad.topRight)
        val bottom = distance(quad.bottomLeft, quad.bottomRight)
        return (top + bottom) / 2f
    }

    private fun averageHeight(quad: CropQuad): Float {
        val left = distance(quad.topLeft, quad.bottomLeft)
        val right = distance(quad.topRight, quad.bottomRight)
        return (left + right) / 2f
    }

    private fun polygonArea(quad: CropQuad): Float {
        val points = quad.asList()
        var area = 0f
        for (index in points.indices) {
            val current = points[index]
            val next = points[(index + 1) % points.size]
            area += (current.x * next.y) - (next.x * current.y)
        }
        return abs(area) / 2f
    }

    private fun hasDistinctCorners(quad: CropQuad): Boolean {
        val points = quad.asList()
        val minDistance = max(4f, min(averageWidth(quad), averageHeight(quad)) * 0.04f)
        for (i in points.indices) {
            for (j in (i + 1) until points.size) {
                if (distance(points[i], points[j]) < minDistance) {
                    return false
                }
            }
        }
        return true
    }

    private fun distance(a: CropPoint, b: CropPoint): Float {
        return hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
    }
}

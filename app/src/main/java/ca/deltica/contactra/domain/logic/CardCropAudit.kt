package ca.deltica.contactra.domain.logic

import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class CardCropTransformType {
    PERSPECTIVE_WARP,
    BOUNDING_BOX,
    CENTER_FALLBACK
}

data class CardCropAuditEvaluation(
    val geometryValid: Boolean,
    val orderingValid: Boolean,
    val warpAggressive: Boolean,
    val outputValid: Boolean,
    val nearFlat: Boolean,
    val passedStrongly: Boolean,
    val areaRatio: Float,
    val edgeRatioWidth: Float,
    val edgeRatioHeight: Float,
    val diagonalRatio: Float,
    val minCornerAngleDeg: Float,
    val preAspectRatio: Float,
    val postAspectRatio: Float,
    val failReasons: List<String>
)

data class CardCropAuditRecord(
    val inputWidth: Int,
    val inputHeight: Int,
    val detectedCorners: List<CropPoint>,
    val orderedCorners: List<CropPoint>,
    val preAspectRatio: Float,
    val postAspectRatio: Float,
    val transformType: CardCropTransformType,
    val auditPassed: Boolean,
    val reason: String,
    val areaRatio: Float,
    val edgeRatioWidth: Float,
    val edgeRatioHeight: Float,
    val diagonalRatio: Float,
    val minCornerAngleDeg: Float
)

object CardCropAudit {
    const val MIN_AREA_RATIO = 0.15f
    const val MAX_EDGE_RATIO = 1.6f
    const val MAX_DIAGONAL_RATIO = 1.4f
    const val MIN_CORNER_ANGLE_DEG = 25f
    const val OUTPUT_MIN_ASPECT = 1.3f
    const val OUTPUT_MAX_ASPECT = 2.2f
    const val MIN_OUTPUT_AREA_RATIO = 0.08f
    const val NEAR_FLAT_MAX_EDGE_RATIO = 1.12f
    const val NEAR_FLAT_MAX_DIAGONAL_RATIO = 1.1f
    const val NEAR_FLAT_MIN_ANGLE = 78f
    const val NEAR_FLAT_MAX_ANGLE = 102f

    fun orderCorners(points: List<CropPoint>): CropQuad? {
        return CardCropGeometry.quadFromExtremes(points)
    }

    fun isConvex(quad: CropQuad): Boolean {
        val points = quad.asList()
        var previousSign = 0f
        for (index in points.indices) {
            val a = points[index]
            val b = points[(index + 1) % points.size]
            val c = points[(index + 2) % points.size]
            val cross = crossZ(
                ax = b.x - a.x,
                ay = b.y - a.y,
                bx = c.x - b.x,
                by = c.y - b.y
            )
            if (cross == 0f) continue
            if (previousSign == 0f) {
                previousSign = cross
            } else if (previousSign * cross < 0f) {
                return false
            }
        }
        return previousSign != 0f
    }

    fun hasConsistentCornerOrder(quad: CropQuad): Boolean {
        if (!isConvex(quad)) return false
        val topAverageY = (quad.topLeft.y + quad.topRight.y) / 2f
        val bottomAverageY = (quad.bottomLeft.y + quad.bottomRight.y) / 2f
        if (topAverageY >= bottomAverageY) return false

        val leftAverageX = (quad.topLeft.x + quad.bottomLeft.x) / 2f
        val rightAverageX = (quad.topRight.x + quad.bottomRight.x) / 2f
        if (leftAverageX >= rightAverageX) return false

        val topWidth = distance(quad.topLeft, quad.topRight)
        val bottomWidth = distance(quad.bottomLeft, quad.bottomRight)
        val leftHeight = distance(quad.topLeft, quad.bottomLeft)
        val rightHeight = distance(quad.topRight, quad.bottomRight)
        if (min(topWidth, bottomWidth) <= 0f || min(leftHeight, rightHeight) <= 0f) return false

        val normalizedAspect = normalizedRatio((topWidth + bottomWidth) / 2f, (leftHeight + rightHeight) / 2f)
        return normalizedAspect in 1.1f..2.7f
    }

    fun evaluate(
        quad: CropQuad,
        imageWidth: Int,
        imageHeight: Int,
        outputWidth: Int,
        outputHeight: Int
    ): CardCropAuditEvaluation {
        val failReasons = mutableListOf<String>()
        val pointsInside = quad.asList().all { point ->
            point.x in 0f..(imageWidth - 1).coerceAtLeast(0).toFloat() &&
                point.y in 0f..(imageHeight - 1).coerceAtLeast(0).toFloat()
        }
        if (!pointsInside) {
            failReasons += "points_out_of_bounds"
        }

        val areaRatio = CardCropGeometry.areaRatio(quad, imageWidth, imageHeight)
        if (areaRatio <= MIN_AREA_RATIO) {
            failReasons += "area_too_small"
        }

        val convex = isConvex(quad)
        if (!convex) {
            failReasons += "non_convex_quad"
        }

        val geometryValid = pointsInside && areaRatio > MIN_AREA_RATIO && convex

        val orderingValid = hasConsistentCornerOrder(quad)
        if (!orderingValid) {
            failReasons += "corner_order_invalid"
        }

        val topWidth = distance(quad.topLeft, quad.topRight)
        val bottomWidth = distance(quad.bottomLeft, quad.bottomRight)
        val leftHeight = distance(quad.topLeft, quad.bottomLeft)
        val rightHeight = distance(quad.topRight, quad.bottomRight)
        val edgeRatioWidth = normalizedRatio(topWidth, bottomWidth)
        val edgeRatioHeight = normalizedRatio(leftHeight, rightHeight)
        val diagonalRatio = normalizedRatio(
            distance(quad.topLeft, quad.bottomRight),
            distance(quad.topRight, quad.bottomLeft)
        )
        val angles = cornerAngles(quad)
        val minCornerAngleDeg = angles.minOrNull() ?: 0f
        val warpAggressive =
            edgeRatioWidth > MAX_EDGE_RATIO ||
                edgeRatioHeight > MAX_EDGE_RATIO ||
                diagonalRatio > MAX_DIAGONAL_RATIO ||
                minCornerAngleDeg < MIN_CORNER_ANGLE_DEG
        if (warpAggressive) {
            failReasons += "warp_aggressive"
        }

        val preAspectRatio = normalizedRatio((topWidth + bottomWidth) / 2f, (leftHeight + rightHeight) / 2f)
        val postAspectRatio = normalizedRatio(outputWidth.toFloat(), outputHeight.toFloat())
        val outputAreaRatio = if (imageWidth > 0 && imageHeight > 0) {
            (outputWidth.toFloat() * outputHeight.toFloat()) / (imageWidth.toFloat() * imageHeight.toFloat())
        } else {
            0f
        }
        val outputValid =
            postAspectRatio in OUTPUT_MIN_ASPECT..OUTPUT_MAX_ASPECT &&
                outputAreaRatio >= MIN_OUTPUT_AREA_RATIO &&
                outputWidth > 0 &&
                outputHeight > 0
        if (!outputValid) {
            failReasons += "output_sanity_failed"
        }

        val nearFlat = edgeRatioWidth <= NEAR_FLAT_MAX_EDGE_RATIO &&
            edgeRatioHeight <= NEAR_FLAT_MAX_EDGE_RATIO &&
            diagonalRatio <= NEAR_FLAT_MAX_DIAGONAL_RATIO &&
            angles.all { it in NEAR_FLAT_MIN_ANGLE..NEAR_FLAT_MAX_ANGLE }

        val passedStrongly = geometryValid && orderingValid && !warpAggressive && outputValid
        return CardCropAuditEvaluation(
            geometryValid = geometryValid,
            orderingValid = orderingValid,
            warpAggressive = warpAggressive,
            outputValid = outputValid,
            nearFlat = nearFlat,
            passedStrongly = passedStrongly,
            areaRatio = areaRatio,
            edgeRatioWidth = edgeRatioWidth,
            edgeRatioHeight = edgeRatioHeight,
            diagonalRatio = diagonalRatio,
            minCornerAngleDeg = minCornerAngleDeg,
            preAspectRatio = preAspectRatio,
            postAspectRatio = postAspectRatio,
            failReasons = failReasons
        )
    }

    fun chooseTransform(evaluation: CardCropAuditEvaluation): CardCropTransformType {
        if (!evaluation.geometryValid || !evaluation.orderingValid) {
            return CardCropTransformType.CENTER_FALLBACK
        }
        if (evaluation.nearFlat) {
            return CardCropTransformType.BOUNDING_BOX
        }
        if (evaluation.passedStrongly) {
            return CardCropTransformType.PERSPECTIVE_WARP
        }
        return CardCropTransformType.BOUNDING_BOX
    }

    fun createRecord(
        inputWidth: Int,
        inputHeight: Int,
        detectedCorners: List<CropPoint>,
        orderedCorners: List<CropPoint>,
        evaluation: CardCropAuditEvaluation,
        transformType: CardCropTransformType,
        extraReason: String? = null
    ): CardCropAuditRecord {
        val reasons = buildList {
            addAll(evaluation.failReasons)
            if (!extraReason.isNullOrBlank()) {
                add(extraReason)
            }
        }.distinct().joinToString("|").ifBlank { "pass" }

        return CardCropAuditRecord(
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            detectedCorners = detectedCorners,
            orderedCorners = orderedCorners,
            preAspectRatio = evaluation.preAspectRatio,
            postAspectRatio = evaluation.postAspectRatio,
            transformType = transformType,
            auditPassed = evaluation.passedStrongly,
            reason = reasons,
            areaRatio = evaluation.areaRatio,
            edgeRatioWidth = evaluation.edgeRatioWidth,
            edgeRatioHeight = evaluation.edgeRatioHeight,
            diagonalRatio = evaluation.diagonalRatio,
            minCornerAngleDeg = evaluation.minCornerAngleDeg
        )
    }

    private fun cornerAngles(quad: CropQuad): List<Float> {
        val points = quad.asList()
        val result = mutableListOf<Float>()
        for (index in points.indices) {
            val prev = points[(index - 1 + points.size) % points.size]
            val current = points[index]
            val next = points[(index + 1) % points.size]
            val ux = prev.x - current.x
            val uy = prev.y - current.y
            val vx = next.x - current.x
            val vy = next.y - current.y
            val uLen = sqrt((ux * ux + uy * uy).toDouble()).toFloat()
            val vLen = sqrt((vx * vx + vy * vy).toDouble()).toFloat()
            if (uLen <= 0f || vLen <= 0f) {
                result += 0f
                continue
            }
            val dot = (ux * vx + uy * vy) / (uLen * vLen)
            val clampedDot = dot.coerceIn(-1f, 1f)
            result += Math.toDegrees(acos(clampedDot).toDouble()).toFloat()
        }
        return result
    }

    private fun crossZ(ax: Float, ay: Float, bx: Float, by: Float): Float {
        return ax * by - ay * bx
    }

    private fun distance(a: CropPoint, b: CropPoint): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun normalizedRatio(a: Float, b: Float): Float {
        if (a <= 0f || b <= 0f) return Float.POSITIVE_INFINITY
        return max(a, b) / min(a, b)
    }
}

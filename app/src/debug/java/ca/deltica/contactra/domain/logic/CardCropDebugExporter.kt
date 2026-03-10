package ca.deltica.contactra.domain.logic

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException

internal object CardCropDebugExporter {
    private const val DEBUG_ROOT_DIR = "card_crop_debug"
    private const val ENABLE_FLAG = "card_crop_debug/enable.flag"
    private const val LOG_TAG = "CardCropDebug"
    private const val AUDIT_LOG_FILE = "audit_attempts.jsonl"

    fun export(
        context: Context,
        baseId: String,
        before: Bitmap,
        after: Bitmap,
        auditRecord: CardCropAuditRecord? = null
    ) {
        if (!isEnabled(context)) return
        runCatching {
            val rootDir = File(context.filesDir, DEBUG_ROOT_DIR)
            if (!rootDir.exists()) {
                rootDir.mkdirs()
            }
            writeBitmap(File(rootDir, "${baseId}_before.jpg"), before)
            writeBitmap(File(rootDir, "${baseId}_after.jpg"), after)
            if (auditRecord != null) {
                appendAuditRecord(File(rootDir, AUDIT_LOG_FILE), baseId, auditRecord)
            }
        }.onFailure { throwable ->
            Log.w(LOG_TAG, "debug_export_failed", throwable)
        }
    }

    private fun isEnabled(context: Context): Boolean {
        val isDebuggable =
            (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) {
            return false
        }
        return File(context.filesDir, ENABLE_FLAG).exists()
    }

    private fun writeBitmap(file: File, bitmap: Bitmap) {
        val parent = file.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IOException("Failed to create directory ${parent.absolutePath}")
        }
        val saved = FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        if (!saved) {
            throw IOException("Failed to save debug image ${file.absolutePath}")
        }
    }

    private fun appendAuditRecord(file: File, baseId: String, record: CardCropAuditRecord) {
        val parent = file.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IOException("Failed to create directory ${parent.absolutePath}")
        }
        val line = buildString {
            append("{")
            append("\"baseId\":\"").append(escape(baseId)).append("\",")
            append("\"timestampMs\":").append(System.currentTimeMillis()).append(",")
            append("\"inputWidth\":").append(record.inputWidth).append(",")
            append("\"inputHeight\":").append(record.inputHeight).append(",")
            append("\"detectedCorners\":").append(pointsToJson(record.detectedCorners)).append(",")
            append("\"orderedCorners\":").append(pointsToJson(record.orderedCorners)).append(",")
            append("\"preAspect\":").append(formatFloat(record.preAspectRatio)).append(",")
            append("\"postAspect\":").append(formatFloat(record.postAspectRatio)).append(",")
            append("\"transformType\":\"").append(record.transformType.name).append("\",")
            append("\"auditPassed\":").append(record.auditPassed).append(",")
            append("\"reason\":\"").append(escape(record.reason)).append("\",")
            append("\"areaRatio\":").append(formatFloat(record.areaRatio)).append(",")
            append("\"edgeRatioWidth\":").append(formatFloat(record.edgeRatioWidth)).append(",")
            append("\"edgeRatioHeight\":").append(formatFloat(record.edgeRatioHeight)).append(",")
            append("\"diagonalRatio\":").append(formatFloat(record.diagonalRatio)).append(",")
            append("\"minCornerAngleDeg\":").append(formatFloat(record.minCornerAngleDeg))
            append("}")
        }
        BufferedWriter(FileWriter(file, true)).use { writer ->
            writer.appendLine(line)
        }
        Log.d(LOG_TAG, "audit_recorded baseId=$baseId transform=${record.transformType} reason=${record.reason}")
    }

    private fun pointsToJson(points: List<CropPoint>): String {
        return points.joinToString(prefix = "[", postfix = "]") { point ->
            "{\"x\":${formatFloat(point.x)},\"y\":${formatFloat(point.y)}}"
        }
    }

    private fun formatFloat(value: Float): String {
        if (value.isNaN() || value.isInfinite()) return "0.0"
        return String.format(java.util.Locale.US, "%.5f", value)
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
    }
}

package ca.deltica.contactra.data.support

import java.util.Locale

data class SupportEnvironment(
    val appVersion: String,
    val buildVariant: String,
    val apiLevel: Int,
    val deviceModel: String,
    val localeTag: String,
    val timezoneOffset: String
)

data class SupportFeatureFlags(
    val websiteEnrichmentEnabled: Boolean
)

data class SupportAutoCaptureSummary(
    val cycleCount: Int,
    val captureCount: Int,
    val readyPercent: Double,
    val dominantBlockerReason: String?
)

data class SupportRedactedTokenShape(
    val lineIndex: Int,
    val charCount: Int,
    val width: Float?,
    val height: Float?
)

data class SupportRedactedScanDetails(
    val selectedFieldConfidence: Map<String, Double>,
    val tokenShapes: List<SupportRedactedTokenShape>
)

data class SupportDiagnosticsInput(
    val environment: SupportEnvironment,
    val featureFlags: SupportFeatureFlags,
    val schemaVersion: Int,
    val parserVersion: String,
    val lastActionContext: String?,
    val lastScanId: String?,
    val lastParseRecordId: String?,
    val recentFailureReasons: List<String>,
    val autoCaptureSummary: SupportAutoCaptureSummary?,
    val redactedScanDetails: SupportRedactedScanDetails?
)

data class SupportDiagnosticsBundle(
    val subject: String,
    val body: String,
    val diagnosticsJson: String
)

object SupportDiagnosticsBundleBuilder {
    const val SUPPORT_SUBJECT = "Contactra Support"

    fun build(
        input: SupportDiagnosticsInput,
        includeScanDetails: Boolean,
        includeDebugPayload: Boolean = false
    ): SupportDiagnosticsBundle {
        val body = buildBody(input, includeDebugPayload)
        val diagnosticsJson = buildDiagnosticsJson(input, includeScanDetails)
        return SupportDiagnosticsBundle(
            subject = SUPPORT_SUBJECT,
            body = body,
            diagnosticsJson = diagnosticsJson
        )
    }

    private fun buildBody(input: SupportDiagnosticsInput, includeDebugPayload: Boolean): String {
        return buildString {
            appendLine("Hello Support Team,")
            appendLine()
            appendLine("I need help with Contactra by Deltica.")
            appendLine()
            appendLine("App version: ${input.environment.appVersion}")
            appendLine("Build variant: ${input.environment.buildVariant}")
            appendLine("Android API level: ${input.environment.apiLevel}")
            appendLine("Device model: ${input.environment.deviceModel}")
            appendLine("Locale: ${input.environment.localeTag}")
            appendLine("Timezone offset: ${input.environment.timezoneOffset}")
            appendLine("Website enrichment: ${onOff(input.featureFlags.websiteEnrichmentEnabled)}")
            appendLine("Last action context: ${input.lastActionContext ?: "Not available"}")
            appendLine("Last scan id: ${input.lastScanId ?: "Not available"}")
            appendLine("Last parse record id: ${input.lastParseRecordId ?: "Not available"}")
            appendLine("Debug payload attached: ${if (includeDebugPayload) "Yes" else "No"}")
            appendLine()
            appendLine("Thanks.")
        }.trimEnd()
    }

    private fun buildDiagnosticsJson(
        input: SupportDiagnosticsInput,
        includeScanDetails: Boolean
    ): String {
        val failureReasons = input.recentFailureReasons
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val lines = mutableListOf<String>()
        lines += "{"
        lines += "  \"schemaVersion\": ${input.schemaVersion},"
        lines += "  \"parserVersion\": ${quoted(input.parserVersion)},"
        lines += "  \"app\": {"
        lines += "    \"version\": ${quoted(input.environment.appVersion)},"
        lines += "    \"buildVariant\": ${quoted(input.environment.buildVariant)},"
        lines += "    \"androidApiLevel\": ${input.environment.apiLevel},"
        lines += "    \"deviceModel\": ${quoted(input.environment.deviceModel)},"
        lines += "    \"locale\": ${quoted(input.environment.localeTag)},"
        lines += "    \"timezoneOffset\": ${quoted(input.environment.timezoneOffset)}"
        lines += "  },"
        lines += "  \"featureFlags\": {"
        lines += "    \"websiteEnrichmentEnabled\": ${input.featureFlags.websiteEnrichmentEnabled}"
        lines += "  },"
        lines += "  \"lastActionContext\": ${quotedNullable(input.lastActionContext)},"
        lines += "  \"lastScanId\": ${quotedNullable(input.lastScanId)},"
        lines += "  \"lastParseRecordId\": ${quotedNullable(input.lastParseRecordId)},"
        lines += "  \"recentFailureReasons\": [${failureReasons.joinToString(",") { quoted(it) }}]"

        input.autoCaptureSummary?.let { summary ->
            lines += ","
            lines += "  \"autoCaptureSummary\": {"
            lines += "    \"cycleCount\": ${summary.cycleCount},"
            lines += "    \"captureCount\": ${summary.captureCount},"
            lines += "    \"readyPercent\": ${"%.2f".format(Locale.US, summary.readyPercent)},"
            lines += "    \"dominantBlockerReason\": ${quotedNullable(summary.dominantBlockerReason)}"
            lines += "  }"
        }

        if (includeScanDetails) {
            input.redactedScanDetails?.let { details ->
                val confidences = details.selectedFieldConfidence
                    .toList()
                    .sortedBy { it.first }
                    .joinToString(",") { (field, value) ->
                        "${quoted(field)}:${"%.3f".format(Locale.US, value)}"
                    }
                val shapes = details.tokenShapes.joinToString(",") { shape ->
                    "{${quoted("lineIndex")}:${shape.lineIndex}," +
                        "${quoted("charCount")}:${shape.charCount}," +
                        "${quoted("width")}:${shape.width?.let { "%.1f".format(Locale.US, it) } ?: "null"}," +
                        "${quoted("height")}:${shape.height?.let { "%.1f".format(Locale.US, it) } ?: "null"}}"
                }
                lines += ","
                lines += "  \"scanDetails\": {"
                lines += "    \"selectedFieldConfidence\": {$confidences},"
                lines += "    \"tokenShapes\": [$shapes]"
                lines += "  }"
            }
        }

        lines += "}"
        return lines.joinToString("\n")
    }

    private fun onOff(enabled: Boolean): String = if (enabled) "On" else "Off"

    private fun quoted(value: String): String {
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n") + "\""
    }

    private fun quotedNullable(value: String?): String {
        return if (value == null) "null" else quoted(value)
    }
}

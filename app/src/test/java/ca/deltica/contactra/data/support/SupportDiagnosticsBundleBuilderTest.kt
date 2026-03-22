package ca.deltica.contactra.data.support

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportDiagnosticsBundleBuilderTest {

    @Test
    fun build_defaultBundle_excludesScanDetails() {
        val bundle = SupportDiagnosticsBundleBuilder.build(
            input = sampleInput(),
            includeScanDetails = false
        )

        assertTrue(bundle.subject.contains("Contactra Support"))
        assertFalse(bundle.diagnosticsJson.contains("\"scanDetails\""))
        assertTrue(bundle.diagnosticsJson.contains("\"schemaVersion\": 6"))
    }

    @Test
    fun build_whenScanDetailsEnabled_includesRedactedScanDetails() {
        val bundle = SupportDiagnosticsBundleBuilder.build(
            input = sampleInput(),
            includeScanDetails = true
        )

        assertTrue(bundle.diagnosticsJson.contains("\"scanDetails\""))
        assertTrue(bundle.diagnosticsJson.contains("\"tokenShapes\""))
        assertTrue(bundle.diagnosticsJson.contains("\"selectedFieldConfidence\""))
    }

    @Test
    fun build_bodyContainsRequiredSupportContext() {
        val bundle = SupportDiagnosticsBundleBuilder.build(
            input = sampleInput(),
            includeScanDetails = false
        )

        assertTrue(bundle.body.contains("App version: 1.0.0"))
        assertTrue(bundle.body.contains("Build variant: release"))
        assertTrue(bundle.body.contains("Android API level: 34"))
        assertTrue(bundle.body.contains("Device model: Test Device"))
        assertTrue(bundle.body.contains("Locale: en-US"))
        assertTrue(bundle.body.contains("Timezone offset: +00:00"))
        assertTrue(bundle.body.contains("Website enrichment: On"))
        assertTrue(bundle.body.contains("Last action context: settings"))
        assertTrue(bundle.body.contains("Debug payload attached: No"))
    }

    private fun sampleInput(): SupportDiagnosticsInput {
        return SupportDiagnosticsInput(
            environment = SupportEnvironment(
                appVersion = "1.0.0",
                buildVariant = "release",
                apiLevel = 34,
                deviceModel = "Test Device",
                localeTag = "en-US",
                timezoneOffset = "+00:00"
            ),
            featureFlags = SupportFeatureFlags(
                websiteEnrichmentEnabled = true
            ),
            schemaVersion = 6,
            parserVersion = "scan_diagnostics_v1",
            lastActionContext = "settings",
            lastScanId = "session_123",
            lastParseRecordId = "record_9",
            recentFailureReasons = listOf("OCR timeout"),
            redactedScanDetails = SupportRedactedScanDetails(
                selectedFieldConfidence = mapOf("name" to 0.91, "company" to 0.74),
                tokenShapes = listOf(
                    SupportRedactedTokenShape(
                        lineIndex = 1,
                        charCount = 12,
                        width = 120.5f,
                        height = 18.0f
                    )
                )
            )
        )
    }
}

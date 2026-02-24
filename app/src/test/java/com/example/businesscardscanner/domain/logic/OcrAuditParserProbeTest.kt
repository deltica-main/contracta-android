package com.example.businesscardscanner.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrAuditParserProbeTest {

    @Test
    fun probe_multiline_address_selected_in_five_cases() {
        val cases = listOf(
            ProbeCase(
                id = "us_with_suite",
                lines = listOf(
                    "Jane Doe",
                    "Senior Engineer",
                    "Acme Corporation",
                    "123 Main Street",
                    "Suite 200",
                    "Springfield, IL 62704",
                    "jane@acme.com"
                ),
                expectedAddressFragments = listOf("123 Main Street", "Springfield")
            ),
            ProbeCase(
                id = "us_two_line",
                lines = listOf(
                    "John Smith",
                    "Director",
                    "Northwind LLC",
                    "742 Evergreen Terrace",
                    "Springfield, OR 97403",
                    "john@northwind.com"
                ),
                expectedAddressFragments = listOf("Evergreen", "97403")
            ),
            ProbeCase(
                id = "canada_with_unit",
                lines = listOf(
                    "Ava Chen",
                    "Product Lead",
                    "Brightline Inc",
                    "88 Bay Avenue",
                    "Unit 11",
                    "Montreal, QC H2Z 1A4",
                    "ava@brightline.com"
                ),
                expectedAddressFragments = listOf("Bay Avenue", "H2Z")
            ),
            ProbeCase(
                id = "canada_without_comma",
                lines = listOf(
                    "Noah Patel",
                    "Operations Manager",
                    "Vector Labs",
                    "4500 King St W",
                    "Floor 5",
                    "Toronto ON M5V 2T6",
                    "noah@vectorlabs.com"
                ),
                expectedAddressFragments = listOf("King St", "M5V")
            ),
            ProbeCase(
                id = "geometry_grouping",
                lines = listOf(
                    "Maria Lee",
                    "Chief Marketing Officer",
                    "Orbit Limited",
                    "1600 Amphitheatre Parkway",
                    "Mountain View, CA 94043",
                    "maria@orbit.com"
                ),
                providedLines = geometryLines(
                    "Maria Lee",
                    "Chief Marketing Officer",
                    "Orbit Limited",
                    "1600 Amphitheatre Parkway",
                    "Mountain View, CA 94043",
                    "maria@orbit.com"
                ),
                expectedAddressFragments = listOf("Amphitheatre", "94043")
            )
        )

        val selected = cases.count { probe ->
            val result = parse(probe)
            val address = result.values[OcrFieldType.ADDRESS]
            val passed = address != null && probe.expectedAddressFragments.all { fragment ->
                address.contains(fragment, ignoreCase = true)
            }
            emitProbeRecord(probe.id, result, passed)
            passed
        }

        assertEquals(5, selected)
    }

    @Test
    fun probe_address_does_not_trigger_on_marketing_slogans() {
        val sloganOnlyCases = listOf(
            ProbeCase(
                id = "marketing_a",
                lines = listOf(
                    "Innovative solutions for growth",
                    "Delivering trusted outcomes",
                    "Empowering your future"
                )
            ),
            ProbeCase(
                id = "marketing_b",
                lines = listOf(
                    "Leading transformation in 2026",
                    "Trusted partner for excellence",
                    "Connected business outcomes"
                )
            ),
            ProbeCase(
                id = "marketing_c",
                lines = listOf(
                    "Global company solutions",
                    "Committed to your mission",
                    "Powering what matters"
                )
            )
        )

        val cleanCount = sloganOnlyCases.count { probe ->
            val result = parse(probe)
            val passed = result.values[OcrFieldType.ADDRESS] == null
            emitProbeRecord(probe.id, result, passed)
            passed
        }

        assertEquals(3, cleanCount)
    }

    @Test
    fun probe_title_selection_works_when_company_is_on_same_line() {
        val overlapCases = listOf(
            ProbeCase(
                id = "title_overlap_a",
                lines = listOf(
                    "Jane Doe",
                    "Director Acme Corporation",
                    "Acme Corporation",
                    "jane@acme.com"
                )
            ),
            ProbeCase(
                id = "title_overlap_b",
                lines = listOf(
                    "John Roe",
                    "VP Engineering Northwind LLC",
                    "Northwind LLC",
                    "john@northwind.com"
                )
            ),
            ProbeCase(
                id = "title_overlap_c",
                lines = listOf(
                    "Maria Lee",
                    "Chief Marketing Officer Brightline Inc",
                    "Brightline Inc",
                    "maria@brightline.com"
                )
            )
        )

        val hitCount = overlapCases.count { probe ->
            val result = parse(probe)
            val title = result.values[OcrFieldType.TITLE]
            val passed = !title.isNullOrBlank()
            emitProbeRecord(probe.id, result, passed)
            passed
        }

        assertEquals(3, hitCount)
    }

    @Test
    fun probe_title_not_lost_due_to_line_locking() {
        val lockingCases = listOf(
            ProbeCase(
                id = "line_lock_a",
                lines = listOf(
                    "Ava Chen",
                    "Director Acme Corporation",
                    "Acme Corporation",
                    "ava@acme.com"
                )
            ),
            ProbeCase(
                id = "line_lock_b",
                lines = listOf(
                    "Eli Park",
                    "Chief Product Officer Orbit LLC",
                    "Orbit LLC",
                    "eli@orbit.com"
                )
            )
        )

        val preservedCount = lockingCases.count { probe ->
            val result = parse(probe)
            val titleAudit = result.audit.fieldAudits[OcrFieldType.TITLE]
            val companyAudit = result.audit.fieldAudits[OcrFieldType.COMPANY]
            val titleLine = titleAudit?.selectedCandidate?.lineIndex
            val companyLine = companyAudit?.selectedCandidate?.lineIndex
            val passed = !result.values[OcrFieldType.TITLE].isNullOrBlank() &&
                titleLine != null &&
                companyLine != null &&
                titleLine != companyLine
            emitProbeRecord(probe.id, result, passed)
            passed
        }

        assertEquals(2, preservedCount)
    }

    @Test
    fun probe_contact_only_cards_keep_deterministic_fields_without_identity_hallucination() {
        val probe = ProbeCase(
            id = "contact_only",
            lines = listOf(
                "support@contoso.com",
                "+1 (555) 111-2222",
                "www.contoso.com"
            )
        )
        val result = parse(probe)
        emitProbeRecord(probe.id, result, true)

        assertNotNull(result.values[OcrFieldType.EMAIL])
        assertNotNull(result.values[OcrFieldType.PHONE])
        assertNotNull(result.values[OcrFieldType.WEBSITE])
        assertNull(result.values[OcrFieldType.NAME])
        assertNull(result.values[OcrFieldType.TITLE])
        assertNull(result.values[OcrFieldType.COMPANY])
    }

    @Test
    fun probe_slogan_only_cards_quarantine_without_identity_assignment() {
        val cases = listOf(
            ProbeCase(
                id = "slogan_only_a",
                lines = listOf(
                    "Powering your future",
                    "Trusted partner for excellence",
                    "Delivering innovative outcomes"
                )
            ),
            ProbeCase(
                id = "slogan_only_b",
                lines = listOf(
                    "Group of companies",
                    "Connecting what matters",
                    "Mission driven impact"
                )
            ),
            ProbeCase(
                id = "slogan_only_c",
                lines = listOf(
                    "Leading transformation",
                    "Committed to your mission",
                    "Empowering every step"
                )
            )
        )

        val cleanCount = cases.count { probe ->
            val result = parse(probe)
            val passed = result.values[OcrFieldType.NAME] == null &&
                result.values[OcrFieldType.TITLE] == null &&
                result.values[OcrFieldType.COMPANY] == null &&
                result.audit.quarantinedSlogans.isNotEmpty()
            emitProbeRecord(probe.id, result, passed)
            passed
        }
        assertEquals(3, cleanCount)
    }

    @Test
    fun probe_parser_is_deterministic_for_same_input() {
        val probe = ProbeCase(
            id = "deterministic_baseline",
            lines = listOf(
                "Jane Smith",
                "Operations Manager",
                "Oakville Hydro",
                "123 Main Street",
                "Suite 200",
                "Springfield, IL 62704",
                "jane.smith@oakvillehydro.com",
                "+1 (555) 123-4567"
            )
        )
        val first = parse(probe)
        repeat(5) {
            val next = parse(probe)
            assertEquals(first.values, next.values)
            assertEquals(first.confidenceByField, next.confidenceByField)
            assertEquals(first.audit.quarantinedSlogans.map { it.text }, next.audit.quarantinedSlogans.map { it.text })
            assertEquals(first.audit.addressBlocks, next.audit.addressBlocks)
        }
    }

    private fun parse(probe: ProbeCase): OcrAuditParseResult {
        val raw = probe.lines.joinToString("\n")
        return OcrAuditParser.parse(
            rawText = raw,
            providedLines = probe.providedLines,
            relaxed = false
        )
    }

    private fun geometryLines(vararg lines: String): List<NormalizedOcrLine> {
        var top = 10f
        return lines.mapIndexed { index, text ->
            val bottom = top + 18f
            val line = NormalizedOcrLine(
                text = text,
                index = index,
                left = if (index in 3..4) 40f else 12f,
                top = top,
                right = 300f,
                bottom = bottom
            )
            top += if (index in 3..4) 20f else 24f
            line
        }
    }

    private fun emitProbeRecord(caseId: String, result: OcrAuditParseResult, passed: Boolean) {
        val summary = buildString {
            append("{")
            append("\"case\":\"").append(caseId).append("\",")
            append("\"passed\":").append(passed).append(",")
            append("\"name\":").append(asJsonString(result.values[OcrFieldType.NAME])).append(",")
            append("\"title\":").append(asJsonString(result.values[OcrFieldType.TITLE])).append(",")
            append("\"company\":").append(asJsonString(result.values[OcrFieldType.COMPANY])).append(",")
            append("\"email\":").append(asJsonString(result.values[OcrFieldType.EMAIL])).append(",")
            append("\"phone\":").append(asJsonString(result.values[OcrFieldType.PHONE])).append(",")
            append("\"website\":").append(asJsonString(result.values[OcrFieldType.WEBSITE])).append(",")
            append("\"address\":").append(asJsonString(result.values[OcrFieldType.ADDRESS])).append(",")
            append("\"quarantined\":").append(result.audit.quarantinedSlogans.size).append(",")
            append("\"coverageFallback\":").append(result.audit.coverageFallbackLines.size)
            append("}")
        }
        println("PROBE_RECORD $summary")
    }

    private fun asJsonString(value: String?): String {
        return if (value == null) {
            "null"
        } else {
            "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n") + "\""
        }
    }

    private data class ProbeCase(
        val id: String,
        val lines: List<String>,
        val providedLines: List<NormalizedOcrLine> = emptyList(),
        val expectedAddressFragments: List<String> = emptyList()
    )
}

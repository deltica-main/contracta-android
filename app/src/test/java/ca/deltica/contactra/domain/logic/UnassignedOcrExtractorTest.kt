package ca.deltica.contactra.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnassignedOcrExtractorTest {

    @Test
    fun extract_excludes_lines_already_used_by_selected_fields() {
        val audit = OcrExtractionAudit(
            normalizedLines = listOf(
                NormalizedOcrLine(text = "Jane Doe", index = 0),
                NormalizedOcrLine(text = "Senior Engineer", index = 1),
                NormalizedOcrLine(text = "Acme Inc", index = 2),
                NormalizedOcrLine(text = "Asset Management Technologist", index = 3)
            ),
            fieldAudits = mapOf(
                OcrFieldType.NAME to OcrFieldAudit(selectedCandidate = candidate(OcrFieldType.NAME, "Jane Doe", 0)),
                OcrFieldType.TITLE to OcrFieldAudit(selectedCandidate = candidate(OcrFieldType.TITLE, "Senior Engineer", 1)),
                OcrFieldType.COMPANY to OcrFieldAudit(selectedCandidate = candidate(OcrFieldType.COMPANY, "Acme Inc", 2))
            )
        )

        val items = UnassignedOcrExtractor.extract(audit)

        assertEquals(1, items.size)
        assertEquals("Asset Management Technologist", items.first().displayText)
        assertEquals(listOf(3), items.first().lineIndices)
    }

    @Test
    fun extract_excludes_quarantined_slogan_lines() {
        val audit = OcrExtractionAudit(
            normalizedLines = listOf(
                NormalizedOcrLine(text = "Powering your future", index = 0),
                NormalizedOcrLine(text = "Asset Management Technologist", index = 1)
            ),
            quarantinedSlogans = listOf(
                candidate(OcrFieldType.COMPANY, "Powering your future", 0, OcrLineLabel.TAGLINE_OR_SLOGAN)
            )
        )

        val items = UnassignedOcrExtractor.extract(audit)

        assertEquals(1, items.size)
        assertEquals("Asset Management Technologist", items.first().displayText)
    }

    @Test
    fun extract_excludes_deterministic_contact_lines_when_captured() {
        val audit = OcrExtractionAudit(
            normalizedLines = listOf(
                NormalizedOcrLine(text = "support@acme.com", index = 0),
                NormalizedOcrLine(text = "+1 (555) 111-2222", index = 1),
                NormalizedOcrLine(text = "www.acme.com", index = 2),
                NormalizedOcrLine(text = "Asset Management Technologist", index = 3),
                NormalizedOcrLine(text = "support@acme.com", index = 4)
            ),
            fieldAudits = mapOf(
                OcrFieldType.EMAIL to OcrFieldAudit(selectedCandidate = candidate(OcrFieldType.EMAIL, "support@acme.com", 0)),
                OcrFieldType.PHONE to OcrFieldAudit(selectedCandidate = candidate(OcrFieldType.PHONE, "+1 (555) 111-2222", 1)),
                OcrFieldType.WEBSITE to OcrFieldAudit(selectedCandidate = candidate(OcrFieldType.WEBSITE, "www.acme.com", 2))
            )
        )

        val items = UnassignedOcrExtractor.extract(
            audit = audit,
            selectedValues = mapOf(
                OcrFieldType.EMAIL to "support@acme.com",
                OcrFieldType.PHONE to "+1 (555) 111-2222",
                OcrFieldType.WEBSITE to "www.acme.com"
            )
        )

        assertEquals(1, items.size)
        assertEquals("Asset Management Technologist", items.first().displayText)
        assertFalse(items.any { it.displayText.contains("@") })
    }

    @Test
    fun extract_dedupes_by_normalized_text() {
        val audit = OcrExtractionAudit(
            normalizedLines = listOf(
                NormalizedOcrLine(text = "Asset Management Technologist", index = 0),
                NormalizedOcrLine(text = "  asset   management technologist ", index = 1),
                NormalizedOcrLine(text = "Technology Strategy", index = 2)
            )
        )

        val items = UnassignedOcrExtractor.extract(audit)

        assertEquals(2, items.size)
        assertEquals(1, items.count { it.displayText.contains("Asset Management Technologist", ignoreCase = true) })
    }

    @Test
    fun extract_groups_address_block_into_single_item() {
        val audit = OcrExtractionAudit(
            normalizedLines = listOf(
                NormalizedOcrLine(text = "100 Main Street", index = 0, left = 24f, top = 120f, bottom = 138f),
                NormalizedOcrLine(text = "Suite 200", index = 1, left = 24f, top = 142f, bottom = 160f),
                NormalizedOcrLine(text = "Springfield, IL 62704", index = 2, left = 24f, top = 164f, bottom = 182f),
                NormalizedOcrLine(text = "Asset Management Technologist", index = 3, left = 24f, top = 210f, bottom = 228f)
            ),
            addressBlocks = listOf(listOf(0, 1, 2))
        )

        val items = UnassignedOcrExtractor.extract(audit)
        val grouped = items.firstOrNull { it.lineIndices == listOf(0, 1, 2) }

        assertTrue(grouped != null)
        assertTrue(grouped!!.isGrouped)
        assertEquals(3, grouped.lines.size)
    }

    private fun candidate(
        field: OcrFieldType,
        text: String,
        lineIndex: Int,
        label: OcrLineLabel = OcrLineLabel.OTHER
    ): OcrFieldCandidate {
        return OcrFieldCandidate(
            field = field,
            text = text,
            lineIndex = lineIndex,
            confidence = 0.9,
            label = label
        )
    }
}

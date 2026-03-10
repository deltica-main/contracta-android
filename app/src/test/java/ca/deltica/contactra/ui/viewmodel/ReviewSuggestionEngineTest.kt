package ca.deltica.contactra.ui.viewmodel

import ca.deltica.contactra.domain.logic.ExtractedData
import ca.deltica.contactra.domain.logic.NormalizedOcrLine
import ca.deltica.contactra.domain.logic.OcrExtractionAudit
import ca.deltica.contactra.domain.logic.OcrFieldAudit
import ca.deltica.contactra.domain.logic.OcrFieldCandidate
import ca.deltica.contactra.domain.logic.OcrFieldType
import ca.deltica.contactra.domain.logic.OcrLineLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSuggestionEngineTest {

    @Test
    fun build_filters_taglines_and_normalizes_contact_values() {
        val audit = OcrExtractionAudit(
            normalizedLines = listOf(
                NormalizedOcrLine(text = "Jane Smith", index = 0),
                NormalizedOcrLine(text = "Oakville Hydro", index = 1),
                NormalizedOcrLine(text = "OEC group of companies", index = 2)
            ),
            fieldAudits = mapOf(
                OcrFieldType.NAME to OcrFieldAudit(
                    selectedCandidate = candidate(
                        field = OcrFieldType.NAME,
                        text = "Jane Smith",
                        index = 0,
                        confidence = 0.9,
                        label = OcrLineLabel.PERSON_NAME
                    ),
                    alternatives = listOf(
                        candidate(
                            field = OcrFieldType.NAME,
                            text = "OEC group of companies",
                            index = 2,
                            confidence = 0.7,
                            label = OcrLineLabel.TAGLINE_OR_SLOGAN
                        )
                    )
                ),
                OcrFieldType.COMPANY to OcrFieldAudit(
                    selectedCandidate = candidate(
                        field = OcrFieldType.COMPANY,
                        text = "Oakville Hydro",
                        index = 1,
                        confidence = 0.95,
                        label = OcrLineLabel.COMPANY_NAME
                    ),
                    alternatives = listOf(
                        candidate(
                            field = OcrFieldType.COMPANY,
                            text = "OEC group of companies",
                            index = 2,
                            confidence = 0.6,
                            label = OcrLineLabel.TAGLINE_OR_SLOGAN
                        )
                    )
                )
            ),
            quarantinedSlogans = listOf(
                candidate(
                    field = OcrFieldType.COMPANY,
                    text = "OEC group of companies",
                    index = 2,
                    confidence = 0.85,
                    label = OcrLineLabel.TAGLINE_OR_SLOGAN
                )
            )
        )

        val suggestions = ReviewSuggestionEngine.build(
            audit = audit,
            extracted = ExtractedData(
                name = "Jane Smith",
                company = "Oakville Hydro",
                email = "JANE.SMITH@OAKVILLEHYDRO.COM",
                phone = "(555) 123-4567",
                website = "oakvillehydro.com/?utm_source=mail"
            ),
            rawOcrText = "JANE.SMITH@OAKVILLEHYDRO.COM (555) 123-4567 oakvillehydro.com/?utm_source=mail",
            inferredIndustry = null,
            companyIndustry = null
        )

        assertEquals("Jane Smith", suggestions.name.first())
        assertEquals("Oakville Hydro", suggestions.company.first())
        assertFalse(suggestions.name.any { it.contains("group of companies", ignoreCase = true) })
        assertFalse(suggestions.company.any { it.contains("group of companies", ignoreCase = true) })
        assertEquals("jane.smith@oakvillehydro.com", suggestions.email.first())
        assertEquals("+1 (555) 123-4567", suggestions.phone.first())
        assertEquals("https://oakvillehydro.com", suggestions.website.first())
    }

    @Test
    fun build_cleans_and_dedupes_address_and_industry_suggestions() {
        val audit = OcrExtractionAudit(
            normalizedLines = listOf(
                NormalizedOcrLine(text = "123 Main St.", index = 0),
                NormalizedOcrLine(text = "123  Main   St.", index = 1),
                NormalizedOcrLine(text = "Suite 500", index = 2),
                NormalizedOcrLine(text = "Engineering Services", index = 3)
            )
        )

        val suggestions = ReviewSuggestionEngine.build(
            audit = audit,
            extracted = ExtractedData(
                address = "123 Main St.",
                industry = "Engineering"
            ),
            rawOcrText = "123 Main St. Suite 500",
            inferredIndustry = "Engineering",
            companyIndustry = "Engineering"
        )

        assertTrue(suggestions.address.isNotEmpty())
        assertEquals("123 Main St", suggestions.address.first())
        assertEquals(1, suggestions.address.count { it.equals("123 Main St", ignoreCase = true) })
        assertTrue(suggestions.industry.any { it.equals("Engineering", ignoreCase = true) })
    }

    private fun candidate(
        field: OcrFieldType,
        text: String,
        index: Int,
        confidence: Double,
        label: OcrLineLabel
    ): OcrFieldCandidate {
        return OcrFieldCandidate(
            field = field,
            text = text,
            lineIndex = index,
            confidence = confidence,
            label = label
        )
    }
}

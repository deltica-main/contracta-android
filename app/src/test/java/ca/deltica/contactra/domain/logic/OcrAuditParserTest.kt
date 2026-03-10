package ca.deltica.contactra.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrAuditParserTest {

    @Test
    fun parse_prioritizes_person_name_over_corporate_line() {
        val input = """
            Jane Smith
            Operations Manager
            Oakville Hydro
            OEC group of companies
            jane.smith@oakvillehydro.com
            +1 (555) 123-4567
        """.trimIndent()

        val result = OcrAuditParser.parse(input)

        assertEquals("Jane Smith", result.values[OcrFieldType.NAME])
        assertEquals("Operations Manager", result.values[OcrFieldType.TITLE])
        assertEquals("Oakville Hydro", result.values[OcrFieldType.COMPANY])
        assertEquals("jane.smith@oakvillehydro.com", result.values[OcrFieldType.EMAIL])
        assertTrue(result.values[OcrFieldType.PHONE].orEmpty().contains("555"))
    }

    @Test
    fun parse_quarantines_slogans_from_core_fields() {
        val input = """
            OEC group of companies
            Powering your future
            Delivering trusted outcomes
            info@oec.example
        """.trimIndent()

        val result = OcrAuditParser.parse(input)

        assertNull(result.values[OcrFieldType.NAME])
        assertNull(result.values[OcrFieldType.TITLE])
        assertNull(result.values[OcrFieldType.COMPANY])
        assertTrue(
            result.audit.quarantinedSlogans.any {
                it.text.contains("group of companies", ignoreCase = true)
            }
        )
    }

    @Test
    fun parse_keeps_corporate_line_available_as_manual_name_candidate() {
        val input = """
            Oakville Hydro
            Jane Smith
            jane@oakvillehydro.com
        """.trimIndent()

        val result = OcrAuditParser.parse(input, relaxed = true)
        val nameAudit = result.audit.fieldAudits[OcrFieldType.NAME]

        assertNotNull(nameAudit)
        assertTrue(
            nameAudit!!.alternatives.any {
                it.text.contains("Oakville Hydro", ignoreCase = true)
            }
        )
        assertEquals("Jane Smith", result.values[OcrFieldType.NAME])
    }

    @Test
    fun parse_handles_empty_single_line_non_english_without_crash() {
        val empty = OcrAuditParser.parse("")
        assertTrue(empty.audit.normalizedLines.isEmpty())
        assertNull(empty.values[OcrFieldType.NAME])

        val single = OcrAuditParser.parse("Élodie")
        assertNotNull(single.audit)

        val mixedCase = OcrAuditParser.parse(
            """
                société générale
                DÉVELOPPEMENT DURABLE
                München 80331
            """.trimIndent()
        )
        assertNotNull(mixedCase.audit)
    }
}

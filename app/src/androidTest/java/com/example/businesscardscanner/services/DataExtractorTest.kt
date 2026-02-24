package com.example.businesscardscanner.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.businesscardscanner.domain.logic.DataExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataExtractorTest {
    @Test
    fun extract_parses_basic_fields() {
        val text = """
            Jane Doe
            Senior Engineer
            Acme Corp
            jane.doe@acme.com
            15551234567
            www.acme.com
        """.trimIndent()

        val data = DataExtractor.extract(text)

        assertEquals("Jane Doe", data.name)
        assertEquals("Senior Engineer", data.title)
        assertEquals("Acme Corp", data.company)
        assertEquals("jane.doe@acme.com", data.email)
        assertNotNull(data.phone)
        assertEquals("www.acme.com", data.website)
    }

    @Test
    fun extract_normalizesPhoneToE164WhenPossible() {
        val text = """
            John Smith
            ACME INC
            (555) 123-4567
        """.trimIndent()

        val data = DataExtractor.extract(text)

        assertEquals("+15551234567", data.phone)
    }

    @Test
    fun extractReliably_retriesWhenCoreFieldsMissing() {
        val text = """
            jane.doe@acme.com
            +1 (555) 987-6543
        """.trimIndent()

        val result = DataExtractor.extractReliably(text)

        assertTrue(result.didRetry)
        assertNull(result.data.name)
        assertNull(result.data.company)
    }

    @Test
    fun extractReliably_ignoresImageOnlyNoise() {
        val text = """
            ***
            ---
            ////
        """.trimIndent()

        val result = DataExtractor.extractReliably(text)

        assertNull(result.data.name)
        assertNull(result.data.company)
        assertEquals("Ignored image-only OCR content.", result.retryMessage)
    }
}

package ca.deltica.contactra.services

import ca.deltica.contactra.domain.logic.InferenceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InferenceEngineTest {
    @Test
    fun infer_uses_title_and_company() {
        val result = InferenceEngine.infer(
            title = "Software Engineer",
            company = "Acme Corp"
        )

        assertEquals("Technology / Software", result.industry)
        assertEquals("high", result.industryConfidence)
    }

    @Test
    fun infer_returns_null_when_unknown() {
        val result = InferenceEngine.infer(
            title = null,
            company = null
        )

        assertNull(result.industry)
        assertNull(result.industryConfidence)
    }
}

package ca.deltica.contactra.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndustryPrefillPolicyTest {

    @Test
    fun resolve_keeps_existing_user_value_without_overwrite() {
        val decision = IndustryPrefillPolicy.resolve(
            currentIndustry = "Healthcare",
            currentIndustryCustom = null,
            currentSource = IndustrySource.USER_SELECTED,
            enrichmentEnabled = true,
            enrichmentIndustry = "Finance",
            enrichmentConfidence = 0.98,
            companyName = "Rivercity Utility"
        )

        assertEquals("Healthcare", decision.industry)
        assertNull(decision.industryCustom)
        assertEquals(IndustrySource.USER_SELECTED, decision.source)
    }

    @Test
    fun resolve_does_not_prefill_when_enrichment_confidence_is_below_threshold() {
        val decision = IndustryPrefillPolicy.resolve(
            currentIndustry = null,
            currentIndustryCustom = null,
            currentSource = null,
            enrichmentEnabled = true,
            enrichmentIndustry = "Telecom",
            enrichmentConfidence = 0.60,
            companyName = "Northwind Group"
        )

        assertNull(decision.industry)
        assertEquals(IndustrySource.EMPTY, decision.source)
    }

    @Test
    fun resolve_prefills_when_enrichment_confidence_meets_threshold() {
        val threshold = IndustryPrefillPolicy.ENRICHMENT_PREFILL_MIN_CONFIDENCE
        val decision = IndustryPrefillPolicy.resolve(
            currentIndustry = null,
            currentIndustryCustom = null,
            currentSource = null,
            enrichmentEnabled = true,
            enrichmentIndustry = "Telecom",
            enrichmentConfidence = threshold,
            companyName = "Northwind Group"
        )

        assertEquals("Telecom", decision.industry)
        assertEquals(IndustrySource.ENRICHMENT_INFERRED, decision.source)
    }

    @Test
    fun resolve_prefers_heuristic_when_enrichment_is_disabled() {
        val decision = IndustryPrefillPolicy.resolve(
            currentIndustry = null,
            currentIndustryCustom = null,
            currentSource = null,
            enrichmentEnabled = false,
            enrichmentIndustry = "Utilities",
            enrichmentConfidence = 0.95,
            companyName = "Harbor Dental Clinic"
        )

        assertEquals("Healthcare", decision.industry)
        assertEquals(IndustrySource.HEURISTIC_INFERRED, decision.source)
    }

    @Test
    fun resolveMerge_preserves_existing_custom_other_and_user_source() {
        val decision = IndustryPrefillPolicy.resolveMerge(
            existingIndustry = "Other",
            existingIndustryCustom = "Aviation",
            existingSource = IndustrySource.USER_SELECTED,
            incomingIndustry = "Healthcare",
            incomingIndustryCustom = null,
            incomingSource = IndustrySource.ENRICHMENT_INFERRED
        )

        assertEquals("Other", decision.industry)
        assertEquals("Aviation", decision.industryCustom)
        assertEquals(IndustrySource.USER_SELECTED, decision.source)
        assertNull(decision.metadataNote)
    }

    @Test
    fun resolveMerge_prefers_enrichment_over_heuristic_for_inferred_values() {
        val decision = IndustryPrefillPolicy.resolveMerge(
            existingIndustry = "Healthcare",
            existingIndustryCustom = null,
            existingSource = IndustrySource.HEURISTIC_INFERRED,
            incomingIndustry = "Utilities",
            incomingIndustryCustom = null,
            incomingSource = IndustrySource.ENRICHMENT_INFERRED
        )

        assertEquals("Utilities", decision.industry)
        assertEquals(IndustrySource.ENRICHMENT_INFERRED, decision.source)
        assertNull(decision.metadataNote)
    }

    @Test
    fun resolveMerge_keeps_existing_on_same_rank_conflict_and_records_metadata() {
        val decision = IndustryPrefillPolicy.resolveMerge(
            existingIndustry = "Utilities",
            existingIndustryCustom = null,
            existingSource = IndustrySource.ENRICHMENT_INFERRED,
            incomingIndustry = "Healthcare",
            incomingIndustryCustom = null,
            incomingSource = IndustrySource.ENRICHMENT_INFERRED
        )

        assertEquals("Utilities", decision.industry)
        assertEquals(IndustrySource.ENRICHMENT_INFERRED, decision.source)
        assertTrue((decision.metadataNote ?: "").contains("[industry_alt]"))
    }
}

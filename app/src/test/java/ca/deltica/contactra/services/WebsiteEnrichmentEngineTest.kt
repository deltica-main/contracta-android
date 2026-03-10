package ca.deltica.contactra.services

import ca.deltica.contactra.domain.logic.WebsiteEnrichmentEngine
import ca.deltica.contactra.domain.logic.IndustryPrefillPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteEnrichmentEngineTest {
    @Test
    fun enrich_fillsCompanyFromBusinessDomain() {
        val result = WebsiteEnrichmentEngine.enrich(
            company = null,
            website = "https://acme.com/contact",
            email = null,
            title = "Founder"
        )

        assertEquals("Acme", result.enrichedCompany)
        assertTrue(result.companyValidatedFromWebsite)
    }

    @Test
    fun enrich_preservesStrongExistingCompanyWhenDomainDiffers() {
        val result = WebsiteEnrichmentEngine.enrich(
            company = "Acme Corporation",
            website = "https://portal.contoso.com",
            email = null,
            title = "Engineer"
        )

        assertEquals("Acme Corporation", result.enrichedCompany)
    }

    @Test
    fun enrich_ignoresPublicEmailDomainsForCompanyValidation() {
        val result = WebsiteEnrichmentEngine.enrich(
            company = null,
            website = null,
            email = "person@gmail.com",
            title = "Engineer"
        )

        assertNull(result.enrichedCompany)
        assertFalse(result.companyValidatedFromWebsite)
    }

    @Test
    fun enrich_improvesIndustryInferenceFromDomainCompany() {
        val result = WebsiteEnrichmentEngine.enrich(
            company = null,
            website = "https://northwind-consulting.com",
            email = null,
            title = null
        )

        assertEquals("Northwind Consulting", result.enrichedCompany)
        assertEquals("Consulting", result.inferredIndustry)
        assertTrue((result.industryConfidence ?: 0.0) >= IndustryPrefillPolicy.ENRICHMENT_PREFILL_MIN_CONFIDENCE)
    }
}

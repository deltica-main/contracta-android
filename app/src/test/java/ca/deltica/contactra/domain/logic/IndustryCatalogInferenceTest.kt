package ca.deltica.contactra.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndustryCatalogInferenceTest {

    @Test
    fun inferIndustryFromWebsiteSignals_maps_utilities_with_high_confidence() {
        val (industry, confidence) = IndustryCatalog.inferIndustryFromWebsiteSignals(
            website = "https://northcounty-utility.com",
            pageTitle = "North County Utility Board",
            metaDescription = "Reliable power and water distribution services.",
            company = null
        )

        assertEquals("Utilities", industry)
        assertNotNull(confidence)
        assertTrue((confidence ?: 0.0) >= IndustryPrefillPolicy.ENRICHMENT_PREFILL_MIN_CONFIDENCE)
    }

    @Test
    fun inferIndustryFromWebsiteSignals_returns_null_for_ambiguous_matches() {
        val (industry, confidence) = IndustryCatalog.inferIndustryFromWebsiteSignals(
            website = "https://northwind-group.com",
            pageTitle = "Northwind Legal Clinic",
            metaDescription = "Boutique legal and healthcare services for local businesses.",
            company = "Northwind Group"
        )

        assertNull(industry)
        assertNull(confidence)
    }

    @Test
    fun inferIndustryFromCompanyName_maps_conservative_keywords() {
        assertEquals("Legal", IndustryCatalog.inferIndustryFromCompanyName("Baxter LLP Attorneys"))
        assertEquals("Healthcare", IndustryCatalog.inferIndustryFromCompanyName("Oak Ridge Dental Clinic"))
        assertEquals("Utilities", IndustryCatalog.inferIndustryFromCompanyName("River Hydro Utility Co"))
    }

    @Test
    fun inferIndustryFromCompanyName_returns_null_for_ambiguous_name() {
        assertNull(IndustryCatalog.inferIndustryFromCompanyName("Northwind Group"))
    }
}

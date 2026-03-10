package ca.deltica.contactra.services

import ca.deltica.contactra.domain.logic.ContactImagePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactImagePolicyTest {
    @Test
    fun managedPaths_deriveCounterpartsForLogoAndCard() {
        val logoPath = "C:/tmp/contact_123_logo.png"
        val cardPath = "C:/tmp/contact_123_card.jpg"
        val rawPath = "C:/tmp/contact_123_raw.jpg"

        assertEquals(cardPath, ContactImagePolicy.deriveOriginalCardPath(logoPath))
        assertEquals(logoPath, ContactImagePolicy.deriveLogoPath(cardPath))
        assertEquals(rawPath, ContactImagePolicy.deriveRawPath(logoPath))
        assertEquals(rawPath, ContactImagePolicy.deriveRawPath(cardPath))
        assertTrue(ContactImagePolicy.managedVisualCandidates(logoPath).contains(cardPath))
        assertTrue(ContactImagePolicy.managedVisualCandidates(cardPath).contains(logoPath))
        assertTrue(ContactImagePolicy.managedVisualCandidates(cardPath).contains(rawPath))
    }

    @Test
    fun autoLogoDetection_identifiesLogoSuffixOnly() {
        assertTrue(ContactImagePolicy.isAutoLogoPath("/data/contact_1_logo.png"))
        assertFalse(ContactImagePolicy.isAutoLogoPath("/data/contact_1_card.jpg"))
        assertFalse(ContactImagePolicy.isAutoLogoPath("/data/contact_1.jpg"))
    }
}

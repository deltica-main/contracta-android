package ca.deltica.contactra.data.integration

import ca.deltica.contactra.domain.model.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsSyncEngineTest {

    @Test
    fun phoneNormalizationAndMatching_useNanpLast10AndInternationalFullDigits() {
        assertEquals("nanp:5551234567", ContactsSyncRules.phoneMatchKey("+1 (555) 123-4567"))
        assertEquals("full:442079460958", ContactsSyncRules.phoneMatchKey("+44 20 7946 0958"))
    }

    @Test
    fun emailMatching_isCaseInsensitiveExact() {
        val incoming = ContactsSyncRules.prepareIncomingContact(
            contact(email = "JANE.DOE@acme.com")
        )
        val existing = snapshot(
            id = 11L,
            emails = listOf("jane.doe@acme.com")
        )

        val matchId = ContactsSyncRules.chooseMatchContactId(incoming, listOf(existing))

        assertEquals(11L, matchId)
    }

    @Test
    fun nameCompanyFallback_appliesOnlyWithoutEmailOrPhone() {
        val incomingFallback = ContactsSyncRules.prepareIncomingContact(
            contact(name = "Jane Doe", company = "Acme LLC")
        )
        val existing = snapshot(
            id = 12L,
            name = "jane doe",
            company = "ACME LLC"
        )

        val fallbackMatch = ContactsSyncRules.chooseMatchContactId(incomingFallback, listOf(existing))
        assertEquals(12L, fallbackMatch)

        val incomingWithEmail = ContactsSyncRules.prepareIncomingContact(
            contact(name = "Jane Doe", company = "Acme LLC", email = "new@acme.com")
        )
        val noFallbackMatch = ContactsSyncRules.chooseMatchContactId(incomingWithEmail, listOf(existing))
        assertNull(noFallbackMatch)
    }

    @Test
    fun noOverwriteBehavior_keepsExistingNameCompanyAndTitle() {
        val incoming = ContactsSyncRules.prepareIncomingContact(
            contact(
                name = "Jane New Name",
                company = "New Co",
                title = "Director",
                email = "jane@acme.com",
                phone = "+1 555 123 4567"
            )
        )
        val existing = snapshot(
            id = 13L,
            name = "Jane Existing",
            company = "Acme",
            title = "CEO",
            emails = listOf("jane@acme.com"),
            phones = listOf("+15551234567")
        )

        val plan = ContactsSyncRules.buildFieldAddPlan(incoming, existing)

        assertNull(plan.addName)
        assertNull(plan.addCompany)
        assertNull(plan.addTitle)
    }

    @Test
    fun duplicateInsertionBehavior_skipsEquivalentPhoneEmailAndWebsite() {
        val incoming = ContactsSyncRules.prepareIncomingContact(
            contact(
                email = "JANE@acme.com",
                phone = "(555) 123-4567",
                website = "https://www.acme.com/about"
            )
        )
        val existing = snapshot(
            id = 14L,
            emails = listOf("jane@acme.com"),
            phones = listOf("+1 555 123 4567"),
            websites = listOf("http://acme.com")
        )

        val plan = ContactsSyncRules.buildFieldAddPlan(incoming, existing)

        assertTrue(plan.addEmails.isEmpty())
        assertTrue(plan.addPhones.isEmpty())
        assertTrue(plan.addWebsites.isEmpty())
    }

    @Test
    fun notesAppend_isIdempotentWhenScannedPrefixExists() {
        val incoming = ContactsSyncRules.prepareIncomingContact(
            contact(industry = "Technology")
        )
        val existing = snapshot(
            id = 15L,
            notes = listOf("Scanned business card: Industry: Finance")
        )

        val plan = ContactsSyncRules.buildFieldAddPlan(incoming, existing)

        assertNull(plan.addNote)
    }

    @Test
    fun bulkSummary_countsEachResultBucketCorrectly() {
        val summary = ContactsSyncRules.summarizeBulkResults(
            total = 6,
            processed = 6,
            cancelled = false,
            results = listOf(
                ContactsSyncResultType.AddedNew,
                ContactsSyncResultType.AddedNew,
                ContactsSyncResultType.UpdatedExisting,
                ContactsSyncResultType.AlreadyPresent,
                ContactsSyncResultType.SkippedMissingKey,
                ContactsSyncResultType.Failed
            )
        )

        assertEquals(2, summary.added)
        assertEquals(1, summary.updated)
        assertEquals(1, summary.skippedDuplicates)
        assertEquals(1, summary.skippedMissingKey)
        assertEquals(1, summary.failed)
    }

    private fun contact(
        name: String? = null,
        title: String? = null,
        company: String? = null,
        email: String? = null,
        phone: String? = null,
        website: String? = null,
        industry: String? = null
    ): Contact {
        return Contact(
            id = 1L,
            name = name,
            title = title,
            company = company,
            email = email,
            phone = phone,
            website = website,
            industry = industry,
            industrySource = null,
            rawOcrText = null,
            imagePath = null
        )
    }

    private fun snapshot(
        id: Long,
        name: String? = null,
        title: String? = null,
        company: String? = null,
        emails: List<String> = emptyList(),
        phones: List<String> = emptyList(),
        websites: List<String> = emptyList(),
        addresses: List<String> = emptyList(),
        notes: List<String> = emptyList()
    ): ExistingContactSnapshot {
        return ExistingContactSnapshot(
            contactId = id,
            lookupKey = null,
            lookupUri = null,
            rawContactIds = listOf(1L),
            displayName = name,
            company = company,
            title = title,
            emails = emails,
            emailKeys = emails.mapNotNull { ContactsSyncRules.normalizeEmail(it) }.toSet(),
            phones = phones,
            phoneKeys = phones.mapNotNull { ContactsSyncRules.phoneMatchKey(it) }.toSet(),
            websites = websites,
            websiteKeys = websites.mapNotNull { ContactsSyncRules.websiteComparisonKey(it) }.toSet(),
            addresses = addresses,
            addressKeys = addresses.mapNotNull { ContactsSyncRules.normalizeAddress(it) }.toSet(),
            notes = notes,
            hasPhoto = false,
            normalizedName = normalize(name),
            normalizedCompany = normalize(company)
        )
    }

    private fun normalize(value: String?): String? {
        return value
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9]+"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}


package ca.deltica.contactra.ui.viewmodel

import ca.deltica.contactra.domain.model.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateMergePreviewTest {

    @Test
    fun buildDuplicateReasons_reportsEmailAndPhoneMatches() {
        val existing = Contact(
            id = 42L,
            name = "Jane Doe",
            title = "Founder",
            company = "Acme",
            email = "JANE@acme.com",
            phone = "+15551234567",
            website = "acme.com",
            industry = null,
            industrySource = null,
            rawOcrText = null,
            imagePath = null
        )

        val reasons = buildDuplicateReasons(
            existing = existing,
            incomingEmail = "jane@acme.com",
            incomingPhone = "+1 555 123 4567"
        )

        assertTrue(reasons.contains("Same email"))
        assertTrue(reasons.contains("Same phone"))
    }

    @Test
    fun buildMergePreviewFields_marksOverwriteWhenIncomingDiffersFromExisting() {
        val existing = Contact(
            id = 1L,
            name = "Jane Doe",
            title = "CEO",
            company = "Acme",
            email = "jane@acme.com",
            phone = "+15551234567",
            website = "acme.com",
            industry = "Technology",
            industrySource = "manual",
            rawOcrText = "old",
            imagePath = null
        )
        val review = ReviewFields(
            name = "Jane A. Doe",
            title = "CEO",
            company = "Acme",
            email = "jane@acme.com",
            phone = "+1 555 123 4567",
            website = "acme.com",
            industry = "Software",
            industrySource = "manual"
        )

        val previewFields = buildMergePreviewFields(
            existing = existing,
            reviewFields = review,
            normalizedIncomingPhone = "+15551234567",
            resolvedIndustry = "Software",
            resolvedIndustryCustom = null,
            resolvedIndustrySource = "manual",
            incomingRawOcrText = "new ocr"
        )

        val nameField = previewFields.first { it.label == "Name" }
        assertTrue(nameField.willOverwriteExisting)
        assertEquals("Jane Doe", nameField.existingValue)
        assertEquals("Jane A. Doe", nameField.mergedValue)

        val phoneField = previewFields.first { it.label == "Phone" }
        assertTrue(!phoneField.changesExisting)
        assertTrue(!phoneField.willOverwriteExisting)
    }
}

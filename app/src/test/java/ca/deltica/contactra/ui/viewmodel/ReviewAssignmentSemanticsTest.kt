package ca.deltica.contactra.ui.viewmodel

import ca.deltica.contactra.domain.logic.IndustrySource
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewAssignmentSemanticsTest {

    @Test
    fun structuredFieldsReplaceExistingValue() {
        val fields = ReviewFields(
            name = "Old Name",
            title = "Old Title",
            company = "Old Co",
            email = "old@example.com",
            phone = "111",
            website = "old.co",
            address = "Old Street",
            industry = "Old Industry"
        )

        assertEquals(
            "New Name",
            applyReviewAssignmentUpdate(
                fields = fields,
                currentNotes = "",
                targetField = ReviewAssignmentField.NAME,
                incomingText = "New Name"
            ).fields.name
        )
        assertEquals(
            "new@example.com",
            applyReviewAssignmentUpdate(
                fields = fields,
                currentNotes = "",
                targetField = ReviewAssignmentField.EMAIL,
                incomingText = "new@example.com"
            ).fields.email
        )
        assertEquals(
            "123 New Ave",
            applyReviewAssignmentUpdate(
                fields = fields,
                currentNotes = "",
                targetField = ReviewAssignmentField.ADDRESS,
                incomingText = "123 New Ave"
            ).fields.address
        )
    }

    @Test
    fun industryReplaceKeepsUserSelectedSource() {
        val fields = ReviewFields(industry = "Finance", industrySource = null, industryCustom = "legacy")

        val updated = applyReviewAssignmentUpdate(
            fields = fields,
            currentNotes = "",
            targetField = ReviewAssignmentField.INDUSTRY,
            incomingText = "Healthcare"
        ).fields

        assertEquals("Healthcare", updated.industry)
        assertEquals("", updated.industryCustom)
        assertEquals(IndustrySource.USER_SELECTED, updated.industrySource)
    }

    @Test
    fun notesStillAppendIntentionally() {
        val update = applyReviewAssignmentUpdate(
            fields = ReviewFields(),
            currentNotes = "Met at summit",
            targetField = ReviewAssignmentField.NOTES,
            incomingText = "Needs follow up"
        )

        assertEquals("Met at summit\n\nNeeds follow up", update.notes)
    }

    @Test
    fun blankIncomingDoesNotOverwriteStructuredValue() {
        val fields = ReviewFields(name = "Manual Name")

        val update = applyReviewAssignmentUpdate(
            fields = fields,
            currentNotes = "",
            targetField = ReviewAssignmentField.NAME,
            incomingText = "   "
        )

        assertEquals("Manual Name", update.fields.name)
    }

    @Test
    fun repeatedStructuredAssignments_doNotAppendOrDuplicate() {
        val first = applyReviewAssignmentUpdate(
            fields = ReviewFields(email = "legacy@old.com"),
            currentNotes = "",
            targetField = ReviewAssignmentField.EMAIL,
            incomingText = "alex@northpeak.com"
        )

        val second = applyReviewAssignmentUpdate(
            fields = first.fields,
            currentNotes = first.notes,
            targetField = ReviewAssignmentField.EMAIL,
            incomingText = "alex@northpeak.com"
        )

        assertEquals("alex@northpeak.com", second.fields.email)
    }
}

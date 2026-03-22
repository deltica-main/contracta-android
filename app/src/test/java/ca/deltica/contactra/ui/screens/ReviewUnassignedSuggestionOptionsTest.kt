package ca.deltica.contactra.ui.screens

import ca.deltica.contactra.domain.logic.UnassignedItemKind
import ca.deltica.contactra.domain.logic.UnassignedOcrItem
import ca.deltica.contactra.ui.viewmodel.ReviewAssignmentField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewUnassignedSuggestionOptionsTest {

    @Test
    fun contactFieldsExposeOnlyMatchingUnassignedItems() {
        val items = listOf(
            item(id = "email_1", text = "alex@northpeak.com"),
            item(id = "phone_1", text = "+1 (416) 555-1200"),
            item(id = "website_1", text = "www.northpeak.com"),
            item(id = "title_1", text = "Account Manager", kind = UnassignedItemKind.TITLE_LIKE)
        )

        val emailSuggestions = items.toUnassignedSuggestionOptions(ReviewAssignmentField.EMAIL)
        val phoneSuggestions = items.toUnassignedSuggestionOptions(ReviewAssignmentField.PHONE)
        val websiteSuggestions = items.toUnassignedSuggestionOptions(ReviewAssignmentField.WEBSITE)

        assertEquals(listOf("email_1"), emailSuggestions.map { it.value })
        assertEquals(listOf("phone_1"), phoneSuggestions.map { it.value })
        assertEquals(listOf("website_1"), websiteSuggestions.map { it.value })
    }

    @Test
    fun websiteSuggestionsRejectEmailLikeValues() {
        val items = listOf(
            item(id = "email_1", text = "hello@domain.com"),
            item(id = "website_1", text = "domain.com")
        )

        val websiteSuggestions = items.toUnassignedSuggestionOptions(ReviewAssignmentField.WEBSITE)
        val offeredIds = websiteSuggestions.map { it.value }

        assertTrue("website_1" in offeredIds)
        assertTrue("email_1" !in offeredIds)
    }

    private fun item(
        id: String,
        text: String,
        kind: UnassignedItemKind = UnassignedItemKind.OTHER
    ): UnassignedOcrItem {
        return UnassignedOcrItem(
            id = id,
            displayText = text,
            lines = listOf(text),
            lineIndices = listOf(1),
            kind = kind,
            isGrouped = false
        )
    }
}

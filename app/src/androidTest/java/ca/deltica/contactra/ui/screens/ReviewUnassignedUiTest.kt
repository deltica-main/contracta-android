package ca.deltica.contactra.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.deltica.contactra.ComposeHostActivity
import ca.deltica.contactra.domain.logic.UnassignedItemKind
import ca.deltica.contactra.domain.logic.UnassignedOcrItem
import ca.deltica.contactra.ui.viewmodel.ReviewAssignmentField
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewUnassignedUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeHostActivity>()

    @Test
    fun unassignedSection_appears_only_when_items_exist() {
        var items by mutableStateOf<List<UnassignedOcrItem>>(emptyList())

        composeRule.setContent {
            MaterialTheme {
                UnassignedSectionContent(
                    items = items,
                    hasMore = false,
                    onViewAllClick = {},
                    onAssignClick = {}
                )
            }
        }
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("unassigned_section").assertCountEquals(0)

        composeRule.runOnIdle {
            items = listOf(sampleItem(id = "line_1", text = "Asset Management Technologist"))
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("unassigned_section").assertCountEquals(1)
    }

    @Test
    fun assigning_item_to_title_removes_unassigned_item() {
        var items by mutableStateOf(
            listOf(sampleItem(id = "line_1", text = "Asset Management Technologist"))
        )
        var selectedItemId by mutableStateOf<String?>(null)
        var titleValue by mutableStateOf("")

        composeRule.setContent {
            val selected = items.firstOrNull { it.id == selectedItemId }
            MaterialTheme {
                Column {
                    UnassignedSectionContent(
                        items = items,
                        hasMore = false,
                        onViewAllClick = {},
                        onAssignClick = { selectedItemId = it }
                    )
                    Text(
                        text = titleValue,
                        modifier = Modifier.testTag("title_field_value")
                    )
                    if (selected != null) {
                        AssignUnassignedFieldSheet(
                            item = selected,
                            onDismiss = { selectedItemId = null },
                            onAssign = { field ->
                                if (field == ReviewAssignmentField.TITLE) {
                                    titleValue = if (titleValue.isBlank()) {
                                        selected.displayText
                                    } else {
                                        "$titleValue | ${selected.displayText}"
                                    }
                                }
                                items = items.filterNot { it.id == selected.id }
                                selectedItemId = null
                            }
                        )
                    }
                }
            }
        }
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Assign unassigned text")
            .assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Assign unassigned text").performClick()
        composeRule.onAllNodesWithTag("assign_field_sheet").assertCountEquals(1)
        composeRule.onAllNodesWithText("Title").onFirst().performClick()

        composeRule.onNodeWithTag("title_field_value")
            .assertTextContains("Asset Management Technologist")
        composeRule.onAllNodesWithTag("unassigned_section").assertCountEquals(0)
    }

    @Test
    fun viewAll_opens_full_list_when_more_than_six_items_exist() {
        val allItems = (1..7).map { index ->
            sampleItem(
                id = "line_$index",
                text = "Unassigned line $index"
            )
        }
        var showAll by mutableStateOf(false)

        composeRule.setContent {
            MaterialTheme {
                Column {
                    UnassignedSectionContent(
                        items = allItems.take(6),
                        hasMore = allItems.size > 6,
                        onViewAllClick = { showAll = true },
                        onAssignClick = {}
                    )
                    if (showAll) {
                        UnassignedFullListDialog(
                            items = allItems,
                            onDismiss = { showAll = false },
                            onAssign = {}
                        )
                    }
                }
            }
        }
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("unassigned_view_all").assertCountEquals(1)
        composeRule.onNodeWithTag("unassigned_view_all").performClick()
        composeRule.onAllNodesWithTag("unassigned_full_list").assertCountEquals(1)
    }

    @Test
    fun assignSheet_doesNotOfferIndustryField() {
        composeRule.setContent {
            MaterialTheme {
                AssignUnassignedFieldSheet(
                    item = sampleItem(id = "line_1", text = "Operations"),
                    onDismiss = {},
                    onAssign = {}
                )
            }
        }
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Industry").assertCountEquals(0)
    }

    private fun sampleItem(id: String, text: String): UnassignedOcrItem {
        return UnassignedOcrItem(
            id = id,
            displayText = text,
            lines = listOf(text),
            lineIndices = listOf(1),
            kind = UnassignedItemKind.OTHER,
            isGrouped = false
        )
    }
}

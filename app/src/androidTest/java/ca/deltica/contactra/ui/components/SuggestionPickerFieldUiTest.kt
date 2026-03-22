package ca.deltica.contactra.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.deltica.contactra.ComposeHostActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SuggestionPickerFieldUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeHostActivity>()

    @Test
    fun emptyFieldTapOpensPickerAndAssignsUnassignedValue() {
        var value by mutableStateOf("")
        var unassigned by mutableStateOf(
            listOf(
                SuggestionOption(
                    value = "alex@northpeak.com",
                    display = "alex@northpeak.com"
                )
            )
        )

        composeRule.setContent {
            MaterialTheme {
                Column {
                    SuggestionPickerField(
                        label = "Email",
                        value = value,
                        onValueChange = { value = it },
                        suggestions = emptyList(),
                        fromUnassigned = if (value.isBlank()) unassigned else emptyList(),
                        onSelectFromUnassigned = { option ->
                            value = option.value
                            unassigned = unassigned.filterNot { it.value == option.value }
                        },
                        openPickerOnEmptyFieldTap = true,
                        textFieldTestTag = "email_input"
                    )
                    Text(
                        text = value,
                        modifier = Modifier.testTag("picked_value")
                    )
                    Text(
                        text = unassigned.size.toString(),
                        modifier = Modifier.testTag("unassigned_size")
                    )
                }
            }
        }
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Open Email suggestions").performClick()
        composeRule.onNodeWithText("alex@northpeak.com").performClick()

        composeRule.onNodeWithTag("picked_value").assertTextContains("alex@northpeak.com")
        composeRule.onNodeWithTag("unassigned_size").assertTextContains("0")
    }

    @Test
    fun typeManuallyActionUnlocksFieldInput() {
        var value by mutableStateOf("")
        val unassigned = listOf(
            SuggestionOption(
                value = "ignored@example.com",
                display = "ignored@example.com"
            )
        )

        composeRule.setContent {
            MaterialTheme {
                Column {
                    SuggestionPickerField(
                        label = "Email",
                        value = value,
                        onValueChange = { value = it },
                        suggestions = emptyList(),
                        fromUnassigned = if (value.isBlank()) unassigned else emptyList(),
                        openPickerOnEmptyFieldTap = true,
                        textFieldTestTag = "email_input"
                    )
                    Text(
                        text = value,
                        modifier = Modifier.testTag("manual_value")
                    )
                }
            }
        }
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Open Email suggestions").performClick()
        composeRule.onNodeWithText("Type manually").performClick()
        composeRule.onNodeWithTag("email_input").performTextInput("manual@entry.com")

        composeRule.onNodeWithTag("manual_value").assertTextContains("manual@entry.com")
    }
}

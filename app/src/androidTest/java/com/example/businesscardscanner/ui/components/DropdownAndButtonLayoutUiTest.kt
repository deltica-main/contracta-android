package com.example.businesscardscanner.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.businesscardscanner.ComposeHostActivity
import com.example.businesscardscanner.domain.logic.ConnectionCatalog
import com.example.businesscardscanner.domain.logic.IndustryCatalog
import com.example.businesscardscanner.ui.screens.HomeAddContactActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DropdownAndButtonLayoutUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeHostActivity>()

    @Test
    fun connectionDropdown_supports_required_options_and_custom_value() {
        composeRule.setContent {
            var relationship by mutableStateOf("Client")
            var expanded by mutableStateOf(false)
            val selectedOption = if (relationship in ConnectionCatalog.options) {
                relationship
            } else {
                ConnectionCatalog.OTHER
            }
            val isCustomRelationship = selectedOption == ConnectionCatalog.OTHER
            MaterialTheme {
                Column {
                    AnchoredDropdownField(
                        label = "Connection",
                        value = selectedOption,
                        options = ConnectionCatalog.options,
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        onOptionSelected = { option ->
                            relationship = if (option == ConnectionCatalog.OTHER) {
                                ConnectionCatalog.OTHER
                            } else {
                                option
                            }
                        },
                        anchorTestTag = "connection_dropdown_anchor",
                        menuTestTag = "connection_dropdown_menu"
                    )
                    if (isCustomRelationship) {
                        AppTextField(
                            value = if (relationship == ConnectionCatalog.OTHER) "" else relationship,
                            onValueChange = { relationship = it },
                            label = "Custom connection",
                            modifier = Modifier.testTag("custom_connection_input")
                        )
                    }
                    androidx.compose.material3.Text(
                        text = relationship,
                        modifier = Modifier.testTag("relationship_value")
                    )
                }
            }
        }

        composeRule.onNodeWithTag("connection_dropdown_anchor").performClick()
        composeRule.onNodeWithTag("connection_dropdown_menu").assertIsDisplayed()
        ConnectionCatalog.options.forEach { option ->
            val optionTag = option
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
            composeRule.onNodeWithTag("connection_dropdown_menu_option_$optionTag").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("connection_dropdown_menu_option_other").performClick()
        composeRule.onNodeWithTag("custom_connection_input").performTextInput("Advisor")
        composeRule.onNodeWithTag("relationship_value").assertTextContains("Advisor")
    }

    @Test
    fun industrySelector_supports_other_custom_value() {
        composeRule.setContent {
            var industry by mutableStateOf("Technology")
            MaterialTheme {
                Column {
                    IndustrySelector(
                        value = industry,
                        onValueChange = { industry = it },
                        options = IndustryCatalog.manualSelectionIndustries
                    )
                    androidx.compose.material3.Text(
                        text = industry,
                        modifier = Modifier.testTag("industry_value")
                    )
                }
            }
        }

        composeRule.onNodeWithTag("industry_dropdown_anchor").performClick()
        composeRule.onNodeWithTag("industry_dropdown_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("industry_dropdown_menu_option_other").performClick()
        composeRule.onNodeWithTag("custom_industry_input").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_industry_input").performTextInput("Aerospace")
        composeRule.onNodeWithTag("industry_value").assertTextContains("Aerospace")
    }

    @Test
    fun industrySelector_shows_prefill_and_curated_options_and_persists_selection() {
        composeRule.setContent {
            var industry by mutableStateOf("Finance")
            MaterialTheme {
                Column {
                    IndustrySelector(
                        value = industry,
                        onValueChange = { industry = it },
                        options = emptyList()
                    )
                    androidx.compose.material3.Text(
                        text = industry,
                        modifier = Modifier.testTag("industry_value")
                    )
                }
            }
        }

        composeRule.onNodeWithTag("industry_value").assertTextContains("Finance")
        composeRule.onNodeWithTag("industry_dropdown_anchor").performClick()
        composeRule.onNodeWithTag("industry_dropdown_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("industry_dropdown_menu_option_utilities").assertIsDisplayed()
        composeRule.onNodeWithTag("industry_dropdown_menu_option_telecom").assertIsDisplayed()
        composeRule.onNodeWithTag("industry_dropdown_menu_option_consulting").assertIsDisplayed()
        composeRule.onNodeWithTag("industry_dropdown_menu_option_other").assertIsDisplayed()
        composeRule.onNodeWithTag("industry_dropdown_menu_option_healthcare").performClick()
        composeRule.onNodeWithTag("industry_value").assertTextContains("Healthcare")
    }

    @Test
    fun homeActions_use_stacked_layout_on_compact_width() {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(320.dp)) {
                    HomeAddContactActions(
                        onScanClick = {},
                        onImportClick = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("home_scan_button").assertWidthIsAtLeast(240.dp)
        composeRule.onNodeWithTag("home_import_button").assertWidthIsAtLeast(240.dp)
        composeRule.onNodeWithText("Scan card").assertIsDisplayed()
    }
}

package com.example.businesscardscanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.businesscardscanner.domain.logic.IndustryCatalog
import com.example.businesscardscanner.ui.theme.AppDimens

@Composable
fun IndustrySelector(
    value: String,
    onValueChange: (String) -> Unit,
    customValue: String = "",
    onCustomValueChange: ((String) -> Unit)? = null,
    options: List<String>,
    label: String = "Industry",
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null
) {
    var localExpanded by remember { mutableStateOf(false) }
    val dropdownExpanded = expanded ?: localExpanded
    val setExpanded: (Boolean) -> Unit = onExpandedChange ?: { localExpanded = it }
    val normalized = remember(options) {
        options
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
    val selectionOptions = remember(normalized) {
        val base = if (normalized.isEmpty()) {
            IndustryCatalog.manualSelectionIndustries
        } else {
            normalized
        }
        if (base.any { it.equals("Other", ignoreCase = true) }) {
            base
        } else {
            base + "Other"
        }
    }
    val standardOptions = remember(selectionOptions) {
        selectionOptions.filterNot { it.equals("Other", ignoreCase = true) }
    }
    val hasSeparateCustomState = onCustomValueChange != null
    val hasLegacyCustomValue = value.isNotBlank() &&
        !value.equals("Other", ignoreCase = true) &&
        standardOptions.none { it.equals(value, ignoreCase = true) }
    val isCustomIndustry = value.equals("Other", ignoreCase = true) ||
        (!hasSeparateCustomState && hasLegacyCustomValue)
    val selectedOption = if (isCustomIndustry) "Other" else value
    val customFieldValue = when {
        hasSeparateCustomState -> customValue
        value.equals("Other", ignoreCase = true) -> ""
        hasLegacyCustomValue -> value
        else -> ""
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDimens.sm)
    ) {
        AnchoredDropdownField(
            label = label,
            value = selectedOption,
            options = selectionOptions,
            expanded = dropdownExpanded,
            onExpandedChange = setExpanded,
            onOptionSelected = { option ->
                if (option.equals("Other", ignoreCase = true)) {
                    onValueChange("Other")
                } else {
                    onValueChange(option)
                    onCustomValueChange?.invoke("")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            anchorTestTag = "industry_dropdown_anchor",
            menuTestTag = "industry_dropdown_menu"
        )
        if (isCustomIndustry) {
            AppTextField(
                value = customFieldValue,
                onValueChange = { custom ->
                    if (onCustomValueChange != null) {
                        if (!value.equals("Other", ignoreCase = true)) {
                            onValueChange("Other")
                        }
                        onCustomValueChange(custom)
                    } else {
                        onValueChange(custom)
                    }
                },
                label = "Custom industry",
                placeholder = "Aviation, Mining, Energy",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_industry_input")
            )
        }
    }
}

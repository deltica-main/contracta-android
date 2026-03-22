package ca.deltica.contactra.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import ca.deltica.contactra.ui.theme.AppDimens
import java.util.Locale

data class SuggestionOption(
    val value: String,
    val display: String = value,
    val secondaryLabel: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionPickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<SuggestionOption>,
    fromUnassigned: List<SuggestionOption> = emptyList(),
    fromUnassignedTitle: String = "From unassigned",
    onSelectFromUnassigned: ((SuggestionOption) -> Unit)? = null,
    openPickerOnEmptyFieldTap: Boolean = false,
    manualEntryLabel: String = "Type manually",
    textFieldTestTag: String? = null,
    modifier: Modifier = Modifier,
    helperText: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    formatForDisplay: (SuggestionOption) -> String = { it.display }
) {
    var showSheet by rememberSaveable(label) { mutableStateOf(false) }
    var manualEntryRequested by rememberSaveable(label) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dedupedSuggestions = remember(suggestions) { dedupeSuggestions(suggestions) }
    val dedupedUnassigned = remember(fromUnassigned) { dedupeSuggestions(fromUnassigned) }
    val hasPickerOptions = dedupedSuggestions.isNotEmpty() || dedupedUnassigned.isNotEmpty()
    val pickerOnEmptyFieldTapEnabled =
        openPickerOnEmptyFieldTap &&
            value.isBlank() &&
            dedupedUnassigned.isNotEmpty() &&
            !manualEntryRequested

    LaunchedEffect(value) {
        if (value.isNotBlank() && manualEntryRequested) {
            manualEntryRequested = false
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppDimens.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.sm),
            verticalAlignment = Alignment.Top
        ) {
            val textFieldModifier = textFieldTestTag?.let { tag ->
                Modifier.testTag(tag)
            } ?: Modifier
            if (pickerOnEmptyFieldTapEnabled) {
                Box(modifier = Modifier.weight(1f)) {
                    AppTextField(
                        value = value,
                        onValueChange = onValueChange,
                        label = label,
                        helperText = helperText,
                        isError = isError,
                        keyboardOptions = keyboardOptions,
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(textFieldModifier)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showSheet = true }
                            .semantics { contentDescription = "Open $label suggestions" }
                    )
                }
            } else {
                AppTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = label,
                    helperText = helperText,
                    isError = isError,
                    keyboardOptions = keyboardOptions,
                    readOnly = false,
                    modifier = Modifier
                        .weight(1f)
                        .then(textFieldModifier)
                )
            }
            if (hasPickerOptions) {
                val actionLabel = if (value.isBlank()) "Add" else "Change"
                TextButton(
                    onClick = { showSheet = true },
                    modifier = Modifier
                        .heightIn(min = AppDimens.touchTargetMin)
                        .semantics { contentDescription = "$actionLabel $label" },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    if (showSheet) {
        AppModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.md, vertical = AppDimens.sm),
                verticalArrangement = Arrangement.spacedBy(AppDimens.sm)
            ) {
                Text(
                    text = "Choose ${label.lowercase(Locale.US)}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                if (dedupedUnassigned.isNotEmpty()) {
                    Text(
                        text = fromUnassignedTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = AppDimens.xs)
                            .semantics { heading() }
                    )
                    dedupedUnassigned.forEach { suggestion ->
                        val displayValue = formatForDisplay(suggestion)
                        SuggestionOptionRow(
                            displayValue = displayValue,
                            secondaryLabel = suggestion.secondaryLabel,
                            selected = false,
                            onClick = {
                                if (onSelectFromUnassigned != null) {
                                    onSelectFromUnassigned(suggestion)
                                } else {
                                    onValueChange(suggestion.value)
                                }
                                showSheet = false
                            }
                        )
                    }
                }
                if (dedupedSuggestions.isNotEmpty()) {
                    if (dedupedUnassigned.isNotEmpty()) {
                        Text(
                            text = "Suggestions",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = AppDimens.xs)
                                .semantics { heading() }
                        )
                    }
                }
                dedupedSuggestions.forEach { suggestion ->
                    val displayValue = formatForDisplay(suggestion)
                    val isSelected = value.trim().equals(suggestion.value.trim(), ignoreCase = true)
                    SuggestionOptionRow(
                        displayValue = displayValue,
                        secondaryLabel = suggestion.secondaryLabel,
                        selected = isSelected,
                        onClick = {
                            onValueChange(suggestion.value)
                            showSheet = false
                        }
                    )
                }
                if (pickerOnEmptyFieldTapEnabled) {
                    TextButton(
                        onClick = {
                            manualEntryRequested = true
                            showSheet = false
                        },
                        modifier = Modifier
                            .align(Alignment.End)
                            .heightIn(min = AppDimens.touchTargetMin),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(manualEntryLabel)
                    }
                }
                TextButton(
                    onClick = { showSheet = false },
                    modifier = Modifier
                        .align(Alignment.End)
                        .heightIn(min = AppDimens.touchTargetMin),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun SuggestionOptionRow(
    displayValue: String,
    secondaryLabel: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "suggestionRowContainer"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.touchTargetMin)
            .semantics { this.selected = selected }
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.md, vertical = AppDimens.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.sm)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val helper = secondaryLabel ?: displayValue.takeIf { it.length > 46 }
                if (!helper.isNullOrBlank()) {
                    Text(
                        text = helper,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun dedupeSuggestions(suggestions: List<SuggestionOption>): List<SuggestionOption> {
    val seen = linkedSetOf<String>()
    val output = mutableListOf<SuggestionOption>()
    suggestions.forEach { option ->
        val key = option.value.trim().lowercase(Locale.US)
        if (key.isNotBlank() && seen.add(key)) {
            output += option
        }
    }
    return output
}

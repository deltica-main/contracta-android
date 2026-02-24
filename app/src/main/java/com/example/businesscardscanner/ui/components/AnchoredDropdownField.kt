package com.example.businesscardscanner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.businesscardscanner.ui.theme.AppDimens
import java.util.Locale

@Composable
fun AnchoredDropdownField(
    label: String,
    value: String,
    options: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    anchorTestTag: String? = null,
    menuTestTag: String? = null
) {
    val density = LocalDensity.current
    val normalizedOptions = remember(options) {
        options
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
    var anchorWidthPx by remember { mutableIntStateOf(1) }
    val optionTagPrefix = menuTestTag?.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                anchorWidthPx = coordinates.size.width
            }
    ) {
        AppTextField(
            value = value,
            onValueChange = {},
            label = label,
            readOnly = true,
            placeholder = placeholder,
            trailingContent = {
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.medium)
                .then(if (!anchorTestTag.isNullOrBlank()) Modifier.testTag(anchorTestTag) else Modifier)
                .clickable(enabled = enabled) { onExpandedChange(!expanded) }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier
                .width(with(density) { anchorWidthPx.toDp() })
                .heightIn(max = 320.dp)
                .then(if (!menuTestTag.isNullOrBlank()) Modifier.testTag(menuTestTag) else Modifier)
        ) {
            normalizedOptions.forEach { option ->
                val selected = option.equals(value, ignoreCase = true)
                val optionTag = optionTagPrefix?.let { prefix ->
                    val normalizedOptionTag = option
                        .lowercase(Locale.getDefault())
                        .replace(Regex("[^a-z0-9]+"), "_")
                        .trim('_')
                    "${prefix}_option_$normalizedOptionTag"
                }
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    onClick = {
                        onOptionSelected(option)
                        onExpandedChange(false)
                    },
                    modifier = Modifier
                        .heightIn(min = AppDimens.touchTargetMin)
                        .then(if (!optionTag.isNullOrBlank()) Modifier.testTag(optionTag) else Modifier)
                )
            }
        }
    }
}

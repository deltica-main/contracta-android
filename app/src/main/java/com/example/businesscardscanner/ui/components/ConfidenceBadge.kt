package com.example.businesscardscanner.ui.components

import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.businesscardscanner.ui.viewmodel.ConfidenceLevel

@Composable
fun ConfidenceBadge(
    level: ConfidenceLevel,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (level) {
        ConfidenceLevel.HIGH -> "High" to MaterialTheme.colorScheme.tertiary
        ConfidenceLevel.MEDIUM -> "Medium" to MaterialTheme.colorScheme.secondary
        ConfidenceLevel.LOW -> "Low" to MaterialTheme.colorScheme.outline
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        modifier = modifier,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.15f),
            labelColor = color
        )
    )
}

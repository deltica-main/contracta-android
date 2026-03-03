package com.example.businesscardscanner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun InfoChip(
    label: String,
    modifier: Modifier = Modifier
) {
    StatusPill(
        label = label,
        tone = StatusPillTone.Neutral,
        modifier = modifier,
        showDot = false
    )
}

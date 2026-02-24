package com.example.businesscardscanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

object AppTypeTokens {
    val screenTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium)

    val sectionTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)

    val fieldLabel: TextStyle
        @Composable get() = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)

    val body: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium

    val caption: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall

    val buttonText: TextStyle
        @Composable get() = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
}

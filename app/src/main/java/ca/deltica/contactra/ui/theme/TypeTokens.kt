package ca.deltica.contactra.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

object AppTypeTokens {
    val display: TextStyle
        @Composable get() = AppTheme.typography.display

    val screenTitle: TextStyle
        @Composable get() = AppTheme.typography.title

    val sectionTitle: TextStyle
        @Composable get() = AppTheme.typography.subtitle

    val fieldLabel: TextStyle
        @Composable get() = AppTheme.typography.caption.copy(fontWeight = FontWeight.Bold)

    val body: TextStyle
        @Composable get() = AppTheme.typography.body

    val caption: TextStyle
        @Composable get() = AppTheme.typography.caption

    val buttonText: TextStyle
        @Composable get() = AppTheme.typography.button
}

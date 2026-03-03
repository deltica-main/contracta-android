package com.example.businesscardscanner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.businesscardscanner.ui.theme.AppDimens
import com.example.businesscardscanner.ui.theme.AppTheme

@Composable
fun BottomActionBar(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(AppDimens.md),
    supportingContent: (@Composable ColumnScope.() -> Unit)? = null,
    secondaryAction: (@Composable ColumnScope.() -> Unit)? = null,
    primaryAction: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = AppTheme.colors.surfaceStrong,
        border = BorderStroke(
            width = AppDimens.divider,
            color = AppTheme.colors.border.copy(alpha = 0.82f)
        ),
        shadowElevation = AppDimens.cardElevation,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.sm)
        ) {
            supportingContent?.invoke(this)
            secondaryAction?.invoke(this)
            primaryAction()
        }
    }
}

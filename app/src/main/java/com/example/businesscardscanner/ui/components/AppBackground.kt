package com.example.businesscardscanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.example.businesscardscanner.ui.theme.AppTheme

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val brush = Brush.verticalGradient(
        colors = listOf(
            AppTheme.colors.background,
            AppTheme.colors.surface.copy(alpha = 0.98f),
            AppTheme.colors.primarySoft.copy(alpha = 0.72f)
        )
    )
    Box(modifier = modifier.background(brush)) {
        content()
    }
}

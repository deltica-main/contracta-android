package com.example.businesscardscanner.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.businesscardscanner.ui.theme.AppDimens
import com.example.businesscardscanner.ui.theme.AppTheme

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(AppDimens.lg),
    onClick: (() -> Unit)? = null,
    border: BorderStroke? = BorderStroke(
        width = AppDimens.divider,
        color = AppTheme.colors.border.copy(alpha = 0.78f)
    ),
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.994f else 1f,
        label = "appCardPressScale"
    )
    val cardModifier = modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    if (onClick == null) {
        Surface(
            modifier = cardModifier,
            shape = MaterialTheme.shapes.large,
            color = AppTheme.colors.surfaceStrong,
            tonalElevation = 0.dp,
            shadowElevation = AppDimens.cardElevation,
            border = border
        ) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = cardModifier,
            shape = MaterialTheme.shapes.large,
            color = AppTheme.colors.surfaceStrong,
            tonalElevation = 0.dp,
            shadowElevation = AppDimens.cardElevation,
            border = border,
            interactionSource = interactionSource
        ) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    }
}

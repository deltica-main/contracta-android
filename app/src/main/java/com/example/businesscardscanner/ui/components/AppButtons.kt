package com.example.businesscardscanner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.businesscardscanner.ui.theme.AppDimens
import com.example.businesscardscanner.ui.theme.AppTheme
import com.example.businesscardscanner.ui.theme.AppTypeTokens

private val AppButtonContentPadding = PaddingValues(
    horizontal = AppDimens.lg,
    vertical = AppDimens.md
)

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minWidth = AppDimens.buttonMinWidth)
            .heightIn(min = AppDimens.buttonHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppTheme.colors.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = AppTheme.colors.primary.copy(alpha = 0.56f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
        ),
        contentPadding = AppButtonContentPadding,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = AppDimens.cardElevation,
            pressedElevation = AppDimens.floatingElevation,
            disabledElevation = 0.dp
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(AppDimens.iconSizeSm)
            )
            Spacer(modifier = Modifier.width(AppDimens.sm))
        }
        Text(
            text = text,
            style = AppTypeTokens.buttonText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minWidth = AppDimens.buttonMinWidth)
            .heightIn(min = AppDimens.buttonHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(
            width = AppDimens.divider,
            color = AppTheme.colors.border
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = AppTheme.colors.surfaceStrong,
            contentColor = AppTheme.colors.accent,
            disabledContainerColor = AppTheme.colors.surfaceMuted,
            disabledContentColor = AppTheme.colors.textSecondary.copy(alpha = 0.78f)
        ),
        contentPadding = AppButtonContentPadding
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(AppDimens.iconSizeSm)
            )
            Spacer(modifier = Modifier.width(AppDimens.sm))
        }
        Text(
            text = text,
            style = AppTypeTokens.buttonText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minWidth = AppDimens.buttonMinWidth)
            .heightIn(min = AppDimens.touchTargetCompact),
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.textButtonColors(
            contentColor = AppTheme.colors.accent,
            disabledContentColor = AppTheme.colors.textSecondary.copy(alpha = 0.72f)
        ),
        contentPadding = AppButtonContentPadding
    ) {
        Text(
            text = text,
            style = AppTypeTokens.buttonText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minWidth = AppDimens.buttonMinWidth)
            .heightIn(min = AppDimens.buttonHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppTheme.colors.error.copy(alpha = 0.12f),
            contentColor = AppTheme.colors.error,
            disabledContainerColor = AppTheme.colors.error.copy(alpha = 0.08f),
            disabledContentColor = AppTheme.colors.error.copy(alpha = 0.62f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = AppDimens.subtleElevation,
            pressedElevation = AppDimens.cardElevation,
            disabledElevation = 0.dp
        ),
        contentPadding = AppButtonContentPadding
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(AppDimens.iconSizeSm)
            )
            Spacer(modifier = Modifier.width(AppDimens.sm))
        }
        Text(
            text = text,
            style = AppTypeTokens.buttonText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

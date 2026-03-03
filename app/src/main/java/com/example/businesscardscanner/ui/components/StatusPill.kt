package com.example.businesscardscanner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.businesscardscanner.ui.theme.AppDimens
import com.example.businesscardscanner.ui.theme.AppTheme
import com.example.businesscardscanner.ui.theme.AppTypeTokens

enum class StatusPillTone {
    Brand,
    Neutral,
    Success,
    Warning,
    Error
}

@Composable
fun StatusPill(
    label: String,
    tone: StatusPillTone,
    modifier: Modifier = Modifier,
    showDot: Boolean = true
) {
    val (containerColor, contentColor) = when (tone) {
        StatusPillTone.Brand -> AppTheme.colors.primarySoft to AppTheme.colors.accent
        StatusPillTone.Neutral -> AppTheme.colors.surfaceMuted to AppTheme.colors.textSecondary
        StatusPillTone.Success -> AppTheme.colors.success.copy(alpha = 0.14f) to AppTheme.colors.success
        StatusPillTone.Warning -> AppTheme.colors.warning.copy(alpha = 0.14f) to AppTheme.colors.warning
        StatusPillTone.Error -> AppTheme.colors.error.copy(alpha = 0.14f) to AppTheme.colors.error
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            width = AppDimens.divider,
            color = contentColor.copy(alpha = 0.14f)
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AppDimens.md,
                vertical = AppDimens.sm - AppDimens.xs
            ),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(AppDimens.sm - (AppDimens.xs / 2))
                        .background(
                            color = contentColor,
                            shape = MaterialTheme.shapes.extraLarge
                        )
                )
            }
            Text(
                text = label,
                style = AppTypeTokens.caption,
                color = contentColor
            )
        }
    }
}

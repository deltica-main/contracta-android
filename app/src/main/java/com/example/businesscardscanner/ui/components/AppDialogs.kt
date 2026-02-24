package com.example.businesscardscanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.businesscardscanner.ui.theme.AppDimens

@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable RowScope.() -> Unit,
    dismissButton: (@Composable RowScope.() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                title()
            }
        },
        text = text?.let {
            {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.xs)) {
                        it()
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dismissButton != null) {
                    dismissButton()
                    Spacer(modifier = Modifier.width(AppDimens.sm))
                }
                confirmButton()
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    )
}

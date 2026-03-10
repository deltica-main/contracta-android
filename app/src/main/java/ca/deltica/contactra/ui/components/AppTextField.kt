package ca.deltica.contactra.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import ca.deltica.contactra.ui.theme.AppDimens
import ca.deltica.contactra.ui.theme.AppTheme
import ca.deltica.contactra.ui.theme.AppTypeTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    helperText: String? = null,
    isError: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label, style = AppTypeTokens.fieldLabel) },
        placeholder = placeholder?.let {
            {
                Text(
                    text = it,
                    style = AppTypeTokens.body,
                    color = AppTheme.colors.textSecondary
                )
            }
        },
        supportingText = helperText?.let {
            {
                Text(
                    text = it,
                    style = AppTypeTokens.caption
                )
            }
        },
        isError = isError,
        leadingIcon = leadingIcon?.let { { Icon(imageVector = it, contentDescription = null) } },
        trailingIcon = trailingContent ?: trailingIcon?.let { { Icon(imageVector = it, contentDescription = null) } },
        modifier = modifier.heightIn(min = AppDimens.touchTargetMin),
        readOnly = readOnly,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = AppTypeTokens.body,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppTheme.colors.textPrimary,
            unfocusedTextColor = AppTheme.colors.textPrimary,
            disabledTextColor = AppTheme.colors.textSecondary,
            errorTextColor = AppTheme.colors.textPrimary,
            focusedContainerColor = AppTheme.colors.surfaceStrong,
            unfocusedContainerColor = AppTheme.colors.surfaceStrong,
            disabledContainerColor = AppTheme.colors.surfaceMuted,
            errorContainerColor = AppTheme.colors.surfaceStrong,
            focusedBorderColor = AppTheme.colors.primary.copy(alpha = 0.42f),
            unfocusedBorderColor = AppTheme.colors.border,
            disabledBorderColor = AppTheme.colors.border.copy(alpha = 0.62f),
            errorBorderColor = AppTheme.colors.error,
            focusedLabelColor = AppTheme.colors.accent,
            unfocusedLabelColor = AppTheme.colors.textSecondary,
            errorLabelColor = AppTheme.colors.error,
            focusedLeadingIconColor = AppTheme.colors.accent,
            unfocusedLeadingIconColor = AppTheme.colors.textSecondary,
            focusedTrailingIconColor = AppTheme.colors.accent,
            unfocusedTrailingIconColor = AppTheme.colors.textSecondary
        )
    )
}

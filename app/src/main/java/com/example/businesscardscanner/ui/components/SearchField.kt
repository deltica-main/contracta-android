package com.example.businesscardscanner.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = "Search contacts",
        placeholder = placeholder,
        leadingIcon = Icons.Filled.Search,
        modifier = modifier
    )
}

package com.example.businesscardscanner.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.businesscardscanner.R

@Composable
fun DelticaLogo(modifier: Modifier = Modifier, showWordmark: Boolean = true) {
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        Image(
            painter = painterResource(id = R.drawable.deltica_logo_no_text),
            contentDescription = "Deltica logo",
            modifier = Modifier.size(72.dp)
        )
        if (showWordmark) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "DELTICA",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

package ca.deltica.contactra.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import ca.deltica.contactra.ui.theme.AppDimens
import ca.deltica.contactra.ui.theme.AppTheme

@Composable
fun BusinessCardBitmap(
    bitmap: Bitmap,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(AppDimens.businessCardAspectRatio),
        shape = MaterialTheme.shapes.large,
        color = AppTheme.colors.surfaceMuted,
        border = BorderStroke(
            width = AppDimens.divider,
            color = AppTheme.colors.border.copy(alpha = 0.82f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.primarySoft.copy(alpha = 0.48f))
                .padding(AppDimens.xs),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
            )
        }
    }
}

@Composable
fun BusinessCardAsyncImage(
    model: Any?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(AppDimens.businessCardAspectRatio),
        shape = MaterialTheme.shapes.large,
        color = AppTheme.colors.surfaceMuted,
        border = BorderStroke(
            width = AppDimens.divider,
            color = AppTheme.colors.border.copy(alpha = 0.82f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.primarySoft.copy(alpha = 0.42f)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

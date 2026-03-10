package ca.deltica.contactra.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.deltica.contactra.ui.theme.AppDimens
import ca.deltica.contactra.ui.theme.AppTypeTokens

@Composable
fun LoadingState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.width(AppDimens.xl),
            strokeWidth = 2.5.dp
        )
        Spacer(modifier = Modifier.height(AppDimens.sm))
        Text(
            text = message,
            style = AppTypeTokens.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun EmptyStateView(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    tone: StatusPillTone = StatusPillTone.Neutral
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StatusPill(
            label = if (tone == StatusPillTone.Error) "Needs Attention" else "Ready",
            tone = tone,
            showDot = tone != StatusPillTone.Neutral
        )
        Spacer(modifier = Modifier.height(AppDimens.sm))
        Text(
            text = title,
            style = AppTypeTokens.sectionTitle,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(AppDimens.xs))
        Text(
            text = subtitle,
            style = AppTypeTokens.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.92f)
        )
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    EmptyStateView(
        title = title,
        subtitle = subtitle,
        modifier = modifier
    )
}

@Composable
fun ErrorState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    EmptyStateView(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        tone = StatusPillTone.Error
    )
}

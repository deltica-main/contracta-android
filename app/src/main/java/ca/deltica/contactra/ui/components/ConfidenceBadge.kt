package ca.deltica.contactra.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ca.deltica.contactra.ui.viewmodel.ConfidenceLevel

@Composable
fun ConfidenceBadge(
    level: ConfidenceLevel,
    modifier: Modifier = Modifier
) {
    val (label, tone) = when (level) {
        ConfidenceLevel.HIGH -> "Looks Good" to StatusPillTone.Success
        ConfidenceLevel.MEDIUM -> "Needs Review" to StatusPillTone.Warning
        ConfidenceLevel.LOW -> "Needs Review" to StatusPillTone.Warning
    }
    StatusPill(
        label = label,
        tone = tone,
        modifier = modifier,
        showDot = true
    )
}

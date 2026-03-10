package com.example.businesscardscanner.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.example.businesscardscanner.R
import com.example.businesscardscanner.ui.components.AppBackground
import com.example.businesscardscanner.ui.components.AppCard
import com.example.businesscardscanner.ui.components.AppTopBar
import com.example.businesscardscanner.ui.components.PrimaryButton
import com.example.businesscardscanner.ui.components.SectionHeader
import com.example.businesscardscanner.ui.theme.AppDimens
import com.example.businesscardscanner.ui.viewmodel.MainViewModel

private const val SUPPORT_EMAIL_ADDRESS = "support@deltica.ca"

@Composable
fun SupportScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    val expandedFaq = remember { mutableStateMapOf<Int, Boolean>() }
    val faqItems = remember { supportFaqItems() }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.support_screen_title),
                subtitle = stringResource(R.string.brand_full_name),
                navController = navController,
                showBack = true,
                showHome = true,
                showContacts = true,
                showSettings = false
            )
        }
    ) { paddingValues ->
        AppBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppDimens.lg),
                verticalArrangement = Arrangement.spacedBy(AppDimens.md)
            ) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.sm)) {
                            Text(
                                text = stringResource(R.string.support_intro_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.support_intro_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    SectionHeader(title = stringResource(R.string.support_faqs_title))
                }

                itemsIndexed(faqItems) { index, faq ->
                    val isExpanded = expandedFaq[index] == true
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { expandedFaq[index] = !isExpanded }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.sm)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = faq.question,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Text(
                                    text = faq.answer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    SectionHeader(title = stringResource(R.string.support_contact_title))
                }

                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.sm)) {
                            Text(
                                text = stringResource(R.string.support_contact_email_label),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = SUPPORT_EMAIL_ADDRESS,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            PrimaryButton(
                                text = stringResource(R.string.support_contact_button),
                                onClick = {
                                    val intent = Intent(
                                        Intent.ACTION_SENDTO,
                                        Uri.fromParts("mailto", SUPPORT_EMAIL_ADDRESS, null)
                                    )
                                    runCatching {
                                        if (intent.resolveActivity(context.packageManager) != null) {
                                            context.startActivity(intent)
                                            statusMessage = null
                                            statusIsError = false
                                        } else {
                                            statusMessage = context.getString(R.string.support_contact_no_email_app)
                                            statusIsError = true
                                        }
                                    }.onFailure {
                                        statusMessage = context.getString(R.string.support_contact_no_email_app)
                                        statusIsError = true
                                    }
                                },
                                icon = Icons.Filled.SupportAgent,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                statusMessage?.let { status ->
                    item {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (statusIsError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

private data class SupportFaqItem(
    val question: String,
    val answer: String
)

private fun supportFaqItems(): List<SupportFaqItem> {
    return listOf(
        SupportFaqItem(
            question = "What does website enrichment do?",
            answer = "Website enrichment uses lightweight public metadata to help fill company and industry fields. You can turn it off anytime in Settings."
        ),
        SupportFaqItem(
            question = "Why did a field get filled incorrectly?",
            answer = "Text recognition can vary by photo quality and card layout. The review screen lets you edit every field before saving."
        ),
        SupportFaqItem(
            question = "Why did auto capture not trigger?",
            answer = "Auto capture waits for a clear, readable frame. You can always tap the shutter manually when you are ready."
        ),
        SupportFaqItem(
            question = "How is my data stored?",
            answer = "Your contacts are stored on your device. The app follows a local-first approach."
        ),
        SupportFaqItem(
            question = "Can I delete a contact and its images?",
            answer = "Yes. Deleting a contact also removes its saved details and linked images."
        ),
        SupportFaqItem(
            question = "How do I export my contacts?",
            answer = "Open Settings and use the export options to save or share your contact backup."
        )
    )
}

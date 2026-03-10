package ca.deltica.contactra.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import ca.deltica.contactra.R
import ca.deltica.contactra.ui.components.AppBackground
import ca.deltica.contactra.ui.components.AppCard
import ca.deltica.contactra.ui.components.AppTopBar
import ca.deltica.contactra.ui.components.PrimaryButton
import ca.deltica.contactra.ui.components.SectionHeader
import ca.deltica.contactra.ui.components.SecondaryButton
import ca.deltica.contactra.ui.navigation.Screen
import ca.deltica.contactra.ui.theme.AppDimens
import ca.deltica.contactra.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(navController: NavController, viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsState by viewModel.settingsUiState.collectAsState()
    val appVersion = resolveAppVersion(context)

    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                viewModel.exportJsonToUri(uri)
            }
        }
    }

    val vCardExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-vcard")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                viewModel.exportVCardToUri(uri)
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Settings",
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppDimens.lg)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppDimens.lg)
            ) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.sm)) {
                        Text(
                            text = "Export your data",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Export includes contacts and how you met notes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.md)) {
                        SectionHeader(title = "Export options")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.md)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Include vCard (.vcf)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Adds a contacts file for address book apps.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settingsState.includeVCard,
                                onCheckedChange = { enabled ->
                                    viewModel.setIncludeVCardExport(enabled)
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.md)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Website enrichment",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Use website/domain signals to prefill industry.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settingsState.websiteEnrichmentEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.setWebsiteEnrichmentEnabled(enabled)
                                }
                            )
                        }

                        PrimaryButton(
                            text = if (settingsState.isExporting) "Exporting..." else "Export JSON",
                            onClick = {
                                viewModel.clearSettingsMessages()
                                jsonExportLauncher.launch(defaultExportName("json"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !settingsState.isExporting,
                            icon = Icons.Filled.FileDownload
                        )

                        if (settingsState.includeVCard) {
                            SecondaryButton(
                                text = if (settingsState.isExporting) "Exporting..." else "Export vCard",
                                onClick = {
                                    viewModel.clearSettingsMessages()
                                    vCardExportLauncher.launch(defaultExportName("vcf"))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !settingsState.isExporting,
                                icon = Icons.Filled.FileDownload
                            )
                        }

                        SecondaryButton(
                            text = if (settingsState.isExporting) {
                                "Preparing share..."
                            } else if (settingsState.includeVCard) {
                                "Share JSON + vCard"
                            } else {
                                "Share JSON"
                            },
                            onClick = {
                                viewModel.clearSettingsMessages()
                                scope.launch {
                                    viewModel.buildShareExportIntent(settingsState.includeVCard)
                                        .onSuccess { shareIntent ->
                                            runCatching {
                                                context.startActivity(shareIntent)
                                            }.onFailure {
                                                viewModel.setSettingsError("No app available to share export.")
                                            }
                                        }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !settingsState.isExporting,
                            icon = Icons.Filled.Share
                        )

                        settingsState.statusMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        settingsState.errorMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.md)) {
                        SectionHeader(title = "Help")
                        Text(
                            text = "Get help with Contactra and contact Deltica support.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SecondaryButton(
                            text = "Support",
                            onClick = { navController.navigate(Screen.Support.route) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Filled.HelpOutline
                        )
                    }
                }

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.sm)) {
                        SectionHeader(title = "About")
                        Text(
                            text = "Contactra helps you scan and organize business cards.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "By Deltica",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Version $appVersion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AppDimens.sm))
            }
        }
    }
}

private fun defaultExportName(extension: String): String {
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    return "contactra-export-$timestamp.$extension"
}

private fun resolveAppVersion(context: android.content.Context): String {
    return runCatching {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName
            ?.takeIf { it.isNotBlank() }
            ?: packageInfo.longVersionCode.toString()
    }.getOrDefault("Unknown")
}


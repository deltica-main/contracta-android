package com.example.businesscardscanner.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.businesscardscanner.data.integration.ContactsBulkExportSummary
import com.example.businesscardscanner.domain.logic.IndustryCatalog
import com.example.businesscardscanner.domain.model.Contact
import com.example.businesscardscanner.ui.components.AnchoredDropdownField
import com.example.businesscardscanner.ui.components.AppBackground
import com.example.businesscardscanner.ui.components.AppCard
import com.example.businesscardscanner.ui.components.AppAlertDialog
import com.example.businesscardscanner.ui.components.AppModalBottomSheet
import com.example.businesscardscanner.ui.components.AppTopBar
import com.example.businesscardscanner.ui.components.EmptyState
import com.example.businesscardscanner.ui.components.EmptyStateView
import com.example.businesscardscanner.ui.components.LoadingState
import com.example.businesscardscanner.ui.components.PrimaryButton
import com.example.businesscardscanner.ui.components.SearchField
import com.example.businesscardscanner.ui.components.SectionHeader
import com.example.businesscardscanner.ui.components.SecondaryButton
import com.example.businesscardscanner.ui.components.StatusPill
import com.example.businesscardscanner.ui.components.StatusPillTone
import com.example.businesscardscanner.ui.navigation.Screen
import com.example.businesscardscanner.ui.theme.AppDimens
import com.example.businesscardscanner.ui.theme.AppTheme
import com.example.businesscardscanner.ui.viewmodel.ContactMatch
import com.example.businesscardscanner.ui.viewmodel.MainViewModel
import com.example.businesscardscanner.ui.viewmodel.SortOption
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    navController: NavController,
    viewModel: MainViewModel,
    initialQuery: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.contactsUiState.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var industryExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var showBulkConfirm by remember { mutableStateOf(false) }
    var showBulkProgress by remember { mutableStateOf(false) }
    var bulkCancelRequested by remember { mutableStateOf(false) }
    var bulkProgressProcessed by remember { mutableStateOf(0) }
    var bulkProgressTotal by remember { mutableStateOf(0) }
    var bulkSummary by remember { mutableStateOf<ContactsBulkExportSummary?>(null) }
    var bulkMessage by remember { mutableStateOf<String?>(null) }
    var pendingBulkContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }

    val visibleContacts = remember(uiState.results) {
        uiState.results.map { it.summary.contact }
    }
    val bulkEligibleContacts = remember(visibleContacts) {
        visibleContacts.filter { contact ->
            viewModel.shouldIncludeInPhoneBulkExport(contact)
        }
    }

    fun launchBulkExport(contacts: List<Contact>) {
        bulkCancelRequested = false
        bulkProgressProcessed = 0
        bulkProgressTotal = contacts.size
        showBulkProgress = true
        scope.launch {
            val summary = viewModel.addContactsToPhoneContacts(
                contacts = contacts,
                shouldCancel = { bulkCancelRequested },
                onProgress = { processed, total ->
                    bulkProgressProcessed = processed
                    bulkProgressTotal = total
                }
            )
            showBulkProgress = false
            bulkSummary = summary
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val writeGranted = permissions[Manifest.permission.WRITE_CONTACTS] == true
        val readGranted = permissions[Manifest.permission.READ_CONTACTS] == true
        if (!writeGranted || !readGranted) {
            bulkMessage = "Allow Contacts permission to add cards to your phone contacts."
            return@rememberLauncherForActivityResult
        }
        if (pendingBulkContacts.isNotEmpty()) {
            val contacts = pendingBulkContacts
            pendingBulkContacts = emptyList()
            launchBulkExport(contacts)
        }
    }

    val allIndustryLabel = "All industries"
    val availableIndustries = remember(uiState.results, uiState.industryFilter) {
        buildList {
            add(allIndustryLabel)
            addAll(IndustryCatalog.manualSelectionIndustries)
            addAll(
                uiState.results.mapNotNull { match ->
                    match.summary.contact.industry?.takeIf { it.isNotBlank() }
                }
            )
            uiState.industryFilter?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
            .distinct()
    }
    val selectedIndustry = uiState.industryFilter ?: allIndustryLabel

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank() && initialQuery != uiState.query) {
            viewModel.updateSearchQuery(initialQuery)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Contacts",
                subtitle = "Find people",
                navController = navController,
                showBack = true,
                showHome = true,
                showContacts = false,
                actions = {
                    IconButton(
                        onClick = {
                            pendingBulkContacts = bulkEligibleContacts
                            showBulkConfirm = true
                        },
                        enabled = !showBulkProgress
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContactPhone,
                            contentDescription = "Export contacts to phone"
                        )
                    }
                }
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
                    .padding(AppDimens.lg),
                verticalArrangement = Arrangement.spacedBy(AppDimens.sm)
            ) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SearchField(
                            value = uiState.query,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = "Search name, company, notes",
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { showFilterSheet = true },
                            modifier = Modifier.size(AppDimens.iconButtonSize)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Open contact filters",
                                tint = if (
                                    uiState.hasNotes ||
                                    uiState.industryFilter != null ||
                                    uiState.sortOption != SortOption.RECENT
                                ) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                SectionHeader(
                    title = "Directory",
                    supportingText = if (uiState.results.isEmpty()) {
                        "No saved contacts yet."
                    } else {
                        "Showing ${uiState.results.size} contact${if (uiState.results.size == 1) "" else "s"}"
                    }
                )

                bulkMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                AnimatedVisibility(
                    visible = uiState.isLoading,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LoadingState(message = "Loading contacts...", modifier = Modifier.fillMaxWidth())
                }

                if (!uiState.isLoading) {
                    if (uiState.results.isEmpty()) {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            EmptyStateView(
                                title = "No contacts found",
                                subtitle = "Try another search or scan a card.",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        PrimaryButton(
                            text = "Scan card",
                            onClick = { navController.navigate(Screen.Scan.createRoute()) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.md)
                        ) {
                            itemsIndexed(uiState.results, key = { _, item -> item.summary.contact.id }) { _, match ->
                                ContactRow(match = match, onClick = {
                                    navController.navigate(Screen.ContactDetail.createRoute(match.summary.contact.id))
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val sortOptions = SortOption.values().toList()
        AppModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = filterSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.md, vertical = AppDimens.sm),
                verticalArrangement = Arrangement.spacedBy(AppDimens.md)
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleLarge
                )
                AnchoredDropdownField(
                    label = "Industry",
                    value = selectedIndustry,
                    options = availableIndustries,
                    expanded = industryExpanded,
                    onExpandedChange = { industryExpanded = it },
                    onOptionSelected = { option ->
                        if (option == allIndustryLabel) {
                            viewModel.updateIndustryFilter(null)
                        } else {
                            viewModel.updateIndustryFilter(option)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.xs)
                    ) {
                        Text(
                            text = "Has notes",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Only show contacts with saved notes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.hasNotes,
                        onCheckedChange = { enabled ->
                            viewModel.updateHasNotesFilter(enabled)
                        }
                    )
                }
                AnchoredDropdownField(
                    label = "Sort by",
                    value = sortLabel(uiState.sortOption),
                    options = sortOptions.map { sortLabel(it) },
                    expanded = sortExpanded,
                    onExpandedChange = { sortExpanded = it },
                    onOptionSelected = { label ->
                        val selectedSort = sortOptions.firstOrNull { sortLabel(it) == label }
                        if (selectedSort != null) {
                            viewModel.updateSortOption(selectedSort)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                SecondaryButton(
                    text = "Done",
                    onClick = { showFilterSheet = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showBulkConfirm) {
        val bulkConfirmSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        AppModalBottomSheet(
            onDismissRequest = { showBulkConfirm = false },
            sheetState = bulkConfirmSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.md, vertical = AppDimens.sm),
                verticalArrangement = Arrangement.spacedBy(AppDimens.md)
            ) {
                Text(
                    text = "Export contacts to phone",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Add all saved business card contacts to your phone. Existing contacts will not be duplicated.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PrimaryButton(
                    text = "Export",
                    onClick = {
                        showBulkConfirm = false
                        if (pendingBulkContacts.isEmpty()) {
                            bulkMessage = "All visible contacts are already up to date in phone contacts."
                            return@PrimaryButton
                        }
                        val hasContactsPermission =
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_CONTACTS
                            ) == PackageManager.PERMISSION_GRANTED &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.READ_CONTACTS
                                ) == PackageManager.PERMISSION_GRANTED
                        if (hasContactsPermission) {
                            launchBulkExport(pendingBulkContacts)
                            pendingBulkContacts = emptyList()
                        } else {
                            contactsPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.WRITE_CONTACTS
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                SecondaryButton(
                    text = "Cancel",
                    onClick = { showBulkConfirm = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showBulkProgress) {
        AppAlertDialog(
            onDismissRequest = {},
            title = { Text("Adding to phone contacts") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.sm)) {
                    CircularProgressIndicator()
                    Text(
                        text = "Processed $bulkProgressProcessed of $bulkProgressTotal contacts",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (bulkCancelRequested) {
                        Text(
                            text = "Cancelling...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                SecondaryButton(
                    text = "Cancel",
                    onClick = { bulkCancelRequested = true },
                    enabled = !bulkCancelRequested
                )
            }
        )
    }

    bulkSummary?.let { summary ->
        AppAlertDialog(
            onDismissRequest = { bulkSummary = null },
            title = {
                Text(
                    if (summary.cancelled) {
                        "Phone contacts sync cancelled"
                    } else {
                        "Phone contacts sync complete"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.xs)) {
                    Text("Added ${summary.added}")
                    Text("Updated ${summary.updated}")
                    Text("Skipped ${summary.skippedDuplicates} duplicates")
                    Text("Failed ${summary.failed}")
                    if (summary.skippedMissingKey > 0) {
                        Text("Skipped ${summary.skippedMissingKey} missing details")
                    }
                    if (summary.cancelled) {
                        Text(
                            text = "Cancelled after ${summary.processed} of ${summary.total}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                SecondaryButton(text = "Done", onClick = { bulkSummary = null })
            }
        )
    }
}

@Composable
private fun ContactRow(match: ContactMatch, onClick: () -> Unit) {
    val summary = match.summary
    val tokens = match.matchedTokens
    val phoneExported = !summary.contact.phoneExportStatus.isNullOrBlank()
    val highlightColor = AppTheme.colors.warning.copy(alpha = 0.18f)
    val relativeLabel = summary.lastInteractionTime?.let { lastInteraction ->
        DateUtils.getRelativeTimeSpanString(
            lastInteraction,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.md),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(AppDimens.avatarSize)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (summary.contact.imagePath != null) {
                    AsyncImage(
                        model = File(summary.contact.imagePath),
                        contentDescription = "Business card thumbnail",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppDimens.xs)
            ) {
                Text(
                    text = highlightTokens(
                        text = summary.contact.name ?: "Unknown",
                        tokens = tokens,
                        highlightColor = highlightColor
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                summary.contact.company?.let {
                    Text(
                        text = highlightTokens(
                            text = it,
                            tokens = tokens,
                            highlightColor = highlightColor
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                summary.contact.title?.let {
                    Text(
                        text = highlightTokens(
                            text = it,
                            tokens = tokens,
                            highlightColor = highlightColor
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(AppDimens.sm)
            ) {
                if (phoneExported) {
                    StatusPill(
                        label = "Phone synced",
                        tone = StatusPillTone.Brand,
                        showDot = false
                    )
                }
                if (!relativeLabel.isNullOrBlank()) {
                    Text(
                        text = relativeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun highlightTokens(
    text: String,
    tokens: List<String>,
    highlightColor: Color
): AnnotatedString {
    if (tokens.isEmpty() || text.isBlank()) return AnnotatedString(text)
    val safeTokens = tokens.filter { it.isNotBlank() }.distinct()
    if (safeTokens.isEmpty()) return AnnotatedString(text)
    val regex = Regex(safeTokens.joinToString("|") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    val matches = regex.findAll(text).toList()
    if (matches.isEmpty()) return AnnotatedString(text)
    var lastIndex = 0
    return buildAnnotatedString {
        matches.forEach { match ->
            if (match.range.first > lastIndex) {
                append(text.substring(lastIndex, match.range.first))
            }
            withStyle(
                style = SpanStyle(
                    background = highlightColor,
                    color = Color.Unspecified,
                    fontWeight = FontWeight.SemiBold
                )
            ) {
                append(text.substring(match.range))
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

private fun sortLabel(option: SortOption): String {
    return when (option) {
        SortOption.RECENT -> "Recent"
        SortOption.NAME -> "Name"
        SortOption.COMPANY -> "Company"
    }
}

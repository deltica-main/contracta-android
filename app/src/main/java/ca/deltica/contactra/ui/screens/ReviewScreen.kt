package ca.deltica.contactra.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import ca.deltica.contactra.ui.components.AppBackground
import ca.deltica.contactra.ui.components.AppAlertDialog
import ca.deltica.contactra.ui.components.AppCard
import ca.deltica.contactra.ui.components.AppModalBottomSheet
import ca.deltica.contactra.ui.components.AppTopBar
import ca.deltica.contactra.ui.components.BusinessCardBitmap
import ca.deltica.contactra.ui.components.BottomActionBar
import ca.deltica.contactra.ui.components.ConfidenceBadge
import ca.deltica.contactra.ui.components.EditableFieldRow
import ca.deltica.contactra.ui.components.EmptyState
import ca.deltica.contactra.ui.components.GhostButton
import ca.deltica.contactra.ui.components.SuggestionOption
import ca.deltica.contactra.ui.components.SuggestionPickerField
import ca.deltica.contactra.ui.components.LoadingState
import ca.deltica.contactra.ui.components.PrimaryButton
import ca.deltica.contactra.ui.components.SecondaryButton
import ca.deltica.contactra.ui.components.SectionHeader
import ca.deltica.contactra.ui.components.StatusPill
import ca.deltica.contactra.ui.components.StatusPillTone
import ca.deltica.contactra.ui.components.StepIndicator
import ca.deltica.contactra.ui.navigation.Screen
import ca.deltica.contactra.ui.theme.AppDimens
import ca.deltica.contactra.ui.viewmodel.MainViewModel
import ca.deltica.contactra.ui.viewmodel.ReviewAssignmentField
import ca.deltica.contactra.domain.logic.UnassignedItemKind
import ca.deltica.contactra.domain.logic.UnassignedOcrItem
import java.net.URI
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(navController: NavController, viewModel: MainViewModel) {
    val scanState by viewModel.scanUiState.collectAsState()
    var pendingContinue by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var showDuplicateEducationDialog by remember { mutableStateOf(false) }
    var selectedDuplicateId by remember { mutableStateOf<Long?>(null) }
    var showMissingCoreConfirmDialog by remember { mutableStateOf(false) }
    var assigningUnassignedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAllUnassigned by rememberSaveable { mutableStateOf(false) }

    val fields = scanState.reviewFields
    val suggestions = scanState.reviewSuggestions
    val unassignedItems = scanState.unassignedItems
    val unassignedVisibleItems = remember(unassignedItems) { unassignedItems.take(6) }
    val unassignedHasMore = unassignedItems.size > 6
    val missingNameAndCompany = fields.name.isBlank() && fields.company.isBlank()
    val nameOptions = remember(suggestions.name) { suggestions.name.toSuggestionOptions() }
    val titleOptions = remember(suggestions.title) { suggestions.title.toSuggestionOptions() }
    val companyOptions = remember(suggestions.company) { suggestions.company.toSuggestionOptions() }
    val emailOptions = remember(suggestions.email) { suggestions.email.toEmailSuggestionOptions() }
    val phoneOptions = remember(suggestions.phone) { suggestions.phone.toSuggestionOptions() }
    val websiteOptions = remember(suggestions.website) { suggestions.website.toWebsiteSuggestionOptions() }
    val addressOptions = remember(suggestions.address) { suggestions.address.toSuggestionOptions() }
    val unassignedForName = remember(unassignedItems) {
        unassignedItems.toUnassignedSuggestionOptions(ReviewAssignmentField.NAME)
    }
    val unassignedForTitle = remember(unassignedItems) {
        unassignedItems.toUnassignedSuggestionOptions(ReviewAssignmentField.TITLE)
    }
    val unassignedForCompany = remember(unassignedItems) {
        unassignedItems.toUnassignedSuggestionOptions(ReviewAssignmentField.COMPANY)
    }
    val unassignedForAddress = remember(unassignedItems) {
        unassignedItems.toUnassignedSuggestionOptions(ReviewAssignmentField.ADDRESS)
    }
    val assigningUnassignedItem = remember(assigningUnassignedItemId, unassignedItems) {
        unassignedItems.firstOrNull { it.id == assigningUnassignedItemId }
    }

    LaunchedEffect(scanState.duplicateCandidates) {
        if (scanState.duplicateCandidates.isNotEmpty()) {
            selectedDuplicateId = scanState.duplicateCandidates.first().id
        }
    }

    LaunchedEffect(scanState.isCheckingDuplicates, scanState.duplicateCandidates, pendingContinue) {
        if (pendingContinue && !scanState.isCheckingDuplicates) {
            if (scanState.duplicateCandidates.isEmpty()) {
                viewModel.setMergeTarget(null)
                navController.navigate(Screen.MeetingContext.route)
                pendingContinue = false
            } else {
                if (scanState.showDuplicateEducation) {
                    showDuplicateEducationDialog = true
                } else {
                    showDuplicateDialog = true
                }
                pendingContinue = false
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Review",
                subtitle = "Step 2 of 3",
                navController = navController,
                showBack = true,
                showHome = true,
                showContacts = true
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
                StepIndicator(steps = listOf("Scan", "Review", "Context"), currentIndex = 1)
                Text(
                    text = "Review details before saving.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                scanState.lastCapturedImage?.let { bitmap ->
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(AppDimens.md)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.md)) {
                            SectionHeader(
                                title = "Captured preview",
                                supportingText = "Verify the card before saving."
                            )
                            BusinessCardBitmap(
                                bitmap = bitmap,
                                contentDescription = "Captured business card"
                            )
                        }
                    }
                }

                when {
                    scanState.isProcessing -> {
                        LoadingState(
                            message = scanState.processingMessage ?: "Processing OCR...",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    scanState.errorMessage != null -> {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.md)) {
                                Text(
                                    text = "Could not read text from that photo.\nTry again with better lighting and fill the frame.",
                                    color = MaterialTheme.colorScheme.error
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.sm)) {
                                    PrimaryButton(
                                        text = "Rescan",
                                        onClick = {
                                            viewModel.beginRescan()
                                            navController.popBackStack()
                                        },
                                        icon = Icons.Filled.Refresh
                                    )
                                    SecondaryButton(
                                        text = "Back",
                                        onClick = { navController.popBackStack() }
                                    )
                                }
                            }
                        }
                    }
                    scanState.extractedData == null -> {
                        EmptyState(
                            title = "No OCR results",
                            subtitle = "Scan a card to extract details.",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {
                        StatusPill(
                            label = scanState.parserNoticeMessage ?: "Parsed results ready",
                            tone = if (scanState.parserNoticeMessage != null) {
                                StatusPillTone.Warning
                            } else {
                                StatusPillTone.Success
                            }
                        )

                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.md)) {
                                SectionHeader(
                                    title = "Core details",
                                    supportingText = "Review required fields before saving."
                                )
                                EditableFieldRow(
                                    badge = {
                                        ConfidenceBadge(level = scanState.fieldConfidence.name)
                                    }
                                ) {
                                    SuggestionPickerField(
                                        label = "Name",
                                        value = fields.name,
                                        onValueChange = { value ->
                                            viewModel.updateReviewFields { current -> current.copy(name = value) }
                                        },
                                        suggestions = nameOptions,
                                        fromUnassigned = if (fields.name.isBlank()) unassignedForName else emptyList(),
                                        onSelectFromUnassigned = { option ->
                                            viewModel.assignUnassignedItem(
                                                itemId = option.value,
                                                targetField = ReviewAssignmentField.NAME
                                            )
                                        },
                                        helperText = "Need name or company",
                                        isError = missingNameAndCompany,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                EditableFieldRow(
                                    badge = {
                                        ConfidenceBadge(level = scanState.fieldConfidence.title)
                                    }
                                ) {
                                    SuggestionPickerField(
                                        label = "Title",
                                        value = fields.title,
                                        onValueChange = { value ->
                                            viewModel.updateReviewFields { current -> current.copy(title = value) }
                                        },
                                        suggestions = titleOptions,
                                        fromUnassigned = if (fields.title.isBlank()) unassignedForTitle else emptyList(),
                                        onSelectFromUnassigned = { option ->
                                            viewModel.assignUnassignedItem(
                                                itemId = option.value,
                                                targetField = ReviewAssignmentField.TITLE
                                            )
                                        },
                                        helperText = "Optional",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                EditableFieldRow(
                                    badge = {
                                        ConfidenceBadge(level = scanState.fieldConfidence.company)
                                    }
                                ) {
                                    SuggestionPickerField(
                                        label = "Company",
                                        value = fields.company,
                                        onValueChange = { value ->
                                            viewModel.updateReviewFields { current -> current.copy(company = value) }
                                        },
                                        suggestions = companyOptions,
                                        fromUnassigned = if (fields.company.isBlank()) unassignedForCompany else emptyList(),
                                        onSelectFromUnassigned = { option ->
                                            viewModel.assignUnassignedItem(
                                                itemId = option.value,
                                                targetField = ReviewAssignmentField.COMPANY
                                            )
                                        },
                                        helperText = "Need name or company",
                                        isError = missingNameAndCompany,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.md)) {
                                SectionHeader(
                                    title = "Contact channels",
                                    supportingText = "Inferred from OCR and ready for review."
                                )
                                EditableFieldRow(
                                    badge = {
                                        ConfidenceBadge(level = scanState.fieldConfidence.email)
                                    }
                                ) {
                                    SuggestionPickerField(
                                        label = "Email",
                                        value = fields.email,
                                        onValueChange = { value ->
                                            viewModel.updateReviewFields { current -> current.copy(email = value) }
                                        },
                                        suggestions = emailOptions,
                                        helperText = "Optional",
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                EditableFieldRow(
                                    badge = {
                                        ConfidenceBadge(level = scanState.fieldConfidence.phone)
                                    }
                                ) {
                                    SuggestionPickerField(
                                        label = "Phone",
                                        value = fields.phone,
                                        onValueChange = { value ->
                                            viewModel.updateReviewFields { current -> current.copy(phone = value) }
                                        },
                                        suggestions = phoneOptions,
                                        helperText = "Optional",
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                EditableFieldRow(
                                    badge = {
                                        ConfidenceBadge(level = scanState.fieldConfidence.website)
                                    }
                                ) {
                                    SuggestionPickerField(
                                        label = "Website",
                                        value = fields.website,
                                        onValueChange = { value ->
                                            viewModel.updateReviewFields { current -> current.copy(website = value) }
                                        },
                                        suggestions = websiteOptions,
                                        helperText = "Optional",
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                EditableFieldRow(
                                    badge = {
                                        StatusPill(
                                            label = if (fields.address.isBlank()) "Needs Review" else "Ready",
                                            tone = if (fields.address.isBlank()) {
                                                StatusPillTone.Warning
                                            } else {
                                                StatusPillTone.Neutral
                                            }
                                        )
                                    }
                                ) {
                                    SuggestionPickerField(
                                        label = "Address",
                                        value = fields.address,
                                        onValueChange = { value ->
                                            viewModel.updateReviewFields { current -> current.copy(address = value) }
                                        },
                                        suggestions = addressOptions,
                                        fromUnassigned = if (fields.address.isBlank()) unassignedForAddress else emptyList(),
                                        onSelectFromUnassigned = { option ->
                                            viewModel.assignUnassignedItem(
                                                itemId = option.value,
                                                targetField = ReviewAssignmentField.ADDRESS
                                            )
                                        },
                                        helperText = "Optional",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        UnassignedSectionContent(
                            items = unassignedVisibleItems,
                            hasMore = unassignedHasMore,
                            onViewAllClick = { showAllUnassigned = true },
                            onAssignClick = { itemId -> assigningUnassignedItemId = itemId }
                        )

                        BottomActionBar(
                            supportingContent = {
                                if (scanState.isCheckingDuplicates) {
                                    StatusPill(
                                        label = "Checking duplicates...",
                                        tone = StatusPillTone.Brand
                                    )
                                }
                            },
                            secondaryAction = {
                                SecondaryButton(
                                    text = "Rescan",
                                    onClick = {
                                        viewModel.setAllowIncompleteCoreSave(false)
                                        navController.popBackStack()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        ) {
                            PrimaryButton(
                                text = "Continue",
                                onClick = {
                                    val valid = fields.name.isNotBlank() || fields.company.isNotBlank()
                                    if (!valid) {
                                        showMissingCoreConfirmDialog = true
                                        return@PrimaryButton
                                    }
                                    viewModel.setAllowIncompleteCoreSave(false)
                                    viewModel.checkForDuplicates()
                                    pendingContinue = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Filled.ArrowForward,
                                enabled = !scanState.isCheckingDuplicates
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAllUnassigned) {
        UnassignedFullListDialog(
            items = unassignedItems,
            onDismiss = { showAllUnassigned = false },
            onAssign = { itemId ->
                showAllUnassigned = false
                assigningUnassignedItemId = itemId
            }
        )
    }

    if (assigningUnassignedItem != null) {
        AssignUnassignedFieldSheet(
            item = assigningUnassignedItem,
            onDismiss = { assigningUnassignedItemId = null },
            onAssign = { field ->
                viewModel.assignUnassignedItem(
                    itemId = assigningUnassignedItem.id,
                    targetField = field
                )
                assigningUnassignedItemId = null
            }
        )
    }

    if (showMissingCoreConfirmDialog) {
        AppAlertDialog(
            onDismissRequest = { showMissingCoreConfirmDialog = false },
            title = { Text("Continue without name or company?") },
            text = {
                Text(
                    "No name or company was found."
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = "Continue anyway",
                    onClick = {
                        viewModel.setAllowIncompleteCoreSave(true)
                        viewModel.checkForDuplicates()
                        pendingContinue = true
                        showMissingCoreConfirmDialog = false
                    }
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = "Go back",
                    onClick = { showMissingCoreConfirmDialog = false }
                )
            }
        )
    }

    if (showDuplicateDialog) {
        val selectedCandidate = scanState.duplicateCandidates.firstOrNull { it.id == selectedDuplicateId }
            ?: scanState.duplicateCandidates.firstOrNull()
        val selectedReasons = selectedCandidate?.id
            ?.let { scanState.duplicateReasonsByContactId[it] }
            .orEmpty()
        val reasonCaption = duplicateMatchCaption(selectedReasons)
        val duplicateSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        AppModalBottomSheet(
            onDismissRequest = {
                showDuplicateDialog = false
                viewModel.setMergeTarget(null)
            },
            sheetState = duplicateSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.md, vertical = AppDimens.sm),
                verticalArrangement = Arrangement.spacedBy(AppDimens.md)
            ) {
                Text(
                    text = "Contact already saved",
                    style = MaterialTheme.typography.titleLarge
                )
                if (selectedCandidate != null) {
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(AppDimens.md)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.xs)) {
                            Text(
                                text = selectedCandidate.name ?: "Unnamed contact",
                                style = MaterialTheme.typography.titleMedium
                            )
                            selectedCandidate.company?.takeIf { it.isNotBlank() }?.let { company ->
                                Text(
                                    text = company,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            reasonCaption?.let { caption ->
                                Text(
                                    text = caption,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No duplicate details available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PrimaryButton(
                    text = "Merge with existing",
                    onClick = {
                        viewModel.setMergeTarget(selectedCandidate?.id)
                        showDuplicateDialog = false
                        navController.navigate(Screen.MeetingContext.route)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedCandidate != null
                )
                SecondaryButton(
                    text = "Save as new contact",
                    onClick = {
                        viewModel.setMergeTarget(null)
                        showDuplicateDialog = false
                        navController.navigate(Screen.MeetingContext.route)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                GhostButton(
                    text = "Cancel",
                    onClick = {
                        viewModel.setMergeTarget(null)
                        showDuplicateDialog = false
                    },
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }

    if (showDuplicateEducationDialog) {
        AppAlertDialog(
            onDismissRequest = { showDuplicateEducationDialog = false },
            title = { Text("How duplicate merge works") },
            text = {
                Text(
                    "We match duplicates by phone or email so you can merge safely."
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = "Got it",
                    onClick = {
                        viewModel.acknowledgeDuplicateEducation()
                        showDuplicateEducationDialog = false
                        showDuplicateDialog = true
                    }
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = "Cancel",
                    onClick = { showDuplicateEducationDialog = false }
                )
            }
        )
    }
}

@Composable
internal fun UnassignedSectionContent(
    items: List<UnassignedOcrItem>,
    hasMore: Boolean,
    onViewAllClick: () -> Unit,
    onAssignClick: (String) -> Unit
) {
    if (items.isEmpty()) return
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("unassigned_section")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.md)) {
            SectionHeader(
                title = "Unassigned",
                action = {
                    if (hasMore) {
                        TextButton(
                            onClick = onViewAllClick,
                            modifier = Modifier
                                .heightIn(min = AppDimens.touchTargetMin)
                                .testTag("unassigned_view_all"),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("View all")
                        }
                    }
                }
            )
            Text(
                text = "Assign any leftover text to a field.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            items.forEach { item ->
                UnassignedItemRow(
                    item = item,
                    onAssignClick = { onAssignClick(item.id) }
                )
            }
        }
    }
}

@Composable
internal fun UnassignedFullListDialog(
    items: List<UnassignedOcrItem>,
    onDismiss: () -> Unit,
    onAssign: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.lg, vertical = AppDimens.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.sm)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = AppDimens.touchTargetMin),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Close unassigned list"
                        )
                        Spacer(Modifier.width(AppDimens.xs))
                        Text("Back")
                    }
                    Text(
                        text = "Unassigned",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
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
                        .padding(AppDimens.lg)
                        .testTag("unassigned_full_list"),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.sm)
                ) {
                    items(items, key = { it.id }) { item ->
                        UnassignedItemRow(
                            item = item,
                            onAssignClick = { onAssign(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun UnassignedItemRow(
    item: UnassignedOcrItem,
    onAssignClick: () -> Unit
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("unassigned_item_${item.id}")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.sm)) {
            Text(
                text = item.displayText,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val metaText = if (item.isGrouped) {
                    "${item.lines.size} lines"
                } else {
                    "1 line"
                }
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onAssignClick,
                    modifier = Modifier
                        .heightIn(min = AppDimens.touchTargetMin)
                        .semantics {
                            contentDescription = "Assign unassigned text"
                        },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Assign")
                    Spacer(Modifier.width(AppDimens.xs))
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssignUnassignedFieldSheet(
    item: UnassignedOcrItem,
    onDismiss: () -> Unit,
    onAssign: (ReviewAssignmentField) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val quickPicks = listOf(
        ReviewAssignmentField.TITLE,
        ReviewAssignmentField.COMPANY,
        ReviewAssignmentField.ADDRESS,
        ReviewAssignmentField.NAME,
        ReviewAssignmentField.NOTES
    )
    val allFields = listOf(
        ReviewAssignmentField.NAME,
        ReviewAssignmentField.TITLE,
        ReviewAssignmentField.COMPANY,
        ReviewAssignmentField.EMAIL,
        ReviewAssignmentField.PHONE,
        ReviewAssignmentField.WEBSITE,
        ReviewAssignmentField.ADDRESS,
        ReviewAssignmentField.NOTES
    )

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.md, vertical = AppDimens.sm)
                .testTag("assign_field_sheet"),
            verticalArrangement = Arrangement.spacedBy(AppDimens.md)
        ) {
            Text(
                text = "Assign to field",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = item.displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Quick picks",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.sm)
            ) {
                quickPicks.forEach { field ->
                    AssistChip(
                        onClick = { onAssign(field) },
                        label = { Text(assignmentLabel(field)) },
                        modifier = Modifier.heightIn(min = AppDimens.touchTargetMin)
                    )
                }
            }
            Text(
                text = "All fields",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            allFields.forEach { field ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppDimens.touchTargetMin)
                        .clickable { onAssign(field) }
                        .padding(horizontal = AppDimens.sm, vertical = AppDimens.xs)
                        .semantics { contentDescription = "Assign to ${assignmentLabel(field)}" },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = assignmentLabel(field),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.End)
                    .heightIn(min = AppDimens.touchTargetMin),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("Cancel")
            }
        }
    }
}

private fun assignmentLabel(field: ReviewAssignmentField): String {
    return when (field) {
        ReviewAssignmentField.NAME -> "Name"
        ReviewAssignmentField.TITLE -> "Title"
        ReviewAssignmentField.COMPANY -> "Company"
        ReviewAssignmentField.EMAIL -> "Email"
        ReviewAssignmentField.PHONE -> "Phone"
        ReviewAssignmentField.WEBSITE -> "Website"
        ReviewAssignmentField.ADDRESS -> "Address"
        ReviewAssignmentField.INDUSTRY -> "Industry"
        ReviewAssignmentField.NOTES -> "Notes"
    }
}

private fun duplicateMatchCaption(reasons: List<String>): String? {
    if (reasons.isEmpty()) return null
    val normalized = reasons.joinToString(separator = " ").lowercase(Locale.US)
    return when {
        "phone" in normalized -> "Matched by phone number"
        "email" in normalized -> "Matched by email address"
        else -> "Matched by contact details"
    }
}

private fun List<UnassignedOcrItem>.toUnassignedSuggestionOptions(
    field: ReviewAssignmentField
): List<SuggestionOption> {
    return this
        .asSequence()
        .filter { it.isRelevantFor(field) }
        .map { item ->
            val preview = item.displayText.replace("\n", "  |  ").trim()
            SuggestionOption(
                value = item.id,
                display = item.lines.firstOrNull().orEmpty().ifBlank { item.displayText },
                secondaryLabel = preview.takeIf { it.isNotBlank() && it != item.lines.firstOrNull().orEmpty() }
            )
        }
        .toList()
}

private fun UnassignedOcrItem.isRelevantFor(field: ReviewAssignmentField): Boolean {
    return when (field) {
        ReviewAssignmentField.TITLE -> kind == UnassignedItemKind.TITLE_LIKE || kind == UnassignedItemKind.OTHER
        ReviewAssignmentField.COMPANY -> kind == UnassignedItemKind.COMPANY_LIKE || kind == UnassignedItemKind.OTHER
        ReviewAssignmentField.ADDRESS -> kind == UnassignedItemKind.ADDRESS_LIKE || (isGrouped && lines.size > 1)
        ReviewAssignmentField.NAME -> kind == UnassignedItemKind.TITLE_LIKE || kind == UnassignedItemKind.OTHER
        ReviewAssignmentField.NOTES -> true
        ReviewAssignmentField.EMAIL -> false
        ReviewAssignmentField.PHONE -> false
        ReviewAssignmentField.WEBSITE -> false
        ReviewAssignmentField.INDUSTRY -> kind == UnassignedItemKind.OTHER || kind == UnassignedItemKind.COMPANY_LIKE
    }
}

private fun List<String>.toSuggestionOptions(): List<SuggestionOption> {
    return this
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { value -> SuggestionOption(value = value) }
        .toList()
}

private fun List<String>.toEmailSuggestionOptions(): List<SuggestionOption> {
    return this
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { value ->
            val domain = value.substringAfter("@", "").takeIf { it.isNotBlank() }
            SuggestionOption(
                value = value,
                display = value,
                secondaryLabel = domain?.let { "Domain: $it" }
            )
        }
        .toList()
}

private fun List<String>.toWebsiteSuggestionOptions(): List<SuggestionOption> {
    return this
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { value ->
            val host = runCatching {
                val normalized = if (value.startsWith("http://", ignoreCase = true) ||
                    value.startsWith("https://", ignoreCase = true)
                ) {
                    value
                } else {
                    "https://$value"
                }
                URI(normalized).host?.lowercase(Locale.US)
            }.getOrNull()
            SuggestionOption(
                value = value,
                display = value,
                secondaryLabel = host?.let { "Site: $it" }
            )
        }
        .toList()
}

package com.example.businesscardscanner.ui.screens

import android.content.ClipData
import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.example.businesscardscanner.data.integration.ContactsSyncResult
import com.example.businesscardscanner.data.integration.ContactsSyncResultType
import com.example.businesscardscanner.domain.logic.ConnectionCatalog
import com.example.businesscardscanner.domain.logic.IndustryCatalog
import com.example.businesscardscanner.domain.logic.IndustrySource
import com.example.businesscardscanner.domain.model.Contact
import com.example.businesscardscanner.domain.model.industryDisplayLabel
import com.example.businesscardscanner.ui.components.AnchoredDropdownField
import com.example.businesscardscanner.ui.components.AppBackground
import com.example.businesscardscanner.ui.components.AppCard
import com.example.businesscardscanner.ui.components.AppAlertDialog
import com.example.businesscardscanner.ui.components.AppModalBottomSheet
import com.example.businesscardscanner.ui.components.AppTextField
import com.example.businesscardscanner.ui.components.AppTopBar
import com.example.businesscardscanner.ui.components.BusinessCardAsyncImage
import com.example.businesscardscanner.ui.components.DangerButton
import com.example.businesscardscanner.ui.components.EditableFieldRow
import com.example.businesscardscanner.ui.components.EmptyState
import com.example.businesscardscanner.ui.components.InfoChip
import com.example.businesscardscanner.ui.components.PrimaryButton
import com.example.businesscardscanner.ui.components.SectionHeader
import com.example.businesscardscanner.ui.components.SecondaryButton
import com.example.businesscardscanner.ui.components.IndustrySelector
import com.example.businesscardscanner.ui.components.StatusPill
import com.example.businesscardscanner.ui.components.StatusPillTone
import com.example.businesscardscanner.ui.navigation.Screen
import com.example.businesscardscanner.ui.theme.AppDimens
import com.example.businesscardscanner.ui.viewmodel.MainViewModel
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(navController: NavController, viewModel: MainViewModel, contactId: Long?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val detailState by viewModel.contactDetailUiState.collectAsState()
    var showAddInteraction by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var pendingPhoneContact by remember { mutableStateOf<Contact?>(null) }
    var phoneExportInFlight by remember { mutableStateOf(false) }
    var phoneExportResult by remember { mutableStateOf<ContactsSyncResult?>(null) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }
    var actionFeedbackIsError by remember { mutableStateOf(false) }

    fun startPhoneExport(contact: Contact) {
        if (phoneExportInFlight) return
        phoneExportInFlight = true
        actionFeedback = null
        actionFeedbackIsError = false
        scope.launch {
            val result = viewModel.addContactToPhoneContacts(contact)
            phoneExportResult = result
            if (result.type == ContactsSyncResultType.Failed) {
                actionFeedback = result.reason ?: "Could not add to phone contacts."
                actionFeedbackIsError = true
            } else {
                actionFeedback = null
                actionFeedbackIsError = false
            }
            phoneExportInFlight = false
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val contactToSave = pendingPhoneContact
        pendingPhoneContact = null
        val writeGranted = permissions[Manifest.permission.WRITE_CONTACTS] == true
        val readGranted = permissions[Manifest.permission.READ_CONTACTS] == true
        if (!writeGranted || !readGranted) {
            actionFeedback = "Allow Contacts permission to save scanned cards to your phone contacts."
            actionFeedbackIsError = true
            phoneExportInFlight = false
            return@rememberLauncherForActivityResult
        }
        if (contactToSave != null) {
            startPhoneExport(contactToSave)
        }
    }

    LaunchedEffect(contactId) {
        if (contactId != null) {
            viewModel.setActiveContact(contactId)
        }
    }

    LaunchedEffect(showAddInteraction) {
        if (showAddInteraction) {
            viewModel.updateInteractionDraft { it.copy(time = System.currentTimeMillis()) }
        }
    }

    LaunchedEffect(detailState.deletedContactId, contactId) {
        val deletedId = detailState.deletedContactId ?: return@LaunchedEffect
        if (contactId == deletedId) {
            showDeleteConfirmation = false
            showAddInteraction = false
            navController.navigate(Screen.ContactList.createRoute()) {
                popUpTo(Screen.ContactDetail.route) { inclusive = true }
                launchSingleTop = true
            }
            viewModel.consumeDeletedContactEvent()
        }
    }

    if (contactId == null) {
        EmptyState(
            title = "Contact not found",
            subtitle = "Return to your contacts list.",
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Contact",
                subtitle = "Profile",
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppDimens.lg),
                verticalArrangement = Arrangement.spacedBy(AppDimens.lg)
            ) {
                item {
                    val contact = detailState.contact
                    if (contact == null) {
                        EmptyState(
                            title = if (detailState.isDeleting) "Deleting contact" else "Contact unavailable",
                            subtitle = if (detailState.isDeleting) {
                                "Please wait while the contact is removed."
                            } else {
                                "This contact may have been deleted."
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        return@item
                    }
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.md)) {
                            if (contact.imagePath != null) {
                                BusinessCardAsyncImage(
                                    model = File(contact.imagePath),
                                    contentDescription = "Business card image",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1.75f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No image", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Text(
                                text = contact.name ?: "Unknown",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Medium
                            )
                            contact.title?.let {
                                Text(text = it, style = MaterialTheme.typography.bodyLarge)
                            }
                            contact.company?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(AppDimens.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                contact.industryDisplayLabel()?.let {
                                    InfoChip(label = it)
                                }
                                if (!contact.phoneExportStatus.isNullOrBlank()) {
                                    StatusPill(
                                        label = "Phone ${formatPhoneExportStatus(contact.phoneExportStatus)}",
                                        tone = if (
                                            contact.phoneExportStatus.equals(
                                                ContactsSyncResultType.Failed.name,
                                                ignoreCase = true
                                            )
                                        ) {
                                            StatusPillTone.Error
                                        } else {
                                            StatusPillTone.Brand
                                        },
                                        showDot = false
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    val contact = detailState.contact
                    if (contact != null) {
                        SectionHeader(
                            title = "Quick actions",
                            supportingText = "Reach out fast or save to your phone contacts."
                        )
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            val quickActions = mutableListOf<QuickActionAction>()
                            contact.phone?.let { phone ->
                                quickActions += QuickActionAction(
                                    text = "Call",
                                    icon = Icons.Filled.Call,
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                        runCatching {
                                            if (intent.resolveActivity(context.packageManager) != null) {
                                                actionFeedback = null
                                                context.startActivity(intent)
                                            } else {
                                                actionFeedback = "No dialer available."
                                                actionFeedbackIsError = true
                                            }
                                        }.onFailure {
                                            actionFeedback = "Unable to start a call."
                                            actionFeedbackIsError = true
                                        }
                                    }
                                )
                            }
                            contact.email?.let { email ->
                                quickActions += QuickActionAction(
                                    text = "Email",
                                    icon = Icons.Filled.Email,
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                                        runCatching {
                                            if (intent.resolveActivity(context.packageManager) != null) {
                                                actionFeedback = null
                                                context.startActivity(intent)
                                            } else {
                                                actionFeedback = "No email app available."
                                                actionFeedbackIsError = true
                                            }
                                        }.onFailure {
                                            actionFeedback = "Unable to open email app."
                                            actionFeedbackIsError = true
                                        }
                                    }
                                )
                            }
                            contact.website?.let { website ->
                                quickActions += QuickActionAction(
                                    text = "Website",
                                    icon = Icons.Filled.Language,
                                    onClick = {
                                        val url = if (website.startsWith("http")) website else "https://$website"
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        runCatching {
                                            if (intent.resolveActivity(context.packageManager) != null) {
                                                actionFeedback = null
                                                context.startActivity(intent)
                                            } else {
                                                actionFeedback = "No browser available."
                                                actionFeedbackIsError = true
                                            }
                                        }.onFailure {
                                            actionFeedback = "Unable to open website."
                                            actionFeedbackIsError = true
                                        }
                                    }
                                )
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
                            quickActions += QuickActionAction(
                                text = if (phoneExportInFlight) "Adding..." else "Add to phone",
                                icon = Icons.Filled.Add,
                                onClick = {
                                    actionFeedback = null
                                    if (!phoneExportInFlight) {
                                        if (hasContactsPermission) {
                                            startPhoneExport(contact)
                                        } else {
                                            pendingPhoneContact = contact
                                            contactsPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.READ_CONTACTS,
                                                    Manifest.permission.WRITE_CONTACTS
                                                )
                                            )
                                        }
                                    }
                                },
                                enabled = !phoneExportInFlight
                            )
                            quickActions += QuickActionAction(
                                text = "Copy",
                                icon = Icons.Filled.ContentCopy,
                                onClick = {
                                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                                    val value = listOfNotNull(contact.name, contact.email, contact.phone).joinToString(" | ")
                                    if (clipboard != null) {
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Contact", value))
                                        actionFeedback = "Copied contact info."
                                        actionFeedbackIsError = false
                                    } else {
                                        actionFeedback = "Clipboard unavailable."
                                        actionFeedbackIsError = true
                                    }
                                }
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.md)) {
                                Text(
                                    text = "Use available channels to reach this contact quickly.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                quickActions.chunked(2).forEach { rowActions ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(AppDimens.sm)
                                    ) {
                                        rowActions.forEach { action ->
                                            SecondaryButton(
                                                text = action.text,
                                                onClick = action.onClick,
                                                icon = action.icon,
                                                modifier = Modifier.weight(1f),
                                                enabled = action.enabled
                                            )
                                        }
                                        if (rowActions.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                                if (phoneExportInFlight) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AppDimens.sm)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(AppDimens.iconSizeSm),
                                            strokeWidth = AppDimens.divider + AppDimens.divider
                                        )
                                        Text(
                                            text = "Adding to phone contacts...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (!contact.phoneExportStatus.isNullOrBlank()) {
                                    val status = formatPhoneExportStatus(contact.phoneExportStatus)
                                    val exportedAt = contact.phoneExportedAt
                                    val exportedTime = if (exportedAt != null) {
                                        DateUtils.getRelativeTimeSpanString(
                                            exportedAt,
                                            System.currentTimeMillis(),
                                            DateUtils.MINUTE_IN_MILLIS
                                        ).toString()
                                    } else {
                                        null
                                    }
                                    Text(
                                        text = buildString {
                                            append("Phone contacts: ")
                                            append(status)
                                            if (!exportedTime.isNullOrBlank()) {
                                                append(" - ")
                                                append(exportedTime)
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                actionFeedback?.let { feedback ->
                                    StatusPill(
                                        label = feedback,
                                        tone = if (actionFeedbackIsError) {
                                            StatusPillTone.Error
                                        } else {
                                            StatusPillTone.Brand
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Edit details",
                        supportingText = "Update saved contact details without changing scan data."
                    )
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.md)) {
                            EditableFieldRow {
                                AppTextField(
                                    value = detailState.editFields.name,
                                    onValueChange = { value ->
                                        viewModel.updateContactEditFields { it.copy(name = value) }
                                    },
                                    label = "Name",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            EditableFieldRow {
                                AppTextField(
                                    value = detailState.editFields.title,
                                    onValueChange = { value ->
                                        viewModel.updateContactEditFields { it.copy(title = value) }
                                    },
                                    label = "Title",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            EditableFieldRow {
                                AppTextField(
                                    value = detailState.editFields.company,
                                    onValueChange = { value ->
                                        viewModel.updateContactEditFields { it.copy(company = value) }
                                    },
                                    label = "Company",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            EditableFieldRow {
                                IndustrySelector(
                                    value = detailState.editFields.industry,
                                    customValue = detailState.editFields.industryCustom,
                                    onValueChange = { value ->
                                        viewModel.updateContactEditFields {
                                            it.copy(
                                                industry = value,
                                                industryCustom = if (value.equals("Other", ignoreCase = true)) {
                                                    it.industryCustom
                                                } else {
                                                    ""
                                                },
                                                industrySource = IndustrySource.normalizeForDraft(
                                                    industry = value,
                                                    source = IndustrySource.USER_SELECTED
                                                )
                                            )
                                        }
                                    },
                                    onCustomValueChange = { custom ->
                                        viewModel.updateContactEditFields {
                                            it.copy(
                                                industryCustom = custom,
                                                industrySource = IndustrySource.normalizeForDraft(
                                                    industry = it.industry,
                                                    source = IndustrySource.USER_SELECTED
                                                )
                                            )
                                        }
                                    },
                                    options = IndustryCatalog.manualSelectionIndustries,
                                    label = "Industry"
                                )
                            }
                            EditableFieldRow {
                                AppTextField(
                                    value = detailState.editFields.email,
                                    onValueChange = { value ->
                                        viewModel.updateContactEditFields { it.copy(email = value) }
                                    },
                                    label = "Email",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            EditableFieldRow {
                                AppTextField(
                                    value = detailState.editFields.phone,
                                    onValueChange = { value ->
                                        viewModel.updateContactEditFields { it.copy(phone = value) }
                                    },
                                    label = "Phone",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            EditableFieldRow {
                                AppTextField(
                                    value = detailState.editFields.website,
                                    onValueChange = { value ->
                                        viewModel.updateContactEditFields { it.copy(website = value) }
                                    },
                                    label = "Website",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            PrimaryButton(
                                text = if (detailState.isSaving) "Saving..." else "Save changes",
                                onClick = { viewModel.saveContactEdits() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !detailState.isSaving && !detailState.isDeleting
                            )
                            DangerButton(
                                text = if (detailState.isDeleting) "Deleting..." else "Delete contact",
                                onClick = { showDeleteConfirmation = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !detailState.isSaving && !detailState.isDeleting,
                                icon = Icons.Filled.Delete
                            )
                            detailState.errorMessage?.let { error ->
                                Text(text = error, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "How you met",
                        supportingText = "Keep a running history of conversations and notes.",
                        action = {
                            SecondaryButton(
                                text = "Add",
                                onClick = { showAddInteraction = true },
                                icon = Icons.Filled.Add,
                                enabled = !detailState.isDeleting
                            )
                        }
                    )
                }

                if (detailState.interactions.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No notes yet",
                            subtitle = "Add how you met details to keep context.",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    items(detailState.interactions) { interaction ->
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.sm)) {
                                val relative = DateUtils.getRelativeTimeSpanString(
                                    interaction.dateTime,
                                    System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS
                                )
                                StatusPill(
                                    label = relative.toString(),
                                    tone = StatusPillTone.Neutral,
                                    showDot = false
                                )
                                interaction.relationshipType?.let {
                                    StatusPill(
                                        label = it,
                                        tone = StatusPillTone.Brand,
                                        showDot = false
                                    )
                                }
                                interaction.meetingLocationName?.let {
                                    Text(text = "Place: $it", style = MaterialTheme.typography.bodySmall)
                                }
                                interaction.notes?.let {
                                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddInteraction) {
        var connectionExpanded by remember { mutableStateOf(false) }
        val addInteractionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val selectedInteractionRelationship = if (detailState.interactionDraft.relationship in ConnectionCatalog.options) {
            detailState.interactionDraft.relationship
        } else {
            ConnectionCatalog.OTHER
        }
        val interactionUsesCustomRelationship = selectedInteractionRelationship == ConnectionCatalog.OTHER
        val customInteractionRelationship =
            if (detailState.interactionDraft.relationship.equals(ConnectionCatalog.OTHER, ignoreCase = true)) {
                ""
            } else {
                detailState.interactionDraft.relationship
            }
        val hasValidRelationship =
            !interactionUsesCustomRelationship || customInteractionRelationship.isNotBlank()
        AppModalBottomSheet(
            onDismissRequest = { showAddInteraction = false },
            sheetState = addInteractionSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.md, vertical = AppDimens.sm),
                verticalArrangement = Arrangement.spacedBy(AppDimens.md)
            ) {
                Text(text = "Add how you met", style = MaterialTheme.typography.titleMedium)
                AppTextField(
                    value = detailState.interactionDraft.location,
                    onValueChange = { value ->
                        viewModel.updateInteractionDraft { it.copy(location = value) }
                    },
                    label = "Where you met",
                    placeholder = "Conference booth",
                    modifier = Modifier.fillMaxWidth()
                )
                AnchoredDropdownField(
                    label = "Connection",
                    value = selectedInteractionRelationship,
                    options = ConnectionCatalog.options,
                    expanded = connectionExpanded,
                    onExpandedChange = { connectionExpanded = it },
                    onOptionSelected = { selected ->
                        if (selected == ConnectionCatalog.OTHER) {
                            if (detailState.interactionDraft.relationship in ConnectionCatalog.options) {
                                viewModel.updateInteractionDraft {
                                    it.copy(relationship = ConnectionCatalog.OTHER)
                                }
                            }
                        } else {
                            viewModel.updateInteractionDraft { it.copy(relationship = selected) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (interactionUsesCustomRelationship) {
                    AppTextField(
                        value = customInteractionRelationship,
                        onValueChange = { value ->
                            viewModel.updateInteractionDraft { it.copy(relationship = value) }
                        },
                        label = "Custom connection",
                        placeholder = "Advisor, Investor",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                AppTextField(
                    value = detailState.interactionDraft.notes,
                    onValueChange = { value ->
                        viewModel.updateInteractionDraft { it.copy(notes = value) }
                    },
                    label = "Notes",
                    placeholder = "Follow up next week",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
                PrimaryButton(
                    text = "Save note",
                    onClick = {
                        viewModel.addInteraction()
                        showAddInteraction = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !detailState.isDeleting && hasValidRelationship
                )
                SecondaryButton(
                    text = "Cancel",
                    onClick = { showAddInteraction = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    phoneExportResult?.let { result ->
        val resultStyle = phoneExportDialogStyle(result.type)
        val canViewContact = result.phoneContactLookupUri != null &&
            Intent(Intent.ACTION_VIEW, result.phoneContactLookupUri)
                .resolveActivity(context.packageManager) != null
        AppAlertDialog(
            onDismissRequest = { phoneExportResult = null },
            title = { Text(resultStyle.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.sm)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.sm)
                    ) {
                        Icon(
                            imageVector = resultStyle.icon,
                            contentDescription = null,
                            tint = resultStyle.tint
                        )
                        Text(
                            text = resultStyle.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (result.addedFields.isNotEmpty()) {
                        Text(
                            text = "Added ${formatAddedFields(result.addedFields)}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (result.type == ContactsSyncResultType.Failed && !result.reason.isNullOrBlank()) {
                        Text(
                            text = result.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                SecondaryButton(text = "Close", onClick = { phoneExportResult = null })
            },
            dismissButton = {
                if (canViewContact && result.phoneContactLookupUri != null) {
                    SecondaryButton(
                        text = "View in phone contacts",
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, result.phoneContactLookupUri))
                            }.onFailure {
                                actionFeedback = "Could not open this phone contact."
                                actionFeedbackIsError = true
                            }
                        },
                        enabled = true
                    )
                }
            }
        )
    }

    if (showDeleteConfirmation) {
        AppAlertDialog(
            onDismissRequest = {
                if (!detailState.isDeleting) {
                    showDeleteConfirmation = false
                }
            },
            title = { Text("Delete this contact?") },
            text = {
                Text(
                    "This permanently removes the contact, all how you met notes, OCR images, " +
                        "generated thumbnails, and enrichment metadata. This action cannot be undone."
                )
            },
            confirmButton = {
                DangerButton(
                    text = if (detailState.isDeleting) "Deleting..." else "Delete contact",
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.deleteActiveContact()
                    },
                    enabled = !detailState.isDeleting
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = "Cancel",
                    onClick = { showDeleteConfirmation = false },
                    enabled = !detailState.isDeleting
                )
            }
        )
    }
}

private data class QuickActionAction(
    val text: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true
)

private data class PhoneExportDialogStyle(
    val title: String,
    val message: String,
    val icon: ImageVector,
    val tint: androidx.compose.ui.graphics.Color
)

@Composable
private fun phoneExportDialogStyle(type: ContactsSyncResultType): PhoneExportDialogStyle {
    return when (type) {
        ContactsSyncResultType.AddedNew -> PhoneExportDialogStyle(
            title = "Contact added",
            message = "Added to phone contacts.",
            icon = Icons.Filled.Add,
            tint = MaterialTheme.colorScheme.primary
        )
        ContactsSyncResultType.UpdatedExisting -> PhoneExportDialogStyle(
            title = "Contact updated",
            message = "Updated existing phone contact.",
            icon = Icons.Filled.Update,
            tint = MaterialTheme.colorScheme.primary
        )
        ContactsSyncResultType.AlreadyPresent -> PhoneExportDialogStyle(
            title = "Already saved",
            message = "Already in phone contacts.",
            icon = Icons.Filled.Info,
            tint = MaterialTheme.colorScheme.primary
        )
        ContactsSyncResultType.SkippedMissingKey -> PhoneExportDialogStyle(
            title = "Nothing to add",
            message = "Could not add to phone contacts.",
            icon = Icons.Filled.Info,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ContactsSyncResultType.Failed -> PhoneExportDialogStyle(
            title = "Could not add",
            message = "Could not add to phone contacts.",
            icon = Icons.Filled.ErrorOutline,
            tint = MaterialTheme.colorScheme.error
        )
    }
}

private fun formatAddedFields(addedFields: List<String>): String {
    val normalized = addedFields
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .distinct()
    if (normalized.isEmpty()) return "details"
    if (normalized.size == 1) return normalized.first()
    if (normalized.size == 2) return "${normalized.first()} and ${normalized.last()}"
    return normalized.dropLast(1).joinToString(", ") + ", and " + normalized.last()
}

private fun formatPhoneExportStatus(status: String): String {
    return when (status.lowercase()) {
        ContactsSyncResultType.AddedNew.name.lowercase() -> "Added"
        ContactsSyncResultType.UpdatedExisting.name.lowercase() -> "Updated"
        ContactsSyncResultType.AlreadyPresent.name.lowercase() -> "Already present"
        ContactsSyncResultType.SkippedMissingKey.name.lowercase() -> "Missing details"
        ContactsSyncResultType.Failed.name.lowercase() -> "Failed"
        else -> "Unknown"
    }
}

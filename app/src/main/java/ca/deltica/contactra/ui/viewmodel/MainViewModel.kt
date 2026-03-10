package ca.deltica.contactra.ui.viewmodel

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.util.Patterns
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.deltica.contactra.data.export.ExportBackupManager
import ca.deltica.contactra.data.integration.ContactsBulkExportSummary
import ca.deltica.contactra.data.integration.ContactsSyncEngine
import ca.deltica.contactra.data.integration.ContactsSyncResult
import ca.deltica.contactra.data.location.LocationHelper
import ca.deltica.contactra.data.ocr.TextRecognitionManager
import ca.deltica.contactra.domain.logic.DataExtractor
import ca.deltica.contactra.domain.logic.ExtractedData
import ca.deltica.contactra.domain.logic.InferenceEngine
import ca.deltica.contactra.domain.logic.InferenceResult
import ca.deltica.contactra.domain.logic.IndustryCatalog
import ca.deltica.contactra.domain.logic.ConnectionCatalog
import ca.deltica.contactra.domain.logic.CardImageCropper
import ca.deltica.contactra.domain.logic.CardCropGeometry
import ca.deltica.contactra.domain.logic.CardCropDebugExporter
import ca.deltica.contactra.domain.logic.CardCropAuditRecord
import ca.deltica.contactra.domain.logic.CardCropAttempt
import ca.deltica.contactra.domain.logic.CardCropTransformType
import ca.deltica.contactra.domain.logic.ContactImagePolicy
import ca.deltica.contactra.domain.logic.CropPoint
import ca.deltica.contactra.domain.logic.CropQuad
import ca.deltica.contactra.domain.logic.IndustryPrefillPolicy
import ca.deltica.contactra.domain.logic.IndustrySource
import ca.deltica.contactra.domain.logic.NormalizedOcrLine
import ca.deltica.contactra.domain.logic.OcrExtractionAudit
import ca.deltica.contactra.domain.logic.OcrFieldType
import ca.deltica.contactra.domain.logic.UnassignedOcrExtractor
import ca.deltica.contactra.domain.logic.UnassignedOcrItem
import ca.deltica.contactra.domain.logic.WebsiteEnrichmentEngine
import ca.deltica.contactra.domain.model.Contact
import ca.deltica.contactra.domain.model.ContactSummary
import ca.deltica.contactra.domain.model.Interaction
import ca.deltica.contactra.domain.repository.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class SortOption {
    RECENT,
    NAME,
    COMPANY
}

enum class MatchField {
    NAME,
    COMPANY,
    TITLE,
    EMAIL,
    PHONE,
    WEBSITE,
    INDUSTRY,
    OCR,
    NOTES,
    LOCATION,
    RELATIONSHIP
}

data class ContactMatch(
    val summary: ContactSummary,
    val matchedTokens: List<String>,
    val matchedFields: List<MatchField>,
    val score: Int
)

data class ContactsUiState(
    val query: String = "",
    val tokens: List<String> = emptyList(),
    val industryFilter: String? = null,
    val hasNotes: Boolean = false,
    val sortOption: SortOption = SortOption.RECENT,
    val results: List<ContactMatch> = emptyList(),
    val isLoading: Boolean = true
)

data class HomeUiState(
    val totalContacts: Int = 0,
    val scansThisWeek: Int = 0,
    val recentContacts: List<ContactSummary> = emptyList(),
    val isLoading: Boolean = true
)

data class SettingsUiState(
    val includeVCard: Boolean = false,
    val websiteEnrichmentEnabled: Boolean = true,
    val isExporting: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

data class ReviewFields(
    val name: String = "",
    val title: String = "",
    val company: String = "",
    val email: String = "",
    val phone: String = "",
    val website: String = "",
    val address: String = "",
    val industry: String = "",
    val industryCustom: String = "",
    val industrySource: String? = null
)

enum class ReviewAssignmentField {
    TITLE,
    COMPANY,
    ADDRESS,
    NAME,
    NOTES,
    EMAIL,
    PHONE,
    WEBSITE,
    INDUSTRY
}

enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW
}

data class ReviewFieldConfidence(
    val name: ConfidenceLevel = ConfidenceLevel.LOW,
    val title: ConfidenceLevel = ConfidenceLevel.LOW,
    val company: ConfidenceLevel = ConfidenceLevel.LOW,
    val email: ConfidenceLevel = ConfidenceLevel.LOW,
    val phone: ConfidenceLevel = ConfidenceLevel.LOW,
    val website: ConfidenceLevel = ConfidenceLevel.LOW,
    val industry: ConfidenceLevel = ConfidenceLevel.LOW
)

data class ScanUiState(
    val rawOcrText: String = "",
    val extractedData: ExtractedData? = null,
    val inferenceResult: InferenceResult? = null,
    val reviewFields: ReviewFields = ReviewFields(),
    val reviewSuggestions: ReviewFieldSuggestions = ReviewFieldSuggestions(),
    val unassignedItems: List<UnassignedOcrItem> = emptyList(),
    val reviewAssignmentNotes: String = "",
    val fieldConfidence: ReviewFieldConfidence = ReviewFieldConfidence(),
    val ocrExtractionAudit: OcrExtractionAudit = OcrExtractionAudit(),
    val lastCapturedImage: Bitmap? = null,
    val isProcessing: Boolean = false,
    val processingMessage: String? = null,
    val errorMessage: String? = null,
    val duplicateCandidates: List<Contact> = emptyList(),
    val duplicateReasonsByContactId: Map<Long, List<String>> = emptyMap(),
    val mergePreviewByContactId: Map<Long, List<MergePreviewField>> = emptyMap(),
    val isCheckingDuplicates: Boolean = false,
    val showDuplicateEducation: Boolean = false,
    val mergeTargetContactId: Long? = null,
    val allowIncompleteCoreSave: Boolean = false,
    val parserNoticeMessage: String? = null
)

data class MeetingContextUiState(
    val location: String = "",
    val relationship: String = "Client",
    val notes: String = "",
    val time: Long = System.currentTimeMillis(),
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val inferredPlaceName: String? = null,
    val inferredCity: String? = null,
    val currentLocation: Location? = null,
    val isLocating: Boolean = false,
    val locationError: String? = null
)

data class ContactEditFields(
    val name: String = "",
    val title: String = "",
    val company: String = "",
    val industry: String = "",
    val industryCustom: String = "",
    val industrySource: String? = null,
    val email: String = "",
    val phone: String = "",
    val website: String = ""
)

data class InteractionDraft(
    val location: String = "",
    val relationship: String = "Client",
    val notes: String = "",
    val time: Long = System.currentTimeMillis()
)

data class ContactDetailUiState(
    val contact: Contact? = null,
    val interactions: List<Interaction> = emptyList(),
    val editFields: ContactEditFields = ContactEditFields(),
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val deletedContactId: Long? = null,
    val errorMessage: String? = null,
    val interactionDraft: InteractionDraft = InteractionDraft()
)

private data class ContactDetailOperationState(
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val deletedContactId: Long? = null,
    val errorMessage: String? = null
)

private data class SavedCardImageAssets(
    val imagePath: String,
    val rawImagePath: String,
    val cardCropQuad: String?,
    val cardCropVersion: Int
)

/**
 * MainViewModel orchestrates scanning, OCR, inference, meeting context capture and
 * persistence. It also owns contact list/search and contact detail state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val appContext: Context,
    private val repository: ContactRepository
) : ViewModel() {
    private val uxPrefs = appContext.getSharedPreferences(DUPLICATE_UX_PREFS, Context.MODE_PRIVATE)
    private val settingsPrefs = appContext.getSharedPreferences(APP_SETTINGS_PREFS, Context.MODE_PRIVATE)
    private val exportBackupManager = ExportBackupManager(repository)
    private val contactsSyncEngine = ContactsSyncEngine(appContext)
    private val ocrTimeoutMs = 15_000L

    private val _scanUiState = MutableStateFlow(ScanUiState())
    val scanUiState: StateFlow<ScanUiState> = _scanUiState.asStateFlow()

    private val _meetingContextUiState = MutableStateFlow(MeetingContextUiState())
    val meetingContextUiState: StateFlow<MeetingContextUiState> = _meetingContextUiState.asStateFlow()

    private val contactSummaries = repository.contactSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val queryState = MutableStateFlow("")
    private val industryFilterState = MutableStateFlow<String?>(null)
    private val hasNotesFilterState = MutableStateFlow(false)
    private val sortState = MutableStateFlow(SortOption.RECENT)
    private val includeVCardState = MutableStateFlow(false)
    private val websiteEnrichmentEnabledState = MutableStateFlow(
        settingsPrefs.getBoolean(WEBSITE_ENRICHMENT_ENABLED_KEY, true)
    )
    private val settingsExportingState = MutableStateFlow(false)
    private val settingsStatusState = MutableStateFlow<String?>(null)
    private val settingsErrorState = MutableStateFlow<String?>(null)

    val contactsUiState: StateFlow<ContactsUiState> = combine(
        contactSummaries,
        queryState,
        industryFilterState,
        hasNotesFilterState,
        sortState
    ) { summaries, query, industryFilter, hasNotes, sortOption ->
        val tokens = tokenize(query)
        val filtered = summaries.filter { summary ->
            (industryFilter.isNullOrBlank() || summary.contact.industry == industryFilter) &&
                (!hasNotes || summary.hasNotes)
        }
        val matches = if (tokens.isEmpty()) {
            filtered.map { summary ->
                ContactMatch(summary, emptyList(), emptyList(), 0)
            }
        } else {
            filtered.mapNotNull { summary -> buildMatch(summary, tokens) }
        }
        val sorted = sortMatches(matches, sortOption, tokens.isNotEmpty())
        ContactsUiState(
            query = query,
            tokens = tokens,
            industryFilter = industryFilter,
            hasNotes = hasNotes,
            sortOption = sortOption,
            results = sorted,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    val homeUiState: StateFlow<HomeUiState> = contactSummaries.map { summaries ->
        val now = System.currentTimeMillis()
        val weekAgo = now - 7L * 24L * 60L * 60L * 1000L
        val scansThisWeek = summaries.count { (it.lastInteractionTime ?: 0L) >= weekAgo }
        val recent = summaries.sortedByDescending { it.lastInteractionTime ?: 0L }.take(3)
        HomeUiState(
            totalContacts = summaries.size,
            scansThisWeek = scansThisWeek,
            recentContacts = recent,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val settingsUiState: StateFlow<SettingsUiState> = combine(
        includeVCardState,
        websiteEnrichmentEnabledState,
        settingsExportingState,
        settingsStatusState,
        settingsErrorState
    ) { includeVCard, websiteEnrichmentEnabled, isExporting, statusMessage, errorMessage ->
        SettingsUiState(
            includeVCard = includeVCard,
            websiteEnrichmentEnabled = websiteEnrichmentEnabled,
            isExporting = isExporting,
            statusMessage = statusMessage,
            errorMessage = errorMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val selectedContactId = MutableStateFlow<Long?>(null)
    private val editFieldsState = MutableStateFlow(ContactEditFields())
    private val interactionDraftState = MutableStateFlow(InteractionDraft())
    private val detailSavingState = MutableStateFlow(false)
    private val detailDeletingState = MutableStateFlow(false)
    private val deletedContactIdState = MutableStateFlow<Long?>(null)
    private val detailErrorState = MutableStateFlow<String?>(null)
    private val hasUserEdits = MutableStateFlow(false)
    private val cropUpgradeLock = Any()
    private val activeCropUpgrades = mutableSetOf<Long>()
    private val attemptedCropUpgrades = mutableSetOf<Long>()

    private val contactFlow = selectedContactId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.contactById(id)
    }

    private val interactionsFlow = selectedContactId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.interactionsForContact(id)
    }

    private val detailOperationStateFlow = combine(
        detailSavingState,
        detailDeletingState,
        deletedContactIdState,
        detailErrorState
    ) { isSaving, isDeleting, deletedContactId, error ->
        ContactDetailOperationState(
            isSaving = isSaving,
            isDeleting = isDeleting,
            deletedContactId = deletedContactId,
            errorMessage = error
        )
    }

    val contactDetailUiState: StateFlow<ContactDetailUiState> = combine(
        contactFlow,
        interactionsFlow,
        editFieldsState,
        interactionDraftState,
        detailOperationStateFlow
    ) { contact, interactions, editFields, draft, operationState ->
        ContactDetailUiState(
            contact = contact,
            interactions = interactions,
            editFields = editFields,
            isSaving = operationState.isSaving,
            isDeleting = operationState.isDeleting,
            deletedContactId = operationState.deletedContactId,
            errorMessage = operationState.errorMessage,
            interactionDraft = draft
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactDetailUiState())

    init {
        viewModelScope.launch {
            contactFlow.collect { contact ->
                if (contact != null) {
                    maybeScheduleLegacyCardCropUpgrade(contact)
                }
                if (contact != null && !hasUserEdits.value) {
                    editFieldsState.value = contact.toEditFields()
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        queryState.value = query
    }

    fun updateIndustryFilter(industry: String?) {
        industryFilterState.value = industry
    }

    fun updateHasNotesFilter(enabled: Boolean) {
        hasNotesFilterState.value = enabled
    }

    fun updateSortOption(option: SortOption) {
        sortState.value = option
    }

    fun setActiveContact(contactId: Long) {
        selectedContactId.value = contactId
        detailErrorState.value = null
        detailDeletingState.value = false
        deletedContactIdState.value = null
        hasUserEdits.value = false
        interactionDraftState.value = InteractionDraft()
        viewModelScope.launch(Dispatchers.IO) {
            val contact = repository.contactByIdOnce(contactId)
            if (contact != null) {
                withContext(Dispatchers.Main) {
                    editFieldsState.value = contact.toEditFields()
                }
            }
        }
    }

    fun updateContactEditFields(update: (ContactEditFields) -> ContactEditFields) {
        hasUserEdits.value = true
        editFieldsState.update(update)
    }

    fun updateInteractionDraft(update: (InteractionDraft) -> InteractionDraft) {
        interactionDraftState.update(update)
    }

    fun saveContactEdits() {
        if (detailSavingState.value || detailDeletingState.value) return
        val contact = contactDetailUiState.value.contact ?: return
        detailSavingState.value = true
        detailErrorState.value = null
        val normalizedIndustrySelection = IndustryPrefillPolicy.normalizeSelection(
            industry = editFieldsState.value.industry,
            industryCustom = editFieldsState.value.industryCustom
        )
        val updated = contact.copy(
            name = editFieldsState.value.name.ifBlank { null },
            title = editFieldsState.value.title.ifBlank { null },
            company = editFieldsState.value.company.ifBlank { null },
            industry = normalizedIndustrySelection.industry,
            industryCustom = normalizedIndustrySelection.industryCustom,
            industrySource = IndustrySource.toPersistedValue(
                industry = normalizedIndustrySelection.industry,
                source = editFieldsState.value.industrySource
            ),
            email = editFieldsState.value.email.ifBlank { null },
            phone = DataExtractor.normalizePhoneNumber(editFieldsState.value.phone.ifBlank { null }),
            website = editFieldsState.value.website.ifBlank { null }
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.updateContact(updated)
                saveCompanyIndustryMapping(updated.company, updated.industry)
            }.onFailure { error ->
                detailErrorState.value = error.message ?: "Failed to save contact"
            }
            detailSavingState.value = false
            hasUserEdits.value = false
        }
    }

    fun addInteraction() {
        if (detailDeletingState.value) return
        val contactId = contactDetailUiState.value.contact?.id ?: return
        val draft = interactionDraftState.value
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.insertInteraction(
                    Interaction(
                        contactId = contactId,
                        dateTime = draft.time,
                        meetingLocationName = draft.location.ifBlank { null },
                        city = null,
                        relationshipType = draft.relationship,
                        notes = draft.notes.ifBlank { null },
                        latitude = null,
                        longitude = null
                    )
                )
            }.onFailure { error ->
                detailErrorState.value = error.message ?: "Failed to add how you met note"
            }
            interactionDraftState.value = InteractionDraft()
        }
    }

    fun deleteActiveContact() {
        if (detailDeletingState.value || detailSavingState.value) return
        val contactId = contactDetailUiState.value.contact?.id ?: return
        detailDeletingState.value = true
        detailErrorState.value = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.deleteContact(contactId)
            }.onSuccess {
                if (selectedContactId.value == contactId) {
                    selectedContactId.value = null
                }
                hasUserEdits.value = false
                editFieldsState.value = ContactEditFields()
                interactionDraftState.value = InteractionDraft()
                deletedContactIdState.value = contactId
            }.onFailure { error ->
                detailErrorState.value = error.message ?: "Failed to delete contact"
            }
            detailDeletingState.value = false
        }
    }

    fun consumeDeletedContactEvent() {
        deletedContactIdState.value = null
    }

    fun setIncludeVCardExport(enabled: Boolean) {
        includeVCardState.value = enabled
    }

    fun setWebsiteEnrichmentEnabled(enabled: Boolean) {
        websiteEnrichmentEnabledState.value = enabled
        settingsPrefs.edit().putBoolean(WEBSITE_ENRICHMENT_ENABLED_KEY, enabled).apply()
    }

    fun clearSettingsMessages() {
        settingsStatusState.value = null
        settingsErrorState.value = null
    }

    fun setSettingsError(message: String) {
        settingsStatusState.value = null
        settingsErrorState.value = message
    }

    fun shouldIncludeInPhoneBulkExport(contact: Contact): Boolean {
        return ContactsSyncEngine.shouldIncludeInBulk(contact)
    }

    fun phoneExportPayloadHash(contact: Contact): String {
        return ContactsSyncEngine.exportPayloadHash(contact)
    }

    suspend fun prepareContactPhoneExport(contact: Contact): ContactsSyncResult {
        return contactsSyncEngine.prepareExportSummary(contact)
    }

    suspend fun addContactToPhoneContacts(contact: Contact): ContactsSyncResult {
        val result = contactsSyncEngine.applyExport(contact)
        withContext(Dispatchers.IO) {
            persistPhoneExportState(contact, result)
        }
        return result
    }

    suspend fun prepareBulkPhoneContactsExport(contacts: List<Contact>): ContactsBulkExportSummary {
        return contactsSyncEngine.prepareBulkExportSummary(contacts)
    }

    suspend fun addContactsToPhoneContacts(
        contacts: List<Contact>,
        shouldCancel: () -> Boolean = { false },
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): ContactsBulkExportSummary {
        return contactsSyncEngine.applyBulkExport(
            contacts = contacts,
            shouldCancel = shouldCancel,
            onProgress = { processed, total, contact, result ->
                persistPhoneExportState(contact, result)
                withContext(Dispatchers.Main) {
                    onProgress(processed, total)
                }
            }
        )
    }

    suspend fun latestCroppedCardImagePath(): String? = withContext(Dispatchers.IO) {
        repository.allContacts()
            .asSequence()
            .sortedByDescending { it.id }
            .mapNotNull { contact ->
                contact.imagePath
                    ?.takeIf { it.isNotBlank() }
                    ?.takeIf { path -> File(path).exists() }
            }
            .firstOrNull()
    }

    suspend fun exportJsonToUri(destination: Uri): Result<Unit> {
        return runSettingsExport("Backup JSON saved.") {
            val snapshot = exportBackupManager.captureSnapshot()
            withContext(Dispatchers.IO) {
                appContext.contentResolver.openOutputStream(destination)?.use { output ->
                    exportBackupManager.writeJson(snapshot, output)
                } ?: throw IOException("Unable to open selected destination.")
            }
        }
    }

    private suspend fun persistPhoneExportState(contact: Contact, result: ContactsSyncResult) {
        runCatching {
            repository.updateContact(
                contact.copy(
                    phoneExportStatus = result.type.name,
                    phoneExportedAt = System.currentTimeMillis(),
                    phoneExportPayloadHash = ContactsSyncEngine.exportPayloadHash(contact)
                )
            )
        }
    }

    suspend fun exportVCardToUri(destination: Uri): Result<Unit> {
        return runSettingsExport("vCard file saved.") {
            val snapshot = exportBackupManager.captureSnapshot()
            withContext(Dispatchers.IO) {
                appContext.contentResolver.openOutputStream(destination)?.use { output ->
                    exportBackupManager.writeVCard(snapshot, output)
                } ?: throw IOException("Unable to open selected destination.")
            }
        }
    }

    suspend fun buildShareExportIntent(includeVCard: Boolean): Result<Intent> {
        return runSettingsExport("Export prepared for sharing.") {
            withContext(Dispatchers.IO) {
                val snapshot = exportBackupManager.captureSnapshot()
                val exportDir = File(appContext.cacheDir, "exports")
                if (!exportDir.exists() && !exportDir.mkdirs()) {
                    throw IOException("Unable to prepare export directory.")
                }
                trimOldExportFiles(exportDir)

                val timestamp = System.currentTimeMillis()
                val jsonFile = File(exportDir, "contactra-export-$timestamp.json")
                jsonFile.outputStream().use { output ->
                    exportBackupManager.writeJson(snapshot, output)
                }

                val authority = "${appContext.packageName}.fileprovider"
                val shareUris = mutableListOf(
                    FileProvider.getUriForFile(appContext, authority, jsonFile)
                )

                if (includeVCard) {
                    val vCardFile = File(exportDir, "contactra-export-$timestamp.vcf")
                    vCardFile.outputStream().use { output ->
                        exportBackupManager.writeVCard(snapshot, output)
                    }
                    shareUris += FileProvider.getUriForFile(appContext, authority, vCardFile)
                }

                val sendIntent = if (shareUris.size == 1) {
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, shareUris.first())
                    }
                } else {
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(shareUris))
                    }
                }
                sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val clipData = ClipData.newUri(
                    appContext.contentResolver,
                    "backup_export",
                    shareUris.first()
                )
                shareUris.drop(1).forEach { uri ->
                    clipData.addItem(ClipData.Item(uri))
                }
                sendIntent.clipData = clipData
                Intent.createChooser(sendIntent, "Share Contactra backup")
            }
        }
    }

    fun onImageCaptured(bitmap: Bitmap) {
        if (_scanUiState.value.isProcessing) return
        val safeBitmap = runCatching {
            scaleBitmap(bitmap, maxSize = 2000)
        }.getOrElse {
                _scanUiState.value = ScanUiState(
                    isProcessing = false,
                    errorMessage = "Image is too large. Try a smaller one."
                )
                return
            }
        _scanUiState.value = ScanUiState(
            lastCapturedImage = safeBitmap,
            isProcessing = true,
            processingMessage = "Reading card text...",
            errorMessage = null
        )
        viewModelScope.launch {
            try {
                val ocrResult = withContext(Dispatchers.Default) {
                    withTimeout(ocrTimeoutMs) {
                        TextRecognitionManager.recognizeTextStructured(safeBitmap)
                    }
                }
                val ocrText = ocrResult.text
                val normalizedLines = ocrResult.lines.mapIndexed { index, line ->
                    NormalizedOcrLine(
                        text = line.text,
                        index = index,
                        left = line.left,
                        top = line.top,
                        right = line.right,
                        bottom = line.bottom
                    )
                }
                val extractionResult = withContext(Dispatchers.Default) {
                    DataExtractor.extractReliably(
                        text = ocrText,
                        providedLines = normalizedLines
                    )
                }
                val extracted = extractionResult.data
                val hasMeaningfulData = listOf(
                    extracted.name,
                    extracted.title,
                    extracted.company,
                    extracted.email,
                    extracted.phone,
                    extracted.website,
                    extracted.address
                ).any { !it.isNullOrBlank() }
                if (!hasMeaningfulData) {
                    _scanUiState.value = _scanUiState.value.copy(
                        isProcessing = false,
                        processingMessage = null,
                        errorMessage = extractionResult.retryMessage ?: "No text found in this image."
                    )
                    return@launch
                }

                val enrichmentEnabled = websiteEnrichmentEnabledState.value
                val websiteEnrichment = if (enrichmentEnabled) {
                    WebsiteEnrichmentEngine.enrich(
                        company = extracted.company,
                        website = extracted.website,
                        email = extracted.email,
                        title = extracted.title
                    )
                } else {
                    WebsiteEnrichmentEngine.enrich(
                        company = extracted.company,
                        website = null,
                        email = null,
                        title = null
                    )
                }
                val enrichedCompany = if (enrichmentEnabled) {
                    websiteEnrichment.enrichedCompany ?: extracted.company
                } else {
                    extracted.company
                }
                val inference = InferenceEngine.infer(extracted.title, enrichedCompany)
                val companyIndustry = withContext(Dispatchers.IO) {
                    lookupCompanyIndustry(enrichedCompany)
                }
                val prefillDecision = IndustryPrefillPolicy.resolve(
                    currentIndustry = extracted.industry,
                    currentIndustryCustom = extracted.industryCustom,
                    currentSource = extracted.industrySource,
                    enrichmentEnabled = enrichmentEnabled,
                    enrichmentIndustry = websiteEnrichment.inferredIndustry,
                    enrichmentConfidence = websiteEnrichment.industryConfidence,
                    companyName = enrichedCompany
                )
                val enriched = extracted.copy(
                    company = enrichedCompany,
                    industry = prefillDecision.industry,
                    industryCustom = prefillDecision.industryCustom,
                    industrySource = prefillDecision.source
                )
                val suggestions = ReviewSuggestionEngine.build(
                    audit = extractionResult.audit,
                    extracted = enriched,
                    rawOcrText = ocrText,
                    inferredIndustry = inference.industry,
                    companyIndustry = companyIndustry
                )
                val unassignedItems = UnassignedOcrExtractor.extract(
                    audit = extractionResult.audit,
                    selectedValues = mapOf(
                        OcrFieldType.EMAIL to enriched.email,
                        OcrFieldType.PHONE to enriched.phone,
                        OcrFieldType.WEBSITE to enriched.website
                    )
                )
                val reviewFields = ReviewFields(
                    name = enriched.name.orEmpty(),
                    title = enriched.title.orEmpty(),
                    company = enriched.company.orEmpty(),
                    email = enriched.email.orEmpty(),
                    phone = enriched.phone.orEmpty(),
                    website = enriched.website.orEmpty(),
                    address = suggestions.address.firstOrNull().orEmpty(),
                    industry = enriched.industry.orEmpty(),
                    industryCustom = enriched.industryCustom.orEmpty(),
                    industrySource = enriched.industrySource
                )
                _scanUiState.value = _scanUiState.value.copy(
                    rawOcrText = ocrText,
                    extractedData = enriched,
                    inferenceResult = inference,
                    reviewFields = reviewFields,
                    reviewSuggestions = suggestions,
                    unassignedItems = unassignedItems,
                    reviewAssignmentNotes = "",
                    fieldConfidence = computeConfidence(
                        fields = reviewFields,
                        audit = extractionResult.audit
                    ),
                    ocrExtractionAudit = extractionResult.audit,
                    isProcessing = false,
                    processingMessage = null,
                    errorMessage = null,
                    allowIncompleteCoreSave = false,
                    parserNoticeMessage = extractionResult.retryMessage?.takeIf { extractionResult.didRetry }
                )
            } catch (_: TimeoutCancellationException) {
                _scanUiState.value = _scanUiState.value.copy(
                    isProcessing = false,
                    processingMessage = null,
                    errorMessage = "Reading took too long. Try a clearer image."
                )
            } catch (t: Throwable) {
                _scanUiState.value = _scanUiState.value.copy(
                    isProcessing = false,
                    processingMessage = null,
                    errorMessage = t.message ?: "OCR failed"
                )
            }
        }
    }

    fun updateReviewFields(update: (ReviewFields) -> ReviewFields) {
        _scanUiState.update { current ->
            val updated = update(current.reviewFields)
            val normalizedSelection = IndustryPrefillPolicy.normalizeSelection(
                industry = updated.industry,
                industryCustom = updated.industryCustom
            )
            val normalizedIndustry = normalizedSelection.industry.orEmpty()
            val normalized = updated.copy(
                industry = normalizedIndustry,
                industryCustom = normalizedSelection.industryCustom.orEmpty(),
                industrySource = IndustrySource.normalizeForDraft(
                    industry = normalizedIndustry,
                    source = updated.industrySource
                )
            )
            current.copy(
                reviewFields = normalized,
                fieldConfidence = computeConfidence(
                    fields = normalized,
                    audit = current.ocrExtractionAudit
                ),
                allowIncompleteCoreSave = false
            )
        }
        val updated = _scanUiState.value.reviewFields.toExtractedData()
        _scanUiState.update { it.copy(extractedData = updated, inferenceResult = InferenceEngine.infer(updated.title, updated.company)) }
    }

    fun selectReviewFieldCandidate(field: OcrFieldType, lineIndex: Int?) {
        _scanUiState.update { current ->
            val audit = current.ocrExtractionAudit
            val fieldAudit = audit.fieldAudits[field] ?: return@update current
            val selectedCandidate = if (lineIndex == null) {
                null
            } else {
                fieldAudit.alternatives.firstOrNull { it.lineIndex == lineIndex }
            }
            val updatedFieldAudit = fieldAudit.copy(selectedCandidate = selectedCandidate)
            val updatedAudit = audit.copy(fieldAudits = audit.fieldAudits + (field to updatedFieldAudit))
            val updatedFields = applyCandidateSelection(
                fields = current.reviewFields,
                field = field,
                candidateValue = selectedCandidate?.text.orEmpty()
            )
            val consumedLines = selectedCandidate?.sourceLineIndices.orEmpty().toSet()
            val remainingUnassigned = if (consumedLines.isEmpty()) {
                current.unassignedItems
            } else {
                current.unassignedItems.filterNot { item ->
                    item.lineIndices.any { it in consumedLines }
                }
            }
            current.copy(
                reviewFields = updatedFields,
                fieldConfidence = computeConfidence(
                    fields = updatedFields,
                    audit = updatedAudit
                ),
                ocrExtractionAudit = updatedAudit,
                unassignedItems = remainingUnassigned,
                allowIncompleteCoreSave = false
            )
        }
        val updated = _scanUiState.value.reviewFields.toExtractedData()
        _scanUiState.update {
            it.copy(
                extractedData = updated,
                inferenceResult = InferenceEngine.infer(updated.title, updated.company)
            )
        }
    }

    fun assignUnassignedItem(itemId: String, targetField: ReviewAssignmentField) {
        _scanUiState.update { current ->
            val item = current.unassignedItems.firstOrNull { it.id == itemId } ?: return@update current
            val updatedFields = when (targetField) {
                ReviewAssignmentField.NAME -> current.reviewFields.copy(
                    name = appendAssignedValue(current.reviewFields.name, item.displayText, " | ")
                )
                ReviewAssignmentField.TITLE -> current.reviewFields.copy(
                    title = appendAssignedValue(current.reviewFields.title, item.displayText, " | ")
                )
                ReviewAssignmentField.COMPANY -> current.reviewFields.copy(
                    company = appendAssignedValue(current.reviewFields.company, item.displayText, " | ")
                )
                ReviewAssignmentField.EMAIL -> current.reviewFields.copy(
                    email = appendAssignedValue(current.reviewFields.email, item.displayText, " | ")
                )
                ReviewAssignmentField.PHONE -> current.reviewFields.copy(
                    phone = appendAssignedValue(current.reviewFields.phone, item.displayText, " | ")
                )
                ReviewAssignmentField.WEBSITE -> current.reviewFields.copy(
                    website = appendAssignedValue(current.reviewFields.website, item.displayText, " | ")
                )
                ReviewAssignmentField.ADDRESS -> current.reviewFields.copy(
                    address = appendAssignedValue(current.reviewFields.address, item.displayText, "\n")
                )
                ReviewAssignmentField.INDUSTRY -> current.reviewFields.copy(
                    industry = appendAssignedValue(current.reviewFields.industry, item.displayText, " | "),
                    industryCustom = "",
                    industrySource = IndustrySource.USER_SELECTED
                )
                ReviewAssignmentField.NOTES -> current.reviewFields
            }.normalizeIndustrySource()

            val updatedNotes = if (targetField == ReviewAssignmentField.NOTES) {
                appendAssignedValue(current.reviewAssignmentNotes, item.displayText, "\n\n")
            } else {
                current.reviewAssignmentNotes
            }

            current.copy(
                reviewFields = updatedFields,
                reviewAssignmentNotes = updatedNotes,
                unassignedItems = current.unassignedItems.filterNot { it.id == itemId },
                fieldConfidence = computeConfidence(
                    fields = updatedFields,
                    audit = current.ocrExtractionAudit
                ),
                allowIncompleteCoreSave = false
            )
        }

        val updated = _scanUiState.value.reviewFields.toExtractedData()
        _scanUiState.update {
            it.copy(
                extractedData = updated,
                inferenceResult = InferenceEngine.infer(updated.title, updated.company)
            )
        }
    }

    fun checkForDuplicates() {
        val scanSnapshot = _scanUiState.value
        val updated = scanSnapshot.reviewFields.toExtractedData()
        val normalizedIndustrySelection = IndustryPrefillPolicy.normalizeSelection(
            industry = updated.industry,
            industryCustom = updated.industryCustom
        )
        val resolvedIndustry = normalizedIndustrySelection.industry
        val resolvedIndustryCustom = normalizedIndustrySelection.industryCustom
        val resolvedIndustrySource = IndustrySource.toPersistedValue(
            industry = resolvedIndustry,
            source = scanSnapshot.reviewFields.industrySource
        )
        val normalizedPhone = DataExtractor.normalizePhoneNumber(updated.phone)
        _scanUiState.update {
            it.copy(
                isCheckingDuplicates = true,
                duplicateCandidates = emptyList(),
                duplicateReasonsByContactId = emptyMap(),
                mergePreviewByContactId = emptyMap(),
                showDuplicateEducation = false,
                mergeTargetContactId = null
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val candidates = runCatching {
                repository.findDuplicates(updated.email, updated.phone)
            }.getOrElse { emptyList() }
            val duplicateReasonMap = candidates.associate { contact ->
                contact.id to buildDuplicateReasons(
                    existing = contact,
                    incomingEmail = updated.email,
                    incomingPhone = normalizedPhone
                )
            }
            val mergePreviewMap = candidates.associate { contact ->
                contact.id to buildMergePreviewFields(
                    existing = contact,
                    reviewFields = scanSnapshot.reviewFields,
                    normalizedIncomingPhone = normalizedPhone,
                    resolvedIndustry = resolvedIndustry,
                    resolvedIndustryCustom = resolvedIndustryCustom,
                    resolvedIndustrySource = resolvedIndustrySource,
                    incomingRawOcrText = scanSnapshot.rawOcrText.ifBlank { null }
                )
            }
            val shouldShowEducation = candidates.isNotEmpty() && !hasSeenDuplicateEducation()
            withContext(Dispatchers.Main) {
                _scanUiState.update {
                    it.copy(
                        duplicateCandidates = candidates,
                        duplicateReasonsByContactId = duplicateReasonMap,
                        mergePreviewByContactId = mergePreviewMap,
                        isCheckingDuplicates = false,
                        showDuplicateEducation = shouldShowEducation
                    )
                }
            }
        }
    }

    fun acknowledgeDuplicateEducation() {
        if (!hasSeenDuplicateEducation()) {
            uxPrefs.edit().putBoolean(DUPLICATE_EDUCATION_SEEN_KEY, true).apply()
        }
        _scanUiState.update { it.copy(showDuplicateEducation = false) }
    }

    fun setMergeTarget(contactId: Long?) {
        _scanUiState.update { it.copy(mergeTargetContactId = contactId) }
    }

    fun setAllowIncompleteCoreSave(allowed: Boolean) {
        _scanUiState.update { it.copy(allowIncompleteCoreSave = allowed) }
    }

    fun retryOcr() {
        val image = _scanUiState.value.lastCapturedImage ?: return
        onImageCaptured(image)
    }

    fun beginRescan() {
        _scanUiState.value = ScanUiState()
    }

    fun prepareMeetingContext(useCurrentLocation: Boolean = false) {
        val reviewAddress = _scanUiState.value.reviewFields.address
        val reviewAssignedNotes = _scanUiState.value.reviewAssignmentNotes
        _meetingContextUiState.update { it.copy(isLocating = true, locationError = null) }
        viewModelScope.launch {
            try {
                val loc = LocationHelper.getCurrentLocation(appContext)
                val (place, city) = if (loc != null) {
                    LocationHelper.reverseGeocode(appContext, loc)
                } else {
                    null to null
                }
                _meetingContextUiState.update {
                    val currentPlace = place ?: city
                    val fallbackLocation = it.location.ifBlank {
                        reviewAddress.ifBlank { currentPlace.orEmpty() }
                    }
                    val resolvedLocation = if (useCurrentLocation && !currentPlace.isNullOrBlank()) {
                        currentPlace
                    } else {
                        fallbackLocation
                    }
                    it.copy(
                        currentLocation = loc,
                        inferredPlaceName = place,
                        inferredCity = city,
                        location = resolvedLocation,
                        notes = it.notes.ifBlank { reviewAssignedNotes },
                        isLocating = false,
                        locationError = if (useCurrentLocation && loc == null) "Location unavailable." else null
                    )
                }
            } catch (e: Exception) {
                _meetingContextUiState.update {
                    it.copy(
                        isLocating = false,
                        locationError = e.message ?: "Location unavailable"
                    )
                }
            }
        }
    }

    fun updateMeetingContext(update: (MeetingContextUiState) -> MeetingContextUiState) {
        _meetingContextUiState.update(update)
    }

    suspend fun saveContactAndInteraction(): Boolean {
        val scanSnapshot = scanUiState.value
        val meetingSnapshot = meetingContextUiState.value
        val review = scanSnapshot.reviewFields.toExtractedData()
        val hasCoreIdentity = !review.name.isNullOrBlank() || !review.company.isNullOrBlank()
        val trimmedRelationship = meetingSnapshot.relationship.trim()
        if (trimmedRelationship.isBlank()) {
            _meetingContextUiState.update {
                it.copy(
                    isSaving = false,
                    saveError = "Select a connection or enter a custom value."
                )
            }
            return false
        }
        if (trimmedRelationship.equals(ConnectionCatalog.OTHER, ignoreCase = true)) {
            _meetingContextUiState.update {
                it.copy(
                    isSaving = false,
                    saveError = "Enter a custom connection when Other is selected."
                )
            }
            return false
        }
        if (!hasCoreIdentity && !scanSnapshot.allowIncompleteCoreSave) {
                _meetingContextUiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = "Add a name or company, or confirm this in Review."
                    )
                }
                return false
        }
        _meetingContextUiState.update { it.copy(isSaving = true, saveError = null) }
        return try {
            withContext(Dispatchers.IO) {
                val normalizedSelection = IndustryPrefillPolicy.normalizeSelection(
                    industry = review.industry,
                    industryCustom = review.industryCustom
                )
                val industry = normalizedSelection.industry
                val industryCustom = normalizedSelection.industryCustom
                val industrySource = IndustrySource.toPersistedValue(
                    industry = industry,
                    source = review.industrySource
                )
                val normalizedPhone = DataExtractor.normalizePhoneNumber(review.phone)
                val mergedNotes = mergeReviewAndMeetingNotes(
                    reviewNotes = scanSnapshot.reviewAssignmentNotes,
                    meetingNotes = meetingSnapshot.notes
                )
                val savedAt = System.currentTimeMillis()
                val savedCardAssets = saveCardImages(scanSnapshot.lastCapturedImage)
                val savedCardImagePath = savedCardAssets?.imagePath

                val mergedContactId = scanSnapshot.mergeTargetContactId
                val contactId = if (mergedContactId != null) {
                    val existing = repository.contactByIdOnce(mergedContactId)
                    if (existing != null) {
                        val industryMergeDecision = IndustryPrefillPolicy.resolveMerge(
                            existingIndustry = existing.industry,
                            existingIndustryCustom = existing.industryCustom,
                            existingSource = existing.industrySource,
                            incomingIndustry = industry,
                            incomingIndustryCustom = industryCustom,
                            incomingSource = industrySource
                        )
                        val mergedRawOcrText = IndustryPrefillPolicy.appendIndustryMetadata(
                            rawOcrText = mergePreviewValue(
                                scanSnapshot.rawOcrText.ifBlank { null },
                                existing.rawOcrText
                            ),
                            metadataNote = industryMergeDecision.metadataNote
                        )
                        val mergedPrimaryImagePath = savedCardImagePath ?: existing.imagePath
                        val mergedRawImagePath = savedCardAssets?.rawImagePath ?: existing.rawImagePath
                        val mergedCardCropQuad = savedCardAssets?.cardCropQuad ?: existing.cardCropQuad
                        val mergedCardCropVersion = savedCardAssets?.cardCropVersion ?: existing.cardCropVersion
                        val merged = existing.copy(
                            name = mergePreviewValue(review.name, existing.name),
                            title = mergePreviewValue(review.title, existing.title),
                            company = mergePreviewValue(review.company, existing.company),
                            email = mergePreviewValue(review.email, existing.email),
                            phone = mergePreviewValue(normalizedPhone, existing.phone),
                            website = mergePreviewValue(review.website, existing.website),
                            industry = industryMergeDecision.industry,
                            industryCustom = industryMergeDecision.industryCustom,
                            industrySource = industryMergeDecision.source,
                            rawOcrText = mergedRawOcrText,
                            imagePath = mergedPrimaryImagePath,
                            rawImagePath = mergedRawImagePath,
                            cardCropQuad = mergedCardCropQuad,
                            cardCropVersion = mergedCardCropVersion
                        )
                        repository.updateContact(merged)
                        cleanupObsoleteManagedImageFile(
                            previousPath = existing.imagePath,
                            keepPaths = setOfNotNull(mergedPrimaryImagePath, mergedRawImagePath)
                        )
                        existing.id
                    } else {
                        repository.insertContact(
                            Contact(
                                name = review.name?.ifBlank { null },
                                title = review.title?.ifBlank { null },
                                company = review.company?.ifBlank { null },
                                email = review.email?.ifBlank { null },
                                phone = normalizedPhone,
                                website = review.website?.ifBlank { null },
                                industry = industry,
                                industryCustom = industryCustom,
                                industrySource = industrySource,
                                rawOcrText = scanSnapshot.rawOcrText.ifBlank { null },
                                imagePath = savedCardImagePath,
                                rawImagePath = savedCardAssets?.rawImagePath,
                                cardCropQuad = savedCardAssets?.cardCropQuad,
                                cardCropVersion = savedCardAssets?.cardCropVersion ?: CARD_CROP_VERSION_NONE
                            )
                        )
                    }
                } else {
                    repository.insertContact(
                        Contact(
                            name = review.name?.ifBlank { null },
                            title = review.title?.ifBlank { null },
                            company = review.company?.ifBlank { null },
                            email = review.email?.ifBlank { null },
                            phone = normalizedPhone,
                            website = review.website?.ifBlank { null },
                            industry = industry,
                            industryCustom = industryCustom,
                            industrySource = industrySource,
                            rawOcrText = scanSnapshot.rawOcrText.ifBlank { null },
                            imagePath = savedCardImagePath,
                            rawImagePath = savedCardAssets?.rawImagePath,
                            cardCropQuad = savedCardAssets?.cardCropQuad,
                            cardCropVersion = savedCardAssets?.cardCropVersion ?: CARD_CROP_VERSION_NONE
                        )
                    )
                }

                saveCompanyIndustryMapping(review.company, industry)

                repository.insertInteraction(
                    Interaction(
                        contactId = contactId,
                        dateTime = savedAt,
                        meetingLocationName = meetingSnapshot.location.ifBlank { null },
                        city = meetingSnapshot.inferredCity,
                        relationshipType = trimmedRelationship.ifBlank { null },
                        notes = mergedNotes,
                        latitude = meetingSnapshot.currentLocation?.latitude,
                        longitude = meetingSnapshot.currentLocation?.longitude
                    )
                )
            }
            _scanUiState.value = ScanUiState()
            _meetingContextUiState.value = MeetingContextUiState()
            true
        } catch (t: Throwable) {
            _meetingContextUiState.update { it.copy(isSaving = false, saveError = t.message ?: "Save failed") }
            false
        }
    }

    private suspend fun lookupCompanyIndustry(company: String?): String? {
        val normalized = IndustryCatalog.normalizeCompany(company) ?: return null
        return repository.getCompanyIndustry(normalized)
    }

    private suspend fun saveCompanyIndustryMapping(company: String?, industry: String?) {
        val normalized = IndustryCatalog.normalizeCompany(company) ?: return
        val value = industry?.takeIf { it.isNotBlank() } ?: return
        repository.upsertCompanyIndustry(normalized, value)
    }

    private fun mergeReviewAndMeetingNotes(reviewNotes: String, meetingNotes: String): String? {
        val review = reviewNotes.trim()
        val meeting = meetingNotes.trim()
        if (review.isBlank() && meeting.isBlank()) return null
        if (review.isBlank()) return meeting
        if (meeting.isBlank()) return review

        val meetingLower = meeting.lowercase()
        val reviewLower = review.lowercase()
        return when {
            meetingLower.contains(reviewLower) -> meeting
            reviewLower.contains(meetingLower) -> review
            else -> "$review\n\n$meeting"
        }
    }

    private fun appendAssignedValue(existing: String, incoming: String, separator: String): String {
        val normalizedIncoming = incoming.trim()
        if (normalizedIncoming.isBlank()) return existing
        val normalizedExisting = existing.trim()
        if (normalizedExisting.isBlank()) return normalizedIncoming

        val existingLower = normalizedExisting.lowercase()
        val incomingLower = normalizedIncoming.lowercase()
        if (existingLower.contains(incomingLower)) return normalizedExisting
        if (incomingLower.contains(existingLower)) return normalizedIncoming

        return normalizedExisting + separator + normalizedIncoming
    }

    private fun ReviewFields.normalizeIndustrySource(): ReviewFields {
        val normalizedSelection = IndustryPrefillPolicy.normalizeSelection(
            industry = industry,
            industryCustom = industryCustom
        )
        val normalizedIndustry = normalizedSelection.industry.orEmpty()
        return copy(
            industry = normalizedIndustry,
            industryCustom = normalizedSelection.industryCustom.orEmpty(),
            industrySource = IndustrySource.normalizeForDraft(
                industry = normalizedIndustry,
                source = industrySource
            )
        )
    }

    private fun hasSeenDuplicateEducation(): Boolean {
        return uxPrefs.getBoolean(DUPLICATE_EDUCATION_SEEN_KEY, false)
    }

    private fun maybeScheduleLegacyCardCropUpgrade(contact: Contact) {
        if (contact.imagePath.isNullOrBlank()) return
        if (contact.cardCropVersion >= CARD_CROP_VERSION_EDGE_TIGHT) return
        val shouldSchedule = synchronized(cropUpgradeLock) {
            if (contact.id in attemptedCropUpgrades || contact.id in activeCropUpgrades) {
                false
            } else {
                attemptedCropUpgrades += contact.id
                activeCropUpgrades += contact.id
                true
            }
        }
        if (!shouldSchedule) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                performLegacyCardCropUpgrade(contact)
            } finally {
                synchronized(cropUpgradeLock) {
                    activeCropUpgrades.remove(contact.id)
                }
            }
        }
    }

    private suspend fun performLegacyCardCropUpgrade(contact: Contact) {
        val sourcePath = when {
            !contact.rawImagePath.isNullOrBlank() && File(contact.rawImagePath).exists() -> contact.rawImagePath
            !contact.imagePath.isNullOrBlank() && File(contact.imagePath).exists() -> contact.imagePath
            else -> null
        } ?: return

        val sourceBitmap = loadBitmapFromPath(sourcePath) ?: return
        val persistedQuad = decodeNormalizedQuad(
            encoded = contact.cardCropQuad,
            width = sourceBitmap.width,
            height = sourceBitmap.height
        )
        val cropAttempt = runCatching {
            persistedQuad?.let {
                CardImageCropper.cropCardAttemptFromQuad(
                    bitmap = sourceBitmap,
                    quad = it,
                    marginRatio = CARD_CROP_MARGIN_RATIO
                )
            } ?: CardImageCropper.cropCardAttempt(
                bitmap = sourceBitmap,
                marginRatio = CARD_CROP_MARGIN_RATIO
            )
        }.getOrElse {
            createFallbackAuditRecord(
                width = sourceBitmap.width,
                height = sourceBitmap.height,
                reason = "cropper_exception"
            )
        }
        val fallbackCropped = runCatching {
            cropAttempt.result?.bitmap ?: cropBitmapToCardAspect(sourceBitmap)
        }.getOrDefault(sourceBitmap)
        val scaledCard = runCatching {
            scaleBitmap(fallbackCropped, maxSize = 1600)
        }.getOrDefault(fallbackCropped)

        val baseId = ContactImagePolicy.buildBaseId(System.currentTimeMillis())
        val cardFile = File(appContext.filesDir, ContactImagePolicy.buildCardFileName(baseId))
        if (!writeJpeg(cardFile, scaledCard, quality = 90)) return

        val finalAudit = cropAttempt.auditRecord.copy(
            postAspectRatio = normalizedAspectRatio(scaledCard.width, scaledCard.height),
            transformType = cropAttempt.result?.transformType ?: CardCropTransformType.CENTER_FALLBACK,
            auditPassed = cropAttempt.result != null && cropAttempt.auditRecord.auditPassed,
            reason = cropAttempt.auditRecord.reason.ifBlank {
                if (cropAttempt.result == null) "center_fallback_selected" else "pass"
            }
        )
        CardCropDebugExporter.export(appContext, baseId, sourceBitmap, scaledCard, finalAudit)

        val encodedQuad = cropAttempt.result?.detectedQuad?.let {
            encodeNormalizedQuad(it, sourceBitmap.width, sourceBitmap.height)
        } ?: contact.cardCropQuad

        val updated = contact.copy(
            imagePath = cardFile.absolutePath,
            rawImagePath = contact.rawImagePath ?: sourcePath,
            cardCropQuad = encodedQuad,
            cardCropVersion = CARD_CROP_VERSION_EDGE_TIGHT
        )
        repository.updateContact(updated)
        cleanupObsoleteManagedImageFile(
            previousPath = contact.imagePath,
            keepPaths = setOfNotNull(updated.imagePath, updated.rawImagePath)
        )
    }

    private fun saveCardImages(bitmap: Bitmap?): SavedCardImageAssets? {
        if (bitmap == null) return null
        val baseId = ContactImagePolicy.buildBaseId(System.currentTimeMillis())
        return runCatching {
            val rawScaled = runCatching { scaleBitmap(bitmap, maxSize = 2200) }.getOrDefault(bitmap)
            val rawFile = File(appContext.filesDir, ContactImagePolicy.buildRawFileName(baseId))
            if (!writeJpeg(rawFile, rawScaled, quality = 92)) {
                throw IOException("Failed to save raw card image.")
            }

            val cropAttempt = runCatching {
                CardImageCropper.cropCardAttempt(
                    bitmap = rawScaled,
                    marginRatio = CARD_CROP_MARGIN_RATIO
                )
            }.getOrElse {
                createFallbackAuditRecord(
                    width = rawScaled.width,
                    height = rawScaled.height,
                    reason = "cropper_exception"
                )
            }
            val cropped = runCatching {
                cropAttempt.result?.bitmap ?: cropBitmapToCardAspect(rawScaled)
            }.getOrDefault(rawScaled)
            val scaled = runCatching { scaleBitmap(cropped, maxSize = 1600) }.getOrDefault(cropped)
            val cardFile = File(appContext.filesDir, ContactImagePolicy.buildCardFileName(baseId))
            if (!writeJpeg(cardFile, scaled, quality = 90)) {
                rawFile.delete()
                throw IOException("Failed to save cropped card image.")
            }

            val finalAudit = cropAttempt.auditRecord.copy(
                postAspectRatio = normalizedAspectRatio(scaled.width, scaled.height),
                transformType = cropAttempt.result?.transformType ?: CardCropTransformType.CENTER_FALLBACK,
                auditPassed = cropAttempt.result != null && cropAttempt.auditRecord.auditPassed,
                reason = cropAttempt.auditRecord.reason.ifBlank {
                    if (cropAttempt.result == null) "center_fallback_selected" else "pass"
                }
            )
            CardCropDebugExporter.export(appContext, baseId, rawScaled, scaled, finalAudit)

            SavedCardImageAssets(
                imagePath = cardFile.absolutePath,
                rawImagePath = rawFile.absolutePath,
                cardCropQuad = cropAttempt.result?.detectedQuad?.let {
                    encodeNormalizedQuad(it, rawScaled.width, rawScaled.height)
                },
                cardCropVersion = CARD_CROP_VERSION_EDGE_TIGHT
            )
        }.getOrNull()
    }

    private fun createFallbackAuditRecord(width: Int, height: Int, reason: String): CardCropAttempt {
        return CardCropAttempt(
            result = null,
            auditRecord = CardCropAuditRecord(
                inputWidth = width,
                inputHeight = height,
                detectedCorners = emptyList(),
                orderedCorners = emptyList(),
                preAspectRatio = 0f,
                postAspectRatio = 0f,
                transformType = CardCropTransformType.CENTER_FALLBACK,
                auditPassed = false,
                reason = reason,
                areaRatio = 0f,
                edgeRatioWidth = 0f,
                edgeRatioHeight = 0f,
                diagonalRatio = 0f,
                minCornerAngleDeg = 0f
            )
        )
    }

    private fun normalizedAspectRatio(width: Int, height: Int): Float {
        if (width <= 0 || height <= 0) return 0f
        return max(width, height).toFloat() / min(width, height).toFloat()
    }

    private fun loadBitmapFromPath(path: String?): Bitmap? {
        val value = path?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { BitmapFactory.decodeFile(value) }.getOrNull()
    }

    private fun writeJpeg(file: File, bitmap: Bitmap, quality: Int): Boolean {
        if (bitmap.width <= 0 || bitmap.height <= 0) return false
        val parent = file.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false
        return runCatching {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(60, 100), out)
            }
        }.getOrDefault(false)
    }

    private fun encodeNormalizedQuad(quad: CropQuad, width: Int, height: Int): String? {
        if (width <= 1 || height <= 1) return null
        val maxX = (width - 1).toFloat().coerceAtLeast(1f)
        val maxY = (height - 1).toFloat().coerceAtLeast(1f)
        val values = listOf(
            (quad.topLeft.x / maxX).coerceIn(0f, 1f),
            (quad.topLeft.y / maxY).coerceIn(0f, 1f),
            (quad.topRight.x / maxX).coerceIn(0f, 1f),
            (quad.topRight.y / maxY).coerceIn(0f, 1f),
            (quad.bottomRight.x / maxX).coerceIn(0f, 1f),
            (quad.bottomRight.y / maxY).coerceIn(0f, 1f),
            (quad.bottomLeft.x / maxX).coerceIn(0f, 1f),
            (quad.bottomLeft.y / maxY).coerceIn(0f, 1f)
        )
        return values.joinToString(",") { value ->
            String.format(Locale.US, "%.5f", value)
        }
    }

    private fun decodeNormalizedQuad(encoded: String?, width: Int, height: Int): CropQuad? {
        if (encoded.isNullOrBlank()) return null
        if (width <= 1 || height <= 1) return null
        val values = encoded.split(",")
            .mapNotNull { token -> token.trim().toFloatOrNull() }
        if (values.size != 8) return null

        val maxX = (width - 1).toFloat().coerceAtLeast(1f)
        val maxY = (height - 1).toFloat().coerceAtLeast(1f)
        val decoded = CropQuad(
            topLeft = CropPoint(values[0].coerceIn(0f, 1f) * maxX, values[1].coerceIn(0f, 1f) * maxY),
            topRight = CropPoint(values[2].coerceIn(0f, 1f) * maxX, values[3].coerceIn(0f, 1f) * maxY),
            bottomRight = CropPoint(values[4].coerceIn(0f, 1f) * maxX, values[5].coerceIn(0f, 1f) * maxY),
            bottomLeft = CropPoint(values[6].coerceIn(0f, 1f) * maxX, values[7].coerceIn(0f, 1f) * maxY)
        )
        return if (CardCropGeometry.isValidCardBounds(decoded, width, height)) {
            decoded
        } else {
            null
        }
    }

    private fun cleanupObsoleteManagedImageFile(previousPath: String?, keepPaths: Set<String?>) {
        val previous = previousPath?.takeIf { it.isNotBlank() } ?: return
        val normalizedKeepPaths = keepPaths
            .filterNotNull()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        if (previous in normalizedKeepPaths) return
        if (!previous.startsWith(appContext.filesDir.absolutePath)) return

        val candidates = ContactImagePolicy.managedVisualCandidates(previous)
            .ifEmpty { setOf(previous) }
        candidates.forEach { candidate ->
            if (candidate in normalizedKeepPaths) return@forEach
            if (!candidate.startsWith(appContext.filesDir.absolutePath)) return@forEach
            runCatching {
                val file = File(candidate)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    private fun cropBitmapToCardAspect(bitmap: Bitmap, targetAspect: Float = 1.75f): Bitmap {
        if (bitmap.width <= 0 || bitmap.height <= 0) return bitmap
        if (targetAspect <= 0f) return bitmap
        val currentAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        if (kotlin.math.abs(currentAspect - targetAspect) < 0.01f) {
            return bitmap
        }

        return if (currentAspect > targetAspect) {
            val targetWidth = (bitmap.height * targetAspect).roundToInt().coerceAtLeast(1)
            val startX = ((bitmap.width - targetWidth) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(bitmap, startX, 0, targetWidth, bitmap.height)
        } else {
            val targetHeight = (bitmap.width / targetAspect).roundToInt().coerceAtLeast(1)
            val startY = ((bitmap.height - targetHeight) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(bitmap, 0, startY, bitmap.width, targetHeight)
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        if (maxSize <= 0) return bitmap
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return bitmap
        val largest = max(width, height)
        if (largest <= maxSize) return bitmap
        val scale = maxSize.toFloat() / largest.toFloat()
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private suspend fun <T> runSettingsExport(
        successMessage: String,
        action: suspend () -> T
    ): Result<T> {
        if (settingsExportingState.value) {
            return Result.failure(IllegalStateException("An export is already in progress."))
        }
        settingsExportingState.value = true
        settingsStatusState.value = null
        settingsErrorState.value = null
        return try {
            val value = action()
            settingsStatusState.value = successMessage
            Result.success(value)
        } catch (t: Throwable) {
            settingsErrorState.value = t.message ?: "Export failed."
            Result.failure(t)
        } finally {
            settingsExportingState.value = false
        }
    }

    private fun trimOldExportFiles(exportDir: File) {
        val maxFiles = 20
        val files = exportDir.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
        files.drop(maxFiles).forEach { file ->
            runCatching { file.delete() }
        }
    }

    private fun tokenize(query: String): List<String> {
        return query.lowercase().split(Regex("\\s+")).map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun buildMatch(summary: ContactSummary, tokens: List<String>): ContactMatch? {
        val fields = mapOf(
            MatchField.NAME to summary.contact.name,
            MatchField.COMPANY to summary.contact.company,
            MatchField.TITLE to summary.contact.title,
            MatchField.EMAIL to summary.contact.email,
            MatchField.PHONE to summary.contact.phone,
            MatchField.WEBSITE to summary.contact.website,
            MatchField.INDUSTRY to summary.contact.industry,
            MatchField.OCR to summary.contact.rawOcrText,
            MatchField.NOTES to summary.interactionNotes,
            MatchField.LOCATION to summary.interactionLocations,
            MatchField.RELATIONSHIP to summary.interactionRelationships
        )
        val matchedFields = mutableSetOf<MatchField>()
        val matchedTokens = mutableSetOf<String>()
        tokens.forEach { token ->
            fields.forEach { (field, value) ->
                if (!value.isNullOrBlank() && value.contains(token, ignoreCase = true)) {
                    matchedFields.add(field)
                    matchedTokens.add(token)
                }
            }
        }
        if (matchedTokens.isEmpty()) return null
        return ContactMatch(
            summary = summary,
            matchedTokens = matchedTokens.toList(),
            matchedFields = matchedFields.toList(),
            score = matchedTokens.size
        )
    }

    private fun sortMatches(
        matches: List<ContactMatch>,
        sortOption: SortOption,
        hasQuery: Boolean
    ): List<ContactMatch> {
        return if (hasQuery) {
            matches.sortedWith(
                compareByDescending<ContactMatch> { it.score }
                    .thenBy { it.summary.contact.name ?: "" }
            )
        } else {
            when (sortOption) {
                SortOption.RECENT -> matches.sortedByDescending { it.summary.lastInteractionTime ?: 0L }
                SortOption.NAME -> matches.sortedBy { it.summary.contact.name ?: "" }
                SortOption.COMPANY -> matches.sortedBy { it.summary.contact.company ?: "" }
            }
        }
    }

    private fun computeConfidence(
        fields: ReviewFields,
        audit: OcrExtractionAudit? = null
    ): ReviewFieldConfidence {
        val nameConfidence = resolveConfidenceFromAuditOrHeuristic(
            value = fields.name,
            fieldType = OcrFieldType.NAME,
            audit = audit
        ) {
            if (looksLikePersonName(fields.name)) ConfidenceLevel.HIGH else ConfidenceLevel.MEDIUM
        }
        val titleConfidence = resolveConfidenceFromAuditOrHeuristic(
            value = fields.title,
            fieldType = OcrFieldType.TITLE,
            audit = audit
        ) {
            if (looksLikeTitle(fields.title)) ConfidenceLevel.HIGH else ConfidenceLevel.MEDIUM
        }
        val companyConfidence = resolveConfidenceFromAuditOrHeuristic(
            value = fields.company,
            fieldType = OcrFieldType.COMPANY,
            audit = audit
        ) {
            if (looksLikeCompany(fields.company)) ConfidenceLevel.HIGH else ConfidenceLevel.MEDIUM
        }
        val emailConfidence = resolveConfidenceFromAuditOrHeuristic(
            value = fields.email,
            fieldType = OcrFieldType.EMAIL,
            audit = audit
        ) {
            if (Patterns.EMAIL_ADDRESS.matcher(fields.email).matches()) {
                ConfidenceLevel.HIGH
            } else {
                ConfidenceLevel.MEDIUM
            }
        }
        val phoneConfidence = resolveConfidenceFromAuditOrHeuristic(
            value = fields.phone,
            fieldType = OcrFieldType.PHONE,
            audit = audit
        ) {
            val phoneDigits = fields.phone.count { it.isDigit() }
            when {
                phoneDigits >= 10 -> ConfidenceLevel.HIGH
                phoneDigits >= 7 -> ConfidenceLevel.MEDIUM
                else -> ConfidenceLevel.LOW
            }
        }
        val websiteConfidence = resolveConfidenceFromAuditOrHeuristic(
            value = fields.website,
            fieldType = OcrFieldType.WEBSITE,
            audit = audit
        ) {
            if (Patterns.WEB_URL.matcher(fields.website).find() || looksLikeDomain(fields.website)) {
                ConfidenceLevel.HIGH
            } else {
                ConfidenceLevel.MEDIUM
            }
        }
        val industryConfidence = if (fields.industry.isBlank()) {
            ConfidenceLevel.LOW
        } else {
            ConfidenceLevel.MEDIUM
        }
        return ReviewFieldConfidence(
            name = nameConfidence,
            title = titleConfidence,
            company = companyConfidence,
            email = emailConfidence,
            phone = phoneConfidence,
            website = websiteConfidence,
            industry = industryConfidence
        )
    }

    private fun resolveConfidenceFromAuditOrHeuristic(
        value: String,
        fieldType: OcrFieldType,
        audit: OcrExtractionAudit?,
        fallback: () -> ConfidenceLevel
    ): ConfidenceLevel {
        if (value.isBlank()) return ConfidenceLevel.LOW
        val auditConfidence = findAuditConfidence(fieldType, value, audit)
        return if (auditConfidence != null) {
            toConfidenceLevel(auditConfidence)
        } else {
            fallback()
        }
    }

    private fun findAuditConfidence(
        fieldType: OcrFieldType,
        value: String,
        audit: OcrExtractionAudit?
    ): Double? {
        val fieldAudit = audit?.fieldAudits?.get(fieldType) ?: return null
        val normalizedValue = value.trim()
        val selected = fieldAudit.selectedCandidate
        if (selected != null && selected.text.trim().equals(normalizedValue, ignoreCase = true)) {
            return selected.confidence
        }
        return fieldAudit.alternatives.firstOrNull {
            it.text.trim().equals(normalizedValue, ignoreCase = true)
        }?.confidence
    }

    private fun toConfidenceLevel(score: Double): ConfidenceLevel {
        return when {
            score >= 0.75 -> ConfidenceLevel.HIGH
            score >= 0.45 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
    }

    private fun applyCandidateSelection(
        fields: ReviewFields,
        field: OcrFieldType,
        candidateValue: String
    ): ReviewFields {
        return when (field) {
            OcrFieldType.NAME -> fields.copy(name = candidateValue)
            OcrFieldType.TITLE -> fields.copy(title = candidateValue)
            OcrFieldType.COMPANY -> fields.copy(company = candidateValue)
            OcrFieldType.EMAIL -> fields.copy(email = candidateValue)
            OcrFieldType.PHONE -> fields.copy(phone = candidateValue)
            OcrFieldType.WEBSITE -> fields.copy(website = candidateValue)
            OcrFieldType.ADDRESS -> fields.copy(address = candidateValue)
        }
    }

    private fun looksLikePersonName(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.any { it.isDigit() }) return false
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size !in 2..4) return false
        val properCaseWords = words.count { it.firstOrNull()?.isUpperCase() == true }
        val caseRatio = properCaseWords.toFloat() / words.size.toFloat()
        return caseRatio >= 0.6f
    }

    private fun looksLikeTitle(value: String): Boolean {
        val lower = value.lowercase()
        if (lower.isBlank()) return false
        val titleKeywords = listOf(
            "founder", "co-founder", "owner", "principal", "managing partner",
            "general partner", "senior partner", "associate partner",
            "ceo", "cto", "cfo", "cio", "coo", "cmo", "cso", "cro", "cdo", "cpo",
            "chro", "cao", "president", "vice president", "assistant vice president",
            "associate vice president", "vp", "svp", "evp", "avp", "chair", "chairperson",
            "chairman", "chairwoman", "board member", "board director", "chief", "officer",
            "managing director", "executive director", "director", "senior director",
            "associate director", "art director", "creative director",
            "manager", "general manager", "program manager", "project manager",
            "product manager", "portfolio manager", "relationship manager", "account manager",
            "operations manager", "supervisor", "coordinator", "administrator", "head",
            "lead", "team lead", "strategy", "strategist", "planner",
            "engineer", "engineering", "principal engineer", "staff engineer",
            "software engineer", "systems engineer", "network engineer", "security engineer",
            "quality engineer", "qa engineer", "test engineer", "devops engineer",
            "site reliability engineer", "sre", "data engineer", "ml engineer",
            "machine learning engineer", "ai engineer", "developer", "development",
            "architect", "solution architect", "enterprise architect", "cloud architect",
            "designer", "technologist", "asset management technologist", "technician",
            "scientist", "researcher", "analyst", "business analyst", "specialist",
            "consultant", "advisor", "adviser", "associate",
            "sales", "sales manager", "account executive", "representative", "agent",
            "broker", "marketing", "marketing manager", "communications",
            "business development", "customer success", "customer support", "support engineer",
            "recruiter", "talent acquisition", "human resources", "hr manager",
            "accountant", "controller", "auditor", "treasurer",
            "counsel", "attorney", "lawyer", "paralegal",
            "operations", "operator", "product"
        )
        return titleKeywords.any { lower.contains(it) }
    }

    private fun looksLikeCompany(value: String): Boolean {
        val lower = value.lowercase()
        if (lower.isBlank()) return false
        val companyKeywords = listOf(
            "inc", "inc.", "incorporated",
            "llc", "l.l.c", "ltd", "ltd.", "limited",
            "corp", "corporation", "company", "co", "co.",
            "group", "holdings", "ventures", "partners", "associates",
            "solutions", "systems", "technologies", "technology",
            "labs", "studio", "studios", "media", "consulting"
        )
        if (companyKeywords.any { lower.contains(it) }) return true
        return value == value.uppercase() && value.length >= 3
    }

    private fun looksLikeDomain(value: String): Boolean {
        if (value.contains(" ")) return false
        return Regex("\\b([a-z0-9-]+\\.)+[a-z]{2,}\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(value)
    }
}

private const val DUPLICATE_UX_PREFS = "duplicate_merge_ux_prefs"
private const val DUPLICATE_EDUCATION_SEEN_KEY = "duplicate_education_seen_v1"
private const val APP_SETTINGS_PREFS = "app_settings_prefs"
private const val WEBSITE_ENRICHMENT_ENABLED_KEY = "website_enrichment_enabled"
private const val CARD_CROP_VERSION_NONE = 0
private const val CARD_CROP_VERSION_EDGE_TIGHT = 2
private const val CARD_CROP_MARGIN_RATIO = CardImageCropper.DEFAULT_MARGIN_RATIO

private fun ReviewFields.toExtractedData(): ExtractedData {
    val normalizedSelection = IndustryPrefillPolicy.normalizeSelection(
        industry = industry,
        industryCustom = industryCustom
    )
    val normalizedIndustry = normalizedSelection.industry
    val normalizedSource = IndustrySource.normalizeForDraft(
        industry = normalizedIndustry.orEmpty(),
        source = industrySource
    )
    return ExtractedData(
        name = name.ifBlank { null },
        title = title.ifBlank { null },
        company = company.ifBlank { null },
        email = email.ifBlank { null },
        phone = DataExtractor.normalizePhoneNumber(phone.ifBlank { null }),
        website = website.ifBlank { null },
        address = address.ifBlank { null },
        industry = normalizedIndustry,
        industryCustom = normalizedSelection.industryCustom,
        industrySource = normalizedSource
    )
}

private fun Contact.toEditFields(): ContactEditFields {
    return ContactEditFields(
        name = name.orEmpty(),
        title = title.orEmpty(),
        company = company.orEmpty(),
        industry = industry.orEmpty(),
        industryCustom = industryCustom.orEmpty(),
        industrySource = industrySource,
        email = email.orEmpty(),
        phone = phone.orEmpty(),
        website = website.orEmpty()
    )
}



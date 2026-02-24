package com.example.businesscardscanner.data.integration

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import com.example.businesscardscanner.domain.logic.DataExtractor
import com.example.businesscardscanner.domain.model.Contact
import com.example.businesscardscanner.domain.model.industryDisplayLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

enum class ContactsSyncResultType {
    AddedNew,
    UpdatedExisting,
    AlreadyPresent,
    SkippedMissingKey,
    Failed
}

data class ContactsSyncResult(
    val type: ContactsSyncResultType,
    val reason: String? = null,
    val addedFields: List<String> = emptyList(),
    val phoneContactId: Long? = null,
    val phoneContactLookupUri: Uri? = null
)

data class ContactsBulkItemResult(
    val sourceContactId: Long,
    val result: ContactsSyncResult
)

data class ContactsBulkExportSummary(
    val total: Int,
    val processed: Int,
    val added: Int,
    val updated: Int,
    val skippedDuplicates: Int,
    val skippedMissingKey: Int,
    val failed: Int,
    val cancelled: Boolean,
    val itemResults: List<ContactsBulkItemResult> = emptyList()
)

internal data class PreparedIncomingContact(
    val name: String?,
    val title: String?,
    val company: String?,
    val emails: List<String>,
    val emailKeys: Set<String>,
    val phones: List<String>,
    val phoneKeys: Set<String>,
    val websites: List<String>,
    val websiteKeys: Set<String>,
    val addresses: List<String>,
    val addressKeys: Set<String>,
    val note: String?,
    val photoPath: String?,
    val hasAnyShareableData: Boolean,
    val normalizedName: String?,
    val normalizedCompany: String?
)

internal data class ExistingContactSnapshot(
    val contactId: Long,
    val lookupKey: String?,
    val lookupUri: Uri?,
    val rawContactIds: List<Long>,
    val displayName: String?,
    val company: String?,
    val title: String?,
    val emails: List<String>,
    val emailKeys: Set<String>,
    val phones: List<String>,
    val phoneKeys: Set<String>,
    val websites: List<String>,
    val websiteKeys: Set<String>,
    val addresses: List<String>,
    val addressKeys: Set<String>,
    val notes: List<String>,
    val hasPhoto: Boolean,
    val normalizedName: String?,
    val normalizedCompany: String?
)

internal data class FieldAddPlan(
    val addName: String?,
    val addTitle: String?,
    val addCompany: String?,
    val addPhones: List<String>,
    val addEmails: List<String>,
    val addWebsites: List<String>,
    val addAddresses: List<String>,
    val addNote: String?,
    val addPhotoPath: String?
) {
    val isEmpty: Boolean
        get() = addName == null &&
            addTitle == null &&
            addCompany == null &&
            addPhones.isEmpty() &&
            addEmails.isEmpty() &&
            addWebsites.isEmpty() &&
            addAddresses.isEmpty() &&
            addNote == null &&
            addPhotoPath == null

    val addedFieldLabels: List<String>
        get() = buildList {
            if (addName != null) add("name")
            if (addCompany != null) add("company")
            if (addTitle != null) add("title")
            if (addPhones.isNotEmpty()) add("phone")
            if (addEmails.isNotEmpty()) add("email")
            if (addWebsites.isNotEmpty()) add("website")
            if (addAddresses.isNotEmpty()) add("address")
            if (addNote != null) add("notes")
            if (addPhotoPath != null) add("photo")
        }
}

internal object ContactsSyncRules {
    const val SCANNED_NOTE_PREFIX = "Scanned business card"

    private val valueSplitRegex = Regex("[\\n,;|]+")
    private val websiteSchemeRegex = Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE)
    private val twoLevelSuffixes = setOf("co", "com", "org", "net", "gov", "ac", "edu")

    fun prepareIncomingContact(contact: Contact): PreparedIncomingContact {
        val name = contact.name?.trim()?.takeIf { it.isNotBlank() }
        val title = contact.title?.trim()?.takeIf { it.isNotBlank() }
        val company = contact.company?.trim()?.takeIf { it.isNotBlank() }

        val phones = dedupePreservingOrder(splitValues(contact.phone)) { value ->
            phoneMatchKey(value)
        }.map { value ->
            DataExtractor.normalizePhoneNumber(value) ?: value.trim()
        }
        val phoneKeys = phones.mapNotNull { phoneMatchKey(it) }.toSet()

        val emails = dedupePreservingOrder(splitValues(contact.email)) { normalizeEmail(it) }
            .mapNotNull { value ->
                value.trim().takeIf { it.isNotBlank() }
            }
        val emailKeys = emails.mapNotNull { normalizeEmail(it) }.toSet()

        val websites = dedupePreservingOrder(splitValues(contact.website)) { websiteComparisonKey(it) }
            .mapNotNull { normalizeWebsiteForInsert(it) }
        val websiteKeys = websites.mapNotNull { websiteComparisonKey(it) }.toSet()

        val addresses = emptyList<String>()
        val addressKeys = emptySet<String>()

        val note = contact.industryDisplayLabel()
            ?.takeIf { it.isNotBlank() }
            ?.let { "$SCANNED_NOTE_PREFIX: Industry: $it" }
        val photoPath = contact.imagePath?.trim()?.takeIf { it.isNotBlank() && File(it).exists() }

        val hasAnyShareableData = !name.isNullOrBlank() ||
            !title.isNullOrBlank() ||
            !company.isNullOrBlank() ||
            phones.isNotEmpty() ||
            emails.isNotEmpty() ||
            websites.isNotEmpty() ||
            addresses.isNotEmpty() ||
            !note.isNullOrBlank() ||
            !photoPath.isNullOrBlank()

        return PreparedIncomingContact(
            name = name,
            title = title,
            company = company,
            emails = emails,
            emailKeys = emailKeys,
            phones = phones,
            phoneKeys = phoneKeys,
            websites = websites,
            websiteKeys = websiteKeys,
            addresses = addresses,
            addressKeys = addressKeys,
            note = note,
            photoPath = photoPath,
            hasAnyShareableData = hasAnyShareableData,
            normalizedName = normalizeTextToken(name),
            normalizedCompany = normalizeTextToken(company)
        )
    }

    fun fieldLabelsForNewContact(incoming: PreparedIncomingContact): List<String> {
        return buildList {
            if (!incoming.name.isNullOrBlank()) add("name")
            if (!incoming.company.isNullOrBlank()) add("company")
            if (!incoming.title.isNullOrBlank()) add("title")
            if (incoming.phones.isNotEmpty()) add("phone")
            if (incoming.emails.isNotEmpty()) add("email")
            if (incoming.websites.isNotEmpty()) add("website")
            if (incoming.addresses.isNotEmpty()) add("address")
            if (!incoming.note.isNullOrBlank()) add("notes")
            if (!incoming.photoPath.isNullOrBlank()) add("photo")
        }
    }

    fun chooseMatchContactId(
        incoming: PreparedIncomingContact,
        candidates: List<ExistingContactSnapshot>
    ): Long? {
        if (candidates.isEmpty()) return null

        if (incoming.emailKeys.isNotEmpty() || incoming.phoneKeys.isNotEmpty()) {
            val emailMatches = if (incoming.emailKeys.isNotEmpty()) {
                candidates.filter { candidate -> candidate.emailKeys.any { it in incoming.emailKeys } }
            } else {
                emptyList()
            }
            val phoneMatches = if (incoming.phoneKeys.isNotEmpty()) {
                candidates.filter { candidate -> candidate.phoneKeys.any { it in incoming.phoneKeys } }
            } else {
                emptyList()
            }

            if (incoming.emailKeys.isNotEmpty() && incoming.phoneKeys.isNotEmpty()) {
                val strongest = candidates.filter { candidate ->
                    candidate.emailKeys.any { it in incoming.emailKeys } &&
                        candidate.phoneKeys.any { it in incoming.phoneKeys }
                }
                if (strongest.isNotEmpty()) {
                    return strongest.minByOrNull { it.contactId }?.contactId
                }
            }
            if (emailMatches.isNotEmpty()) {
                return emailMatches.minByOrNull { it.contactId }?.contactId
            }
            if (phoneMatches.isNotEmpty()) {
                return phoneMatches.minByOrNull { it.contactId }?.contactId
            }
            return null
        }

        val normalizedName = incoming.normalizedName
        val normalizedCompany = incoming.normalizedCompany
        if (!normalizedName.isNullOrBlank() && !normalizedCompany.isNullOrBlank()) {
            return candidates
                .filter { candidate ->
                    candidate.normalizedName == normalizedName &&
                        candidate.normalizedCompany == normalizedCompany
                }
                .minByOrNull { it.contactId }
                ?.contactId
        }

        return null
    }

    fun buildFieldAddPlan(
        incoming: PreparedIncomingContact,
        existing: ExistingContactSnapshot
    ): FieldAddPlan {
        val addName = if (existing.displayName.isNullOrBlank()) incoming.name else null
        val addCompany = if (existing.company.isNullOrBlank()) incoming.company else null
        val addTitle = if (existing.title.isNullOrBlank()) incoming.title else null

        val addPhones = incoming.phones.filter { phone ->
            val key = phoneMatchKey(phone) ?: return@filter false
            key !in existing.phoneKeys
        }
        val addEmails = incoming.emails.filter { email ->
            val key = normalizeEmail(email) ?: return@filter false
            key !in existing.emailKeys
        }
        val addWebsites = incoming.websites.filter { website ->
            val key = websiteComparisonKey(website) ?: return@filter false
            key !in existing.websiteKeys
        }
        val addAddresses = incoming.addresses.filter { address ->
            val key = normalizeAddress(address) ?: return@filter false
            key !in existing.addressKeys
        }

        val hasExistingScannedNote = existing.notes.any { note ->
            note.contains(SCANNED_NOTE_PREFIX, ignoreCase = true)
        }
        val addNote = if (!incoming.note.isNullOrBlank() && !hasExistingScannedNote) {
            incoming.note
        } else {
            null
        }
        val addPhotoPath = if (!incoming.photoPath.isNullOrBlank() && !existing.hasPhoto) {
            incoming.photoPath
        } else {
            null
        }

        return FieldAddPlan(
            addName = addName,
            addTitle = addTitle,
            addCompany = addCompany,
            addPhones = addPhones,
            addEmails = addEmails,
            addWebsites = addWebsites,
            addAddresses = addAddresses,
            addNote = addNote,
            addPhotoPath = addPhotoPath
        )
    }

    fun normalizeEmail(value: String?): String? {
        return value?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotBlank() }
    }

    fun phoneDigits(value: String?): String {
        return value.orEmpty().filter { it.isDigit() }
    }

    fun phoneMatchKey(value: String?): String? {
        val digits = phoneDigits(value)
        if (digits.length < 7) return null
        return if (isNanp(digits)) {
            "nanp:${last10Nanp(digits)}"
        } else {
            "full:$digits"
        }
    }

    fun normalizeAddress(value: String?): String? {
        val normalized = value
            ?.lowercase(Locale.US)
            ?.replace(Regex("[^a-z0-9]+"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
        return normalized?.takeIf { it.isNotBlank() }
    }
    fun websiteComparisonKey(value: String?): String? {
        val normalizedUrl = normalizeWebsiteForInsert(value) ?: return null
        val host = parseHost(normalizedUrl)
        val registeredDomain = host?.let { registeredDomain(it) }
        return if (!registeredDomain.isNullOrBlank()) {
            "domain:${registeredDomain.lowercase(Locale.US)}"
        } else {
            "url:${normalizedUrl.lowercase(Locale.US)}"
        }
    }

    fun normalizeWebsiteForInsert(value: String?): String? {
        val trimmed = value?.trim()?.trimEnd('/', ' ', '\t', '\n') ?: return null
        if (trimmed.isBlank()) return null
        return if (websiteSchemeRegex.containsMatchIn(trimmed)) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    fun exportPayloadHash(contact: Contact): String {
        val prepared = prepareIncomingContact(contact)
        val payload = buildString {
            append(prepared.normalizedName.orEmpty())
            append("|")
            append(normalizeTextToken(prepared.title).orEmpty())
            append("|")
            append(prepared.normalizedCompany.orEmpty())
            append("|")
            append(prepared.emailKeys.sorted().joinToString(","))
            append("|")
            append(prepared.phoneKeys.sorted().joinToString(","))
            append("|")
            append(prepared.websiteKeys.sorted().joinToString(","))
            append("|")
            append(prepared.addressKeys.sorted().joinToString(","))
            append("|")
            append(normalizeTextToken(prepared.note).orEmpty())
            append("|")
            append(if (prepared.photoPath.isNullOrBlank()) "no_photo" else "has_photo")
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun shouldIncludeInBulk(contact: Contact): Boolean {
        val incoming = prepareIncomingContact(contact)
        if (!incoming.hasAnyShareableData) {
            return false
        }
        val status = parseResultType(contact.phoneExportStatus)
        if (status == null || status == ContactsSyncResultType.Failed || status == ContactsSyncResultType.SkippedMissingKey) {
            return true
        }
        return exportPayloadHash(contact) != contact.phoneExportPayloadHash
    }

    fun parseResultType(value: String?): ContactsSyncResultType? {
        if (value.isNullOrBlank()) return null
        return ContactsSyncResultType.entries.firstOrNull { enumValue ->
            enumValue.name.equals(value, ignoreCase = true)
        }
    }

    fun summarizeBulkResults(
        total: Int,
        processed: Int,
        cancelled: Boolean,
        results: List<ContactsSyncResultType>
    ): ContactsBulkExportSummary {
        var added = 0
        var updated = 0
        var skippedDuplicates = 0
        var skippedMissingKey = 0
        var failed = 0

        results.forEach { result ->
            when (result) {
                ContactsSyncResultType.AddedNew -> added += 1
                ContactsSyncResultType.UpdatedExisting -> updated += 1
                ContactsSyncResultType.AlreadyPresent -> skippedDuplicates += 1
                ContactsSyncResultType.SkippedMissingKey -> skippedMissingKey += 1
                ContactsSyncResultType.Failed -> failed += 1
            }
        }

        return ContactsBulkExportSummary(
            total = total,
            processed = processed,
            added = added,
            updated = updated,
            skippedDuplicates = skippedDuplicates,
            skippedMissingKey = skippedMissingKey,
            failed = failed,
            cancelled = cancelled
        )
    }

    private fun splitValues(value: String?): List<String> {
        return value
            ?.split(valueSplitRegex)
            ?.map { token -> token.trim() }
            ?.filter { token -> token.isNotBlank() }
            .orEmpty()
    }

    private fun dedupePreservingOrder(
        values: List<String>,
        keyOf: (String) -> String?
    ): List<String> {
        val seen = linkedSetOf<String>()
        val output = mutableListOf<String>()
        values.forEach { value ->
            val key = keyOf(value) ?: return@forEach
            if (key in seen) return@forEach
            seen += key
            output += value.trim()
        }
        return output
    }

    private fun normalizeTextToken(value: String?): String? {
        val normalized = value
            ?.lowercase(Locale.US)
            ?.replace(Regex("[^a-z0-9]+"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
        return normalized?.takeIf { it.isNotBlank() }
    }

    private fun isNanp(digits: String): Boolean {
        return (digits.length == 10) || (digits.length == 11 && digits.startsWith("1"))
    }

    private fun last10Nanp(digits: String): String {
        return if (digits.length == 11 && digits.startsWith("1")) {
            digits.drop(1)
        } else {
            digits.takeLast(10)
        }
    }

    private fun parseHost(url: String): String? {
        return runCatching {
            java.net.URI(url).host
        }.getOrNull()
            ?.trim()
            ?.trimStart('.')
            ?.lowercase(Locale.US)
            ?.removePrefix("www.")
            ?.takeIf { it.isNotBlank() }
    }

    private fun registeredDomain(host: String): String {
        val parts = host.split(".").filter { it.isNotBlank() }
        if (parts.size <= 2) return host
        val last = parts.last()
        val second = parts[parts.size - 2]
        return if (last.length == 2 && second in twoLevelSuffixes && parts.size >= 3) {
            parts.takeLast(3).joinToString(".")
        } else {
            parts.takeLast(2).joinToString(".")
        }
    }
}

class ContactsSyncEngine(
    private val appContext: Context
) {
    private val resolver = appContext.contentResolver

    suspend fun prepareExportSummary(contact: Contact): ContactsSyncResult = withContext(Dispatchers.IO) {
        runCatching {
            prepareExportSummaryInternal(contact)
        }.getOrElse { error ->
            failureResult(error)
        }
    }

    suspend fun prepareBulkExportSummary(contacts: List<Contact>): ContactsBulkExportSummary = withContext(Dispatchers.IO) {
        val results = contacts.map { contact ->
            ContactsBulkItemResult(
                sourceContactId = contact.id,
                result = prepareExportSummaryInternal(contact)
            )
        }
        summarize(
            total = contacts.size,
            processed = contacts.size,
            cancelled = false,
            results = results
        )
    }

    suspend fun applyExport(contact: Contact): ContactsSyncResult = withContext(Dispatchers.IO) {
        runCatching {
            applyExportInternal(contact)
        }.getOrElse { error ->
            failureResult(error)
        }
    }

    suspend fun applyBulkExport(
        contacts: List<Contact>,
        shouldCancel: () -> Boolean = { false },
        onProgress: suspend (processed: Int, total: Int, contact: Contact, result: ContactsSyncResult) -> Unit = { _, _, _, _ -> }
    ): ContactsBulkExportSummary = withContext(Dispatchers.IO) {
        val itemResults = mutableListOf<ContactsBulkItemResult>()
        contacts.forEachIndexed { index, contact ->
            if (shouldCancel()) {
                return@withContext summarize(
                    total = contacts.size,
                    processed = index,
                    cancelled = true,
                    results = itemResults
                )
            }
            val result = runCatching {
                applyExportInternal(contact)
            }.getOrElse { error ->
                failureResult(error)
            }
            itemResults += ContactsBulkItemResult(
                sourceContactId = contact.id,
                result = result
            )
            onProgress(index + 1, contacts.size, contact, result)
        }
        summarize(
            total = contacts.size,
            processed = contacts.size,
            cancelled = false,
            results = itemResults
        )
    }

    private fun prepareExportSummaryInternal(contact: Contact): ContactsSyncResult {
        val incoming = ContactsSyncRules.prepareIncomingContact(contact)
        if (!incoming.hasAnyShareableData) {
            return ContactsSyncResult(
                type = ContactsSyncResultType.SkippedMissingKey,
                reason = "No contact details available to share."
            )
        }

        val matchId = findMatchContactId(incoming)
        if (matchId == null) {
            return ContactsSyncResult(
                type = ContactsSyncResultType.AddedNew,
                addedFields = ContactsSyncRules.fieldLabelsForNewContact(incoming)
            )
        }

        val existing = loadExistingContactSnapshot(matchId)
            ?: return ContactsSyncResult(
                type = ContactsSyncResultType.AddedNew,
                addedFields = ContactsSyncRules.fieldLabelsForNewContact(incoming)
            )

        val plan = ContactsSyncRules.buildFieldAddPlan(incoming, existing)
        if (plan.isEmpty) {
            return ContactsSyncResult(
                type = ContactsSyncResultType.AlreadyPresent,
                phoneContactId = existing.contactId,
                phoneContactLookupUri = existing.lookupUri
            )
        }
        if (existing.rawContactIds.isEmpty()) {
            return ContactsSyncResult(
                type = ContactsSyncResultType.Failed,
                reason = "Could not update the matched phone contact.",
                phoneContactId = existing.contactId,
                phoneContactLookupUri = existing.lookupUri
            )
        }

        return ContactsSyncResult(
            type = ContactsSyncResultType.UpdatedExisting,
            addedFields = plan.addedFieldLabels,
            phoneContactId = existing.contactId,
            phoneContactLookupUri = existing.lookupUri
        )
    }

    private fun applyExportInternal(contact: Contact): ContactsSyncResult {
        val incoming = ContactsSyncRules.prepareIncomingContact(contact)
        if (!incoming.hasAnyShareableData) {
            return ContactsSyncResult(
                type = ContactsSyncResultType.SkippedMissingKey,
                reason = "No contact details available to share."
            )
        }

        val matchId = findMatchContactId(incoming)
        if (matchId == null) {
            return addNewContact(incoming)
        }

        val existing = loadExistingContactSnapshot(matchId) ?: return addNewContact(incoming)
        val plan = ContactsSyncRules.buildFieldAddPlan(incoming, existing)
        if (plan.isEmpty) {
            return ContactsSyncResult(
                type = ContactsSyncResultType.AlreadyPresent,
                phoneContactId = existing.contactId,
                phoneContactLookupUri = existing.lookupUri
            )
        }
        val rawContactId = existing.rawContactIds.firstOrNull()
            ?: return ContactsSyncResult(
                type = ContactsSyncResultType.Failed,
                reason = "Could not update the matched phone contact.",
                phoneContactId = existing.contactId,
                phoneContactLookupUri = existing.lookupUri
            )

        val (operations, addedLabels, photoLoadFailed) = buildUpdateOperations(rawContactId, plan)
        if (operations.isEmpty()) {
            return if (photoLoadFailed) {
                ContactsSyncResult(
                    type = ContactsSyncResultType.Failed,
                    reason = "Could not read the card image for contact photo.",
                    phoneContactId = existing.contactId,
                    phoneContactLookupUri = existing.lookupUri
                )
            } else {
                ContactsSyncResult(
                    type = ContactsSyncResultType.AlreadyPresent,
                    phoneContactId = existing.contactId,
                    phoneContactLookupUri = existing.lookupUri
                )
            }
        }

        resolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(operations))
        return ContactsSyncResult(
            type = ContactsSyncResultType.UpdatedExisting,
            addedFields = addedLabels,
            phoneContactId = existing.contactId,
            phoneContactLookupUri = existing.lookupUri
        )
    }

    private fun addNewContact(incoming: PreparedIncomingContact): ContactsSyncResult {
        val (operations, addedLabels) = buildNewContactOperations(incoming)
        if (operations.size <= 1) {
            return ContactsSyncResult(
                type = ContactsSyncResultType.SkippedMissingKey,
                reason = "No contact details available to share."
            )
        }
        val results = resolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(operations))
        val rawContactUri = results.firstOrNull()?.uri
        val rawContactId = rawContactUri?.let { runCatching { ContentUris.parseId(it) }.getOrNull() }
        val contactId = rawContactId?.let { queryContactIdForRawContactId(it) }
        val lookupUri = contactId?.let { queryLookupUri(it) }
        return ContactsSyncResult(
            type = ContactsSyncResultType.AddedNew,
            addedFields = addedLabels,
            phoneContactId = contactId,
            phoneContactLookupUri = lookupUri
        )
    }
    private fun buildNewContactOperations(
        incoming: PreparedIncomingContact
    ): Pair<List<ContentProviderOperation>, List<String>> {
        val operations = mutableListOf<ContentProviderOperation>()
        val addedLabels = mutableListOf<String>()
        val rawContactInsertIndex = 0

        operations += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            .build()

        incoming.name?.let { name ->
            operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                )
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build()
            addedLabels += "name"
        }

        if (!incoming.company.isNullOrBlank() || !incoming.title.isNullOrBlank()) {
            operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                )
                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, incoming.company)
                .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, incoming.title)
                .withValue(
                    ContactsContract.CommonDataKinds.Organization.TYPE,
                    ContactsContract.CommonDataKinds.Organization.TYPE_WORK
                )
                .build()
            if (!incoming.company.isNullOrBlank()) {
                addedLabels += "company"
            }
            if (!incoming.title.isNullOrBlank()) {
                addedLabels += "title"
            }
        }

        if (incoming.phones.isNotEmpty()) {
            incoming.phones.forEach { phone ->
                operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_WORK
                    )
                    .build()
            }
            addedLabels += "phone"
        }

        if (incoming.emails.isNotEmpty()) {
            incoming.emails.forEach { email ->
                operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                    .withValue(
                        ContactsContract.CommonDataKinds.Email.TYPE,
                        ContactsContract.CommonDataKinds.Email.TYPE_WORK
                    )
                    .build()
            }
            addedLabels += "email"
        }

        if (incoming.websites.isNotEmpty()) {
            incoming.websites.forEach { website ->
                operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Website.URL, website)
                    .withValue(
                        ContactsContract.CommonDataKinds.Website.TYPE,
                        ContactsContract.CommonDataKinds.Website.TYPE_WORK
                    )
                    .build()
            }
            addedLabels += "website"
        }

        if (incoming.addresses.isNotEmpty()) {
            incoming.addresses.forEach { address ->
                operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, address)
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
                        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK
                    )
                    .build()
            }
            addedLabels += "address"
        }

        incoming.note?.let { note ->
            operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
                )
                .withValue(ContactsContract.CommonDataKinds.Note.NOTE, note)
                .build()
            addedLabels += "notes"
        }

        incoming.photoPath?.let { photoPath ->
            val photoBytes = loadPhotoBytes(photoPath)
            if (photoBytes != null) {
                operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                    .build()
                addedLabels += "photo"
            }
        }

        return operations to addedLabels.distinct()
    }

    private fun buildUpdateOperations(
        rawContactId: Long,
        plan: FieldAddPlan
    ): Triple<List<ContentProviderOperation>, List<String>, Boolean> {
        val operations = mutableListOf<ContentProviderOperation>()
        val addedLabels = mutableListOf<String>()
        var photoLoadFailed = false

        plan.addName?.let { name ->
            operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                )
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build()
            addedLabels += "name"
        }

        if (plan.addCompany != null || plan.addTitle != null) {
            operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                )
                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, plan.addCompany)
                .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, plan.addTitle)
                .withValue(
                    ContactsContract.CommonDataKinds.Organization.TYPE,
                    ContactsContract.CommonDataKinds.Organization.TYPE_WORK
                )
                .build()
            if (plan.addCompany != null) {
                addedLabels += "company"
            }
            if (plan.addTitle != null) {
                addedLabels += "title"
            }
        }

        if (plan.addPhones.isNotEmpty()) {
            plan.addPhones.forEach { phone ->
                operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_WORK
                    )
                    .build()
            }
            addedLabels += "phone"
        }

        if (plan.addEmails.isNotEmpty()) {
            plan.addEmails.forEach { email ->
                operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                    .withValue(
                        ContactsContract.CommonDataKinds.Email.TYPE,
                        ContactsContract.CommonDataKinds.Email.TYPE_WORK
                    )
                    .build()
            }
            addedLabels += "email"
        }

        if (plan.addWebsites.isNotEmpty()) {
            plan.addWebsites.forEach { website ->
                operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Website.URL, website)
                    .withValue(
                        ContactsContract.CommonDataKinds.Website.TYPE,
                        ContactsContract.CommonDataKinds.Website.TYPE_WORK
                    )
                    .build()
            }
            addedLabels += "website"
        }

        if (plan.addAddresses.isNotEmpty()) {
            plan.addAddresses.forEach { address ->
                operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, address)
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
                        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK
                    )
                    .build()
            }
            addedLabels += "address"
        }

        plan.addNote?.let { note ->
            operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
                )
                .withValue(ContactsContract.CommonDataKinds.Note.NOTE, note)
                .build()
            addedLabels += "notes"
        }

        plan.addPhotoPath?.let { photoPath ->
            val photoBytes = loadPhotoBytes(photoPath)
            if (photoBytes != null) {
                operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                    .build()
                addedLabels += "photo"
            } else {
                photoLoadFailed = true
            }
        }

        return Triple(operations, addedLabels.distinct(), photoLoadFailed)
    }

    private fun findMatchContactId(incoming: PreparedIncomingContact): Long? {
        if (incoming.emailKeys.isNotEmpty() || incoming.phoneKeys.isNotEmpty()) {
            val allSnapshots = queryAllContactSnapshots()
            val emailMatches = if (incoming.emailKeys.isNotEmpty()) {
                allSnapshots.filter { snapshot ->
                    snapshot.emailKeys.any { it in incoming.emailKeys }
                }
            } else {
                emptyList()
            }
            val phoneMatches = if (incoming.phoneKeys.isNotEmpty()) {
                allSnapshots.filter { snapshot ->
                    snapshot.phoneKeys.any { it in incoming.phoneKeys }
                }
            } else {
                emptyList()
            }
            val candidates = (emailMatches + phoneMatches)
                .distinctBy { it.contactId }
            return ContactsSyncRules.chooseMatchContactId(incoming, candidates)
        }

        val normalizedName = incoming.normalizedName
        val normalizedCompany = incoming.normalizedCompany
        if (!normalizedName.isNullOrBlank() && !normalizedCompany.isNullOrBlank()) {
            val candidates = queryAllContactSnapshots().filter { snapshot ->
                snapshot.normalizedName == normalizedName &&
                    snapshot.normalizedCompany == normalizedCompany
            }
            return ContactsSyncRules.chooseMatchContactId(incoming, candidates)
        }
        return null
    }

    private fun queryAllContactSnapshots(): List<ExistingContactSnapshot> {
        val contactsMap = linkedMapOf<Long, ExistingContactSnapshotBuilder>()

        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.PHOTO_ID,
                ContactsContract.Contacts.PHOTO_URI
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLongValue(ContactsContract.Contacts._ID)
                val lookupKey = cursor.getStringValue(ContactsContract.Contacts.LOOKUP_KEY)
                val displayName = cursor.getStringValue(ContactsContract.Contacts.DISPLAY_NAME)
                val hasPhoto = (cursor.getLongValue(ContactsContract.Contacts.PHOTO_ID) > 0L) ||
                    !cursor.getStringValue(ContactsContract.Contacts.PHOTO_URI).isNullOrBlank()
                contactsMap[id] = ExistingContactSnapshotBuilder(
                    contactId = id,
                    lookupKey = lookupKey,
                    displayName = displayName,
                    hasPhoto = hasPhoto
                )
            }
        }

        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.CONTACT_ID,
                ContactsContract.RawContacts.DELETED
            ),
            "${ContactsContract.RawContacts.DELETED}=0",
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val rawId = cursor.getLongValue(ContactsContract.RawContacts._ID)
                val contactId = cursor.getLongValue(ContactsContract.RawContacts.CONTACT_ID)
                val builder = contactsMap[contactId] ?: continue
                builder.rawContactIds += rawId
            }
        }

        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA4,
                ContactsContract.Data.DATA15
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val contactId = cursor.getLongValue(ContactsContract.Data.CONTACT_ID)
                val builder = contactsMap[contactId] ?: continue
                val mime = cursor.getStringValue(ContactsContract.Data.MIMETYPE).orEmpty()
                val data1 = cursor.getStringValue(ContactsContract.Data.DATA1)
                val data4 = cursor.getStringValue(ContactsContract.Data.DATA4)
                when (mime) {
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        if (builder.displayName.isNullOrBlank() && !data1.isNullOrBlank()) {
                            builder.displayName = data1
                        }
                    }
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                        if (builder.company.isNullOrBlank() && !data1.isNullOrBlank()) {
                            builder.company = data1
                        }
                        if (builder.title.isNullOrBlank() && !data4.isNullOrBlank()) {
                            builder.title = data4
                        }
                    }
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        if (!data1.isNullOrBlank()) {
                            builder.emails += data1
                        }
                    }
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        if (!data1.isNullOrBlank()) {
                            builder.phones += data1
                        }
                    }
                    ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE -> {
                        if (!data1.isNullOrBlank()) {
                            builder.websites += data1
                        }
                    }
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                        if (!data1.isNullOrBlank()) {
                            builder.addresses += data1
                        }
                    }
                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                        if (!data1.isNullOrBlank()) {
                            builder.notes += data1
                        }
                    }
                    ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE -> {
                        if (cursor.getBlobValue(ContactsContract.Data.DATA15) != null) {
                            builder.hasPhoto = true
                        }
                    }
                }
            }
        }

        return contactsMap.values.map { builder -> builder.build() }
    }

    private fun loadExistingContactSnapshot(contactId: Long): ExistingContactSnapshot? {
        return queryAllContactSnapshots().firstOrNull { it.contactId == contactId }
    }

    private fun queryContactIdForRawContactId(rawContactId: Long): Long? {
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.CONTACT_ID),
            "${ContactsContract.RawContacts._ID}=?",
            arrayOf(rawContactId.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLongValue(ContactsContract.RawContacts.CONTACT_ID)
            }
        }
        return null
    }

    private fun queryLookupUri(contactId: Long): Uri? {
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.LOOKUP_KEY),
            "${ContactsContract.Contacts._ID}=?",
            arrayOf(contactId.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val lookup = cursor.getStringValue(ContactsContract.Contacts.LOOKUP_KEY)
                if (!lookup.isNullOrBlank()) {
                    return ContactsContract.Contacts.getLookupUri(contactId, lookup)
                }
            }
        }
        return ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
    }

    private fun summarize(
        total: Int,
        processed: Int,
        cancelled: Boolean,
        results: List<ContactsBulkItemResult>
    ): ContactsBulkExportSummary {
        val summary = ContactsSyncRules.summarizeBulkResults(
            total = total,
            processed = processed,
            cancelled = cancelled,
            results = results.map { it.result.type }
        )
        return summary.copy(itemResults = results)
    }

    private fun failureResult(error: Throwable): ContactsSyncResult {
        return ContactsSyncResult(
            type = ContactsSyncResultType.Failed,
            reason = when (error) {
                is SecurityException -> "Contacts permission required."
                else -> error.message?.takeIf { it.isNotBlank() } ?: "Could not add to phone contacts."
            }
        )
    }

    private fun loadPhotoBytes(path: String): ByteArray? {
        return runCatching {
            val file = File(path)
            if (!file.exists() || !file.isFile) return null
            if (file.length() > MAX_PHOTO_BYTES) return null
            file.readBytes()
        }.getOrNull()
    }

    companion object {
        private const val MAX_PHOTO_BYTES = 4L * 1024L * 1024L

        fun exportPayloadHash(contact: Contact): String = ContactsSyncRules.exportPayloadHash(contact)

        fun shouldIncludeInBulk(contact: Contact): Boolean = ContactsSyncRules.shouldIncludeInBulk(contact)

        fun hasAnyExportableData(contact: Contact): Boolean =
            ContactsSyncRules.prepareIncomingContact(contact).hasAnyShareableData
    }
}

private data class ExistingContactSnapshotBuilder(
    val contactId: Long,
    val lookupKey: String?,
    var displayName: String?,
    var hasPhoto: Boolean
) {
    val rawContactIds: MutableList<Long> = mutableListOf()
    var company: String? = null
    var title: String? = null
    val emails: MutableList<String> = mutableListOf()
    val phones: MutableList<String> = mutableListOf()
    val websites: MutableList<String> = mutableListOf()
    val addresses: MutableList<String> = mutableListOf()
    val notes: MutableList<String> = mutableListOf()

    fun build(): ExistingContactSnapshot {
        val emailKeys = emails.mapNotNull { ContactsSyncRules.normalizeEmail(it) }.toSet()
        val phoneKeys = phones.mapNotNull { ContactsSyncRules.phoneMatchKey(it) }.toSet()
        val websiteKeys = websites.mapNotNull { ContactsSyncRules.websiteComparisonKey(it) }.toSet()
        val addressKeys = addresses.mapNotNull { ContactsSyncRules.normalizeAddress(it) }.toSet()
        return ExistingContactSnapshot(
            contactId = contactId,
            lookupKey = lookupKey,
            lookupUri = if (!lookupKey.isNullOrBlank()) {
                ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
            } else {
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            },
            rawContactIds = rawContactIds.distinct(),
            displayName = displayName?.trim()?.takeIf { it.isNotBlank() },
            company = company?.trim()?.takeIf { it.isNotBlank() },
            title = title?.trim()?.takeIf { it.isNotBlank() },
            emails = emails.distinct(),
            emailKeys = emailKeys,
            phones = phones.distinct(),
            phoneKeys = phoneKeys,
            websites = websites.distinct(),
            websiteKeys = websiteKeys,
            addresses = addresses.distinct(),
            addressKeys = addressKeys,
            notes = notes.distinct(),
            hasPhoto = hasPhoto,
            normalizedName = normalizeForMatch(displayName),
            normalizedCompany = normalizeForMatch(company)
        )
    }

    private fun normalizeForMatch(value: String?): String? {
        return value
            ?.lowercase(Locale.US)
            ?.replace(Regex("[^a-z0-9]+"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}

private fun Cursor.getStringValue(columnName: String): String? {
    val index = getColumnIndex(columnName)
    if (index < 0 || isNull(index)) return null
    return getString(index)
}

private fun Cursor.getLongValue(columnName: String): Long {
    val index = getColumnIndex(columnName)
    if (index < 0 || isNull(index)) return 0L
    return getLong(index)
}

private fun Cursor.getBlobValue(columnName: String): ByteArray? {
    val index = getColumnIndex(columnName)
    if (index < 0 || isNull(index)) return null
    return getBlob(index)
}

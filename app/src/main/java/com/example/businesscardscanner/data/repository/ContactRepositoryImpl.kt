package com.example.businesscardscanner.data.repository

import androidx.room.withTransaction
import com.example.businesscardscanner.data.local.AppDatabase
import com.example.businesscardscanner.data.local.CompanyIndustryEntity
import com.example.businesscardscanner.data.local.ContactEntity
import com.example.businesscardscanner.data.local.ContactSummaryEntity
import com.example.businesscardscanner.data.local.InteractionEntity
import com.example.businesscardscanner.domain.logic.ContactImagePolicy
import com.example.businesscardscanner.domain.logic.IndustryCatalog
import com.example.businesscardscanner.domain.model.Contact
import com.example.businesscardscanner.domain.model.ContactSummary
import com.example.businesscardscanner.domain.model.Interaction
import com.example.businesscardscanner.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException

class ContactRepositoryImpl(
    private val database: AppDatabase
) : ContactRepository {
    override fun contactSummaries(): Flow<List<ContactSummary>> {
        return database.contactDao().getContactSummaries().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun contactById(id: Long): Flow<Contact?> {
        return database.contactDao().getById(id).map { it?.toDomain() }
    }

    override suspend fun contactByIdOnce(id: Long): Contact? {
        return database.contactDao().getByIdOnce(id)?.toDomain()
    }

    override suspend fun allContacts(): List<Contact> {
        return database.contactDao().getAllOnce().map { it.toDomain() }
    }

    override suspend fun allInteractions(): List<Interaction> {
        return database.interactionDao().getAllOnce().map { it.toDomain() }
    }

    override fun interactionsForContact(contactId: Long): Flow<List<Interaction>> {
        return database.interactionDao().interactionsForContact(contactId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun insertContact(contact: Contact): Long {
        return database.contactDao().insert(contact.toEntity())
    }

    override suspend fun updateContact(contact: Contact) {
        database.contactDao().update(contact.toEntity())
    }

    override suspend fun deleteContact(contactId: Long) {
        val contact = database.contactDao().getByIdOnce(contactId)
            ?: throw IllegalStateException("Contact does not exist.")
        val fileCandidates = resolveFileCandidates(
            imagePath = contact.imagePath,
            rawImagePath = contact.rawImagePath
        ).filter { it.exists() }
        val fileBackups = snapshotFilesForRollback(fileCandidates)

        try {
            deleteFilesWithRollback(fileBackups)

            val companyKey = IndustryCatalog.normalizeCompany(contact.company)
            database.withTransaction {
                val deletedRows = database.contactDao().deleteById(contactId)
                if (deletedRows == 0) {
                    throw IllegalStateException("Contact does not exist.")
                }
                val lingeringInteractions = database.interactionDao().countForContact(contactId)
                if (lingeringInteractions > 0) {
                    throw IllegalStateException("Failed to remove related interactions.")
                }

                if (!companyKey.isNullOrBlank()) {
                    val hasRemainingCompanyUsage = database.contactDao()
                        .getAllCompanyNames()
                        .any { name ->
                            IndustryCatalog.normalizeCompany(name) == companyKey
                        }
                    if (!hasRemainingCompanyUsage) {
                        database.companyIndustryDao().deleteByCompany(companyKey)
                    }
                }
            }
        } catch (failure: Throwable) {
            val restoreFailure = runCatching {
                restoreFilesFromBackups(fileBackups)
            }.exceptionOrNull()
            if (restoreFailure != null) {
                failure.addSuppressed(restoreFailure)
            }
            throw failure
        }
    }

    override suspend fun insertInteraction(interaction: Interaction): Long {
        return database.interactionDao().insert(interaction.toEntity())
    }

    override suspend fun findDuplicates(email: String?, phone: String?): List<Contact> {
        return database.contactDao().findDuplicates(email, phone).map { it.toDomain() }
    }

    override suspend fun getCompanyIndustry(normalizedCompany: String): String? {
        return database.companyIndustryDao().getIndustry(normalizedCompany)
    }

    override suspend fun upsertCompanyIndustry(normalizedCompany: String, industry: String) {
        database.companyIndustryDao().upsert(
            CompanyIndustryEntity(
                companyNormalized = normalizedCompany,
                industry = industry
            )
        )
    }
}

private data class FileBackup(
    val file: File,
    val bytes: ByteArray
)

private fun resolveFileCandidates(imagePath: String?, rawImagePath: String?): List<File> {
    val primary = imagePath?.takeIf { it.isNotBlank() }?.let { File(it) }
    val raw = rawImagePath?.takeIf { it.isNotBlank() }?.let { File(it) }
    if (primary == null && raw == null) return emptyList()

    val candidates = linkedSetOf<String>()
    primary?.let { file ->
        val managedCandidates = ContactImagePolicy.managedVisualCandidates(file.absolutePath)
        if (managedCandidates.isNotEmpty()) {
            candidates += managedCandidates
        } else {
            candidates += file.absolutePath
        }
    }
    raw?.let { file ->
        val managedCandidates = ContactImagePolicy.managedVisualCandidates(file.absolutePath)
        if (managedCandidates.isNotEmpty()) {
            candidates += managedCandidates
        } else {
            candidates += file.absolutePath
        }
    }

    return candidates
        .flatMap { path ->
            val file = File(path)
            val parent = file.parentFile
            val baseName = file.nameWithoutExtension
            val extension = file.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
            val values = linkedSetOf<String>()
            values += file.absolutePath
            if (parent != null) {
                values += File(parent, "${baseName}_thumb$extension").absolutePath
                values += File(parent, "${baseName}_thumbnail$extension").absolutePath
            }
            values.map { File(it) }
        }
}

private fun snapshotFilesForRollback(files: List<File>): List<FileBackup> {
    return files.map { file ->
        if (!file.exists()) {
            throw IOException("Source file disappeared before deletion: ${file.absolutePath}")
        }
        FileBackup(
            file = file,
            bytes = file.readBytes()
        )
    }
}

private fun deleteFilesWithRollback(backups: List<FileBackup>) {
    val deleted = mutableListOf<FileBackup>()
    try {
        backups.forEach { backup ->
            if (!backup.file.exists()) {
                throw IOException("Source file disappeared before deletion: ${backup.file.absolutePath}")
            }
            if (!backup.file.delete()) {
                throw IOException("Failed to delete file: ${backup.file.absolutePath}")
            }
            deleted += backup
        }
    } catch (failure: Throwable) {
        val restoreFailure = runCatching {
            restoreFilesFromBackups(deleted)
        }.exceptionOrNull()
        if (restoreFailure != null) {
            failure.addSuppressed(restoreFailure)
        }
        throw failure
    }
}

private fun restoreFilesFromBackups(backups: List<FileBackup>) {
    backups.asReversed().forEach { backup ->
        val parent = backup.file.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IOException("Failed to recreate parent directory for ${backup.file.absolutePath}")
        }
        runCatching {
            backup.file.writeBytes(backup.bytes)
        }.getOrElse { error ->
            throw IOException("Failed to restore file ${backup.file.absolutePath}", error)
        }
    }
}

private fun ContactEntity.toDomain(): Contact = Contact(
    id = id,
    name = name,
    title = title,
    company = company,
    email = email,
    phone = phone,
    website = website,
    industry = industry,
    industryCustom = industryCustom,
    industrySource = industrySource,
    rawOcrText = rawOcrText,
    imagePath = imagePath,
    rawImagePath = rawImagePath,
    cardCropQuad = cardCropQuad,
    cardCropVersion = cardCropVersion,
    phoneExportStatus = phoneExportStatus,
    phoneExportedAt = phoneExportedAt,
    phoneExportPayloadHash = phoneExportPayloadHash
)

private fun Contact.toEntity(): ContactEntity = ContactEntity(
    id = id,
    name = name,
    title = title,
    company = company,
    email = email,
    phone = phone,
    website = website,
    industry = industry,
    industryCustom = industryCustom,
    industrySource = industrySource,
    rawOcrText = rawOcrText,
    imagePath = imagePath,
    rawImagePath = rawImagePath,
    cardCropQuad = cardCropQuad,
    cardCropVersion = cardCropVersion,
    phoneExportStatus = phoneExportStatus,
    phoneExportedAt = phoneExportedAt,
    phoneExportPayloadHash = phoneExportPayloadHash
)

private fun InteractionEntity.toDomain(): Interaction = Interaction(
    id = id,
    contactId = contactId,
    dateTime = dateTime,
    meetingLocationName = meetingLocationName,
    city = city,
    relationshipType = relationshipType,
    notes = notes,
    latitude = latitude,
    longitude = longitude
)

private fun Interaction.toEntity(): InteractionEntity = InteractionEntity(
    id = id,
    contactId = contactId,
    dateTime = dateTime,
    meetingLocationName = meetingLocationName,
    city = city,
    relationshipType = relationshipType,
    notes = notes,
    latitude = latitude,
    longitude = longitude
)

private fun ContactSummaryEntity.toDomain(): ContactSummary = ContactSummary(
    contact = contact.toDomain(),
    lastInteractionTime = lastInteractionTime,
    hasNotes = hasNotes,
    interactionNotes = interactionNotes,
    interactionLocations = interactionLocations,
    interactionRelationships = interactionRelationships
)

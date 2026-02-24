package com.example.businesscardscanner.data.export

import android.util.JsonWriter
import com.example.businesscardscanner.domain.model.Contact
import com.example.businesscardscanner.domain.model.Interaction
import com.example.businesscardscanner.domain.model.industryDisplayLabel
import com.example.businesscardscanner.domain.repository.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Stable export contract for backup/migration:
 * - `format` identifies this payload independently from Room table names.
 * - `version` is incremented only for contract changes.
 * - Importers can branch by `version` to stay compatible across app schema updates.
 *
 * JSON shape (version 1):
 * {
 *   "format": "contactra_export",
 *   "version": 1,
 *   "generatedAtEpochMs": 1700000000000,
 *   "contacts": [...],
 *   "interactions": [...]
 * }
 */
class ExportBackupManager(
    private val repository: ContactRepository
) {
    suspend fun captureSnapshot(): ExportSnapshot = withContext(Dispatchers.IO) {
        ExportSnapshot(
            contacts = repository.allContacts().sortedBy { it.id },
            interactions = repository.allInteractions().sortedBy { it.id },
            generatedAtEpochMs = System.currentTimeMillis()
        )
    }

    fun writeJson(snapshot: ExportSnapshot, outputStream: OutputStream) {
        OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
            JsonWriter(writer).use { json ->
                json.setIndent("  ")
                json.beginObject()
                json.name("format").value(EXPORT_FORMAT_NAME)
                json.name("version").value(EXPORT_FORMAT_VERSION.toLong())
                json.name("generatedAtEpochMs").value(snapshot.generatedAtEpochMs)

                json.name("contacts")
                json.beginArray()
                snapshot.contacts.forEach { contact ->
                    json.beginObject()
                    json.name("id").value(contact.id)
                    json.name("name").value(contact.name)
                    json.name("title").value(contact.title)
                    json.name("company").value(contact.company)
                    json.name("email").value(contact.email)
                    json.name("phone").value(contact.phone)
                    json.name("website").value(contact.website)
                    json.name("industry").value(contact.industry)
                    json.name("industryCustom").value(contact.industryCustom)
                    json.name("industrySource").value(contact.industrySource)
                    json.name("rawOcrText").value(contact.rawOcrText)
                    json.name("imagePath").value(contact.imagePath)
                    json.endObject()
                }
                json.endArray()

                json.name("interactions")
                json.beginArray()
                snapshot.interactions.forEach { interaction ->
                    json.beginObject()
                    json.name("id").value(interaction.id)
                    json.name("contactId").value(interaction.contactId)
                    json.name("dateTime").value(interaction.dateTime)
                    json.name("meetingLocationName").value(interaction.meetingLocationName)
                    json.name("city").value(interaction.city)
                    json.name("relationshipType").value(interaction.relationshipType)
                    json.name("notes").value(interaction.notes)
                    interaction.latitude?.let { json.name("latitude").value(it) }
                        ?: json.name("latitude").nullValue()
                    interaction.longitude?.let { json.name("longitude").value(it) }
                        ?: json.name("longitude").nullValue()
                    json.endObject()
                }
                json.endArray()
                json.endObject()
            }
        }
    }

    fun writeVCard(snapshot: ExportSnapshot, outputStream: OutputStream) {
        OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
            snapshot.contacts.forEach { contact ->
                writer.appendLine("BEGIN:VCARD")
                writer.appendLine("VERSION:3.0")
                writer.appendLine("UID:${contact.id}")
                contact.name?.takeIf { it.isNotBlank() }?.let {
                    writer.appendLine("FN:${escapeVCardValue(it)}")
                    writer.appendLine("N:${escapeVCardValue(it)};;;;")
                }
                contact.company?.takeIf { it.isNotBlank() }?.let {
                    writer.appendLine("ORG:${escapeVCardValue(it)}")
                }
                contact.title?.takeIf { it.isNotBlank() }?.let {
                    writer.appendLine("TITLE:${escapeVCardValue(it)}")
                }
                contact.email?.takeIf { it.isNotBlank() }?.let {
                    writer.appendLine("EMAIL;TYPE=INTERNET:${escapeVCardValue(it)}")
                }
                contact.phone?.takeIf { it.isNotBlank() }?.let {
                    writer.appendLine("TEL;TYPE=CELL:${escapeVCardValue(it)}")
                }
                contact.website?.takeIf { it.isNotBlank() }?.let {
                    writer.appendLine("URL:${escapeVCardValue(it)}")
                }
                buildVCardNote(contact)?.let {
                    writer.appendLine("NOTE:${escapeVCardValue(it)}")
                }
                writer.appendLine("END:VCARD")
            }
        }
    }

    private fun buildVCardNote(contact: Contact): String? {
        val parts = mutableListOf<String>()
        contact.industryDisplayLabel()?.let { parts += "Industry: $it" }
        contact.industrySource?.takeIf { it.isNotBlank() }?.let { parts += "Industry Source: $it" }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\\n")
    }

    private fun escapeVCardValue(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\r", "")
            .replace("\n", "\\n")
            .replace(";", "\\;")
            .replace(",", "\\,")
    }
}

data class ExportSnapshot(
    val contacts: List<Contact>,
    val interactions: List<Interaction>,
    val generatedAtEpochMs: Long
)

const val EXPORT_FORMAT_NAME = "contactra_export"
const val EXPORT_FORMAT_VERSION = 1

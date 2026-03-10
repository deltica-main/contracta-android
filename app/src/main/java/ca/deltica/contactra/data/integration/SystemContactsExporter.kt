package ca.deltica.contactra.data.integration

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import ca.deltica.contactra.domain.model.Contact
import ca.deltica.contactra.domain.model.industryDisplayLabel

/**
 * Writes app contacts to the system Contacts provider in one atomic batch.
 *
 * Field mapping:
 * - name -> StructuredName.DISPLAY_NAME
 * - company/title -> Organization.COMPANY and Organization.TITLE
 * - phone -> Phone.NUMBER (TYPE_WORK)
 * - email -> Email.ADDRESS (TYPE_WORK)
 * - website -> Website.URL (TYPE_WORK)
 * - industry -> Note.NOTE (as "Industry: ...")
 */
object SystemContactsExporter {

    fun buildSharePreview(contact: Contact): List<String> {
        val preview = mutableListOf<String>()
        contact.displayNameCandidate()?.let { preview.add("Name: $it") }
        if (!contact.company.isNullOrBlank() || !contact.title.isNullOrBlank()) {
            val orgLabel = buildString {
                if (!contact.company.isNullOrBlank()) {
                    append(contact.company)
                }
                if (!contact.title.isNullOrBlank()) {
                    if (isNotEmpty()) append(" - ")
                    append(contact.title)
                }
            }
            preview.add("Work: $orgLabel")
        }
        contact.phone?.takeIf { it.isNotBlank() }?.let { preview.add("Phone: $it") }
        contact.email?.takeIf { it.isNotBlank() }?.let { preview.add("Email: $it") }
        contact.website?.takeIf { it.isNotBlank() }?.let { preview.add("Website: $it") }
        contact.industryDisplayLabel()?.let { preview.add("Industry note: $it") }
        return preview
    }

    fun addToPhoneContacts(context: Context, contact: Contact): Result<Unit> {
        val operations = buildInsertOperations(contact)
        if (operations.size <= 1) {
            return Result.failure(IllegalArgumentException("No contact details available to share."))
        }
        return runCatching {
            context.contentResolver.applyBatch(
                ContactsContract.AUTHORITY,
                ArrayList(operations)
            )
            Unit
        }
    }

    private fun buildInsertOperations(contact: Contact): List<ContentProviderOperation> {
        val operations = mutableListOf<ContentProviderOperation>()
        val rawContactInsertIndex = 0
        operations += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            .build()

        contact.displayNameCandidate()?.let { displayName ->
            operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                )
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                .build()
        }

        if (!contact.company.isNullOrBlank() || !contact.title.isNullOrBlank()) {
            operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                )
                .withValue(
                    ContactsContract.CommonDataKinds.Organization.COMPANY,
                    contact.company?.takeIf { it.isNotBlank() }
                )
                .withValue(
                    ContactsContract.CommonDataKinds.Organization.TITLE,
                    contact.title?.takeIf { it.isNotBlank() }
                )
                .withValue(
                    ContactsContract.CommonDataKinds.Organization.TYPE,
                    ContactsContract.CommonDataKinds.Organization.TYPE_WORK
                )
                .build()
        }

        contact.phone?.takeIf { it.isNotBlank() }?.let { phone ->
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

        contact.email?.takeIf { it.isNotBlank() }?.let { email ->
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

        contact.website?.takeIf { it.isNotBlank() }?.let { website ->
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

        contact.industryDisplayLabel()?.let { industry ->
            operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
                )
                .withValue(ContactsContract.CommonDataKinds.Note.NOTE, "Industry: $industry")
                .build()
        }

        return operations
    }

    private fun Contact.displayNameCandidate(): String? {
        return listOf(name, company, email, phone).firstOrNull { !it.isNullOrBlank() }
    }
}

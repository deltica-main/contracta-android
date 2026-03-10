package ca.deltica.contactra.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.deltica.contactra.data.local.AppDatabase
import ca.deltica.contactra.domain.logic.IndustryCatalog
import ca.deltica.contactra.domain.model.Contact
import ca.deltica.contactra.domain.model.Interaction
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ContactDeletionRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: ContactRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ContactRepositoryImpl(database)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun deleteContact_removesRowsFilesAndMetadata() {
        runBlocking {
            val imageDir = File(context.cacheDir, "delete_contact_test_${System.currentTimeMillis()}").apply {
                mkdirs()
            }
            val imageFile = File(imageDir, "contact_42.jpg").apply { writeText("image") }
            val thumbFile = File(imageDir, "contact_42_thumb.jpg").apply { writeText("thumb") }
            val thumbnailFile = File(imageDir, "contact_42_thumbnail.jpg").apply { writeText("thumbnail") }

            val contactId = repository.insertContact(
                Contact(
                    name = "Alex Example",
                    title = "CTO",
                    company = "Acme Corp",
                    email = "alex@acme.com",
                    phone = "15551234567",
                    website = "acme.com",
                    industry = "Technology / Software",
                    industrySource = "manual",
                    rawOcrText = "Alex Example Acme Corp",
                    imagePath = imageFile.absolutePath
                )
            )
            repository.insertInteraction(
                Interaction(
                    contactId = contactId,
                    dateTime = System.currentTimeMillis(),
                    meetingLocationName = "Conference Hall",
                    city = "Seattle",
                    relationshipType = "Client",
                    notes = "Follow up",
                    latitude = null,
                    longitude = null
                )
            )

            val companyKey = IndustryCatalog.normalizeCompany("Acme Corp")!!
            repository.upsertCompanyIndustry(companyKey, "Technology / Software")
            assertEquals(1, database.companyIndustryDao().countByCompany(companyKey))
            assertEquals(1, database.contactDao().countContacts())
            assertNotNull(database.contactDao().getByIdOnce(contactId))
            assertEquals(1, database.interactionDao().countForContact(contactId))

            repository.deleteContact(contactId)

            assertNull(database.contactDao().getByIdOnce(contactId))
            assertEquals(0, database.contactDao().countContacts())
            assertEquals(0, database.interactionDao().countForContact(contactId))
            assertEquals(0, database.companyIndustryDao().countByCompany(companyKey))
            assertFalse(imageFile.exists())
            assertFalse(thumbFile.exists())
            assertFalse(thumbnailFile.exists())

            imageDir.deleteRecursively()
        }
    }

    @Test
    fun deleteContact_keepsMetadataWhenCompanyStillUsed() {
        runBlocking {
            val firstContactId = repository.insertContact(
                Contact(
                    name = "Alex Example",
                    title = null,
                    company = "Acme Corp",
                    email = null,
                    phone = null,
                    website = null,
                    industry = "Technology / Software",
                    industrySource = "manual",
                    rawOcrText = null,
                    imagePath = null
                )
            )
            repository.insertContact(
                Contact(
                    name = "Blair Example",
                    title = null,
                    company = "Acme Corp",
                    email = null,
                    phone = null,
                    website = null,
                    industry = "Technology / Software",
                    industrySource = "manual",
                    rawOcrText = null,
                    imagePath = null
                )
            )

            val companyKey = IndustryCatalog.normalizeCompany("Acme Corp")!!
            repository.upsertCompanyIndustry(companyKey, "Technology / Software")

            repository.deleteContact(firstContactId)

            assertEquals(1, database.companyIndustryDao().countByCompany(companyKey))
        }
    }

    @Test
    fun deleteContact_removesManagedCardAndLogoPair() {
        runBlocking {
            val imageDir = File(context.filesDir, "delete_contact_logo_pair_${System.currentTimeMillis()}").apply {
                mkdirs()
            }
            val logoFile = File(imageDir, "contact_77_logo.png").apply { writeText("logo") }
            val cardFile = File(imageDir, "contact_77_card.jpg").apply { writeText("card") }
            val rawFile = File(imageDir, "contact_77_raw.jpg").apply { writeText("raw") }

            val contactId = repository.insertContact(
                Contact(
                    name = "Logo User",
                    title = "Founder",
                    company = "Logo Corp",
                    email = "logo@corp.com",
                    phone = "15559990000",
                    website = "logo.example",
                    industry = null,
                    industrySource = null,
                    rawOcrText = "Logo User",
                    imagePath = logoFile.absolutePath
                )
            )

            repository.deleteContact(contactId)

            assertFalse(logoFile.exists())
            assertFalse(cardFile.exists())
            assertFalse(rawFile.exists())
            imageDir.deleteRecursively()
        }
    }
}

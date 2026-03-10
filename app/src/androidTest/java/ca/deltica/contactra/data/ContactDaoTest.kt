package ca.deltica.contactra.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.deltica.contactra.data.local.AppDatabase
import ca.deltica.contactra.data.local.ContactDao
import ca.deltica.contactra.data.local.ContactEntity
import ca.deltica.contactra.data.local.InteractionDao
import ca.deltica.contactra.data.local.InteractionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var contactDao: ContactDao
    private lateinit var interactionDao: InteractionDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        contactDao = db.contactDao()
        interactionDao = db.interactionDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun search_includes_contact_and_interaction_fields() = runBlocking {
        val contactId = contactDao.insert(
            ContactEntity(
                name = "Jane Doe",
                title = "Engineer",
                company = "Acme Corp",
                email = "jane@acme.com",
                phone = "15551234567",
                website = "acme.com",
                industry = "Technology",
                industrySource = "auto",
                rawOcrText = "Jane Doe Acme Corp",
                imagePath = null
            )
        )

        interactionDao.insert(
            InteractionEntity(
                contactId = contactId,
                dateTime = 1L,
                meetingLocationName = "Conference Hall",
                city = "Boston",
                relationshipType = "Client",
                notes = "Met at trade show",
                latitude = null,
                longitude = null
            )
        )

        val byCompany = contactDao.search("Acme").first()
        val byNotes = contactDao.search("trade show").first()

        assertEquals(1, byCompany.size)
        assertEquals(1, byNotes.size)
    }

    @Test
    fun findDuplicates_matches_email_or_phone() = runBlocking {
        contactDao.insert(
            ContactEntity(
                name = "Jane Doe",
                title = null,
                company = null,
                email = "jane@acme.com",
                phone = "15551234567",
                website = null,
                industry = null,
                industrySource = null,
                rawOcrText = null,
                imagePath = null
            )
        )

        val byEmail = contactDao.findDuplicates("jane@acme.com", null)
        val byPhone = contactDao.findDuplicates(null, "15551234567")

        assertEquals(1, byEmail.size)
        assertEquals(1, byPhone.size)
    }

}

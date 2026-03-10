package ca.deltica.contactra.data.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.deltica.contactra.data.local.AppDatabase
import ca.deltica.contactra.data.repository.ContactRepositoryImpl
import ca.deltica.contactra.domain.model.Contact
import ca.deltica.contactra.domain.model.Interaction
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class ExportBackupManagerTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: ContactRepositoryImpl
    private lateinit var manager: ExportBackupManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ContactRepositoryImpl(database)
        manager = ExportBackupManager(repository)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun exportJsonAndVCard_matchDatabaseRows() {
        runBlocking {
            val firstContactId = repository.insertContact(
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
                    imagePath = "/tmp/contact_1.jpg"
                )
            )
            val secondContactId = repository.insertContact(
                Contact(
                    name = "Jordan Example",
                    title = "Founder",
                    company = "Northwind",
                    email = "jordan@northwind.com",
                    phone = "15550001111",
                    website = "northwind.com",
                    industry = "Professional Services",
                    industrySource = "auto",
                    rawOcrText = "Jordan Example Northwind",
                    imagePath = "/tmp/contact_2.jpg"
                )
            )
            repository.insertInteraction(
                Interaction(
                    contactId = firstContactId,
                    dateTime = 1700000000000,
                    meetingLocationName = "Expo Hall",
                    city = "Seattle",
                    relationshipType = "Client",
                    notes = "Follow up in Q2",
                    latitude = 47.6062,
                    longitude = -122.3321
                )
            )
            repository.insertInteraction(
                Interaction(
                    contactId = secondContactId,
                    dateTime = 1700001000000,
                    meetingLocationName = "HQ",
                    city = "Portland",
                    relationshipType = "Partner",
                    notes = "Send proposal",
                    latitude = null,
                    longitude = null
                )
            )

            val snapshot = manager.captureSnapshot()
            val jsonOutput = ByteArrayOutputStream()
            manager.writeJson(snapshot, jsonOutput)
            val json = JSONObject(jsonOutput.toString(Charsets.UTF_8.name()))

            assertEquals(EXPORT_FORMAT_NAME, json.getString("format"))
            assertEquals(EXPORT_FORMAT_VERSION, json.getInt("version"))

            val contacts = json.getJSONArray("contacts")
            val interactions = json.getJSONArray("interactions")
            assertEquals(database.contactDao().countContacts(), contacts.length())
            assertEquals(database.interactionDao().getAllOnce().size, interactions.length())

            val contactIds = mutableSetOf<Long>()
            for (index in 0 until contacts.length()) {
                contactIds += contacts.getJSONObject(index).getLong("id")
            }
            for (index in 0 until interactions.length()) {
                val interactionContactId = interactions.getJSONObject(index).getLong("contactId")
                assertTrue("Interaction references missing contact", contactIds.contains(interactionContactId))
            }

            val vCardOutput = ByteArrayOutputStream()
            manager.writeVCard(snapshot, vCardOutput)
            val vCard = vCardOutput.toString(Charsets.UTF_8.name())
            assertTrue(vCard.contains("BEGIN:VCARD"))
            assertTrue(vCard.contains("FN:Alex Example"))
            assertTrue(vCard.contains("FN:Jordan Example"))
        }
    }

    @Test
    fun exportJson_largeDataset_completesWithoutFailure() {
        runBlocking {
            repeat(750) { index ->
                val contactId = repository.insertContact(
                    Contact(
                        name = "Contact $index",
                        title = "Role $index",
                        company = "Company $index",
                        email = "contact$index@example.com",
                        phone = "1555000$index",
                        website = "example$index.com",
                        industry = "Technology / Software",
                        industrySource = "manual",
                        rawOcrText = "Contact $index Company $index",
                        imagePath = null
                    )
                )
                repository.insertInteraction(
                    Interaction(
                        contactId = contactId,
                        dateTime = 1700000000000 + index,
                        meetingLocationName = "Location $index",
                        city = "City $index",
                        relationshipType = "Client",
                        notes = "Notes $index",
                        latitude = null,
                        longitude = null
                    )
                )
            }

            val snapshot = manager.captureSnapshot()
            val jsonOutput = ByteArrayOutputStream()
            manager.writeJson(snapshot, jsonOutput)

            assertTrue(snapshot.contacts.size >= 750)
            assertTrue(snapshot.interactions.size >= 750)
            assertTrue(jsonOutput.size() > 20_000)
        }
    }
}

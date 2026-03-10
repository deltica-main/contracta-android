package ca.deltica.contactra.ui.assets

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.deltica.contactra.ComposeHostActivity
import ca.deltica.contactra.domain.logic.ExtractedData
import ca.deltica.contactra.domain.logic.UnassignedItemKind
import ca.deltica.contactra.domain.logic.UnassignedOcrItem
import ca.deltica.contactra.domain.model.Contact
import ca.deltica.contactra.domain.model.ContactSummary
import ca.deltica.contactra.domain.model.Interaction
import ca.deltica.contactra.domain.repository.ContactRepository
import ca.deltica.contactra.ui.screens.ContactListScreen
import ca.deltica.contactra.ui.screens.HomeScreen
import ca.deltica.contactra.ui.screens.ReviewScreen
import ca.deltica.contactra.ui.screens.ScanScreen
import ca.deltica.contactra.ui.theme.ContactraTheme
import ca.deltica.contactra.ui.viewmodel.ConfidenceLevel
import ca.deltica.contactra.ui.viewmodel.MainViewModel
import ca.deltica.contactra.ui.viewmodel.ReviewFieldConfidence
import ca.deltica.contactra.ui.viewmodel.ReviewFieldSuggestions
import ca.deltica.contactra.ui.viewmodel.ReviewFields
import ca.deltica.contactra.ui.viewmodel.SortOption
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayStoreAssetCaptureTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeHostActivity>()

    private lateinit var repository: ScreenshotContactRepository
    private lateinit var viewModel: MainViewModel
    private lateinit var outputDir: File
    private val screenStage = mutableStateOf(AssetScreen.HOME)

    @Before
    fun setup() {
        val context = composeRule.activity.applicationContext
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val demoContacts = buildDemoContacts(context.filesDir)
        repository = ScreenshotContactRepository(demoContacts)
        viewModel = MainViewModel(context, repository)

        outputDir = File(composeRule.activity.filesDir, "play-store-assets/raw-captures")
        if (outputDir.exists()) {
            outputDir.listFiles()?.forEach { it.delete() }
        }
        outputDir.mkdirs()
    }

    @Test
    fun capturePlayStoreScreens() {
        composeRule.setContent {
            AppContent {
                when (screenStage.value) {
                    AssetScreen.HOME -> HomeScreen(
                        navController = rememberNavController(),
                        viewModel = viewModel
                    )

                    AssetScreen.SCAN -> ScanScreen(
                        navController = rememberNavController(),
                        viewModel = viewModel,
                        launchGallery = false,
                        requestCameraPermissionOnLaunch = false
                    )

                    AssetScreen.REVIEW -> ReviewScreen(
                        navController = rememberNavController(),
                        viewModel = viewModel
                    )

                    AssetScreen.CONTACTS -> ContactListScreen(
                        navController = rememberNavController(),
                        viewModel = viewModel,
                        initialQuery = ""
                    )
                }
            }
        }
        composeRule.waitForIdle()
        captureHomeScreen()
        captureScanScreen()
        captureReviewScreen()
        captureContactsScreen()
    }

    private fun captureHomeScreen() {
        screenStage.value = AssetScreen.HOME
        composeRule.waitForIdle()
        SystemClock.sleep(350)
        saveRootCapture("01-home-workspace.png")
    }

    private fun captureScanScreen() {
        screenStage.value = AssetScreen.SCAN
        composeRule.waitForIdle()
        SystemClock.sleep(2600)
        saveRootCapture("02-scan-flow.png")
    }

    private fun captureReviewScreen() {
        val cardBitmap = createCardBitmap(
            width = 1100,
            height = 630,
            name = "John Doe",
            title = "Account Executive",
            company = "Northstar Industrial",
            email = "john.doe@northstarindustrial.com",
            phone = "+1 416 555 0142",
            primary = Color.parseColor("#0E5CC7"),
            accent = Color.parseColor("#2F7FE6")
        )

        setScanState(
            viewModel,
            viewModel.scanUiState.value.copy(
                rawOcrText = """
                    John Doe
                    Account Executive
                    Northstar Industrial
                    john.doe@northstarindustrial.com
                    +1 416 555 0142
                    northstarindustrial.com
                    120 King Street West
                    Toronto, ON M5H 1J9
                """.trimIndent(),
                extractedData = ExtractedData(
                    name = "John Doe",
                    title = "Account Executive",
                    company = "Northstar Industrial",
                    email = "john.doe@northstarindustrial.com",
                    phone = "+14165550142",
                    website = "https://northstarindustrial.com",
                    address = "120 King Street West, Toronto, ON M5H 1J9",
                    industry = "Manufacturing"
                ),
                reviewFields = ReviewFields(
                    name = "John Doe",
                    title = "Account Executive",
                    company = "Northstar Industrial",
                    email = "john.doe@northstarindustrial.com",
                    phone = "+1 416 555 0142",
                    website = "https://northstarindustrial.com",
                    address = "120 King Street West, Toronto, ON M5H 1J9",
                    industry = "Manufacturing"
                ),
                reviewSuggestions = ReviewFieldSuggestions(
                    name = listOf("John Doe"),
                    title = listOf("Account Executive", "Regional Account Executive"),
                    company = listOf("Northstar Industrial"),
                    email = listOf("john.doe@northstarindustrial.com"),
                    phone = listOf("+1 (416) 555-0142"),
                    website = listOf("https://northstarindustrial.com"),
                    address = listOf("120 King Street West, Toronto, ON M5H 1J9"),
                    industry = listOf("Manufacturing")
                ),
                unassignedItems = listOf(
                    UnassignedOcrItem(
                        id = "line_8",
                        displayText = "Enterprise supply chain partnerships",
                        lines = listOf("Enterprise supply chain partnerships"),
                        lineIndices = listOf(8),
                        kind = UnassignedItemKind.OTHER,
                        isGrouped = false
                    )
                ),
                fieldConfidence = ReviewFieldConfidence(
                    name = ConfidenceLevel.HIGH,
                    title = ConfidenceLevel.HIGH,
                    company = ConfidenceLevel.HIGH,
                    email = ConfidenceLevel.HIGH,
                    phone = ConfidenceLevel.HIGH,
                    website = ConfidenceLevel.HIGH,
                    industry = ConfidenceLevel.MEDIUM
                ),
                lastCapturedImage = cardBitmap,
                isProcessing = false,
                processingMessage = null,
                errorMessage = null,
                parserNoticeMessage = null
            )
        )

        screenStage.value = AssetScreen.REVIEW
        composeRule.waitForIdle()
        SystemClock.sleep(350)
        saveRootCapture("03-review-parsed-contact.png")
    }

    private fun captureContactsScreen() {
        viewModel.updateSearchQuery("")
        viewModel.updateIndustryFilter(null)
        viewModel.updateHasNotesFilter(true)
        viewModel.updateSortOption(SortOption.NAME)
        screenStage.value = AssetScreen.CONTACTS
        composeRule.waitForIdle()
        SystemClock.sleep(350)
        saveRootCapture("04-contacts-filtered-list.png")
    }

    private fun saveRootCapture(fileName: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val target = File(outputDir, fileName)
        FileOutputStream(target).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun setScanState(viewModel: MainViewModel, state: ca.deltica.contactra.ui.viewmodel.ScanUiState) {
        val field = MainViewModel::class.java.getDeclaredField("_scanUiState")
        field.isAccessible = true
        val flow = field.get(viewModel) as MutableStateFlow<ca.deltica.contactra.ui.viewmodel.ScanUiState>
        flow.value = state
    }

    @Composable
    private fun AppContent(content: @Composable () -> Unit) {
        ContactraTheme {
            content()
        }
    }

    private fun buildDemoContacts(filesDir: File): List<DemoContact> {
        val cardDir = File(filesDir, "play-store-assets/demo-cards")
        cardDir.mkdirs()

        val now = System.currentTimeMillis()

        fun writeCard(
            fileName: String,
            name: String,
            title: String,
            company: String,
            email: String,
            phone: String,
            primary: Int,
            accent: Int
        ): String {
            val file = File(cardDir, fileName)
            val bitmap = createCardBitmap(
                width = 900,
                height = 515,
                name = name,
                title = title,
                company = company,
                email = email,
                phone = phone,
                primary = primary,
                accent = accent
            )
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            return file.absolutePath
        }

        val johnCard = writeCard(
            fileName = "john-doe.png",
            name = "John Doe",
            title = "Account Executive",
            company = "Northstar Industrial",
            email = "john.doe@northstarindustrial.com",
            phone = "+1 416 555 0142",
            primary = Color.parseColor("#0E5CC7"),
            accent = Color.parseColor("#2F7FE6")
        )
        val priyaCard = writeCard(
            fileName = "priya-shah.png",
            name = "Priya Shah",
            title = "Regional Partnerships Lead",
            company = "Summit Grid Energy",
            email = "priya.shah@summitgridenergy.com",
            phone = "+1 647 555 0186",
            primary = Color.parseColor("#0A4B9F"),
            accent = Color.parseColor("#1A73DA")
        )
        val miguelCard = writeCard(
            fileName = "miguel-santos.png",
            name = "Miguel Santos",
            title = "Operations Director",
            company = "Harborline Logistics",
            email = "miguel.santos@harborlinelogistics.com",
            phone = "+1 437 555 0134",
            primary = Color.parseColor("#1B4D9C"),
            accent = Color.parseColor("#3A8EF0")
        )
        val aminaCard = writeCard(
            fileName = "amina-hassan.png",
            name = "Amina Hassan",
            title = "Client Success Manager",
            company = "Bluepath Advisory",
            email = "amina.hassan@bluepathadvisory.com",
            phone = "+1 416 555 0198",
            primary = Color.parseColor("#1B66C9"),
            accent = Color.parseColor("#4D95F2")
        )

        return listOf(
            DemoContact(
                summary = ContactSummary(
                    contact = Contact(
                        id = 101,
                        name = "John Doe",
                        title = "Account Executive",
                        company = "Northstar Industrial",
                        email = "john.doe@northstarindustrial.com",
                        phone = "+14165550142",
                        website = "https://northstarindustrial.com",
                        industry = "Manufacturing",
                        industryCustom = null,
                        industrySource = "manual",
                        rawOcrText = "John Doe Northstar Industrial",
                        imagePath = johnCard,
                        rawImagePath = johnCard,
                        cardCropQuad = null,
                        cardCropVersion = 2,
                        phoneExportStatus = "synced",
                        phoneExportedAt = now - 4L * 60L * 60L * 1000L,
                        phoneExportPayloadHash = "demo-john"
                    ),
                    lastInteractionTime = now - 2L * 60L * 60L * 1000L,
                    hasNotes = true,
                    interactionNotes = "Discussed expansion plans for Toronto operations.",
                    interactionLocations = "Toronto",
                    interactionRelationships = "Client"
                ),
                interactions = listOf(
                    Interaction(
                        id = 501,
                        contactId = 101,
                        dateTime = now - 2L * 60L * 60L * 1000L,
                        meetingLocationName = "Deltica HQ",
                        city = "Toronto",
                        relationshipType = "Client",
                        notes = "Discussed expansion plans for Toronto operations.",
                        latitude = null,
                        longitude = null
                    )
                )
            ),
            DemoContact(
                summary = ContactSummary(
                    contact = Contact(
                        id = 102,
                        name = "Priya Shah",
                        title = "Regional Partnerships Lead",
                        company = "Summit Grid Energy",
                        email = "priya.shah@summitgridenergy.com",
                        phone = "+16475550186",
                        website = "https://summitgridenergy.com",
                        industry = "Energy",
                        industryCustom = null,
                        industrySource = "manual",
                        rawOcrText = "Priya Shah Summit Grid Energy",
                        imagePath = priyaCard,
                        rawImagePath = priyaCard,
                        cardCropQuad = null,
                        cardCropVersion = 2,
                        phoneExportStatus = null,
                        phoneExportedAt = null,
                        phoneExportPayloadHash = null
                    ),
                    lastInteractionTime = now - 18L * 60L * 60L * 1000L,
                    hasNotes = true,
                    interactionNotes = "Follow-up for procurement workshop in April.",
                    interactionLocations = "Calgary",
                    interactionRelationships = "Partner"
                ),
                interactions = listOf(
                    Interaction(
                        id = 502,
                        contactId = 102,
                        dateTime = now - 18L * 60L * 60L * 1000L,
                        meetingLocationName = "Energy Connect Summit",
                        city = "Calgary",
                        relationshipType = "Partner",
                        notes = "Follow-up for procurement workshop in April.",
                        latitude = null,
                        longitude = null
                    )
                )
            ),
            DemoContact(
                summary = ContactSummary(
                    contact = Contact(
                        id = 103,
                        name = "Miguel Santos",
                        title = "Operations Director",
                        company = "Harborline Logistics",
                        email = "miguel.santos@harborlinelogistics.com",
                        phone = "+14375550134",
                        website = "https://harborlinelogistics.com",
                        industry = "Logistics",
                        industryCustom = null,
                        industrySource = "manual",
                        rawOcrText = "Miguel Santos Harborline Logistics",
                        imagePath = miguelCard,
                        rawImagePath = miguelCard,
                        cardCropQuad = null,
                        cardCropVersion = 2,
                        phoneExportStatus = null,
                        phoneExportedAt = null,
                        phoneExportPayloadHash = null
                    ),
                    lastInteractionTime = now - 3L * 24L * 60L * 60L * 1000L,
                    hasNotes = false,
                    interactionNotes = null,
                    interactionLocations = "Vancouver",
                    interactionRelationships = "Vendor"
                ),
                interactions = listOf(
                    Interaction(
                        id = 503,
                        contactId = 103,
                        dateTime = now - 3L * 24L * 60L * 60L * 1000L,
                        meetingLocationName = "Portside Hub",
                        city = "Vancouver",
                        relationshipType = "Vendor",
                        notes = null,
                        latitude = null,
                        longitude = null
                    )
                )
            ),
            DemoContact(
                summary = ContactSummary(
                    contact = Contact(
                        id = 104,
                        name = "Amina Hassan",
                        title = "Client Success Manager",
                        company = "Bluepath Advisory",
                        email = "amina.hassan@bluepathadvisory.com",
                        phone = "+14165550198",
                        website = "https://bluepathadvisory.com",
                        industry = "Consulting",
                        industryCustom = null,
                        industrySource = "manual",
                        rawOcrText = "Amina Hassan Bluepath Advisory",
                        imagePath = aminaCard,
                        rawImagePath = aminaCard,
                        cardCropQuad = null,
                        cardCropVersion = 2,
                        phoneExportStatus = "synced",
                        phoneExportedAt = now - 6L * 24L * 60L * 60L * 1000L,
                        phoneExportPayloadHash = "demo-amina"
                    ),
                    lastInteractionTime = now - 6L * 24L * 60L * 60L * 1000L,
                    hasNotes = true,
                    interactionNotes = "Requested onboarding checklist after kickoff call.",
                    interactionLocations = "Ottawa",
                    interactionRelationships = "Client"
                ),
                interactions = listOf(
                    Interaction(
                        id = 504,
                        contactId = 104,
                        dateTime = now - 6L * 24L * 60L * 60L * 1000L,
                        meetingLocationName = "Video call",
                        city = "Ottawa",
                        relationshipType = "Client",
                        notes = "Requested onboarding checklist after kickoff call.",
                        latitude = null,
                        longitude = null
                    )
                )
            )
        )
    }

    private fun createCardBitmap(
        width: Int,
        height: Int,
        name: String,
        title: String,
        company: String,
        email: String,
        phone: String,
        primary: Int,
        accent: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val topBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primary }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B1730")
            textSize = height * 0.078f
            isFakeBoldText = true
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#344865")
            textSize = height * 0.056f
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4A5E7C")
            textSize = height * 0.052f
        }

        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 28f, 28f, backgroundPaint)
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height * 0.28f), 28f, 28f, topBandPaint)
        canvas.drawRoundRect(
            RectF(width * 0.67f, 0f, width.toFloat(), height.toFloat()),
            0f,
            0f,
            accentPaint
        )

        val left = width * 0.06f
        canvas.drawText(company, left, height * 0.16f, Paint(titlePaint).apply {
            color = Color.WHITE
            textSize = height * 0.062f
        })
        canvas.drawText(name, left, height * 0.45f, titlePaint)
        canvas.drawText(title, left, height * 0.57f, subtitlePaint)
        canvas.drawText(email, left, height * 0.73f, bodyPaint)
        canvas.drawText(phone, left, height * 0.84f, bodyPaint)

        return bitmap
    }

    private enum class AssetScreen {
        HOME,
        SCAN,
        REVIEW,
        CONTACTS
    }
}

private data class DemoContact(
    val summary: ContactSummary,
    val interactions: List<Interaction>
)

private class ScreenshotContactRepository(
    initialContacts: List<DemoContact>
) : ContactRepository {

    private val contactsFlow = MutableStateFlow(initialContacts.map { it.summary })
    private val interactionsByContact = MutableStateFlow(
        initialContacts.associate { it.summary.contact.id to it.interactions }
    )
    private val companyIndustryMap = ConcurrentHashMap<String, String>()

    override fun contactSummaries(): Flow<List<ContactSummary>> = contactsFlow

    override fun contactById(id: Long): Flow<Contact?> {
        return contactsFlow.map { summaries -> summaries.firstOrNull { it.contact.id == id }?.contact }
    }

    override suspend fun contactByIdOnce(id: Long): Contact? {
        return contactsFlow.value.firstOrNull { it.contact.id == id }?.contact
    }

    override suspend fun allContacts(): List<Contact> {
        return contactsFlow.value.map { it.contact }
    }

    override suspend fun allInteractions(): List<Interaction> {
        return interactionsByContact.value.values.flatten()
    }

    override fun interactionsForContact(contactId: Long): Flow<List<Interaction>> {
        return interactionsByContact.map { map -> map[contactId].orEmpty() }
    }

    override suspend fun insertContact(contact: Contact): Long {
        val nextId = ((contactsFlow.value.maxOfOrNull { it.contact.id } ?: 100L) + 1L)
        val saved = contact.copy(id = nextId)
        contactsFlow.value = contactsFlow.value + ContactSummary(
            contact = saved,
            lastInteractionTime = System.currentTimeMillis(),
            hasNotes = false,
            interactionNotes = null,
            interactionLocations = null,
            interactionRelationships = null
        )
        return nextId
    }

    override suspend fun updateContact(contact: Contact) {
        contactsFlow.value = contactsFlow.value.map { summary ->
            if (summary.contact.id == contact.id) {
                summary.copy(contact = contact)
            } else {
                summary
            }
        }
    }

    override suspend fun deleteContact(contactId: Long) {
        contactsFlow.value = contactsFlow.value.filterNot { it.contact.id == contactId }
        interactionsByContact.value = interactionsByContact.value - contactId
    }

    override suspend fun insertInteraction(interaction: Interaction): Long {
        val entries = interactionsByContact.value[interaction.contactId].orEmpty()
        val nextId = ((entries.maxOfOrNull { it.id } ?: 0L) + 1L)
        val saved = interaction.copy(id = nextId)
        interactionsByContact.value = interactionsByContact.value + (
            interaction.contactId to (entries + saved)
        )
        return nextId
    }

    override suspend fun findDuplicates(email: String?, phone: String?): List<Contact> {
        val normalizedEmail = email?.trim()?.lowercase().orEmpty()
        val normalizedPhone = phone?.filter { it.isDigit() }.orEmpty()
        return contactsFlow.value.map { it.contact }.filter { contact ->
            val emailMatch = normalizedEmail.isNotBlank() &&
                contact.email?.trim()?.lowercase() == normalizedEmail
            val phoneMatch = normalizedPhone.isNotBlank() &&
                contact.phone?.filter { it.isDigit() } == normalizedPhone
            emailMatch || phoneMatch
        }
    }

    override suspend fun getCompanyIndustry(normalizedCompany: String): String? {
        return companyIndustryMap[normalizedCompany]
    }

    override suspend fun upsertCompanyIndustry(normalizedCompany: String, industry: String) {
        companyIndustryMap[normalizedCompany] = industry
    }
}


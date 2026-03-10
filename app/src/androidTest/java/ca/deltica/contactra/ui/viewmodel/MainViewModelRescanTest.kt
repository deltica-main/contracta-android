package ca.deltica.contactra.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.deltica.contactra.domain.model.Contact
import ca.deltica.contactra.domain.model.ContactSummary
import ca.deltica.contactra.domain.model.Interaction
import ca.deltica.contactra.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainViewModelRescanTest {

    @Test
    fun beginRescan_clears_failed_capture_state_and_stays_idle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = MainViewModel(context, FakeContactRepository())
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

        setScanState(
            viewModel = viewModel,
            state = ScanUiState(
                rawOcrText = "Unreadable OCR",
                lastCapturedImage = bitmap,
                isProcessing = true,
                processingMessage = "Reading card text...",
                errorMessage = "No text found in this image."
            )
        )

        viewModel.beginRescan()

        Thread.sleep(120)
        val state = viewModel.scanUiState.value
        assertNull(state.lastCapturedImage)
        assertFalse(state.isProcessing)
        assertNull(state.processingMessage)
        assertNull(state.errorMessage)
        assertEquals("", state.rawOcrText)
    }

    @Suppress("UNCHECKED_CAST")
    private fun setScanState(viewModel: MainViewModel, state: ScanUiState) {
        val field = MainViewModel::class.java.getDeclaredField("_scanUiState")
        field.isAccessible = true
        val flow = field.get(viewModel) as MutableStateFlow<ScanUiState>
        flow.update { state }
    }
}

private class FakeContactRepository : ContactRepository {
    override fun contactSummaries(): Flow<List<ContactSummary>> = flowOf(emptyList())

    override fun contactById(id: Long): Flow<Contact?> = flowOf(null)

    override suspend fun contactByIdOnce(id: Long): Contact? = null

    override suspend fun allContacts(): List<Contact> = emptyList()

    override suspend fun allInteractions(): List<Interaction> = emptyList()

    override fun interactionsForContact(contactId: Long): Flow<List<Interaction>> = flowOf(emptyList())

    override suspend fun insertContact(contact: Contact): Long = 1L

    override suspend fun updateContact(contact: Contact) = Unit

    override suspend fun deleteContact(contactId: Long) = Unit

    override suspend fun insertInteraction(interaction: Interaction): Long = 1L

    override suspend fun findDuplicates(email: String?, phone: String?): List<Contact> = emptyList()

    override suspend fun getCompanyIndustry(normalizedCompany: String): String? = null

    override suspend fun upsertCompanyIndustry(normalizedCompany: String, industry: String) = Unit
}

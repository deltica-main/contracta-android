package com.example.businesscardscanner.domain.repository

import com.example.businesscardscanner.domain.model.Contact
import com.example.businesscardscanner.domain.model.ContactSummary
import com.example.businesscardscanner.domain.model.Interaction
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun contactSummaries(): Flow<List<ContactSummary>>
    fun contactById(id: Long): Flow<Contact?>
    suspend fun contactByIdOnce(id: Long): Contact?
    suspend fun allContacts(): List<Contact>
    suspend fun allInteractions(): List<Interaction>
    fun interactionsForContact(contactId: Long): Flow<List<Interaction>>
    suspend fun insertContact(contact: Contact): Long
    suspend fun updateContact(contact: Contact)
    suspend fun deleteContact(contactId: Long)
    suspend fun insertInteraction(interaction: Interaction): Long
    suspend fun findDuplicates(email: String?, phone: String?): List<Contact>
    suspend fun getCompanyIndustry(normalizedCompany: String): String?
    suspend fun upsertCompanyIndustry(normalizedCompany: String, industry: String)
}

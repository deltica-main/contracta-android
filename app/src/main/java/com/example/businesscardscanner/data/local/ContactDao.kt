package com.example.businesscardscanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert
    suspend fun insert(contact: ContactEntity): Long

    @Update
    suspend fun update(contact: ContactEntity)

    @Query("SELECT * FROM contacts WHERE id = :id")
    fun getById(id: Long): Flow<ContactEntity?>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getByIdOnce(id: Long): ContactEntity?

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY id ASC")
    suspend fun getAllOnce(): List<ContactEntity>

    @Query("SELECT company FROM contacts WHERE company IS NOT NULL")
    suspend fun getAllCompanyNames(): List<String>

    @Query("""
        SELECT
            contacts.*,
            MAX(interactions.dateTime) AS lastInteractionTime,
            MAX(CASE
                WHEN interactions.notes IS NOT NULL AND interactions.notes != '' THEN 1
                ELSE 0
            END) AS hasNotes,
            GROUP_CONCAT(interactions.notes, ' ') AS interactionNotes,
            GROUP_CONCAT(interactions.meetingLocationName, ' ') AS interactionLocations,
            GROUP_CONCAT(interactions.relationshipType, ' ') AS interactionRelationships
        FROM contacts
        LEFT JOIN interactions ON interactions.contactId = contacts.id
        GROUP BY contacts.id
    """)
    fun getContactSummaries(): Flow<List<ContactSummaryEntity>>

    @Query("""
        SELECT DISTINCT contacts.* FROM contacts
        LEFT JOIN interactions ON interactions.contactId = contacts.id
        WHERE contacts.name LIKE '%' || :query || '%'
           OR contacts.company LIKE '%' || :query || '%'
           OR contacts.title LIKE '%' || :query || '%'
           OR contacts.email LIKE '%' || :query || '%'
           OR contacts.phone LIKE '%' || :query || '%'
           OR contacts.website LIKE '%' || :query || '%'
           OR contacts.industry LIKE '%' || :query || '%'
           OR contacts.industryCustom LIKE '%' || :query || '%'
           OR contacts.rawOcrText LIKE '%' || :query || '%'
           OR interactions.meetingLocationName LIKE '%' || :query || '%'
           OR interactions.city LIKE '%' || :query || '%'
           OR interactions.relationshipType LIKE '%' || :query || '%'
           OR interactions.notes LIKE '%' || :query || '%'
        ORDER BY contacts.name ASC
    """)
    fun search(query: String): Flow<List<ContactEntity>>


    @Query("""
        SELECT * FROM contacts
        WHERE (:email IS NOT NULL AND email = :email)
           OR (:phone IS NOT NULL AND phone = :phone)
    """)
    suspend fun findDuplicates(email: String?, phone: String?): List<ContactEntity>

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun countContacts(): Int
}

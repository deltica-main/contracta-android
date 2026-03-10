package ca.deltica.contactra.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InteractionDao {
    @Insert
    suspend fun insert(interaction: InteractionEntity): Long

    @Update
    suspend fun update(interaction: InteractionEntity)

    @Query("SELECT * FROM interactions WHERE contactId = :contactId ORDER BY dateTime DESC")
    fun interactionsForContact(contactId: Long): Flow<List<InteractionEntity>>

    @Query("SELECT * FROM interactions ORDER BY id ASC")
    suspend fun getAllOnce(): List<InteractionEntity>

    @Query("SELECT COUNT(*) FROM interactions WHERE contactId = :contactId")
    suspend fun countForContact(contactId: Long): Int
}

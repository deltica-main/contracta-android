package ca.deltica.contactra.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single interaction or meeting with a contact. Stores when and
 * where you met, relationship type and optional notes. Latitude/longitude are
 * stored separately to support reverse geocoding.
 */
@Entity(
    tableName = "interactions",
    indices = [Index(value = ["contactId"])],
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class InteractionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val dateTime: Long,
    val meetingLocationName: String?,
    val city: String?,
    val relationshipType: String?,
    val notes: String?,
    val latitude: Double?,
    val longitude: Double?
)

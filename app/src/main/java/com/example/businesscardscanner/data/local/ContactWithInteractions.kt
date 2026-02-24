package com.example.businesscardscanner.data.local

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Represents a contact along with all of its interactions. This is useful for
 * displaying full contact details.
 */
data class ContactWithInteractions(
    @Embedded val contact: ContactEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "contactId"
    )
    val interactions: List<InteractionEntity>
)

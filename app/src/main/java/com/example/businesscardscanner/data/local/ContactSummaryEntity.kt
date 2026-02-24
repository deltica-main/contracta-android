package com.example.businesscardscanner.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class ContactSummaryEntity(
    @Embedded val contact: ContactEntity,
    @ColumnInfo(name = "lastInteractionTime") val lastInteractionTime: Long?,
    @ColumnInfo(name = "hasNotes") val hasNotes: Boolean,
    @ColumnInfo(name = "interactionNotes") val interactionNotes: String?,
    @ColumnInfo(name = "interactionLocations") val interactionLocations: String?,
    @ColumnInfo(name = "interactionRelationships") val interactionRelationships: String?
)

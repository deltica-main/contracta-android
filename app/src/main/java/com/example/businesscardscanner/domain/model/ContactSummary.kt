package com.example.businesscardscanner.domain.model

data class ContactSummary(
    val contact: Contact,
    val lastInteractionTime: Long?,
    val hasNotes: Boolean,
    val interactionNotes: String?,
    val interactionLocations: String?,
    val interactionRelationships: String?
)

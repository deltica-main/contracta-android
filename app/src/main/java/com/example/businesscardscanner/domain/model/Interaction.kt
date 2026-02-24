package com.example.businesscardscanner.domain.model

data class Interaction(
    val id: Long = 0,
    val contactId: Long,
    val dateTime: Long,
    val meetingLocationName: String?,
    val city: String?,
    val relationshipType: String?,
    val notes: String?,
    val latitude: Double?,
    val longitude: Double?
)

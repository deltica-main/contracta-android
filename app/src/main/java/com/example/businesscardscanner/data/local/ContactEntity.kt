package com.example.businesscardscanner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a contact extracted from a business card. The raw OCR text is stored
 * for future improvement of parsing. Industry fields store the final value
 * plus optional custom text and provenance ("user_selected", "enrichment_inferred",
 * or "heuristic_inferred").
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String?,
    val title: String?,
    val company: String?,
    val email: String?,
    val phone: String?,
    val website: String?,
    val industry: String?,
    val industryCustom: String? = null,
    val industrySource: String?,
    val rawOcrText: String?,
    val imagePath: String? = null,
    val rawImagePath: String? = null,
    val cardCropQuad: String? = null,
    val cardCropVersion: Int = 0,
    val phoneExportStatus: String? = null,
    val phoneExportedAt: Long? = null,
    val phoneExportPayloadHash: String? = null
)

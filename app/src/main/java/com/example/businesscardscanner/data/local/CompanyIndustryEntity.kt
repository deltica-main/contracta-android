package com.example.businesscardscanner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores a normalized company name to industry mapping so future scans can
 * auto-populate the industry for the same company.
 */
@Entity(tableName = "company_industry")
data class CompanyIndustryEntity(
    @PrimaryKey val companyNormalized: String,
    val industry: String,
    val updatedAt: Long = System.currentTimeMillis()
)

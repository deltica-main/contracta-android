package ca.deltica.contactra.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CompanyIndustryDao {
    @Query("SELECT industry FROM company_industry WHERE companyNormalized = :companyNormalized LIMIT 1")
    suspend fun getIndustry(companyNormalized: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: CompanyIndustryEntity)

    @Query("DELETE FROM company_industry WHERE companyNormalized = :companyNormalized")
    suspend fun deleteByCompany(companyNormalized: String): Int

    @Query("SELECT COUNT(*) FROM company_industry WHERE companyNormalized = :companyNormalized")
    suspend fun countByCompany(companyNormalized: String): Int
}

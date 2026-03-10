package ca.deltica.contactra.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The Room database for contacts and interactions. 
 * Version 4: Added industry custom field to ContactEntity.
 * Version 5: Added card crop metadata fields for contact image upgrades.
 * Version 6: Added phone contacts export audit fields.
 * Version 7: Added index on interactions.contactId for foreign-key performance.
 * Use destructive migration for simplicity in an MVP.
 */
@Database(
    entities = [ContactEntity::class, InteractionEntity::class, CompanyIndustryEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun interactionDao(): InteractionDao
    abstract fun companyIndustryDao(): CompanyIndustryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "business_card_scanner_db"
                )
                    .addMigrations(MIGRATION_4_5)
                    .addMigrations(MIGRATION_5_6)
                    .addMigrations(MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE contacts ADD COLUMN rawImagePath TEXT")
                database.execSQL("ALTER TABLE contacts ADD COLUMN cardCropQuad TEXT")
                database.execSQL("ALTER TABLE contacts ADD COLUMN cardCropVersion INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE contacts ADD COLUMN phoneExportStatus TEXT")
                database.execSQL("ALTER TABLE contacts ADD COLUMN phoneExportedAt INTEGER")
                database.execSQL("ALTER TABLE contacts ADD COLUMN phoneExportPayloadHash TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_interactions_contactId ON interactions(contactId)"
                )
            }
        }
    }
}

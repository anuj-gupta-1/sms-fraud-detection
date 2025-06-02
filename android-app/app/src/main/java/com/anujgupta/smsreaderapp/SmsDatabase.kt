package com.anujgupta.smsreaderapp

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

/**
 * SmsDatabase - The actual database container
 * Think of this as the library building that houses all your message files
 */
@Database(
    entities = [SmsMessage::class],
    version = 2,  // Version 2 to include scam detection fields
    exportSchema = false
)
abstract class SmsDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao

    companion object {
        @Volatile
        private var INSTANCE: SmsDatabase? = null

        // This handles upgrading from the old database structure to the new one
        // Think of it as a renovation plan that tells the database how to add the new columns
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add the new scam detection columns to existing table
                database.execSQL("""
                    ALTER TABLE sms_messages ADD COLUMN isAnalyzed INTEGER NOT NULL DEFAULT 0
                """)
                database.execSQL("""
                    ALTER TABLE sms_messages ADD COLUMN classification TEXT
                """)
                database.execSQL("""
                    ALTER TABLE sms_messages ADD COLUMN confidence TEXT
                """)
                database.execSQL("""
                    ALTER TABLE sms_messages ADD COLUMN analysisDate INTEGER
                """)
            }
        }

        /**
         * Get database instance - creates one if it doesn't exist
         * Like having only one key to the library building
         */
        fun getDatabase(context: Context): SmsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmsDatabase::class.java,
                    "sms_database"
                )
                    .addMigrations(MIGRATION_1_2)  // Handle database upgrades
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
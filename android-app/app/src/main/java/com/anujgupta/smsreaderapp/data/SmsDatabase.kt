package com.anujgupta.smsreaderapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SmsMessage::class],
    version = 3,
    exportSchema = false
)
abstract class SmsDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao

    companion object {
        @Volatile
        private var INSTANCE: SmsDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    // First check if the column already exists
                    val cursor = database.query("SELECT * FROM sqlite_master WHERE type='table' AND name='sms_messages'")
                    cursor.use { 
                        if (cursor.moveToFirst()) {
                            val tableInfo = cursor.getString(cursor.getColumnIndex("sql"))
                            if (!tableInfo.contains("isRead")) {
                                // Add the isRead column only if it doesn't exist
                                database.execSQL("ALTER TABLE sms_messages ADD COLUMN isRead INTEGER NOT NULL DEFAULT 1")
                            }
                        }
                    }
                } catch (e: Exception) {
                    // If anything goes wrong, recreate the table
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS sms_messages_new (
                            id INTEGER NOT NULL PRIMARY KEY,
                            address TEXT NOT NULL,
                            body TEXT NOT NULL,
                            date INTEGER NOT NULL,
                            type INTEGER NOT NULL,
                            isRead INTEGER NOT NULL DEFAULT 1,
                            isAnalyzed INTEGER NOT NULL DEFAULT 0,
                            classification TEXT,
                            confidence TEXT,
                            analysisDate INTEGER
                        )
                    """)

                    // Copy data from old table if it exists
                    database.execSQL("""
                        INSERT OR IGNORE INTO sms_messages_new (
                            id, address, body, date, type, isAnalyzed, 
                            classification, confidence, analysisDate
                        )
                        SELECT id, address, body, date, type, isAnalyzed, 
                               classification, confidence, analysisDate
                        FROM sms_messages
                    """)

                    // Drop old table
                    database.execSQL("DROP TABLE IF EXISTS sms_messages")

                    // Rename new table to original name
                    database.execSQL("ALTER TABLE sms_messages_new RENAME TO sms_messages")
                }
            }
        }

        fun getDatabase(context: Context): SmsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmsDatabase::class.java,
                    "sms_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration() // This will delete the database if schema changes
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 
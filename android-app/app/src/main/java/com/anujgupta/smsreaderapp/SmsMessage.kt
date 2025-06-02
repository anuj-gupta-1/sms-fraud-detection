package com.anujgupta.smsreaderapp

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SmsMessage - This represents one text message in your database
 * Think of it like a digital filing card for each SMS
 */
@Entity(tableName = "sms_messages")
data class SmsMessage(
    @PrimaryKey val id: Long,                    // Unique ID for each message
    val address: String,                         // Phone number who sent it
    val body: String,                           // The actual message text
    val date: Long,                             // When it was received (timestamp)
    val type: Int,                              // 1 = received, 2 = sent

    // Scam detection fields - these get filled in after AI analysis
    val isAnalyzed: Boolean = false,            // Has this message been checked for scams?
    val classification: String? = null,          // SCAM, LEGITIMATE, SUSPICIOUS, etc.
    val confidence: String? = null,             // HIGH, MEDIUM, LOW confidence level
    val analysisDate: Long? = null              // When the analysis was done
)
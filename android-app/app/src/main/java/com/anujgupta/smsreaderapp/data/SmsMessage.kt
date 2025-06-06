package com.anujgupta.smsreaderapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_messages")
data class SmsMessage(
    @PrimaryKey val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int, // 1 for received, 2 for sent
    val isRead: Boolean = true,
    val isAnalyzed: Boolean = false,
    val classification: String? = null,
    val confidence: String? = null,
    val analysisDate: Long? = null
) 
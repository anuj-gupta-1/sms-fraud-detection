package com.anujgupta.smsreaderapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    @Query("SELECT * FROM sms_messages ORDER BY date DESC")
    fun getAllMessages(): Flow<List<SmsMessage>>

    @Query("SELECT * FROM sms_messages WHERE classification = 'SCAM' ORDER BY date DESC")
    fun getScamMessages(): Flow<List<SmsMessage>>

    @Query("SELECT COUNT(*) FROM sms_messages")
    suspend fun getMessageCount(): Int

    @Query("SELECT COUNT(*) FROM sms_messages WHERE classification = 'SCAM'")
    suspend fun getScamCount(): Int

    @Query("""
        SELECT * FROM sms_messages 
        WHERE isAnalyzed = 0 
        AND type = 1 
        ORDER BY 
            isRead ASC,  -- Unread messages first
            date DESC    -- Most recent first
        LIMIT :limit
    """)
    suspend fun getUnanalyzedReceivedMessages(limit: Int): List<SmsMessage>

    @Query("SELECT * FROM sms_messages WHERE id IN (:messageIds)")
    suspend fun getMessagesByIds(messageIds: List<Long>): List<SmsMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<SmsMessage>)

    @Query("UPDATE sms_messages SET isAnalyzed = :isAnalyzed, classification = :classification, confidence = :confidence, analysisDate = :analysisDate WHERE id = :messageId")
    suspend fun updateScamAnalysis(
        messageId: Long,
        isAnalyzed: Boolean,
        classification: String?,
        confidence: String?,
        analysisDate: Long?
    )

    @Query("DELETE FROM sms_messages")
    suspend fun deleteAllMessages()
} 
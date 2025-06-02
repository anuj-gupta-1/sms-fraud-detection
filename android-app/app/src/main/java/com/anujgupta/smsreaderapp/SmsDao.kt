package com.anujgupta.smsreaderapp

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * SmsDao - Database Access Object
 * This is like your app's librarian - it knows how to find, store, and organize messages
 */
@Dao
interface SmsDao {

    /**
     * Get all messages from the database
     * Like asking the librarian: "Show me every book you have"
     */
    @Query("SELECT * FROM sms_messages ORDER BY date DESC")
    fun getAllMessages(): Flow<List<SmsMessage>>

    /**
     * Get only messages that were classified as scams
     * Like asking: "Show me only the suspicious messages"
     */
    @Query("SELECT * FROM sms_messages WHERE classification = 'SCAM' ORDER BY date DESC")
    fun getScamMessages(): Flow<List<SmsMessage>>

    /**
     * Get messages that haven't been analyzed yet (only received messages)
     * Like asking: "Show me new messages I haven't checked for scams yet"
     */
    @Query("SELECT * FROM sms_messages WHERE isAnalyzed = 0 AND type = 1 LIMIT :limit")
    suspend fun getUnanalyzedReceivedMessages(limit: Int): List<SmsMessage>

    /**
     * Count how many scam messages we've found
     * Like asking: "How many suspicious messages do we have?"
     */
    @Query("SELECT COUNT(*) FROM sms_messages WHERE classification = 'SCAM'")
    suspend fun getScamCount(): Int

    /**
     * Insert new messages into the database
     * Like giving the librarian a stack of new books to catalog
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<SmsMessage>)

    /**
     * Insert a single message
     * Like handing the librarian one book to add to the collection
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: SmsMessage)

    /**
     * Update a message with scam analysis results
     * Like updating a book's catalog card with new information
     */
    @Query("""
        UPDATE sms_messages 
        SET isAnalyzed = :isAnalyzed, 
            classification = :classification, 
            confidence = :confidence, 
            analysisDate = :analysisDate 
        WHERE id = :messageId
    """)
    suspend fun updateScamAnalysis(
        messageId: Long,
        isAnalyzed: Boolean,
        classification: String,
        confidence: String,
        analysisDate: Long
    )

    /**
     * Delete all messages (for testing purposes)
     * Like asking the librarian to clear out the entire collection
     */
    @Query("DELETE FROM sms_messages")
    suspend fun deleteAllMessages()

    /**
     * Get total message count
     * Like asking: "How many messages do we have in total?"
     */
    @Query("SELECT COUNT(*) FROM sms_messages")
    suspend fun getMessageCount(): Int
}
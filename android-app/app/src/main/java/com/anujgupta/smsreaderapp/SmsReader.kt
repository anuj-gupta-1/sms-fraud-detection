package com.anujgupta.smsreaderapp

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log

/**
 * SmsReader - Reads SMS messages from your phone's storage
 * Think of this as a digital assistant that can look through your phone's
 * message history and organize it for analysis
 */
class SmsReader(private val context: Context) {

    companion object {
        private const val TAG = "SmsReader"
    }

    /**
     * Read all SMS messages from the phone
     * This is like asking your assistant: "Please go through all my text messages
     * and make me a list of each one with details"
     */
    fun readAllSmsMessages(): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()

        try {
            // This is like asking Android: "Can I see the SMS inbox?"
            val uri = Telephony.Sms.CONTENT_URI

            // These are the specific details we want about each message
            val projection = arrayOf(
                Telephony.Sms._ID,          // Unique ID
                Telephony.Sms.ADDRESS,      // Phone number
                Telephony.Sms.BODY,         // Message text
                Telephony.Sms.DATE,         // When received
                Telephony.Sms.TYPE          // Sent or received
            )

            // Get messages sorted by newest first
            val sortOrder = "${Telephony.Sms.DATE} DESC"

            // This is like opening the SMS database and asking for specific information
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                null,  // We want all messages (no filter)
                null,
                sortOrder
            )

            cursor?.use { c ->
                // Go through each message like reading through a filing cabinet
                while (c.moveToNext()) {
                    try {
                        // Extract information from each message
                        val id = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms._ID))
                        val address = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "Unknown"
                        val body = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                        val date = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.DATE))
                        val type = c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.TYPE))

                        // Create a SmsMessage object (like making a digital index card)
                        val message = SmsMessage(
                            id = id,
                            address = address,
                            body = body,
                            date = date,
                            type = type
                        )

                        messages.add(message)

                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading SMS message: ${e.message}")
                        // Continue reading other messages even if one fails
                    }
                }
            }

            Log.i(TAG, "Successfully read ${messages.size} SMS messages")

        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied to read SMS: ${e.message}")
            throw Exception("Permission denied. Please grant SMS reading permission.")

        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS messages: ${e.message}")
            throw Exception("Failed to read SMS messages: ${e.message}")
        }

        return messages
    }

    /**
     * Read only received messages (not sent messages)
     * Like asking: "Show me only messages that came TO me, not ones I sent"
     */
    fun readReceivedMessages(): List<SmsMessage> {
        return readAllSmsMessages().filter { it.type == Telephony.Sms.MESSAGE_TYPE_INBOX }
    }

    /**
     * Read messages from a specific phone number
     * Like asking: "Show me all messages from this particular contact"
     */
    fun readMessagesFromNumber(phoneNumber: String): List<SmsMessage> {
        return readAllSmsMessages().filter {
            it.address.contains(phoneNumber, ignoreCase = true)
        }
    }

    /**
     * Read recent messages (last N messages)
     * Like asking: "Show me just the most recent 50 text messages"
     */
    fun readRecentMessages(count: Int): List<SmsMessage> {
        return readAllSmsMessages().take(count)
    }

    /**
     * Get message statistics
     * Like asking for a summary: "How many messages do I have in total?"
     */
    fun getMessageStatistics(): MessageStatistics {
        val allMessages = readAllSmsMessages()
        val receivedMessages = allMessages.filter { it.type == Telephony.Sms.MESSAGE_TYPE_INBOX }
        val sentMessages = allMessages.filter { it.type == Telephony.Sms.MESSAGE_TYPE_SENT }

        return MessageStatistics(
            totalMessages = allMessages.size,
            receivedMessages = receivedMessages.size,
            sentMessages = sentMessages.size,
            uniqueSenders = receivedMessages.map { it.address }.distinct().size
        )
    }
}

/**
 * Data class to hold SMS statistics
 * Like a summary report of your messaging activity
 */
data class MessageStatistics(
    val totalMessages: Int,
    val receivedMessages: Int,
    val sentMessages: Int,
    val uniqueSenders: Int
)
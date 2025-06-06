package com.anujgupta.smsreaderapp.services

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.anujgupta.smsreaderapp.data.SmsMessage

class SmsReader(private val context: Context) {
    companion object {
        private const val TAG = "SmsReader"
    }

    fun readAllSmsMessages(): List<SmsMessage> {
        Log.d(TAG, "Starting to read SMS messages")
        val messages = mutableListOf<SmsMessage>()
        
        try {
            // First query unread messages
            Log.d(TAG, "Querying unread messages")
            var cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                null,
                "${Telephony.Sms.READ} = 0", // Select unread messages
                null,
                "${Telephony.Sms.DATE} DESC" // Sort by date descending
            )

            cursor?.use {
                Log.d(TAG, "Processing unread messages cursor")
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
                val readIndex = it.getColumnIndex(Telephony.Sms.READ)

                var count = 0
                while (it.moveToNext()) {
                    try {
                        val id = it.getLong(idIndex)
                        val address = it.getString(addressIndex) ?: "Unknown"
                        val body = it.getString(bodyIndex) ?: ""
                        val date = it.getLong(dateIndex)
                        val type = it.getInt(typeIndex)
                        val isRead = it.getInt(readIndex) == 1

                        messages.add(
                            SmsMessage(
                                id = id,
                                address = address,
                                body = body,
                                date = date,
                                type = type,
                                isRead = isRead
                            )
                        )
                        count++
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing unread message", e)
                    }
                }
                Log.d(TAG, "Processed $count unread messages")
            }

            // Then query read messages if needed
            if (messages.isEmpty()) {
                Log.d(TAG, "No unread messages found, querying read messages")
                cursor = context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    null,
                    "${Telephony.Sms.READ} = 1", // Select read messages
                    null,
                    "${Telephony.Sms.DATE} DESC" // Sort by date descending
                )

                cursor?.use {
                    Log.d(TAG, "Processing read messages cursor")
                    val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                    val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                    val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                    val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
                    val readIndex = it.getColumnIndex(Telephony.Sms.READ)

                    var count = 0
                    while (it.moveToNext()) {
                        try {
                            val id = it.getLong(idIndex)
                            val address = it.getString(addressIndex) ?: "Unknown"
                            val body = it.getString(bodyIndex) ?: ""
                            val date = it.getLong(dateIndex)
                            val type = it.getInt(typeIndex)
                            val isRead = it.getInt(readIndex) == 1

                            messages.add(
                                SmsMessage(
                                    id = id,
                                    address = address,
                                    body = body,
                                    date = date,
                                    type = type,
                                    isRead = isRead
                                )
                            )
                            count++
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing read message", e)
                        }
                    }
                    Log.d(TAG, "Processed $count read messages")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS messages", e)
            throw e
        }

        Log.d(TAG, "Finished reading ${messages.size} total messages")
        return messages
    }
} 
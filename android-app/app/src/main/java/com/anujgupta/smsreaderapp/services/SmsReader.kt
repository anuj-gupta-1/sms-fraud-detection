package com.anujgupta.smsreaderapp.services

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.anujgupta.smsreaderapp.data.SmsMessage
import java.util.Calendar

class SmsReader(private val context: Context) {
    companion object {
        private const val TAG = "SmsReader"
    }

    fun readAllSmsMessages(): List<SmsMessage> {
        Log.d(TAG, "Starting to read SMS messages from the last 24 hours")
        val messages = mutableListOf<SmsMessage>()
        
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val twentyFourHoursAgo = calendar.timeInMillis

        val selection = "${Telephony.Sms.DATE} > ?"
        val selectionArgs = arrayOf(twentyFourHoursAgo.toString())

        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                null,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                Log.d(TAG, "Processing messages from the last 24 hours")
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
                        Log.e(TAG, "Error processing message", e)
                    }
                }
                Log.d(TAG, "Processed $count messages")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS messages", e)
            throw e
        }

        Log.d(TAG, "Finished reading ${messages.size} total messages")
        return messages
    }
} 
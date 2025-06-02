package com.anujgupta.smsreaderapp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ScamDetectionService - Connects to AI backend for scam analysis
 * Think of this as your app's connection to a smart detective who can
 * analyze messages and tell you if they look suspicious
 */
class ScamDetectionService {

    companion object {
        private const val TAG = "ScamDetectionService"

        // This is the address where your Python AI server will be running
        // Like the address of the detective's office
   //     private const val BASE_URL = "http://10.0.2.2:5000"  // For Android emulator
        // If testing on real phone connected via USB, use your computer's IP:
        private const val BASE_URL = "http://192.168.29.36:5000"  // Replace with your computer's IP

        private const val ANALYZE_ENDPOINT = "/analyze"
        private const val HEALTH_ENDPOINT = "/health"
    }

    // HTTP client - like having a messenger to carry messages back and forth
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)      // Wait 30 seconds to connect
        .readTimeout(60, TimeUnit.SECONDS)         // Wait 60 seconds for response
        .writeTimeout(30, TimeUnit.SECONDS)        // Wait 30 seconds to send data
        .build()

    /**
     * Test if the Python AI server is running and reachable
     * Like calling the detective's office to see if anyone answers
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Testing connection to $BASE_URL$HEALTH_ENDPOINT")

            val request = Request.Builder()
                .url("$BASE_URL$HEALTH_ENDPOINT")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val isSuccess = response.isSuccessful

            Log.d(TAG, "Connection test result: $isSuccess")
            response.close()

            return@withContext isSuccess

        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Send an SMS message to the AI for scam analysis
     * Like handing a suspicious letter to a detective and asking "Is this a scam?"
     */
    suspend fun analyzeSms(messageBody: String, senderAddress: String): ScamAnalysisResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Analyzing message from $senderAddress")

            // Prepare the data to send to the AI (like filling out a case form)
            val jsonData = JSONObject().apply {
                put("message", messageBody)
                put("sender", senderAddress)
                put("timestamp", System.currentTimeMillis())
            }

            // Create the request to send to the Python server
            val requestBody = jsonData.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL$ANALYZE_ENDPOINT")
                .post(requestBody)
                .build()

            // Send the request and wait for response
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                // Parse the AI's response
                val responseBody = response.body?.string()
                response.close()

                if (responseBody != null) {
                    return@withContext parseAnalysisResponse(responseBody)
                } else {
                    return@withContext ScamAnalysisResult(
                        classification = "ERROR",
                        confidence = "NONE",
                        error = "Empty response from server"
                    )
                }
            } else {
                Log.e(TAG, "Server error: ${response.code}")
                response.close()
                return@withContext ScamAnalysisResult(
                    classification = "ERROR",
                    confidence = "NONE",
                    error = "Server returned error: ${response.code}"
                )
            }

        } catch (e: IOException) {
            Log.e(TAG, "Network error during analysis: ${e.message}")
            return@withContext ScamAnalysisResult(
                classification = "ERROR",
                confidence = "NONE",
                error = "Network error: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during analysis: ${e.message}")
            return@withContext ScamAnalysisResult(
                classification = "ERROR",
                confidence = "NONE",
                error = "Analysis failed: ${e.message}"
            )
        }
    }

    /**
     * Parse the AI's response into a structured result
     * Like reading the detective's report and organizing the findings
     */
    private fun parseAnalysisResponse(responseBody: String): ScamAnalysisResult {
        try {
            val jsonResponse = JSONObject(responseBody)

            val classification = jsonResponse.optString("classification", "UNKNOWN")
            val confidence = jsonResponse.optString("confidence", "UNKNOWN")
            val reason = jsonResponse.optString("reason", "")
            val riskScore = jsonResponse.optDouble("risk_score", 0.0)

            Log.d(TAG, "Analysis result: $classification ($confidence confidence)")

            return ScamAnalysisResult(
                classification = classification,
                confidence = confidence,
                reason = reason,
                riskScore = riskScore
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing analysis response: ${e.message}")
            return ScamAnalysisResult(
                classification = "ERROR",
                confidence = "NONE",
                error = "Failed to parse server response: ${e.message}"
            )
        }
    }
}

/**
 * ScamAnalysisResult - The AI's verdict on whether a message is a scam
 * Think of this as the detective's report after investigating a suspicious message
 */
data class ScamAnalysisResult(
    val classification: String,        // SCAM, LEGITIMATE, SUSPICIOUS, etc.
    val confidence: String,           // HIGH, MEDIUM, LOW
    val reason: String = "",          // Why the AI thinks this (optional)
    val riskScore: Double = 0.0,      // Risk score from 0.0 to 1.0
    val error: String? = null         // Any error that occurred
) {
    /**
     * Quick check: Is this message classified as a scam?
     * Like asking: "Should I be worried about this message?"
     */
    val isScam: Boolean
        get() = classification.equals("SCAM", ignoreCase = true)

    /**
     * Quick check: Is this message suspicious (not definitely scam, but concerning)?
     * Like asking: "Should I be cautious about this message?"
     */
    val isSuspicious: Boolean
        get() = classification.equals("SUSPICIOUS", ignoreCase = true)

    /**
     * Was there an error during analysis?
     * Like asking: "Did something go wrong during the investigation?"
     */
    val hasError: Boolean
        get() = error != null || classification.equals("ERROR", ignoreCase = true)
}
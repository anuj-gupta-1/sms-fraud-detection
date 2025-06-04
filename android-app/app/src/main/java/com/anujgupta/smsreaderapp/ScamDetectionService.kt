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
 */
class ScamDetectionService {

    companion object {
        private const val TAG = "ScamDetectionService"
        // Ensure this IP address is correct for your setup.
        // This should be your computer's IP address on your local network if running the Python server there.
        // The Android emulator uses 10.0.2.2 to refer to the host machine's localhost.
        // If using a physical device for testing, ensure it's on the same Wi-Fi network
        // and replace 10.0.2.2 with your computer's actual local IP address.
        private const val BASE_URL = "http://192.168.29.36:5000" // As per your original file
        private const val ANALYZE_ENDPOINT = "/analyze" //
        private const val HEALTH_ENDPOINT = "/health" //
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)      // Wait 30 seconds to connect
        .readTimeout(60, TimeUnit.SECONDS)         // Wait 60 seconds for response
        .writeTimeout(30, TimeUnit.SECONDS)        // Wait 30 seconds to send data
        .build()

    /**
     * Test if the Python AI server is running and reachable.
     * Returns a pair: Boolean for success, and a String for a detailed status message.
     */
    suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Testing connection to $BASE_URL$HEALTH_ENDPOINT")
            val request = Request.Builder()
                .url("$BASE_URL$HEALTH_ENDPOINT")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            val isSuccess = response.isSuccessful

            Log.d(TAG, "Connection test result: $isSuccess, Body: $responseBody")

            var statusMessage = if (isSuccess) "Connected ✓" else "Disconnected ✗ (Code: ${response.code})"

            if (isSuccess && responseBody != null) {
                try {
                    val jsonResponse = JSONObject(responseBody)
                    val ollamaStatus = jsonResponse.optString("ollama_status", "N/A") // From main.py health check
                    val currentModel = jsonResponse.optString("current_model", "N/A") // From main.py health check
                    statusMessage = "Connected ✓ (Ollama: $ollamaStatus, Model: $currentModel)"
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing health response: ${e.message}")
                    statusMessage = "Connected ✓ (but error parsing health details)"
                }
            } else if (!isSuccess) {
                statusMessage = "Server Unreachable (Code: ${response.code})"
            }

            response.close()
            return@withContext Pair(isSuccess, statusMessage)

        } catch (e: IOException) {
            Log.e(TAG, "Connection test (network error): ${e.message}")
            return@withContext Pair(false, "Network Error: ${e.message}")
        }
        catch (e: Exception) {
            Log.e(TAG, "Connection test failed: ${e.message}")
            return@withContext Pair(false, "Connection Error: ${e.message}")
        }
    }

    /**
     * Send an SMS message to the AI for scam analysis
     */
    suspend fun analyzeSms(messageBody: String, senderAddress: String): ScamAnalysisResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Analyzing message from $senderAddress to $BASE_URL$ANALYZE_ENDPOINT")

            // Prepare the data to send to the AI
            val jsonData = JSONObject().apply {
                put("message", messageBody) // Expected by main.py /analyze
                put("sender", senderAddress) // Expected by main.py /analyze
                put("timestamp", System.currentTimeMillis()) //
            }

            val requestBody = jsonData.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL$ANALYZE_ENDPOINT")
                .post(requestBody)
                .build()

            // Send the request and wait for response
            val response = client.newCall(request).execute()
            val responseBodyString = response.body?.string() // Read body once
            Log.d(TAG, "Raw response from /analyze: $responseBodyString")


            if (response.isSuccessful && responseBodyString != null) {
                response.close() // Close after reading
                return@withContext parseAnalysisResponse(responseBodyString)
            } else {
                Log.e(TAG, "Server error: ${response.code}, Body: $responseBodyString")
                response.close()
                return@withContext ScamAnalysisResult(
                    classification = "ERROR",
                    confidence = "NONE",
                    error = "Server returned error: ${response.code} - ${response.message}"
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
     */
    private fun parseAnalysisResponse(responseBody: String): ScamAnalysisResult {
        try {
            val jsonResponse = JSONObject(responseBody)

            // Helper functions for robust optional field parsing
            fun JSONObject.optNullableString(name: String): String? = if (has(name)) optString(name) else null
            fun JSONObject.optNullableBoolean(name: String): Boolean? = if (has(name)) optBoolean(name) else null
            fun JSONObject.optNullableInt(name: String): Int? = if (has(name)) optInt(name) else null
            fun JSONObject.optNullableDouble(name: String): Double? = if (has(name)) optDouble(name) else null

            // Fields from main.py's classify_sms_with_ollama or analyze_with_fallback_rules, plus Flask route additions
            val classification = jsonResponse.getString("classification")
            val confidence = jsonResponse.getString("confidence")
            val confidenceScore = jsonResponse.optNullableInt("confidence_score")
            val reason = jsonResponse.optString("reason", "N/A") // Default if missing
            val riskScore = jsonResponse.optDouble("risk_score", 0.0) // Default if missing

            val detectionMethod = jsonResponse.optNullableString("detection_method")
            val modelUsed = jsonResponse.optNullableString("model_used")

            val sender = jsonResponse.optNullableString("sender")
            val messagePreview = jsonResponse.optNullableString("message_preview")
            val alertLevel = jsonResponse.optNullableString("alert_level")
            val processingTimeSeconds = jsonResponse.optNullableDouble("processing_time_seconds")
            val timestamp = jsonResponse.optNullableString("timestamp")

            val error = jsonResponse.optNullableString("error")
            val fallbackUsed = jsonResponse.optNullableBoolean("fallback_used")

            Log.d(TAG, "Parsed Analysis result: $classification ($confidence)")

            return ScamAnalysisResult(
                classification = classification,
                confidence = confidence,
                confidence_score = confidenceScore,
                reason = reason,
                risk_score = riskScore,
                detection_method = detectionMethod,
                model_used = modelUsed,
                sender = sender,
                message_preview = messagePreview,
                alert_level = alertLevel,
                processing_time_seconds = processingTimeSeconds,
                timestamp = timestamp,
                error = error,
                fallback_used = fallbackUsed
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing analysis response: ${e.message} from body: $responseBody")
            return ScamAnalysisResult(
                classification = "ERROR",
                confidence = "NONE",
                error = "Failed to parse server response: ${e.message}"
            )
        }
    }
}
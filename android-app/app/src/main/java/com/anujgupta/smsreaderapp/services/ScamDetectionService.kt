package com.anujgupta.smsreaderapp.services

import com.anujgupta.smsreaderapp.models.SmsAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ScamDetectionService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "http://10.0.2.2:5000" // For Android Emulator, points to localhost
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val isSuccess = response.isSuccessful
            val status = if (isSuccess) {
                val json = JSONObject(response.body?.string() ?: "{}")
                "Connected ✓ (${json.optString("ollama_status", "Unknown")})"
            } else {
                "Error ✗"
            }
            return@withContext Pair(isSuccess, status)
        } catch (e: Exception) {
            return@withContext Pair(false, "Offline ✗")
        }
    }

    suspend fun analyzeSms(message: String, sender: String): SmsAnalysisResult = withContext(Dispatchers.IO) {
        val requestJson = JSONObject().apply {
            put("message", message)
            put("sender", sender)
        }

        val request = Request.Builder()
            .url("$baseUrl/analyze")
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Analysis failed: ${response.code}")
        }

        val json = JSONObject(response.body?.string() ?: throw Exception("Empty response"))
        
        return@withContext SmsAnalysisResult(
            sender = json.getString("sender"),
            messagePreview = json.getString("message_preview"),
            classification = json.getString("classification"),
            confidence = json.getString("confidence"),
            confidenceScore = json.getInt("confidence_score"),
            reason = json.optString("reason"),
            riskScore = json.getDouble("risk_score"),
            detectionMethod = json.getString("detection_method"),
            alertLevel = json.getString("alert_level"),
            sender_watchlist_status = json.getString("sender_watchlist_status"),
            processingTimeSeconds = json.getDouble("processing_time_seconds"),
            timestamp = json.getString("timestamp")
        )
    }
} 
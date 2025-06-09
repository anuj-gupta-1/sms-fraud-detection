package com.anujgupta.smsreaderapp.services

import com.anujgupta.smsreaderapp.models.SmsAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class BatchSmsInfo(val id: Long, val message: String, val sender: String)

class ScamDetectionService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "http://192.168.29.36:5000"
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

    suspend fun analyzeMultipleSms(messages: List<BatchSmsInfo>): List<SmsAnalysisResult> = withContext(Dispatchers.IO) {
        val messagesJsonArray = JSONArray()
        messages.forEach { msg ->
            val msgJson = JSONObject().apply {
                put("id", msg.id)
                put("message", msg.message)
                put("sender", msg.sender)
            }
            messagesJsonArray.put(msgJson)
        }
        val requestJson = JSONObject().apply {
            put("messages", messagesJsonArray)
        }

        val request = Request.Builder()
            .url("$baseUrl/batch")
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Batch analysis failed: ${response.code}")
        }

        val jsonResponse = JSONArray(response.body?.string() ?: throw Exception("Empty response"))
        val results = mutableListOf<SmsAnalysisResult>()
        for (i in 0 until jsonResponse.length()) {
            val json = jsonResponse.getJSONObject(i)
            results.add(SmsAnalysisResult(
                id = json.getLong("id"),
                sender = json.getString("sender"),
                message_content = json.getString("message_content"),
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
            ))
        }
        return@withContext results
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
            message_content = json.getString("message_content"),
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
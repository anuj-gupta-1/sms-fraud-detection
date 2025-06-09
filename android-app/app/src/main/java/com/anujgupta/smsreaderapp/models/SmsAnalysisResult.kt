package com.anujgupta.smsreaderapp.models

data class SmsAnalysisResult(
    val id: Long? = null,
    val sender: String,
    val message_content: String,
    val classification: String,
    val confidence: String,
    val confidenceScore: Int,
    val reason: String?,
    val riskScore: Double,
    val detectionMethod: String,
    val alertLevel: String,
    val sender_watchlist_status: String = "not_on_watchlist",
    val processingTimeSeconds: Double,
    val timestamp: String,
    val isScam: Boolean = classification.equals("SCAM", ignoreCase = true)
) {
    fun toDisplayString(contactName: String? = null): String {
        val senderInfo = if (contactName != null) {
            "Contact: $contactName"
        } else {
            "Unknown Sender"
        }
        
        val watchlistInfo = if (sender_watchlist_status == "on_watchlist") {
            "\n⚠️ Sender is on watchlist!"
        } else {
            ""
        }

        return """
            $senderInfo
            Classification: $classification
            Confidence: $confidence$watchlistInfo
        """.trimIndent()
    }
} 
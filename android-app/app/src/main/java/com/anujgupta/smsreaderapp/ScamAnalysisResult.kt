package com.anujgupta.smsreaderapp

/**
 * ScamAnalysisResult - The AI's verdict on whether a message is a scam
 * Reflects the JSON response from the Python backend's /analyze endpoint.
 */
data class ScamAnalysisResult(
    // Core analysis fields
    val classification: String,        // e.g., "SCAM", "LEGITIMATE"
    val confidence: String,           // e.g., "HIGH", "MEDIUM", "LOW"
    val confidence_score: Int? = null, // e.g., 85 (numeric score)
    val reason: String = "",          // Explanation from the AI
    val risk_score: Double = 0.0,     // Risk score (e.g., 0.0 to 1.0)
    val detection_method: String? = null, // e.g., "LLM", "RULE_BASED"
    val model_used: String? = null,       // e.g., "llama3.2:3b", "Fallback Rules"

    // Fields added by the Flask route wrapper
    val sender: String? = null,              // Sender info from the request
    val message_preview: String? = null,     // Preview of the analyzed message
    val alert_level: String? = null,         // e.g., "HIGH", "MEDIUM", "LOW", "NONE"
    val processing_time_seconds: Double? = null, // Server processing time
    val timestamp: String? = null,           // Timestamp of the analysis

    // Error and fallback indicators
    val error: String? = null,        // Any error message from the backend
    val fallback_used: Boolean? = null, // True if fallback rules were used

    // Enhancement 1: Backend sender number check
    val sender_watchlist_status: String? = null // e.g., "on_watchlist", "none"
) {
    /**
     * Quick check: Is this message classified as a scam?
     */
    val isScam: Boolean //
        get() = classification.equals("SCAM", ignoreCase = true) //

    /**
     * Was there an error during analysis or reported by the server?
     */
    val hasError: Boolean //
        get() = error != null || classification.equals("ERROR", ignoreCase = true) //

    /**
     * Provides a summary string for display.
     */
    fun toDisplayString(contactName: String? = null): String { // Added contactName parameter
        if (hasError) {
            return "Error: ${error ?: "Analysis failed."}" //
        }
        val confidenceText = confidence_score?.let { "$confidence ($it%)" } ?: confidence //

        var senderDisplay = sender ?: "Unknown Sender"
        contactName?.let { senderDisplay = "$it ($sender)" }

        var displayText = "Sender: $senderDisplay\n" + // Display contact name if available
                "Classification: $classification\n" +
                "Confidence: $confidenceText\n" +
                "Reason: $reason\n" +
                "Risk Score: $risk_score\n" +
                "Method: ${detection_method ?: "N/A"} (Model: ${model_used ?: "N/A"})" //

        if (sender_watchlist_status == "on_watchlist") {
            displayText += "\nWatchlist Status: ❗ ON WATCHLIST ❗"
        }

        if (fallback_used == true) {
            displayText += "\n(Used fallback rules)" //
        }
        alert_level?.let {
            if (it != "NONE") displayText += "\nAlert Level: $it" //
        }
        return displayText
    }
}
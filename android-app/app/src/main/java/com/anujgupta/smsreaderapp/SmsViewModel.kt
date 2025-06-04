package com.anujgupta.smsreaderapp

import android.content.Context
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
// Make sure ScamAnalysisResult is imported if it's in its own file
// import com.anujgupta.smsreaderapp.ScamAnalysisResult


class SmsViewModel(
    private val smsDao: SmsDao, //
    private val context: android.content.Context //
) : ViewModel() {

    private val smsReader = SmsReader(context) //
    private val scamDetectionService = ScamDetectionService() //

    // Observable data for the UI
    val allMessages: LiveData<List<SmsMessage>> = smsDao.getAllMessages().asLiveData() //
    val scamMessages: LiveData<List<SmsMessage>> = smsDao.getScamMessages().asLiveData() //

    // UI State
    private var _isLoading = mutableStateOf(false) //
    val isLoading: State<Boolean> = _isLoading //

    private var _isAnalyzing = mutableStateOf(false) //
    val isAnalyzing: State<Boolean> = _isAnalyzing //

    private var _messageCount = mutableStateOf(0) //
    val messageCount: State<Int> = _messageCount //

    private var _scamCount = mutableStateOf(0) //
    val scamCount: State<Int> = _scamCount //

    private var _serverStatus = mutableStateOf("Unknown") //
    val serverStatus: State<String> = _serverStatus //

    // This will hold the formatted string for display in the UI's results card
    private var _lastAnalysisResultDisplay = mutableStateOf("No operations performed yet.") //
    val lastAnalysisResultDisplay: State<String> = _lastAnalysisResultDisplay //

    /**
     * Load SMS messages from phone storage
     */
    fun loadSmsMessages() { //
        viewModelScope.launch {
            _isLoading.value = true
            _lastAnalysisResultDisplay.value = "Loading messages..."
            try {
                withContext(Dispatchers.IO) {
                    val messages = smsReader.readAllSmsMessages() //
                    smsDao.insertMessages(messages) //
                    _messageCount.value = smsDao.getMessageCount() //
                    _scamCount.value = smsDao.getScamCount() //
                }
                _lastAnalysisResultDisplay.value = "Loaded ${_messageCount.value} messages. Found ${_scamCount.value} known scams."
            } catch (e: Exception) {
                _lastAnalysisResultDisplay.value = "Error loading messages: ${e.message}"
                Log.e("SmsViewModel", "Error loading SMS", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Test connection to Python backend server
     */
    fun testServerConnection() { //
        viewModelScope.launch {
            _serverStatus.value = "Testing..."
            try {
                // scamDetectionService.testConnection() now returns Pair<Boolean, String>
                val (isConnected, statusMessage) = scamDetectionService.testConnection()
                _serverStatus.value = statusMessage // Use the detailed message from the service
                if (!isConnected) {
                    _lastAnalysisResultDisplay.value = "Cannot connect to server. Make sure Python backend is running and accessible via the configured IP."
                } else {
                    _lastAnalysisResultDisplay.value = "Server connection test complete. Status: $statusMessage"
                }
            } catch (e: Exception) {
                _serverStatus.value = "Error ✗"
                _lastAnalysisResultDisplay.value = "Connection test failed: ${e.message}"
                Log.e("SmsViewModel", "Error testing server connection", e)
            }
        }
    }

    /**
     * Analyze unscanned messages for scams
     */
    fun analyzeForScams() { //
        viewModelScope.launch {
            if (_messageCount.value == 0) {
                _lastAnalysisResultDisplay.value = "No messages loaded to analyze. Please load messages first."
                return@launch
            }
            _isAnalyzing.value = true
            _lastAnalysisResultDisplay.value = "Starting scam analysis for new messages..."

            try {
                val unanalyzedMessages = withContext(Dispatchers.IO) {
                    // Get messages that haven't been analyzed yet (only received messages)
                    smsDao.getUnanalyzedReceivedMessages(20) // Limit to 20 messages per batch
                }

                if (unanalyzedMessages.isEmpty()) {
                    _lastAnalysisResultDisplay.value = "No new messages to analyze."
                    _isAnalyzing.value = false
                    return@launch
                }

                _lastAnalysisResultDisplay.value = "Analyzing ${unanalyzedMessages.size} new messages..."
                var newScamsFoundInBatch = 0

                unanalyzedMessages.forEachIndexed { index, message ->
                    withContext(Dispatchers.Main) { // Ensure UI updates are on the main thread
                        _lastAnalysisResultDisplay.value = "Analyzing message ${index + 1}/${unanalyzedMessages.size}: \"${message.body.take(30)}...\""
                    }
                    try {
                        val result = scamDetectionService.analyzeSms(message.body, message.address) //

                        // Update the database with the analysis result
                        withContext(Dispatchers.IO) {
                            smsDao.updateScamAnalysis( //
                                messageId = message.id,
                                isAnalyzed = true,
                                classification = result.classification, // Store main classification
                                confidence = result.confidence, // Store confidence string (e.g., "HIGH", "MEDIUM")
                                analysisDate = System.currentTimeMillis()
                                // If you added more fields to SmsMessage (e.g., risk_score, reason), update them here.
                            )
                            if (result.isScam) { // isScam is a property in ScamAnalysisResult
                                newScamsFoundInBatch++
                            }
                        }
                        // Update UI with individual result if needed, or wait for batch summary
                        withContext(Dispatchers.Main){
                            _lastAnalysisResultDisplay.value = "Msg ${index + 1} Result: ${result.classification} (${result.confidence})"
                        }

                    } catch (e: Exception) {
                        Log.e("SmsViewModel", "Error analyzing message ID ${message.id}", e)
                        // Mark as analyzed but with error to avoid re-analyzing constantly
                        withContext(Dispatchers.IO) {
                            smsDao.updateScamAnalysis(
                                messageId = message.id,
                                isAnalyzed = true,
                                classification = "ERROR_ANALYSIS", // Custom classification for app-side errors
                                confidence = "NONE",
                                analysisDate = System.currentTimeMillis()
                            )
                        }
                        withContext(Dispatchers.Main) { // Ensure UI updates are on the main thread
                            _lastAnalysisResultDisplay.value = "Error analyzing message ${index + 1}. ${e.message?.take(100)}"
                        }
                    }
                }

                // After batch processing, update total scam count from DB
                val totalScamsAfterAnalysis = withContext(Dispatchers.IO) { smsDao.getScamCount() }
                _scamCount.value = totalScamsAfterAnalysis
                _lastAnalysisResultDisplay.value = "Analysis complete! Processed ${unanalyzedMessages.size} messages. New scams in this batch: $newScamsFoundInBatch. Total scams in DB: $totalScamsAfterAnalysis."

            } catch (e: Exception) {
                _lastAnalysisResultDisplay.value = "Analysis failed: ${e.message}"
                Log.e("SmsViewModel", "Error during scam analysis batch", e)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    /**
     * Test scam detection with a sample message
     */
    fun testScamDetection() { //
        viewModelScope.launch {
            _isAnalyzing.value = true
            _lastAnalysisResultDisplay.value = "Testing scam detection with a sample message..."
            try {
                val testMessage = "CONGRATULATIONS! You've won \$1000! Click here to claim your prize now: http://suspicious-link.com" //
                val result = scamDetectionService.analyzeSms(testMessage, "TEST_SENDER") //

                // Use the toDisplayString() method from ScamAnalysisResult for formatted output
                _lastAnalysisResultDisplay.value = "Test Result:\n${result.toDisplayString()}"

            } catch (e: Exception) {
                _lastAnalysisResultDisplay.value = "Test failed: ${e.message}"
                Log.e("SmsViewModel", "Error testing scam detection", e)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    /**
     * Clear all messages from the local database
     */
    fun clearMessages() { //
        viewModelScope.launch {
            _isLoading.value = true
            _lastAnalysisResultDisplay.value = "Clearing all messages..."
            withContext(Dispatchers.IO) {
                smsDao.deleteAllMessages() //
                _messageCount.value = 0 //
                _scamCount.value = 0 //
            }
            _lastAnalysisResultDisplay.value = "All messages cleared from the local database."
            _isLoading.value = false
        }
    }

    // Initialize by testing server connection and loading initial counts
    init { //
        viewModelScope.launch {
            // Load initial counts from DB on startup
            _messageCount.value = withContext(Dispatchers.IO) { smsDao.getMessageCount() }
            _scamCount.value = withContext(Dispatchers.IO) { smsDao.getScamCount() }
        }
        testServerConnection() //
    }
}
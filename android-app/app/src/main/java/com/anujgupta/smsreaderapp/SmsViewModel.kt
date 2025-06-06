package com.anujgupta.smsreaderapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.anujgupta.smsreaderapp.data.SmsDao
import com.anujgupta.smsreaderapp.data.SmsMessage
import com.anujgupta.smsreaderapp.models.SmsAnalysisResult
import com.anujgupta.smsreaderapp.services.ScamDetectionService
import com.anujgupta.smsreaderapp.services.SmsReader

class SmsViewModel(
    private val smsDao: SmsDao,
    private val context: Context
) : ViewModel() {
    companion object {
        private const val TAG = "SmsViewModel"
    }

    private val smsReader = SmsReader(context)
    private val scamDetectionService = ScamDetectionService()

    val allMessages: LiveData<List<SmsMessage>> = smsDao.getAllMessages().asLiveData()
    val scamMessages: LiveData<List<SmsMessage>> = smsDao.getScamMessages().asLiveData()

    private var _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private var _isAnalyzing = mutableStateOf(false)
    val isAnalyzing: State<Boolean> = _isAnalyzing

    private var _messageCount = mutableStateOf(0)
    val messageCount: State<Int> = _messageCount

    private var _scamCount = mutableStateOf(0)
    val scamCount: State<Int> = _scamCount

    private var _serverStatus = mutableStateOf("Unknown")
    val serverStatus: State<String> = _serverStatus

    private var _lastAnalysisResultDisplay = mutableStateOf("No operations performed yet.")
    val lastAnalysisResultDisplay: State<String> = _lastAnalysisResultDisplay

    private val _hasContactPermission = mutableStateOf(checkContactsPermission())
    val hasContactPermission: State<Boolean> = _hasContactPermission

    private fun checkContactsPermission(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Contacts permission check: $hasPermission")
        return hasPermission
    }

    fun updateContactPermissionStatus() {
        Log.d(TAG, "Updating contact permission status")
        _hasContactPermission.value = checkContactsPermission()
    }

    private suspend fun getContactName(phoneNumber: String): String? = withContext(Dispatchers.IO) {
        if (!hasContactPermission.value) {
            Log.w(TAG, "READ_CONTACTS permission not granted.")
            return@withContext null
        }
        if (phoneNumber.isBlank()) return@withContext null

        var contactName: String? = null
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            val cursor = context.contentResolver.query(uri, projection, null, null, null)

            cursor?.use {
                if (it.moveToFirst()) {
                    contactName = it.getString(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact for $phoneNumber: ${e.message}")
        }
        return@withContext contactName
    }

    fun loadSmsMessages() {
        viewModelScope.launch {
            Log.d(TAG, "Starting to load SMS messages")
            _isLoading.value = true
            _lastAnalysisResultDisplay.value = "Loading messages..."
            try {
                withContext(Dispatchers.IO) {
                    val messages = smsReader.readAllSmsMessages()
                    Log.d(TAG, "Read ${messages.size} messages from device")
                    smsDao.insertMessages(messages)
                    _messageCount.value = smsDao.getMessageCount()
                    _scamCount.value = smsDao.getScamCount()
                }
                _lastAnalysisResultDisplay.value = "Loaded ${_messageCount.value} messages. Found ${_scamCount.value} known scams."
                Log.d(TAG, "Successfully loaded messages: count=${_messageCount.value}, scams=${_scamCount.value}")
            } catch (e: Exception) {
                val errorMsg = "Error loading messages: ${e.message}"
                _lastAnalysisResultDisplay.value = errorMsg
                Log.e(TAG, errorMsg, e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun testServerConnection() {
        viewModelScope.launch {
            _serverStatus.value = "Testing..."
            try {
                val (isConnected, statusMessage) = scamDetectionService.testConnection()
                _serverStatus.value = statusMessage
                if (!isConnected) {
                    _lastAnalysisResultDisplay.value = "Cannot connect to server. Make sure Python backend is running and accessible."
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

    fun analyzeForScams() {
        viewModelScope.launch {
            if (_messageCount.value == 0) {
                _lastAnalysisResultDisplay.value = "No messages loaded to analyze. Please load messages first."
                return@launch
            }
            _isAnalyzing.value = true
            _lastAnalysisResultDisplay.value = "Starting scam analysis for new messages..."

            try {
                val unanalyzedMessages = withContext(Dispatchers.IO) {
                    smsDao.getUnanalyzedReceivedMessages(5)
                }

                if (unanalyzedMessages.isEmpty()) {
                    _lastAnalysisResultDisplay.value = "No new messages to analyze."
                    _isAnalyzing.value = false
                    return@launch
                }

                var resultsSummary = "Analysis Summary for ${unanalyzedMessages.size} messages:\n"
                var newScamsFound = 0

                unanalyzedMessages.forEachIndexed { index, message ->
                    withContext(Dispatchers.Main) {
                        _lastAnalysisResultDisplay.value = "Analyzing message ${index + 1}/${unanalyzedMessages.size}: ${message.body.take(30)}..."
                    }
                    try {
                        val result = scamDetectionService.analyzeSms(message.body, message.address)
                        val contactName = getContactName(message.address)

                        resultsSummary += "Msg from ${contactName ?: message.address}: ${result.classification}"
                        if (result.sender_watchlist_status == "on_watchlist") resultsSummary += " (ON WATCHLIST)"
                        resultsSummary += "\n"

                        withContext(Dispatchers.IO) {
                            smsDao.updateScamAnalysis(
                                messageId = message.id,
                                isAnalyzed = true,
                                classification = result.classification,
                                confidence = result.confidence,
                                analysisDate = System.currentTimeMillis()
                            )
                            if (result.isScam) {
                                newScamsFound++
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SmsViewModel", "Error analyzing message ID ${message.id}", e)
                        withContext(Dispatchers.IO) {
                            smsDao.updateScamAnalysis(
                                messageId = message.id,
                                isAnalyzed = true,
                                classification = "ERROR",
                                confidence = "NONE",
                                analysisDate = System.currentTimeMillis()
                            )
                        }
                        withContext(Dispatchers.Main) {
                            _lastAnalysisResultDisplay.value = "Error analyzing message ${index + 1}. ${e.message}"
                        }
                    }
                }
                val totalScams = withContext(Dispatchers.IO) { smsDao.getScamCount() }
                _scamCount.value = totalScams
                _lastAnalysisResultDisplay.value = "$resultsSummary\nBatch complete! New scams: $newScamsFound. Total scams: $totalScams."

            } catch (e: Exception) {
                _lastAnalysisResultDisplay.value = "Analysis failed: ${e.message}"
                Log.e("SmsViewModel", "Error during scam analysis batch", e)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun testScamDetection() {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _lastAnalysisResultDisplay.value = "Testing scam detection with a sample message..."
            try {
                val testMessage = "CONGRATULATIONS! You've won \$1000! Click http://example.com to claim."
                val testSender = "+1234560000"
                val result = scamDetectionService.analyzeSms(testMessage, testSender)

                val contactName = getContactName(testSender)
                var contactStatus = "Sender: ${contactName ?: testSender}"
                if (contactName == null && hasContactPermission.value) {
                    contactStatus += " (Not in contacts)"
                } else if (!hasContactPermission.value) {
                    contactStatus += " (Contact check disabled - permission needed)"
                }

                _lastAnalysisResultDisplay.value = "$contactStatus\n${result.toDisplayString(contactName)}"

            } catch (e: Exception) {
                _lastAnalysisResultDisplay.value = "Test failed: ${e.message}"
                Log.e("SmsViewModel", "Error testing scam detection", e)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun clearMessages() {
        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                smsDao.deleteAllMessages()
                _messageCount.value = 0
                _scamCount.value = 0
            }
            _lastAnalysisResultDisplay.value = "All messages cleared from the local database."
            _isLoading.value = false
        }
    }

    init {
        viewModelScope.launch {
            _messageCount.value = withContext(Dispatchers.IO) { smsDao.getMessageCount() }
            _scamCount.value = withContext(Dispatchers.IO) { smsDao.getScamCount() }
        }
        testServerConnection()
        updateContactPermissionStatus()
    }
}
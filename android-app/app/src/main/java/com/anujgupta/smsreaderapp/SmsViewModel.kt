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

class SmsViewModel(
    private val smsDao: SmsDao, // [SmsViewModel.kt (from previous user context if available, else general structure)]
    private val context: Context // [SmsViewModel.kt (from previous user context if available, else general structure)]
) : ViewModel() {

    private val smsReader = SmsReader(context) // [SmsViewModel.kt (from previous user context if available, else general structure)]
    private val scamDetectionService = ScamDetectionService() // [SmsViewModel.kt (from previous user context if available, else general structure)]

    val allMessages: LiveData<List<SmsMessage>> = smsDao.getAllMessages().asLiveData() // [SmsViewModel.kt (from previous user context if available, else general structure)]
    val scamMessages: LiveData<List<SmsMessage>> = smsDao.getScamMessages().asLiveData() // [SmsViewModel.kt (from previous user context if available, else general structure)]

    private var _isLoading = mutableStateOf(false) // [SmsViewModel.kt (from previous user context if available, else general structure)]
    val isLoading: State<Boolean> = _isLoading // [SmsViewModel.kt (from previous user context if available, else general structure)]

    private var _isAnalyzing = mutableStateOf(false) // [SmsViewModel.kt (from previous user context if available, else general structure)]
    val isAnalyzing: State<Boolean> = _isAnalyzing // [SmsViewModel.kt (from previous user context if available, else general structure)]

    private var _messageCount = mutableStateOf(0) // [SmsViewModel.kt (from previous user context if available, else general structure)]
    val messageCount: State<Int> = _messageCount // [SmsViewModel.kt (from previous user context if available, else general structure)]

    private var _scamCount = mutableStateOf(0) // [SmsViewModel.kt (from previous user context if available, else general structure)]
    val scamCount: State<Int> = _scamCount // [SmsViewModel.kt (from previous user context if available, else general structure)]

    private var _serverStatus = mutableStateOf("Unknown") // [SmsViewModel.kt (from previous user context if available, else general structure)]
    val serverStatus: State<String> = _serverStatus // [SmsViewModel.kt (from previous user context if available, else general structure)]

    private var _lastAnalysisResultDisplay = mutableStateOf("No operations performed yet.") // [SmsViewModel.kt (from previous user context if available, else general structure)]
    val lastAnalysisResultDisplay: State<String> = _lastAnalysisResultDisplay // [SmsViewModel.kt (from previous user context if available, else general structure)]

    // For READ_CONTACTS permission status
    private val _hasContactPermission = mutableStateOf(checkContactsPermission())
    val hasContactPermission: State<Boolean> = _hasContactPermission

    private fun checkContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun updateContactPermissionStatus() {
        _hasContactPermission.value = checkContactsPermission()
    }

    /**
     * Enhancement 2: Get contact name for a given phone number.
     * Returns null if not found or permission not granted.
     * This is a simplified lookup. Real-world number normalization can be complex.
     */
    private suspend fun getContactName(phoneNumber: String): String? = withContext(Dispatchers.IO) {
        if (!hasContactPermission.value) {
            Log.w("SmsViewModel", "READ_CONTACTS permission not granted.")
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
            Log.e("SmsViewModel", "Error looking up contact for $phoneNumber: ${e.message}")
        }
        return@withContext contactName
    }


    fun loadSmsMessages() { // [SmsViewModel.kt (from previous user context if available, else general structure)]
        viewModelScope.launch { // [SmsViewModel.kt (from previous user context if available, else general structure)]
            _isLoading.value = true // [SmsViewModel.kt (from previous user context if available, else general structure)]
            _lastAnalysisResultDisplay.value = "Loading messages..." // [SmsViewModel.kt (from previous user context if available, else general structure)]
            try { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                withContext(Dispatchers.IO) { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                    val messages = smsReader.readAllSmsMessages() // [SmsViewModel.kt (from previous user context if available, else general structure)]
                    smsDao.insertMessages(messages) // [SmsViewModel.kt (from previous user context if available, else general structure)]
                    _messageCount.value = smsDao.getMessageCount() // [SmsViewModel.kt (from previous user context if available, else general structure)]
                    _scamCount.value = smsDao.getScamCount() // [SmsViewModel.kt (from previous user context if available, else general structure)]
                }
                _lastAnalysisResultDisplay.value = "Loaded ${_messageCount.value} messages. Found ${_scamCount.value} known scams." // [SmsViewModel.kt (from previous user context if available, else general structure)]
            } catch (e: Exception) { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                _lastAnalysisResultDisplay.value = "Error loading messages: ${e.message}" // [SmsViewModel.kt (from previous user context if available, else general structure)]
                Log.e("SmsViewModel", "Error loading SMS", e) // [SmsViewModel.kt (from previous user context if available, else general structure)]
            } finally { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                _isLoading.value = false // [SmsViewModel.kt (from previous user context if available, else general structure)]
            }
        }
    }

    fun testServerConnection() { // [SmsViewModel.kt (from previous user context if available, else general structure)]
        viewModelScope.launch { // [SmsViewModel.kt (from previous user context if available, else general structure)]
            _serverStatus.value = "Testing..." // [SmsViewModel.kt (from previous user context if available, else general structure)]
            try { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                val (isConnected, statusMessage) = scamDetectionService.testConnection() // [SmsViewModel.kt (from previous user context if available, else general structure)]
                _serverStatus.value = statusMessage // [SmsViewModel.kt (from previous user context if available, else general structure)]
                if (!isConnected) { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                    _lastAnalysisResultDisplay.value = "Cannot connect to server. Make sure Python backend is running and accessible." // [SmsViewModel.kt (from previous user context if available, else general structure)]
                } else { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                    _lastAnalysisResultDisplay.value = "Server connection test complete. Status: $statusMessage" // [SmsViewModel.kt (from previous user context if available, else general structure)]
                }
            } catch (e: Exception) { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                _serverStatus.value = "Error ✗" // [SmsViewModel.kt (from previous user context if available, else general structure)]
                _lastAnalysisResultDisplay.value = "Connection test failed: ${e.message}" // [SmsViewModel.kt (from previous user context if available, else general structure)]
                Log.e("SmsViewModel", "Error testing server connection", e) // [SmsViewModel.kt (from previous user context if available, else general structure)]
            }
        }
    }

    fun analyzeForScams() { // [SmsViewModel.kt (from previous user context if available, else general structure)]
        viewModelScope.launch { // [SmsViewModel.kt (from previous user context if available, else general structure)]
            if (_messageCount.value == 0) { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                _lastAnalysisResultDisplay.value = "No messages loaded to analyze. Please load messages first." // [SmsViewModel.kt (from previous user context if available, else general structure)]
                return@launch // [SmsViewModel.kt (from previous user context if available, else general structure)]
            }
            _isAnalyzing.value = true // [SmsViewModel.kt (from previous user context if available, else general structure)]
            _lastAnalysisResultDisplay.value = "Starting scam analysis for new messages..." // [SmsViewModel.kt (from previous user context if available, else general structure)]

            try { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                val unanalyzedMessages = withContext(Dispatchers.IO) { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                    smsDao.getUnanalyzedReceivedMessages(5) // Limit batch size further for demo [SmsViewModel.kt (from previous user context if available, else general structure)]
                }

                if (unanalyzedMessages.isEmpty()) { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                    _lastAnalysisResultDisplay.value = "No new messages to analyze." // [SmsViewModel.kt (from previous user context if available, else general structure)]
                    _isAnalyzing.value = false // [SmsViewModel.kt (from previous user context if available, else general structure)]
                    return@launch // [SmsViewModel.kt (from previous user context if available, else general structure)]
                }

                var resultsSummary = "Analysis Summary for ${unanalyzedMessages.size} messages:\n"
                var newScamsFound = 0 // [SmsViewModel.kt (from previous user context if available, else general structure)]

                unanalyzedMessages.forEachIndexed { index, message -> // [SmsViewModel.kt (from previous user context if available, else general structure)]
                    withContext(Dispatchers.Main) { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                        _lastAnalysisResultDisplay.value = "Analyzing message ${index + 1}/${unanalyzedMessages.size}: ${message.body.take(30)}..." // [SmsViewModel.kt (from previous user context if available, else general structure)]
                    }
                    try { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                        val result = scamDetectionService.analyzeSms(message.body, message.address) // [SmsViewModel.kt (from previous user context if available, else general structure)]
                        val contactName = getContactName(message.address)

                        resultsSummary += "Msg from ${contactName ?: message.address}: ${result.classification}"
                        if (result.sender_watchlist_status == "on_watchlist") resultsSummary += " (ON WATCHLIST)"
                        resultsSummary += "\n"


                        withContext(Dispatchers.IO) { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                            smsDao.updateScamAnalysis( // [SmsViewModel.kt (from previous user context if available, else general structure)]
                                messageId = message.id, // [SmsViewModel.kt (from previous user context if available, else general structure)]
                                isAnalyzed = true, // [SmsViewModel.kt (from previous user context if available, else general structure)]
                                classification = result.classification, // [SmsViewModel.kt (from previous user context if available, else general structure)]
                                confidence = result.confidence, // [SmsViewModel.kt (from previous user context if available, else general structure)]
                                analysisDate = System.currentTimeMillis() // [SmsViewModel.kt (from previous user context if available, else general structure)]
                            )
                            if (result.isScam) { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                                newScamsFound++ // [SmsViewModel.kt (from previous user context if available, else general structure)]
                            }
                        }
                    } catch (e: Exception) { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                        Log.e("SmsViewModel", "Error analyzing message ID ${message.id}", e) // [SmsViewModel.kt (from previous user context if available, else general structure)]
                        withContext(Dispatchers.IO) { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                            smsDao.updateScamAnalysis( // [SmsViewModel.kt (from previous user context if available, else general structure)]
                                messageId = message.id, // [SmsViewModel.kt (from previous user context if available, else general structure)]
                                isAnalyzed = true, // [SmsViewModel.kt (from previous user context if available, else general structure)]
                                classification = "ERROR", // [SmsViewModel.kt (from previous user context if available, else general structure)]
                                confidence = "NONE", // [SmsViewModel.kt (from previous user context if available, else general structure)]
                                analysisDate = System.currentTimeMillis() // [SmsViewModel.kt (from previous user context if available, else general structure)]
                            )
                        }
                        withContext(Dispatchers.Main) { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                            _lastAnalysisResultDisplay.value = "Error analyzing message ${index + 1}. ${e.message}" // [SmsViewModel.kt (from previous user context if available, else general structure)]
                        }
                    }
                }
                val totalScams = withContext(Dispatchers.IO) { smsDao.getScamCount() } // [SmsViewModel.kt (from previous user context if available, else general structure)]
                _scamCount.value = totalScams // [SmsViewModel.kt (from previous user context if available, else general structure)]
                _lastAnalysisResultDisplay.value = "$resultsSummary\nBatch complete! New scams: $newScamsFound. Total scams: $totalScams." // [SmsViewModel.kt (from previous user context if available, else general structure)]

            } catch (e: Exception) { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                _lastAnalysisResultDisplay.value = "Analysis failed: ${e.message}" // [SmsViewModel.kt (from previous user context if available, else general structure)]
                Log.e("SmsViewModel", "Error during scam analysis batch", e) // [SmsViewModel.kt (from previous user context if available, else general structure)]
            } finally { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                _isAnalyzing.value = false // [SmsViewModel.kt (from previous user context if available, else general structure)]
            }
        }
    }

    fun testScamDetection() { // [SmsViewModel.kt (from previous user context if available, else general structure)]
        viewModelScope.launch { // [SmsViewModel.kt (from previous user context if available, else general structure)]
            _isAnalyzing.value = true // [SmsViewModel.kt (from previous user context if available, else general structure)]
            _lastAnalysisResultDisplay.value = "Testing scam detection with a sample message..." // [SmsViewModel.kt (from previous user context if available, else general structure)]
            try { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                val testMessage = "CONGRATULATIONS! You've won \$1000! Click http://example.com to claim." // [SmsViewModel.kt (from previous user context if available, else general structure)]
                val testSender = "+1234560000" // Use a number for contact testing
                val result = scamDetectionService.analyzeSms(testMessage, testSender) // [SmsViewModel.kt (from previous user context if available, else general structure)]

                val contactName = getContactName(testSender)
                var contactStatus = "Sender: ${contactName ?: testSender}"
                if (contactName == null && hasContactPermission.value) {
                    contactStatus += " (Not in contacts)"
                } else if (!hasContactPermission.value) {
                    contactStatus += " (Contact check disabled - permission needed)"
                }

                _lastAnalysisResultDisplay.value = "$contactStatus\n${result.toDisplayString(contactName)}" // Use updated toDisplayString

            } catch (e: Exception) { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                _lastAnalysisResultDisplay.value = "Test failed: ${e.message}" // [SmsViewModel.kt (from previous user context if available, else general structure)]
                Log.e("SmsViewModel", "Error testing scam detection", e) // [SmsViewModel.kt (from previous user context if available, else general structure)]
            } finally { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                _isAnalyzing.value = false // [SmsViewModel.kt (from previous user context if available, else general structure)]
            }
        }
    }

    fun clearMessages() { // [SmsViewModel.kt (from previous user context if available, else general structure)]
        viewModelScope.launch { // [SmsViewModel.kt (from previous user context if available, else general structure)]
            _isLoading.value = true // [SmsViewModel.kt (from previous user context if available, else general structure)]
            withContext(Dispatchers.IO) { // [SmsViewModel.kt (from previous user context if available, else general structure)]
                smsDao.deleteAllMessages() // [SmsViewModel.kt (from previous user context if available, else general structure)]
                _messageCount.value = 0 // [SmsViewModel.kt (from previous user context if available, else general structure)]
                _scamCount.value = 0 // [SmsViewModel.kt (from previous user context if available, else general structure)]
            }
            _lastAnalysisResultDisplay.value = "All messages cleared from the local database." // [SmsViewModel.kt (from previous user context if available, else general structure)]
            _isLoading.value = false // [SmsViewModel.kt (from previous user context if available, else general structure)]
        }
    }

    init { // [SmsViewModel.kt (from previous user context if available, else general structure)]
        viewModelScope.launch { // [SmsViewModel.kt (from previous user context if available, else general structure)]
            _messageCount.value = withContext(Dispatchers.IO) { smsDao.getMessageCount() } // [SmsViewModel.kt (from previous user context if available, else general structure)]
            _scamCount.value = withContext(Dispatchers.IO) { smsDao.getScamCount() } // [SmsViewModel.kt (from previous user context if available, else general structure)]
        }
        testServerConnection() // [SmsViewModel.kt (from previous user context if available, else general structure)]
        updateContactPermissionStatus() // Check initial contact permission
    }
}
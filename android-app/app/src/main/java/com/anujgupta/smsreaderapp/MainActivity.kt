package com.anujgupta.smsreaderapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Lock  // Using Lock icon for security
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Permission granted! You can now read SMS.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permission denied. Cannot read SMS.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SmsReaderApp(
                onRequestPermission = { requestSmsPermission() },
                hasPermission = { checkSmsPermission() }
            )
        }
    }

    private fun checkSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestSmsPermission() {
        requestPermissionLauncher.launch(Manifest.permission.READ_SMS)
    }
}

// Added: The missing SmsReaderApp Composable function
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsReaderApp(
    onRequestPermission: () -> Unit,
    hasPermission: () -> Boolean
) {
    // This creates the main theme and structure for your app
    MaterialTheme {
        // Main content area with padding
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App title
            Text(
                text = "SMS Scam Detective",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // Check if we have permission to read SMS
            if (hasPermission()) {
                // Show the main app content
                SmsAnalysisScreen()
            } else {
                // Show permission request screen
                PermissionRequestScreen(onRequestPermission = onRequestPermission)
            }
        }
    }
}

// Screen shown when permission is needed
@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Permission Required",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                text = "This app needs permission to read your SMS messages to detect potential scams. Your messages are analyzed locally and securely.",
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant SMS Permission")
            }
        }
    }
}

// Main screen shown when permission is granted
@Composable
fun SmsAnalysisScreen() {
    val context = LocalContext.current

    // Create ViewModel with database access
    val viewModel: SmsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = SmsDatabase.getDatabase(context)
                @Suppress("UNCHECKED_CAST")
                return SmsViewModel(database.smsDao(), context) as T
            }
        }
    )

    // Observe state from ViewModel
    val isLoading by viewModel.isLoading
    val isAnalyzing by viewModel.isAnalyzing
    val messageCount by viewModel.messageCount
    val scamCount by viewModel.scamCount
    val serverStatus by viewModel.serverStatus
    val lastResult by viewModel.lastAnalysisResult

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Security",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "SMS Scam Protection",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Server Status: $serverStatus",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Messages: $messageCount | Scams Found: $scamCount",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Action buttons
        Button(
            onClick = { viewModel.loadSmsMessages() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && !isAnalyzing
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (isLoading) "Loading..." else "Load SMS Messages")
        }

        Button(
            onClick = { viewModel.testServerConnection() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && !isAnalyzing
        ) {
            Text("Test Server Connection")
        }

        Button(
            onClick = { viewModel.analyzeForScams() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && !isAnalyzing && messageCount > 0
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (isAnalyzing) "Analyzing..." else "Analyze for Scams")
        }

        Button(
            onClick = { viewModel.testScamDetection() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && !isAnalyzing
        ) {
            Text("Test Scam Detection")
        }

        // Results card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Latest Result",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (lastResult.isNotEmpty()) lastResult else "No operations performed yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Enhanced ViewModel with scam detection capabilities
class SmsViewModel(
    private val smsDao: SmsDao,
    private val context: android.content.Context
) : ViewModel() {

    private val smsReader = SmsReader(context)
    private val scamDetectionService = ScamDetectionService()

    // Observable data for the UI
    val allMessages: LiveData<List<SmsMessage>> = smsDao.getAllMessages().asLiveData()
    val scamMessages: LiveData<List<SmsMessage>> = smsDao.getScamMessages().asLiveData()

    // UI State
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

    private var _lastAnalysisResult = mutableStateOf("")
    val lastAnalysisResult: State<String> = _lastAnalysisResult

    /**
     * Load SMS messages from phone storage
     * This is like reading all your text messages and storing them in the app
     */
    fun loadSmsMessages() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    val messages = smsReader.readAllSmsMessages()
                    smsDao.insertMessages(messages)
                    _messageCount.value = messages.size

                    // Update scam count
                    _scamCount.value = smsDao.getScamCount()
                }
                _lastAnalysisResult.value = "Loaded ${_messageCount.value} messages successfully"
            } catch (e: Exception) {
                _lastAnalysisResult.value = "Error loading messages: ${e.message}"
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Test connection to Python backend server
     * Like calling your friend to make sure they're home before visiting
     */
    fun testServerConnection() {
        viewModelScope.launch {
            _serverStatus.value = "Testing..."
            try {
                val isConnected = scamDetectionService.testConnection()
                _serverStatus.value = if (isConnected) "Connected ✓" else "Disconnected ✗"

                if (!isConnected) {
                    _lastAnalysisResult.value = "Cannot connect to server. Make sure Python backend is running on your computer."
                }
            } catch (e: Exception) {
                _serverStatus.value = "Error ✗"
                _lastAnalysisResult.value = "Connection test failed: ${e.message}"
            }
        }
    }

    /**
     * Analyze unscanned messages for scams
     * This sends your messages to the AI for analysis
     */
    fun analyzeForScams() {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _lastAnalysisResult.value = "Starting scam analysis..."

            try {
                withContext(Dispatchers.IO) {
                    // Get messages that haven't been analyzed yet
                    val unanalyzedMessages = smsDao.getUnanalyzedReceivedMessages(20)

                    if (unanalyzedMessages.isEmpty()) {
                        _lastAnalysisResult.value = "No new messages to analyze"
                        return@withContext
                    }

                    _lastAnalysisResult.value = "Analyzing ${unanalyzedMessages.size} messages..."
                    var scamsFound = 0

                    // Analyze each message
                    unanalyzedMessages.forEachIndexed { index, message ->
                        try {
                            // Update progress
                            withContext(Dispatchers.Main) {
                                _lastAnalysisResult.value = "Analyzing message ${index + 1}/${unanalyzedMessages.size}..."
                            }

                            // Send to AI for analysis
                            val result = scamDetectionService.analyzeSms(message.body, message.address)

                            // Save the analysis result to database
                            smsDao.updateScamAnalysis(
                                messageId = message.id,
                                isAnalyzed = true,
                                classification = result.classification,
                                confidence = result.confidence,
                                analysisDate = System.currentTimeMillis()
                            )

                            if (result.isScam) {
                                scamsFound++
                            }

                        } catch (e: Exception) {
                            // Mark as analyzed but with error
                            smsDao.updateScamAnalysis(
                                messageId = message.id,
                                isAnalyzed = true,
                                classification = "ERROR",
                                confidence = "NONE",
                                analysisDate = System.currentTimeMillis()
                            )
                        }
                    }

                    // Update counts
                    _scamCount.value = smsDao.getScamCount()
                    _lastAnalysisResult.value = "Analysis complete! Found $scamsFound potential scams"
                }

            } catch (e: Exception) {
                _lastAnalysisResult.value = "Analysis failed: ${e.message}"
                e.printStackTrace()
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    /**
     * Test scam detection with a sample message
     * Like sending a test message to make sure everything works
     */
    fun testScamDetection() {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _lastAnalysisResult.value = "Testing scam detection..."

            try {
                val testMessage = "CONGRATULATIONS! You've won $1000! Click here to claim your prize now: http://suspicious-link.com"
                val result = scamDetectionService.analyzeSms(testMessage, "TEST")

                _lastAnalysisResult.value = "Test result: ${result.classification} (${result.confidence} confidence)"

                if (result.error != null) {
                    _lastAnalysisResult.value += "\nError: ${result.error}"
                }

            } catch (e: Exception) {
                _lastAnalysisResult.value = "Test failed: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun clearMessages() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                smsDao.deleteAllMessages()
                _messageCount.value = 0
                _scamCount.value = 0
            }
            _lastAnalysisResult.value = "All messages cleared"
        }
    }

    // Initialize by testing server connection
    init {
        testServerConnection()
    }
}
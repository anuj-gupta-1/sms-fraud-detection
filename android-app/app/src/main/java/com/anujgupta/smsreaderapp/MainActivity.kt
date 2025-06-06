package com.anujgupta.smsreaderapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person // For contact permission button
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anujgupta.smsreaderapp.data.SmsDatabase
import com.anujgupta.smsreaderapp.models.SmsAnalysisResult
import com.anujgupta.smsreaderapp.ui.theme.SMSFraudDetectionTheme

// Add these color extensions
val ColorScheme.warning: Color
    get() = Color(0xFFFFA000) // Amber 700

val ColorScheme.warningContainer: Color
    get() = Color(0xFFFFF3E0) // Orange 50

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    // Separate launcher for READ_CONTACTS
    private val requestContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        Log.d(TAG, "Contacts permission result: $isGranted")
        if (isGranted) {
            Toast.makeText(this, "Contacts permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Contacts permission denied.", Toast.LENGTH_SHORT).show()
        }
        // Trigger ViewModel to update its permission status, which will trigger recomposition if needed
        // This assumes you have a ViewModel instance or a way to signal it.
        // For simplicity, we'll have the Composable update the ViewModel's state.
    }

    private val requestSmsPermissionLauncher = registerForActivityResult( // [MainActivity.kt]
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean -> // [MainActivity.kt]
        Log.d(TAG, "SMS permission result: $isGranted")
        if (isGranted) { // [MainActivity.kt]
            Toast.makeText(this, "SMS Permission granted! You can now read SMS.", Toast.LENGTH_SHORT).show() // [MainActivity.kt]
        } else { // [MainActivity.kt]
            Toast.makeText(this, "SMS Permission denied. Cannot read SMS.", Toast.LENGTH_SHORT).show() // [MainActivity.kt]
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) { // [MainActivity.kt]
        super.onCreate(savedInstanceState) // [MainActivity.kt]
        Log.d(TAG, "onCreate started")
        
        setContent {
            Log.d(TAG, "Setting up content")
            SMSFraudDetectionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmsReaderAppContainer( // Renamed for clarity, to pass launchers
                        onRequestSmsPermission = { requestSmsPermission() },
                        checkSmsPermission = { checkSmsPermission() },
                        onRequestContactsPermission = { requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                        checkContactsPermission = { checkContactsPermission() }
                    )
                }
            }
        }
        Log.d(TAG, "Content setup completed")
    }

    private fun checkSmsPermission(): Boolean { // [MainActivity.kt]
        val hasPermission = ContextCompat.checkSelfPermission( // [MainActivity.kt]
            this,
            Manifest.permission.READ_SMS // [MainActivity.kt]
        ) == PackageManager.PERMISSION_GRANTED // [MainActivity.kt]
        Log.d(TAG, "SMS permission check: $hasPermission")
        return hasPermission // [MainActivity.kt]
    }

    private fun requestSmsPermission() { // [MainActivity.kt]
        Log.d(TAG, "Requesting SMS permission")
        requestSmsPermissionLauncher.launch(Manifest.permission.READ_SMS) // [MainActivity.kt]
    }

    // Helper to check contacts permission
    private fun checkContactsPermission(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Contacts permission check: $hasPermission")
        return hasPermission
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsReaderAppContainer( // Renamed from SmsReaderApp
    onRequestSmsPermission: () -> Unit,
    checkSmsPermission: () -> Boolean,
    onRequestContactsPermission: () -> Unit,
    checkContactsPermission: () -> Boolean
) {
    val context = LocalContext.current
    val TAG = "SmsReaderAppContainer"
    
    var errorState by remember { mutableStateOf<String?>(null) }
    
    Log.d(TAG, "Creating ViewModel")
    val viewModel: SmsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return try {
                    Log.d(TAG, "Initializing database")
                    val database = SmsDatabase.getDatabase(context.applicationContext)
                    Log.d(TAG, "Creating ViewModel instance")
                    @Suppress("UNCHECKED_CAST")
                    SmsViewModel(database.smsDao(), context.applicationContext) as T
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating ViewModel", e)
                    errorState = "Error initializing app: ${e.message}"
                    throw e
                }
            }
        }
    )
    Log.d(TAG, "ViewModel created successfully")

    LaunchedEffect(checkContactsPermission()) {
        viewModel.updateContactPermissionStatus()
    }

    Column( // [MainActivity.kt]
        modifier = Modifier // [MainActivity.kt]
            .fillMaxSize() // [MainActivity.kt]
            .padding(16.dp), // [MainActivity.kt]
        verticalArrangement = Arrangement.spacedBy(16.dp) // [MainActivity.kt]
    ) {
        Text( // [MainActivity.kt]
            text = "SMS Scam Detective", // [MainActivity.kt]
            style = MaterialTheme.typography.headlineMedium, // [MainActivity.kt]
            fontWeight = FontWeight.Bold // [MainActivity.kt]
        )

        errorState?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }

        if (errorState == null) {
            val hasSmsPerm by remember { mutableStateOf(checkSmsPermission()) }
            val hasContactsPerm by viewModel.hasContactPermission

            if (hasSmsPerm) {
                SmsAnalysisScreen(
                    viewModel = viewModel,
                    hasContactsPermission = hasContactsPerm,
                    onRequestContactsPermission = onRequestContactsPermission
                )
            } else {
                PermissionRequestScreen(
                    permissionType = "SMS Reading",
                    reason = "This app needs permission to read your SMS messages to detect potential scams. Your messages are analyzed locally on your device and by a secure backend.",
                    onRequestPermission = onRequestSmsPermission
                )
            }
        }
    }
}

@Composable
fun PermissionRequestScreen( // [MainActivity.kt]
    permissionType: String,
    reason: String,
    onRequestPermission: () -> Unit) {
    Card( // [MainActivity.kt]
        modifier = Modifier.fillMaxWidth(), // [MainActivity.kt]
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) // [MainActivity.kt]
    ) {
        Column( // [MainActivity.kt]
            modifier = Modifier.padding(16.dp), // [MainActivity.kt]
            verticalArrangement = Arrangement.spacedBy(12.dp), // [MainActivity.kt]
            horizontalAlignment = Alignment.CenterHorizontally // [MainActivity.kt]
        ) {
            Row( // [MainActivity.kt]
                verticalAlignment = Alignment.CenterVertically, // [MainActivity.kt]
                horizontalArrangement = Arrangement.spacedBy(8.dp) // [MainActivity.kt]
            ) {
                Icon( // [MainActivity.kt]
                    imageVector = Icons.Default.Warning, // [MainActivity.kt]
                    contentDescription = "Warning", // [MainActivity.kt]
                    tint = MaterialTheme.colorScheme.error // [MainActivity.kt]
                )
                Text( // [MainActivity.kt]
                    text = "$permissionType Permission Required", // [MainActivity.kt]
                    fontWeight = FontWeight.Bold, // [MainActivity.kt]
                    color = MaterialTheme.colorScheme.error, // [MainActivity.kt]
                    style = MaterialTheme.typography.titleMedium // [MainActivity.kt]
                )
            }

            Text( // [MainActivity.kt]
                text = reason, // [MainActivity.kt]
                color = MaterialTheme.colorScheme.onErrorContainer, // [MainActivity.kt]
                style = MaterialTheme.typography.bodyMedium // [MainActivity.kt]
            )

            Button( // [MainActivity.kt]
                onClick = onRequestPermission, // [MainActivity.kt]
                modifier = Modifier.fillMaxWidth() // [MainActivity.kt]
            ) {
                Text("Grant $permissionType Permission") // [MainActivity.kt]
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsAnalysisScreen( // [MainActivity.kt]
    viewModel: SmsViewModel, // Accept ViewModel as parameter
    hasContactsPermission: Boolean,
    onRequestContactsPermission: () -> Unit
) {
    // Observe state from ViewModel
    val isLoading by viewModel.isLoading // [MainActivity.kt]
    val isAnalyzing by viewModel.isAnalyzing // [MainActivity.kt]
    val messageCount by viewModel.messageCount // [MainActivity.kt]
    val scamCount by viewModel.scamCount // [MainActivity.kt]
    val serverStatus by viewModel.serverStatus // [MainActivity.kt]
    val lastResultDisplay by viewModel.lastAnalysisResultDisplay // [MainActivity.kt]

    Column( // [MainActivity.kt]
        verticalArrangement = Arrangement.spacedBy(12.dp) // [MainActivity.kt]
    ) {
        Card( // [MainActivity.kt]
            modifier = Modifier.fillMaxWidth(), // [MainActivity.kt]
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) // [MainActivity.kt]
        ) {
            Column( // [MainActivity.kt]
                modifier = Modifier.padding(16.dp), // [MainActivity.kt]
                verticalArrangement = Arrangement.spacedBy(8.dp) // [MainActivity.kt]
            ) {
                Row( // [MainActivity.kt]
                    verticalAlignment = Alignment.CenterVertically, // [MainActivity.kt]
                    horizontalArrangement = Arrangement.spacedBy(8.dp) // [MainActivity.kt]
                ) {
                    Icon( // [MainActivity.kt]
                        imageVector = Icons.Default.Lock, // [MainActivity.kt]
                        contentDescription = "Security", // [MainActivity.kt]
                        tint = MaterialTheme.colorScheme.primary // [MainActivity.kt]
                    )
                    Text( // [MainActivity.kt]
                        text = "SMS Scam Protection Status", // [MainActivity.kt]
                        fontWeight = FontWeight.Bold, // [MainActivity.kt]
                        color = MaterialTheme.colorScheme.primary, // [MainActivity.kt]
                        style = MaterialTheme.typography.titleMedium // [MainActivity.kt]
                    )
                }
                Text( // [MainActivity.kt]
                    text = "Server: $serverStatus", // [MainActivity.kt]
                    color = MaterialTheme.colorScheme.onPrimaryContainer, // [MainActivity.kt]
                    style = MaterialTheme.typography.bodyMedium // [MainActivity.kt]
                )
                Text( // [MainActivity.kt]
                    text = "Loaded Messages: $messageCount | Potential Scams Found: $scamCount", // [MainActivity.kt]
                    color = MaterialTheme.colorScheme.onPrimaryContainer, // [MainActivity.kt]
                    style = MaterialTheme.typography.bodyMedium // [MainActivity.kt]
                )
                // Contact Permission Button/Status
                if (!hasContactsPermission) {
                    Button(onClick = {
                        onRequestContactsPermission()
                        // ViewModel will update its status via LaunchedEffect in SmsReaderAppContainer
                        // or after permission result in Activity.
                        // For immediate UI feedback on click, can also call viewModel.updateContactPermissionStatus()
                        // but it's better if Activity drives this after permission result.
                        // We need to ensure viewModel.updateContactPermissionStatus() is called after
                        // the permission dialog closes. The Activity's callback for
                        // requestContactsPermissionLauncher should ensure this.
                        // A simpler way for now:
                        // viewModel.updateContactPermissionStatus() // Call this to recheck (might not be immediate)
                    }) {
//                        Icon(Icons.Default.Contacts, contentDescription = "Contacts Icon")
                        Icon(Icons.Filled.Person, contentDescription = "Contacts Icon") // CORRECTED LINE
                        Spacer(Modifier.width(8.dp))
                        Text("Enable Contact Matching")
                    }
                } else {
                    Text(
                        text = "Contact Matching: Enabled",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { // [MainActivity.kt]
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { // [MainActivity.kt]
                Button( // [MainActivity.kt]
                    onClick = { viewModel.loadSmsMessages() }, // [MainActivity.kt]
                    modifier = Modifier.weight(1f), // [MainActivity.kt]
                    enabled = !isLoading && !isAnalyzing // [MainActivity.kt]
                ) {
                    if (isLoading && viewModel.lastAnalysisResultDisplay.value.startsWith("Loading messages...")) { // [MainActivity.kt]
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) // [MainActivity.kt]
                        Spacer(modifier = Modifier.width(8.dp)) // [MainActivity.kt]
                        Text("Loading...") // [MainActivity.kt]
                    } else { // [MainActivity.kt]
                        Text("Load SMS") // [MainActivity.kt]
                    }
                }
                Button( // [MainActivity.kt]
                    onClick = { viewModel.testServerConnection() }, // [MainActivity.kt]
                    modifier = Modifier.weight(1f), // [MainActivity.kt]
                    enabled = !isLoading && !isAnalyzing // [MainActivity.kt]
                ) {
                    Text("Test Server") // [MainActivity.kt]
                }
            }

            Button( // [MainActivity.kt]
                onClick = { viewModel.analyzeForScams() }, // [MainActivity.kt]
                modifier = Modifier.fillMaxWidth(), // [MainActivity.kt]
                enabled = !isLoading && !isAnalyzing && messageCount > 0 // [MainActivity.kt]
            ) {
                if (isAnalyzing && viewModel.lastAnalysisResultDisplay.value.contains("Analyzing message")) { // [MainActivity.kt]
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) // [MainActivity.kt]
                    Spacer(modifier = Modifier.width(8.dp)) // [MainActivity.kt]
                    Text("Analyzing...") // [MainActivity.kt]
                } else { // [MainActivity.kt]
                    Text("Analyze New SMS for Scams") // [MainActivity.kt]
                }
            }

            Button( // [MainActivity.kt]
                onClick = { viewModel.testScamDetection() }, // [MainActivity.kt]
                modifier = Modifier.fillMaxWidth(), // [MainActivity.kt]
                enabled = !isLoading && !isAnalyzing // [MainActivity.kt]
            ) {
                if (isAnalyzing && viewModel.lastAnalysisResultDisplay.value.startsWith("Testing scam detection")) { // [MainActivity.kt]
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) // [MainActivity.kt]
                    Spacer(modifier = Modifier.width(8.dp)) // [MainActivity.kt]
                    Text("Testing...") // [MainActivity.kt]
                } else { // [MainActivity.kt]
                    Text("Test Sample Scam Detection") // [MainActivity.kt]
                }
            }
            Button( // [MainActivity.kt]
                onClick = { viewModel.clearMessages() }, // [MainActivity.kt]
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), // [MainActivity.kt]
                modifier = Modifier.fillMaxWidth() // [MainActivity.kt]
            ) {
                Text("Clear All Local Messages") // [MainActivity.kt]
            }
        }


        Card( // [MainActivity.kt]
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 120.dp) // [MainActivity.kt]
        ) {
            Column( // [MainActivity.kt]
                modifier = Modifier.padding(16.dp) // [MainActivity.kt]
            ) {
                Text( // [MainActivity.kt]
                    text = "Latest Operation Result", // [MainActivity.kt]
                    fontWeight = FontWeight.Bold, // [MainActivity.kt]
                    style = MaterialTheme.typography.titleSmall // [MainActivity.kt]
                )
                Spacer(modifier = Modifier.height(4.dp)) // [MainActivity.kt]
                Text( // [MainActivity.kt]
                    text = lastResultDisplay, // [MainActivity.kt]
                    color = MaterialTheme.colorScheme.onSurfaceVariant, // [MainActivity.kt]
                    style = MaterialTheme.typography.bodyMedium, // [MainActivity.kt]
                    lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.4f // CORRECTED LINE, added 2.sp back [MainActivity.kt (corrected lineHeight from user input)]
                )
            }
        }
    }
}

@Composable
fun WatchlistIndicator(
    isOnWatchlist: Boolean,
    detectionMethod: String?,
    modifier: Modifier = Modifier
) {
    if (isOnWatchlist) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Watchlist Warning",
                    tint = MaterialTheme.colorScheme.error
                )
                Column {
                    Text(
                        text = "⚠️ Number on Suspicious Watchlist",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    if (detectionMethod == "WATCHLIST_OVERRIDE") {
                        Text(
                            text = "High-confidence threat due to watchlist status",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SmsResultCard(
    result: SmsAnalysisResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (result.alertLevel) {
                "HIGH" -> MaterialTheme.colorScheme.errorContainer
                "MEDIUM" -> MaterialTheme.colorScheme.warningContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Show watchlist status first if applicable
            if (result.sender_watchlist_status == "on_watchlist") {
                WatchlistIndicator(
                    isOnWatchlist = true,
                    detectionMethod = result.detectionMethod
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Rest of the existing SMS result display
            Text(
                text = "From: ${result.sender}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = result.messagePreview,
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.classification,
                    style = MaterialTheme.typography.titleSmall,
                    color = when (result.alertLevel) {
                        "HIGH" -> MaterialTheme.colorScheme.error
                        "MEDIUM" -> MaterialTheme.colorScheme.warning
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Text(
                    text = "Confidence: ${result.confidence}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            if (result.reason != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
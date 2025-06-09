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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person // For contact permission button
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
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
import com.anujgupta.smsreaderapp.data.SmsMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.material.icons.filled.Cached
import androidx.compose.ui.graphics.graphicsLayer
import com.anujgupta.smsreaderapp.ui.theme.AlertGreen
import com.anujgupta.smsreaderapp.ui.theme.AlertOrange
import com.anujgupta.smsreaderapp.ui.theme.AlertRed
import androidx.compose.runtime.setValue

// Add these color extensions
val ColorScheme.warning: Color
    get() = Color(0xFFFFA000) // Amber 700

val ColorScheme.warningContainer: Color
    get() = Color(0xFFFFF3E0) // Orange 50

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private var hasSmsPermission by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        hasSmsPermission = checkSmsPermission()
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

    private val requestSmsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasSmsPermission = isGranted
        Log.d(TAG, "SMS permission result: $isGranted")
        if (isGranted) {
            Toast.makeText(this, "SMS Permission granted! You can now read SMS.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "SMS Permission denied. Cannot read SMS.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasSmsPermission = checkSmsPermission()
        Log.d(TAG, "onCreate started")
        
        setContent {
            Log.d(TAG, "Setting up content")
            SMSFraudDetectionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmsReaderAppContainer(
                        hasSmsPermission = hasSmsPermission,
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

    private fun checkSmsPermission(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "SMS permission check: $hasPermission")
        return hasPermission
    }

    private fun requestSmsPermission() {
        Log.d(TAG, "Requesting SMS permission")
        requestSmsPermissionLauncher.launch(Manifest.permission.READ_SMS)
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
fun SmsReaderAppContainer(
    hasSmsPermission: Boolean,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SMS Scam Detective",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        errorState?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }

        if (errorState == null) {
            val hasContactsPerm by viewModel.hasContactPermission

            if (hasSmsPermission) {
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
fun PermissionRequestScreen(
    permissionType: String,
    reason: String,
    onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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
                    text = "$permissionType Permission Required",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Text(
                text = reason,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant $permissionType Permission")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsAnalysisScreen(
    viewModel: SmsViewModel,
    hasContactsPermission: Boolean,
    onRequestContactsPermission: () -> Unit
) {
    val serverStatus by viewModel.serverStatus
    val isAnalyzing by viewModel.isAnalyzing
    val lastAnalysisResultDisplay by viewModel.lastAnalysisResultDisplay
    val allMessages by viewModel.allMessages.observeAsState(initial = emptyList())
    val selectedMessages by viewModel.selectedMessages
    val analysisResults by viewModel.analysisResults
    val flippedCards by viewModel.flippedCards

    LaunchedEffect(key1 = true) {
        viewModel.loadSmsMessages()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS Protection") },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "App Icon",
                        modifier = Modifier.padding(start = 12.dp)
                    )
                },
                actions = {
                    val isConnected = serverStatus.startsWith("Connected")
                    Icon(
                        imageVector = if (isConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = "Server Status",
                        tint = if (isConnected) AlertGreen else AlertRed,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    IconButton(onClick = { viewModel.loadSmsMessages() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh SMS")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusInfo(
                messageCount = allMessages.size,
                hasContactsPermission = hasContactsPermission,
                lastOperation = lastAnalysisResultDisplay,
                onRequestContactsPermission = onRequestContactsPermission
            )
            
            Button(
                onClick = { viewModel.analyzeSelectedMessages() },
                enabled = selectedMessages.isNotEmpty() && !isAnalyzing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyzing...")
                } else {
                    Text("Analyze Selected (${selectedMessages.size})")
                }
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allMessages, key = { it.id }) { message ->
                    val analysisResult = analysisResults.find { it.id == message.id }
                    // This is a simplification. In a real app, you'd have a better way 
                    // to check contacts for each number.
                    val hasContact = false 

                    FlippableSmsCard(
                        message = message,
                        analysisResult = analysisResult,
                        isSelected = selectedMessages.contains(message.id),
                        isFlipped = flippedCards.contains(message.id),
                        onToggleSelection = { viewModel.toggleSelection(message.id) },
                        onToggleFlip = { viewModel.toggleCardFlip(message.id) },
                        hasContact = hasContact
                    )
                }
            }
        }
    }
}

@Composable
fun StatusInfo(
    messageCount: Int,
    hasContactsPermission: Boolean,
    lastOperation: String,
    onRequestContactsPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Messages (last 24h): $messageCount",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Contact Access: ${if (hasContactsPermission) "Enabled" else "Disabled"}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (!hasContactsPermission) {
                Button(
                    onClick = onRequestContactsPermission,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text("Enable Contact Matching")
                }
            }
            Text(
                text = "Last Operation: $lastOperation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                text = result.message_content,
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

@Composable
fun SmsList(
    messages: List<SmsMessage>,
    selectedMessages: Set<Long>,
    onMessageSelected: (Long) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(messages) { message ->
            SmsListItem(
                message = message,
                isSelected = selectedMessages.contains(message.id),
                onMessageSelected = { onMessageSelected(message.id) }
            )
        }
    }
}

@Composable
fun SmsListItem(
    message: SmsMessage,
    isSelected: Boolean,
    onMessageSelected: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onMessageSelected() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onMessageSelected() }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = "From: ${message.address}", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = message.body)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(message.date)),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlippableSmsCard(
    message: SmsMessage,
    analysisResult: SmsAnalysisResult?,
    isSelected: Boolean,
    isFlipped: Boolean,
    onToggleSelection: () -> Unit,
    onToggleFlip: () -> Unit,
    hasContact: Boolean
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "flip"
    )

    Card(
        onClick = { if (analysisResult != null) onToggleFlip() },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12 * density
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        if (rotation < 90f) {
            SmsCardFront(message, isSelected, analysisResult != null, onToggleSelection, onToggleFlip)
        } else {
            AnalysisCardBack(analysisResult, hasContact, Modifier.graphicsLayer { rotationY = 180f })
        }
    }
}

@Composable
fun SmsCardFront(
    message: SmsMessage,
    isSelected: Boolean,
    hasAnalysis: Boolean,
    onToggleSelection: () -> Unit,
    onToggleFlip: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelection() },
            modifier = Modifier.padding(end = 12.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = message.address, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = message.body, style = MaterialTheme.typography.bodyMedium)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween) {
            if (hasAnalysis) {
                IconButton(onClick = onToggleFlip) {
                    Icon(Icons.Default.Cached, contentDescription = "Flip to see analysis")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.date)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AnalysisCardBack(result: SmsAnalysisResult?, hasContact: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (result == null) {
            Text("No analysis available.")
            return
        }

        val (resultColor, resultText) = when (result.alertLevel) {
            "HIGH" -> Pair(AlertRed, "SCAM")
            "MEDIUM" -> Pair(AlertOrange, "SUSPICIOUS")
            else -> Pair(AlertGreen, "LEGITIMATE")
        }

        Text(
            text = "RESULT: $resultText",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = resultColor
        )
        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))

        Text("Confidence: ${result.confidence}", style = MaterialTheme.typography.bodyMedium)
        result.reason?.let { Text("Reason: $it", style = MaterialTheme.typography.bodyMedium) }
        Text("On Watchlist: ${if (result.sender_watchlist_status == "on_watchlist") "✅ Yes" else "❌ No"}", style = MaterialTheme.typography.bodyMedium)
        Text("In Contacts: ${if (hasContact) "✅ Yes" else "❌ No"}", style = MaterialTheme.typography.bodyMedium)
    }
}
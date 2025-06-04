package com.anujgupta.smsreaderapp

import android.Manifest //
import android.content.pm.PackageManager //
import android.os.Bundle //
import android.widget.Toast //
import androidx.activity.ComponentActivity //
import androidx.activity.compose.setContent //
import androidx.activity.result.contract.ActivityResultContracts //
import androidx.compose.foundation.layout.* //
import androidx.compose.material.icons.Icons //
import androidx.compose.material.icons.filled.Lock //
import androidx.compose.material.icons.filled.Warning //
import androidx.compose.material3.* //
import androidx.compose.runtime.* //
import androidx.compose.ui.Alignment //
import androidx.compose.ui.Modifier //
import androidx.compose.ui.platform.LocalContext //
import androidx.compose.ui.text.font.FontWeight //
import androidx.compose.ui.unit.dp //
import androidx.compose.ui.unit.sp // For line height
import androidx.core.content.ContextCompat //
import androidx.lifecycle.ViewModel //
import androidx.lifecycle.ViewModelProvider //
import androidx.lifecycle.viewmodel.compose.viewModel //


class MainActivity : ComponentActivity() { //

    private val requestPermissionLauncher = registerForActivityResult( //
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Permission granted! You can now read SMS.", Toast.LENGTH_SHORT).show()
            // The UI will recompose due to hasPermission() check, or user can click "Load SMS".
        } else {
            Toast.makeText(this, "Permission denied. Cannot read SMS.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) { //
        super.onCreate(savedInstanceState)
        setContent {
            SmsReaderApp( //
                onRequestPermission = { requestSmsPermission() },
                hasPermission = { checkSmsPermission() }
            )
        }
    }

    private fun checkSmsPermission(): Boolean { //
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestSmsPermission() { //
        requestPermissionLauncher.launch(Manifest.permission.READ_SMS)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsReaderApp( //
    onRequestPermission: () -> Unit,
    hasPermission: () -> Boolean
) {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp) //
        ) {
            Text(
                text = "SMS Scam Detective", //
                style = MaterialTheme.typography.headlineMedium, //
                fontWeight = FontWeight.Bold //
            )

            // Use rememberUpdatedState to ensure the latest hasPermission is used for recomposition
            val currentHasPermission by rememberUpdatedState(hasPermission())

            if (currentHasPermission) {
                SmsAnalysisScreen() //
            } else {
                PermissionRequestScreen(onRequestPermission = onRequestPermission) //
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) { //
    Card(
        modifier = Modifier.fillMaxWidth(), //
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) //
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), //
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp) //
            ) {
                Icon(
                    imageVector = Icons.Default.Warning, //
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error //
                )
                Text(
                    text = "Permission Required", //
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error, //
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Text(
                text = "This app needs permission to read your SMS messages to detect potential scams. Your messages are analyzed locally on your device and by a secure backend.", //
                color = MaterialTheme.colorScheme.onErrorContainer, //
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = onRequestPermission, //
                modifier = Modifier.fillMaxWidth() //
            ) {
                Text("Grant SMS Permission") //
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsAnalysisScreen() { //
    val context = LocalContext.current //

    // Create ViewModel with database access
    val viewModel: SmsViewModel = viewModel( //
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = SmsDatabase.getDatabase(context.applicationContext) // Use applicationContext
                @Suppress("UNCHECKED_CAST")
                return SmsViewModel(database.smsDao(), context.applicationContext) as T //
            }
        }
    )

    // Observe state from ViewModel
    val isLoading by viewModel.isLoading //
    val isAnalyzing by viewModel.isAnalyzing //
    val messageCount by viewModel.messageCount //
    val scamCount by viewModel.scamCount //
    val serverStatus by viewModel.serverStatus //
    // Use the new display string from ViewModel
    val lastResultDisplay by viewModel.lastAnalysisResultDisplay //

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp) // Adjusted spacing
    ) {
        // Status card
        Card(
            modifier = Modifier.fillMaxWidth(), //
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) //
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp) //
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp) //
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock, //
                        contentDescription = "Security",
                        tint = MaterialTheme.colorScheme.primary //
                    )
                    Text(
                        text = "SMS Scam Protection Status",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, //
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = "Server: $serverStatus", // Displays more detailed server status from ViewModel
                    color = MaterialTheme.colorScheme.onPrimaryContainer, //
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Loaded Messages: $messageCount | Potential Scams Found: $scamCount", //
                    color = MaterialTheme.colorScheme.onPrimaryContainer, //
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Action buttons
        // Using a Column for buttons now for simpler layout, can be Row or Grid as needed.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.loadSmsMessages() }, //
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading && !isAnalyzing //
                ) {
                    // Check a more specific condition for "Loading..." state if desired
                    if (isLoading && viewModel.lastAnalysisResultDisplay.value.startsWith("Loading messages...")) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) //
                        Spacer(modifier = Modifier.width(8.dp)) //
                        Text("Loading...")
                    } else {
                        Text("Load SMS") //
                    }
                }
                Button(
                    onClick = { viewModel.testServerConnection() }, //
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading && !isAnalyzing //
                ) {
                    Text("Test Server") //
                }
            }

            Button(
                onClick = { viewModel.analyzeForScams() }, //
                modifier = Modifier.fillMaxWidth(), //
                enabled = !isLoading && !isAnalyzing && messageCount > 0 //
            ) {
                if (isAnalyzing && viewModel.lastAnalysisResultDisplay.value.contains("Analyzing message")) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) //
                    Spacer(modifier = Modifier.width(8.dp)) //
                    Text("Analyzing...")
                } else {
                    Text("Analyze New SMS for Scams") //
                }
            }

            Button(
                onClick = { viewModel.testScamDetection() }, //
                modifier = Modifier.fillMaxWidth(), //
                enabled = !isLoading && !isAnalyzing //
            ) {
                if (isAnalyzing && viewModel.lastAnalysisResultDisplay.value.startsWith("Testing scam detection")) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) //
                    Spacer(modifier = Modifier.width(8.dp)) //
                    Text("Testing...")
                } else {
                    Text("Test Sample Scam Detection") //
                }
            }
            Button(
                onClick = { viewModel.clearMessages() }, //
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear All Local Messages")
            }
        }


        // Results card
        Card(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 120.dp) // Ensure card has some min height
        ) {
            Column(
                modifier = Modifier.padding(16.dp) //
            ) {
                Text(
                    text = "Latest Operation Result", //
                    fontWeight = FontWeight.Bold, //
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lastResultDisplay, // Display the formatted string from ViewModel
                    color = MaterialTheme.colorScheme.onSurfaceVariant, //
                    style = MaterialTheme.typography.bodyMedium,
                 //   lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.4 + 2.sp // Adjusted line height
                    lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.4f  // CORRECTED LINE
                )
            }
        }
    }
}
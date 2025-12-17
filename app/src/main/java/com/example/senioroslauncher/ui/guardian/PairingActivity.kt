package com.example.senioroslauncher.ui.guardian

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.senioroslauncher.data.guardian.PairingApiClient
import com.example.senioroslauncher.data.guardian.PairingCodeResult
import com.example.senioroslauncher.services.GuardianMonitoringService
import com.example.senioroslauncher.ui.components.LargeActionButton
import com.example.senioroslauncher.ui.components.SeniorTopAppBar
import com.example.senioroslauncher.ui.theme.PrimaryBlue
import com.example.senioroslauncher.ui.theme.SeniorLauncherTheme
import com.example.senioroslauncher.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

class PairingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SeniorLauncherTheme {
                PairingScreen(onBackClick = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pairingClient = remember { PairingApiClient(context) }

    var pairingCode by remember { mutableStateOf<String?>(null) }
    var expiresAt by remember { mutableStateOf<Instant?>(null) }
    var remainingSeconds by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Countdown timer effect
    LaunchedEffect(expiresAt) {
        while (expiresAt != null) {
            val now = Instant.now()
            val remaining = ChronoUnit.SECONDS.between(now, expiresAt)
            if (remaining <= 0) {
                pairingCode = null
                expiresAt = null
                remainingSeconds = 0
                break
            }
            remainingSeconds = remaining.toInt()
            delay(1000)
        }
    }

    fun generateCode() {
        scope.launch {
            isLoading = true
            errorMessage = null

            // Start Guardian Monitoring Service to establish WebSocket connection
            val serviceIntent = Intent(context, GuardianMonitoringService::class.java)
            serviceIntent.action = GuardianMonitoringService.ACTION_START
            context.startForegroundService(serviceIntent)

            when (val result = pairingClient.generatePairingCode()) {
                is PairingCodeResult.Success -> {
                    pairingCode = result.code
                    expiresAt = try {
                        Instant.parse(result.expiresAt)
                    } catch (e: Exception) {
                        // Default to 10 minutes from now
                        Instant.now().plus(10, ChronoUnit.MINUTES)
                    }
                }
                is PairingCodeResult.Error -> {
                    errorMessage = result.message
                }
            }

            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            SeniorTopAppBar(
                title = "Pair with Guardian",
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Icon(
                imageVector = Icons.Default.QrCode2,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = "Connect with Family",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            Text(
                text = "Generate a code to allow your family members to connect their Guardian app with your device.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Pairing Code Display
            AnimatedVisibility(
                visible = pairingCode != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Your Pairing Code",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Large code display
                        Text(
                            text = pairingCode?.chunked(3)?.joinToString(" ") ?: "",
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 8.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Timer
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (remainingSeconds < 60)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatTime(remainingSeconds),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (remainingSeconds < 60)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Share this code with your caregiver",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Loading indicator
            AnimatedVisibility(visible = isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = PrimaryBlue
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Generating code...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Error message
            AnimatedVisibility(visible = errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Generate/Regenerate button
            if (!isLoading) {
                LargeActionButton(
                    text = if (pairingCode != null) "Generate New Code" else "Generate Pairing Code",
                    onClick = { generateCode() },
                    backgroundColor = PrimaryBlue,
                    icon = Icons.Default.Refresh
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Done button
            if (pairingCode != null) {
                OutlinedButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "How to pair:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    InstructionStep(1, "Generate a code above")
                    InstructionStep(2, "Share the code with your caregiver")
                    InstructionStep(3, "They enter the code in their Guardian app")
                    InstructionStep(4, "You're connected!")
                }
            }
        }
    }
}

@Composable
private fun InstructionStep(number: Int, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = PrimaryBlue,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d remaining", mins, secs)
}

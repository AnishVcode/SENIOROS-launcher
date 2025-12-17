package com.example.senioroslauncher.ui.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.senioroslauncher.ui.theme.*
import kotlinx.coroutines.delay

class VoiceAssistantActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Microphone permission granted")
        } else {
            Log.e(TAG, "Microphone permission denied")
        }
    }

    companion object {
        private const val TAG = "VoiceAssistantActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check microphone permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            SeniorLauncherTheme {
                VoiceAssistantScreen(
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
fun VoiceAssistantScreen(
    onClose: () -> Unit,
    viewModel: VoiceAssistantViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar
            TopBar(onClose = onClose)

            // Main Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (state) {
                    is AssistantState.Idle -> IdleScreen(
                        onStartListening = { viewModel.startListening() }
                    )
                    is AssistantState.Listening -> ListeningScreen(
                        partialText = (state as? AssistantState.Listening)?.partialText,
                        onCancel = { viewModel.stopListening() }
                    )
                    is AssistantState.Processing -> ProcessingScreen()
                    is AssistantState.Speaking -> SpeakingScreen(
                        message = (state as AssistantState.Speaking).message
                    )
                    is AssistantState.ConfirmationRequired -> ConfirmationScreen(
                        message = (state as AssistantState.ConfirmationRequired).message,
                        onConfirm = { viewModel.confirmAction() },
                        onCancel = { viewModel.cancelAction() }
                    )
                    is AssistantState.Error -> ErrorScreen(
                        error = (state as AssistantState.Error).message,
                        onRetry = { viewModel.startListening() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(onClose: () -> Unit) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Voice Assistant",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun IdleScreen(onStartListening: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Microphone icon
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Microphone",
            modifier = Modifier.size(120.dp),
            tint = PrimaryBlue.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Tap to speak",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "I can help you with:\n• Making calls\n• Sending messages\n• Managing medications\n• Setting alarms\n• And much more!",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Large tap button
        Button(
            onClick = onStartListening,
            modifier = Modifier
                .size(120.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryBlue
            )
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Start",
                modifier = Modifier.size(48.dp),
                tint = White
            )
        }
    }
}

@Composable
private fun ListeningScreen(
    partialText: String?,
    onCancel: () -> Unit
) {
    // Animated pulse effect
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Pulsing microphone
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer ring
            Surface(
                modifier = Modifier
                    .size(160.dp)
                    .scale(scale),
                shape = CircleShape,
                color = PrimaryBlue.copy(alpha = 0.2f)
            ) {}

            // Inner icon
            Surface(
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                color = PrimaryBlue
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Listening",
                        modifier = Modifier.size(60.dp),
                        tint = White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Listening...",
            style = MaterialTheme.typography.headlineMedium,
            color = PrimaryBlue
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Partial transcript
        if (!partialText.isNullOrEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = CardBlue
                )
            ) {
                Text(
                    text = partialText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Cancel button
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.size(80.dp),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel",
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun ProcessingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            strokeWidth = 6.dp,
            color = PrimaryBlue
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Processing...",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SpeakingScreen(message: String) {
    // Animated sound waves
    val infiniteTransition = rememberInfiniteTransition(label = "waves")
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave1"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Sound wave visualization
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(5) { index ->
                val delay = index * 100
                val height by infiniteTransition.animateFloat(
                    initialValue = 20f,
                    targetValue = 80f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, delayMillis = delay, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar$index"
                )
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(height.dp)
                        .background(
                            color = SecondaryGreen,
                            shape = RoundedCornerShape(6.dp)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Message
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = CardGreen
            )
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(24.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ConfirmationScreen(
    message: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = WarningOrange
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Confirmation Required",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = CardOrange
            )
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SecondaryGreen
                )
            ) {
                Text(
                    text = "Confirm",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Composable
private fun ErrorScreen(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = EmergencyRed
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Oops!",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = CardRed
            )
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryBlue
            )
        ) {
            Text(
                text = "Try Again",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
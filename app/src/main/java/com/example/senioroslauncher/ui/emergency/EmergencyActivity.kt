package com.example.senioroslauncher.ui.emergency

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.senioroslauncher.SeniorLauncherApp
import com.example.senioroslauncher.data.database.entity.EmergencyContactEntity
import com.example.senioroslauncher.data.guardian.AlertManager
import com.example.senioroslauncher.ui.components.LargeActionButton
import com.example.senioroslauncher.ui.components.SeniorTopAppBar
import com.example.senioroslauncher.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EmergencyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SeniorLauncherTheme {
                EmergencyScreen(onBackClick = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SeniorLauncherApp
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var countdownSeconds by remember { mutableIntStateOf(10) }
    var isCountdownActive by remember { mutableStateOf(true) }
    var emergencyTriggered by remember { mutableStateOf(false) }

    val emergencyContacts by app.database.emergencyContactDao()
        .getAllContacts()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Countdown timer
    LaunchedEffect(isCountdownActive) {
        if (isCountdownActive && !emergencyTriggered) {
            while (countdownSeconds > 0 && isCountdownActive) {
                delay(1000)
                countdownSeconds--
                // Vibrate each second
                vibrateDevice(context)
            }
            if (countdownSeconds == 0 && isCountdownActive) {
                emergencyTriggered = true
                triggerEmergency(context, emergencyContacts)
            }
        }
    }

    Scaffold(
        topBar = {
            SeniorTopAppBar(
                title = "EMERGENCY",
                onBackClick = {
                    isCountdownActive = false
                    onBackClick()
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(if (isCountdownActive && !emergencyTriggered) EmergencyRed.copy(alpha = 0.1f) else MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            if (!emergencyTriggered) {
                // Countdown Section
                if (isCountdownActive) {
                    CountdownSection(
                        seconds = countdownSeconds,
                        onCancel = {
                            isCountdownActive = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Emergency Services
                Text(
                    text = "Emergency Services",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EmergencyServiceButton(
                        icon = Icons.Default.LocalPolice,
                        label = "Police",
                        number = "100",
                        color = PrimaryBlue,
                        onClick = { callNumber(context, "100") },
                        modifier = Modifier.weight(1f)
                    )
                    EmergencyServiceButton(
                        icon = Icons.Default.LocalHospital,
                        label = "Ambulance",
                        number = "102",
                        color = EmergencyRed,
                        onClick = { callNumber(context, "102") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EmergencyServiceButton(
                        icon = Icons.Default.LocalFireDepartment,
                        label = "Fire",
                        number = "101",
                        color = WarningOrange,
                        onClick = { callNumber(context, "101") },
                        modifier = Modifier.weight(1f)
                    )
                    EmergencyServiceButton(
                        icon = Icons.Default.Emergency,
                        label = "Emergency",
                        number = "112",
                        color = SecondaryGreen,
                        onClick = { callNumber(context, "112") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Emergency Contacts
                if (emergencyContacts.isNotEmpty()) {
                    Text(
                        text = "Your Emergency Contacts",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(emergencyContacts) { contact ->
                            EmergencyContactCard(
                                contact = contact,
                                onCallClick = { callNumber(context, contact.phoneNumber) },
                                onMessageClick = { sendSMS(context, contact.phoneNumber) }
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardOrange)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = WarningOrange
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No emergency contacts set up",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Add emergency contacts in Settings to enable automatic alerts",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // Emergency Triggered State
                EmergencyTriggeredSection(
                    contacts = emergencyContacts,
                    onDismiss = {
                        emergencyTriggered = false
                        isCountdownActive = false
                        onBackClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun CountdownSection(
    seconds: Int,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = EmergencyRed)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "EMERGENCY ALERT",
                style = MaterialTheme.typography.headlineMedium,
                color = White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Calling emergency contacts in",
                style = MaterialTheme.typography.bodyLarge,
                color = White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$seconds",
                style = MaterialTheme.typography.displayLarge,
                color = White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "seconds",
                style = MaterialTheme.typography.titleMedium,
                color = White
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = White,
                    contentColor = EmergencyRed
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I'M OK - CANCEL",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmergencyServiceButton(
    icon: ImageVector,
    label: String,
    number: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(36.dp),
                tint = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = number,
                style = MaterialTheme.typography.bodyLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EmergencyContactCard(
    contact: EmergencyContactEntity,
    onCallClick: () -> Unit,
    onMessageClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = if (contact.isPrimary) EmergencyRed.copy(alpha = 0.15f) else CardBlue
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (contact.isPrimary) EmergencyRed else PrimaryBlue
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (contact.isPrimary) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = EmergencyRed.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "PRIMARY",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmergencyRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (contact.relationship.isNotEmpty()) {
                    Text(
                        text = contact.relationship,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Actions
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMessageClick()
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Message,
                    contentDescription = "Send Message",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCallClick()
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = SecondaryGreen,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun EmergencyTriggeredSection(
    contacts: List<EmergencyContactEntity>,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = SuccessGreen.copy(alpha = 0.15f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = SuccessGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Emergency Alert Sent",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (contacts.isNotEmpty()) {
            Text(
                text = "Your emergency contacts have been notified with your location",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "Please call emergency services manually",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        LargeActionButton(
            text = "Return Home",
            onClick = onDismiss,
            icon = Icons.Default.Home,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

// Utility functions
private fun vibrateDevice(context: Context) {
    val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(200)
    }
}

private fun callNumber(context: Context, number: String) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        context.startActivity(intent)
    } else {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        context.startActivity(intent)
    }
}

private fun sendSMS(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
    context.startActivity(intent)
}

private fun triggerEmergency(context: Context, contacts: List<EmergencyContactEntity>) {
    // Get location if available
    val location = getLocation(context)

    // Trigger Guardian alert (SOS)
    AlertManager.triggerSOSAlert(
        context = context,
        latitude = location?.latitude,
        longitude = location?.longitude
    )

    if (contacts.isEmpty()) return

    // Get location text for SMS
    val locationText = getLocationText(context)

    // Send SMS to all contacts
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
        val smsManager = SmsManager.getDefault()
        val message = "EMERGENCY ALERT! I need help! $locationText"

        contacts.forEach { contact ->
            try {
                smsManager.sendTextMessage(contact.phoneNumber, null, message, null, null)
            } catch (e: Exception) {
                // SMS failed
            }
        }
    }

    // Call primary contact
    val primaryContact = contacts.find { it.isPrimary } ?: contacts.firstOrNull()
    primaryContact?.let { contact ->
        callNumber(context, contact.phoneNumber)
    }
}

private fun getLocation(context: Context): Location? {
    return try {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

private fun getLocationText(context: Context): String {
    return try {
        val location = getLocation(context)
        if (location != null) {
            "My location: https://maps.google.com/?q=${location.latitude},${location.longitude}"
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            "Location permission not granted"
        } else {
            "Location unavailable"
        }
    } catch (e: Exception) {
        "Location unavailable"
    }
}

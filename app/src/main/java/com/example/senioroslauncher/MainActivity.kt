package com.example.senioroslauncher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.hardware.camera2.CameraManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.net.wifi.WifiManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.senioroslauncher.data.database.entity.SpeedDialContactEntity
import com.example.senioroslauncher.data.preferences.PreferencesManager
import com.example.senioroslauncher.ui.apps.AllAppsActivity
import com.example.senioroslauncher.ui.assistant.VoiceAssistantActivity
import com.example.senioroslauncher.ui.calendar.CalendarActivity
import com.example.senioroslauncher.ui.components.*
import com.example.senioroslauncher.ui.contacts.ContactsActivity
import com.example.senioroslauncher.ui.contacts.SpeedDialActivity
import com.example.senioroslauncher.ui.emergency.EmergencyActivity
import com.example.senioroslauncher.ui.health.HealthActivity
import com.example.senioroslauncher.ui.help.HelpActivity
import com.example.senioroslauncher.ui.medication.MedicationActivity
import com.example.senioroslauncher.ui.messages.MessagesActivity
import com.example.senioroslauncher.ui.notes.NotesActivity
import com.example.senioroslauncher.ui.ride.RideBookingActivity
import com.example.senioroslauncher.ui.settings.SettingsActivity
import com.example.senioroslauncher.ui.theme.*
import com.example.senioroslauncher.ui.video.VideoContactsActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SeniorLauncherTheme {
                HomeScreen()
            }
        }
    }

    override fun onBackPressed() {
        // Do nothing - this is a launcher, back should not exit
    }
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as SeniorLauncherApp
    val prefsManager = remember { PreferencesManager(context) }

    val scrollState = rememberScrollState()
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }
    var sosHoldProgress by remember { mutableFloatStateOf(0f) }
    var isSosHolding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Speed dial contacts from database
    val speedDialContacts by app.database.speedDialContactDao()
        .getAllSpeedDialContacts()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Update time every second
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance()
            delay(1000)
        }
    }

    // SOS hold timer
    LaunchedEffect(isSosHolding) {
        if (isSosHolding) {
            sosHoldProgress = 0f
            while (isSosHolding && sosHoldProgress < 1f) {
                delay(30)
                sosHoldProgress += 0.01f
                if (sosHoldProgress >= 1f) {
                    // Trigger SOS
                    context.startActivity(Intent(context, EmergencyActivity::class.java))
                    isSosHolding = false
                    sosHoldProgress = 0f
                }
            }
        } else {
            sosHoldProgress = 0f
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                .statusBarsPadding()
        ) {
            // Greeting and Time
            GreetingSection(currentTime)

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Settings Panel
            QuickSettingsPanel()

            Spacer(modifier = Modifier.height(16.dp))

            // Speed Dial Section
            SpeedDialSection(
                contacts = speedDialContacts,
                onContactClick = { contact ->
                    makePhoneCall(context, contact.phoneNumber)
                },
                onAddClick = {
                    context.startActivity(Intent(context, SpeedDialActivity::class.java))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Main App Grid
            AppGrid(context)

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Section with SOS and Voice
            BottomActionsSection(
                sosHoldProgress = sosHoldProgress,
                onSosStart = { isSosHolding = true },
                onSosEnd = { isSosHolding = false },
                context = context
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GreetingSection(currentTime: Calendar) {
    val hour = currentTime.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }

    val timeFormat = SimpleDateFormat("h:mm", Locale.getDefault())
    val amPmFormat = SimpleDateFormat("a", Locale.getDefault())
    val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = timeFormat.format(currentTime.time),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = amPmFormat.format(currentTime.time),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Text(
            text = dateFormat.format(currentTime.time),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickSettingsPanel() {
    val context = LocalContext.current
    var wifiEnabled by remember { mutableStateOf(isWifiEnabled(context)) }
    var bluetoothEnabled by remember { mutableStateOf(isBluetoothEnabled(context)) }
    var flashlightOn by remember { mutableStateOf(false) }
    var ringerMode by remember { mutableIntStateOf(getRingerMode(context)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Quick Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickSettingButton(
                    icon = if (wifiEnabled) Icons.Default.Wifi else Icons.Default.WifiOff,
                    label = "WiFi",
                    isActive = wifiEnabled,
                    onClick = {
                        // Open WiFi settings
                        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    }
                )
                QuickSettingButton(
                    icon = if (bluetoothEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                    label = "Bluetooth",
                    isActive = bluetoothEnabled,
                    onClick = {
                        // Open Bluetooth settings
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                )
                QuickSettingButton(
                    icon = if (flashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                    label = "Torch",
                    isActive = flashlightOn,
                    onClick = {
                        flashlightOn = !flashlightOn
                        toggleFlashlight(context, flashlightOn)
                    }
                )
                QuickSettingButton(
                    icon = when (ringerMode) {
                        AudioManager.RINGER_MODE_NORMAL -> Icons.Default.VolumeUp
                        AudioManager.RINGER_MODE_VIBRATE -> Icons.Default.Vibration
                        else -> Icons.Default.VolumeOff
                    },
                    label = when (ringerMode) {
                        AudioManager.RINGER_MODE_NORMAL -> "Sound"
                        AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                        else -> "Silent"
                    },
                    isActive = ringerMode == AudioManager.RINGER_MODE_NORMAL,
                    onClick = {
                        ringerMode = cycleRingerMode(context, ringerMode)
                    }
                )
            }
        }
    }
}

@Composable
private fun SpeedDialSection(
    contacts: List<SpeedDialContactEntity>,
    onContactClick: (SpeedDialContactEntity) -> Unit,
    onAddClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speed Dial",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onAddClick) {
                    Text("Manage", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (i in 0 until 5) {
                    val contact = contacts.find { it.position == i }
                    if (contact != null) {
                        SpeedDialButton(
                            name = contact.name,
                            photoUri = contact.photoUri,
                            onClick = { onContactClick(contact) },
                            onLongClick = { }
                        )
                    } else {
                        EmptySpeedDialSlot(onClick = onAddClick)
                    }
                }
            }
        }
    }
}

data class AppItem(
    val icon: ImageVector,
    val label: String,
    val backgroundColor: Color,
    val iconColor: Color,
    val onClick: (Context) -> Unit
)

@Composable
private fun AppGrid(context: Context) {
    val appItems = remember {
        listOf(
            AppItem(Icons.Default.Phone, "Phone", CardGreen, PhoneGreen) { ctx ->
                ctx.startActivity(Intent(Intent.ACTION_DIAL))
            },
            AppItem(Icons.Default.Message, "Messages", CardBlue, MessageBlue) { ctx ->
                ctx.startActivity(Intent(ctx, MessagesActivity::class.java))
            },
            AppItem(Icons.Default.CameraAlt, "Camera", CardTeal, CameraGray) { ctx ->
                ctx.startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
            },
            AppItem(Icons.Default.Contacts, "Contacts", CardPurple, ContactsBlue) { ctx ->
                ctx.startActivity(Intent(ctx, ContactsActivity::class.java))
            },
            AppItem(Icons.Default.PhotoLibrary, "Gallery", CardPurple, GalleryPurple) { ctx ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.type = "image/*"
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                ctx.startActivity(intent)
            },
            AppItem(Icons.Default.Medication, "Medication", CardOrange, MedicationOrange) { ctx ->
                ctx.startActivity(Intent(ctx, MedicationActivity::class.java))
            },
            AppItem(Icons.Default.Favorite, "Health", CardRed, HealthRed) { ctx ->
                ctx.startActivity(Intent(ctx, HealthActivity::class.java))
            },
            AppItem(Icons.Default.CalendarMonth, "Calendar", CardTeal, CalendarTeal) { ctx ->
                ctx.startActivity(Intent(ctx, CalendarActivity::class.java))
            },
            AppItem(Icons.Default.VideoCall, "Video Call", CardBlue, VideoCallGreen) { ctx ->
                ctx.startActivity(Intent(ctx, VideoContactsActivity::class.java))
            },
            AppItem(Icons.Default.DirectionsCar, "Ride", CardYellow, RideYellow) { ctx ->
                ctx.startActivity(Intent(ctx, RideBookingActivity::class.java))
            },
            AppItem(Icons.Default.Notes, "Notes", CardYellow, NotesAmber) { ctx ->
                ctx.startActivity(Intent(ctx, NotesActivity::class.java))
            },
            AppItem(Icons.Default.Settings, "Settings", LightGray, SettingsGray) { ctx ->
                ctx.startActivity(Intent(ctx, SettingsActivity::class.java))
            }
        )
    }

    // Add All Apps and Help to the list
    val allAppItems = appItems + listOf(
        AppItem(Icons.Default.Apps, "All Apps", CardPurple, AppsIndigo) { ctx ->
            ctx.startActivity(Intent(ctx, AllAppsActivity::class.java))
        },
        AppItem(Icons.Default.Help, "Help", CardBlue, HelpBlue) { ctx ->
            ctx.startActivity(Intent(ctx, HelpActivity::class.java))
        }
    )

    Column {
        Text(
            text = "Apps",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // 2 columns grid with larger buttons
        val totalRows = (allAppItems.size + 1) / 2
        for (row in 0 until totalRows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (col in 0 until 2) {
                    val index = row * 2 + col
                    if (index < allAppItems.size) {
                        val item = allAppItems[index]
                        LargeAppButton(
                            icon = item.icon,
                            label = item.label,
                            backgroundColor = item.backgroundColor,
                            iconColor = item.iconColor,
                            onClick = { item.onClick(context) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            if (row < totalRows - 1) Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BottomActionsSection(
    sosHoldProgress: Float,
    onSosStart: () -> Unit,
    onSosEnd: () -> Unit,
    context: Context
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Voice Assistant Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .size(72.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                // Launch voice assistant
                                val intent = Intent(context, VoiceAssistantActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            }
                        )
                    },
                shape = CircleShape,
                color = PrimaryBlue,
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Assistant",
                        modifier = Modifier.size(36.dp),
                        tint = White
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Voice",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // SOS Button with hold progress
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Progress ring
                if (sosHoldProgress > 0) {
                    CircularProgressIndicator(
                        progress = { sosHoldProgress },
                        modifier = Modifier.size(96.dp),
                        strokeWidth = 6.dp,
                        color = EmergencyRedDark
                    )
                }

                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSosStart()
                                    tryAwaitRelease()
                                    onSosEnd()
                                }
                            )
                        },
                    shape = CircleShape,
                    color = EmergencyRed,
                    shadowElevation = 8.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SOS",
                            style = MaterialTheme.typography.headlineSmall,
                            color = White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Hold 3 sec",
                style = MaterialTheme.typography.labelSmall,
                color = EmergencyRed
            )
        }

        // Quick Call Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .size(72.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                // Open phone dialer
                                context.startActivity(Intent(Intent.ACTION_DIAL))
                            }
                        )
                    },
                shape = CircleShape,
                color = SecondaryGreen,
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call",
                        modifier = Modifier.size(36.dp),
                        tint = White
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Call",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// Utility functions
private fun isWifiEnabled(context: Context): Boolean {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    return wifiManager.isWifiEnabled
}

private fun isBluetoothEnabled(context: Context): Boolean {
    return try {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter?.isEnabled == true
    } catch (e: Exception) {
        false
    }
}

private fun toggleFlashlight(context: Context, on: Boolean) {
    try {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[0]
        cameraManager.setTorchMode(cameraId, on)
    } catch (e: Exception) {
        // Flashlight not available
    }
}

private fun getRingerMode(context: Context): Int {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return audioManager.ringerMode
}

private fun cycleRingerMode(context: Context, currentMode: Int): Int {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val newMode = when (currentMode) {
        AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
        AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
        else -> AudioManager.RINGER_MODE_NORMAL
    }
    try {
        audioManager.ringerMode = newMode
    } catch (e: Exception) {
        // May need DND permission
    }
    return newMode
}

private fun makePhoneCall(context: Context, phoneNumber: String) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber"))
        context.startActivity(intent)
    } else {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
        context.startActivity(intent)
    }
}

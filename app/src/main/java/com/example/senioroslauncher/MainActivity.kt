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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.senioroslauncher.data.database.entity.SpeedDialContactEntity
import com.example.senioroslauncher.data.preferences.PreferencesManager
import com.example.senioroslauncher.ui.apps.AllAppsActivity
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
import com.example.senioroslauncher.util.LocaleHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LocaleHelper.getLanguageCode(newBase)
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase, languageCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SeniorLauncherTheme {
                HomeScreen()
            }
        }
    }

    @Deprecated("Deprecated in Java")
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
        in 5..11 -> stringResource(R.string.good_morning)
        in 12..16 -> stringResource(R.string.good_afternoon)
        in 17..20 -> stringResource(R.string.good_evening)
        else -> stringResource(R.string.good_night)
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
                text = stringResource(R.string.quick_settings),
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
                    label = stringResource(R.string.wifi),
                    isActive = wifiEnabled,
                    onClick = {
                        // Open WiFi settings
                        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    }
                )
                QuickSettingButton(
                    icon = if (bluetoothEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                    label = stringResource(R.string.bluetooth),
                    isActive = bluetoothEnabled,
                    onClick = {
                        // Open Bluetooth settings
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                )
                QuickSettingButton(
                    icon = if (flashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                    label = stringResource(R.string.torch),
                    isActive = flashlightOn,
                    onClick = {
                        flashlightOn = !flashlightOn
                        toggleFlashlight(context, flashlightOn)
                    }
                )
                val soundLabel = stringResource(R.string.sound)
                val vibrateLabel = stringResource(R.string.vibrate)
                val silentLabel = stringResource(R.string.silent)
                QuickSettingButton(
                    icon = when (ringerMode) {
                        AudioManager.RINGER_MODE_NORMAL -> Icons.Default.VolumeUp
                        AudioManager.RINGER_MODE_VIBRATE -> Icons.Default.Vibration
                        else -> Icons.Default.VolumeOff
                    },
                    label = when (ringerMode) {
                        AudioManager.RINGER_MODE_NORMAL -> soundLabel
                        AudioManager.RINGER_MODE_VIBRATE -> vibrateLabel
                        else -> silentLabel
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
                    text = stringResource(R.string.speed_dial),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onAddClick) {
                    Text(stringResource(R.string.manage), style = MaterialTheme.typography.labelLarge)
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
    val labelResId: Int,
    val backgroundColor: Color,
    val iconColor: Color,
    val onClick: (Context) -> Unit
)

@Composable
private fun AppGrid(context: Context) {
    val appItems = remember {
        listOf(
            AppItem(Icons.Default.Phone, R.string.phone, CardGreen, PhoneGreen) { ctx ->
                ctx.startActivity(Intent(Intent.ACTION_DIAL))
            },
            AppItem(Icons.Default.Message, R.string.messages, CardBlue, MessageBlue) { ctx ->
                ctx.startActivity(Intent(ctx, MessagesActivity::class.java))
            },
            AppItem(Icons.Default.CameraAlt, R.string.camera, CardTeal, CameraGray) { ctx ->
                // Try to open the default camera app
                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                if (intent.resolveActivity(ctx.packageManager) != null) {
                    ctx.startActivity(intent)
                } else {
                    // Fallback to any camera app
                    val fallbackIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    if (fallbackIntent.resolveActivity(ctx.packageManager) != null) {
                        ctx.startActivity(fallbackIntent)
                    }
                }
            },
            AppItem(Icons.Default.Contacts, R.string.contacts, CardPurple, ContactsBlue) { ctx ->
                ctx.startActivity(Intent(ctx, ContactsActivity::class.java))
            },
            AppItem(Icons.Default.PhotoLibrary, R.string.gallery, CardPurple, GalleryPurple) { ctx ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.type = "image/*"
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                ctx.startActivity(intent)
            },
            AppItem(Icons.Default.Medication, R.string.medication, CardOrange, MedicationOrange) { ctx ->
                ctx.startActivity(Intent(ctx, MedicationActivity::class.java))
            },
            AppItem(Icons.Default.Favorite, R.string.health, CardRed, HealthRed) { ctx ->
                ctx.startActivity(Intent(ctx, HealthActivity::class.java))
            },
            AppItem(Icons.Default.CalendarMonth, R.string.calendar, CardTeal, CalendarTeal) { ctx ->
                ctx.startActivity(Intent(ctx, CalendarActivity::class.java))
            },
            AppItem(Icons.Default.VideoCall, R.string.video_call, CardBlue, VideoCallGreen) { ctx ->
                ctx.startActivity(Intent(ctx, VideoContactsActivity::class.java))
            },
            AppItem(Icons.Default.DirectionsCar, R.string.ride, CardYellow, RideYellow) { ctx ->
                ctx.startActivity(Intent(ctx, RideBookingActivity::class.java))
            },
            AppItem(Icons.Default.Notes, R.string.notes, CardYellow, NotesAmber) { ctx ->
                ctx.startActivity(Intent(ctx, NotesActivity::class.java))
            },
            AppItem(Icons.Default.Settings, R.string.settings, LightGray, SettingsGray) { ctx ->
                ctx.startActivity(Intent(ctx, SettingsActivity::class.java))
            }
        )
    }

    // Add All Apps and Help to the list
    val allAppItems = appItems + listOf(
        AppItem(Icons.Default.Apps, R.string.all_apps, CardPurple, AppsIndigo) { ctx ->
            ctx.startActivity(Intent(ctx, AllAppsActivity::class.java))
        },
        AppItem(Icons.Default.Help, R.string.help, CardBlue, HelpBlue) { ctx ->
            ctx.startActivity(Intent(ctx, HelpActivity::class.java))
        }
    )

    Column {
        Text(
            text = stringResource(R.string.apps),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // 1 app per row with full-width buttons
        allAppItems.forEachIndexed { index, item ->
            FullWidthAppButton(
                icon = item.icon,
                label = stringResource(item.labelResId),
                backgroundColor = item.backgroundColor,
                iconColor = item.iconColor,
                onClick = { item.onClick(context) },
                modifier = Modifier.fillMaxWidth()
            )
            if (index < allAppItems.size - 1) {
                Spacer(modifier = Modifier.height(12.dp))
            }
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
                                val intent = Intent(Intent.ACTION_VOICE_COMMAND)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Voice assistant not available
                                }
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
                text = stringResource(R.string.voice),
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
                            text = stringResource(R.string.sos),
                            style = MaterialTheme.typography.headlineSmall,
                            color = White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.hold_3_sec),
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
                text = stringResource(R.string.call),
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

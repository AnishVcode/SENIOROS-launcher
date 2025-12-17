package com.example.senioroslauncher.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.senioroslauncher.R
import com.example.senioroslauncher.SeniorLauncherApp
import com.example.senioroslauncher.data.preferences.PreferencesManager
import com.example.senioroslauncher.services.FallDetectionService
import com.example.senioroslauncher.ui.components.LargeListItem
import com.example.senioroslauncher.ui.components.LargeSettingsSwitch
import com.example.senioroslauncher.ui.components.SeniorTopAppBar
import com.example.senioroslauncher.ui.guardian.PairingActivity
import com.example.senioroslauncher.ui.theme.*
import com.example.senioroslauncher.util.LocaleHelper
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val languageCode = LocaleHelper.getLanguageCode(newBase)
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase, languageCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SeniorLauncherTheme {
                SettingsScreen(onBackClick = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager(context) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Collect settings
    val hearingAidMode by prefsManager.hearingAidMode.collectAsStateWithLifecycle(initialValue = false)
    val voiceFeedback by prefsManager.voiceFeedback.collectAsStateWithLifecycle(initialValue = false)
    val antiShake by prefsManager.antiShake.collectAsStateWithLifecycle(initialValue = false)
    val doubleTapConfirm by prefsManager.doubleTapConfirm.collectAsStateWithLifecycle(initialValue = false)
    val touchVibration by prefsManager.touchVibration.collectAsStateWithLifecycle(initialValue = true)
    val fallDetection by prefsManager.fallDetection.collectAsStateWithLifecycle(initialValue = false)
    val locationSharing by prefsManager.locationSharing.collectAsStateWithLifecycle(initialValue = false)
    val autoAnswerCalls by prefsManager.autoAnswerCalls.collectAsStateWithLifecycle(initialValue = false)
    val language by prefsManager.language.collectAsStateWithLifecycle(initialValue = "en")
    val elderName by prefsManager.elderName.collectAsStateWithLifecycle(initialValue = "")
    val elderAge by prefsManager.elderAge.collectAsStateWithLifecycle(initialValue = null)

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SeniorTopAppBar(
                title = stringResource(R.string.settings),
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Accessibility Section
            Text(
                text = stringResource(R.string.accessibility),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LargeSettingsSwitch(
                title = stringResource(R.string.hearing_aid_mode),
                description = stringResource(R.string.hearing_aid_mode_desc),
                checked = hearingAidMode,
                onCheckedChange = {
                    scope.launch { prefsManager.setHearingAidMode(it) }
                },
                icon = Icons.Default.Hearing
            )

            LargeSettingsSwitch(
                title = stringResource(R.string.voice_feedback),
                description = stringResource(R.string.voice_feedback_desc),
                checked = voiceFeedback,
                onCheckedChange = {
                    scope.launch { prefsManager.setVoiceFeedback(it) }
                },
                icon = Icons.Default.RecordVoiceOver
            )

            LargeSettingsSwitch(
                title = stringResource(R.string.anti_shake),
                description = stringResource(R.string.anti_shake_desc),
                checked = antiShake,
                onCheckedChange = {
                    scope.launch { prefsManager.setAntiShake(it) }
                },
                icon = Icons.Default.DoNotTouch
            )

            LargeSettingsSwitch(
                title = stringResource(R.string.double_tap_confirm),
                description = stringResource(R.string.double_tap_confirm_desc),
                checked = doubleTapConfirm,
                onCheckedChange = {
                    scope.launch { prefsManager.setDoubleTapConfirm(it) }
                },
                icon = Icons.Default.TouchApp
            )

            LargeSettingsSwitch(
                title = stringResource(R.string.touch_vibration),
                description = stringResource(R.string.touch_vibration_desc),
                checked = touchVibration,
                onCheckedChange = {
                    scope.launch { prefsManager.setTouchVibration(it) }
                },
                icon = Icons.Default.Vibration
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Safety Section
            Text(
                text = stringResource(R.string.safety),
                style = MaterialTheme.typography.titleLarge,
                color = EmergencyRed,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LargeSettingsSwitch(
                title = stringResource(R.string.fall_detection),
                description = stringResource(R.string.fall_detection_desc),
                checked = fallDetection,
                onCheckedChange = {
                    scope.launch {
                        prefsManager.setFallDetection(it)
                        if (it) {
                            // Start fall detection service
                            val intent = Intent(context, FallDetectionService::class.java)
                            context.startForegroundService(intent)
                        } else {
                            // Stop fall detection service
                            val intent = Intent(context, FallDetectionService::class.java)
                            context.stopService(intent)
                        }
                    }
                },
                icon = Icons.Default.PersonOff
            )

            LargeSettingsSwitch(
                title = stringResource(R.string.location_sharing),
                description = stringResource(R.string.location_sharing_desc),
                checked = locationSharing,
                onCheckedChange = {
                    scope.launch { prefsManager.setLocationSharing(it) }
                },
                icon = Icons.Default.LocationOn
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Calls Section
            Text(
                text = stringResource(R.string.calls),
                style = MaterialTheme.typography.titleLarge,
                color = PhoneGreen,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LargeSettingsSwitch(
                title = stringResource(R.string.auto_answer_calls),
                description = stringResource(R.string.auto_answer_calls_desc),
                checked = autoAnswerCalls,
                onCheckedChange = {
                    scope.launch { prefsManager.setAutoAnswerCalls(it) }
                },
                icon = Icons.Default.CallReceived
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Language Section
            Text(
                text = stringResource(R.string.language),
                style = MaterialTheme.typography.titleLarge,
                color = PrimaryBlue,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LargeListItem(
                title = stringResource(R.string.app_language),
                subtitle = getLanguageName(language),
                onClick = { showLanguageDialog = true },
                leadingIcon = Icons.Default.Language,
                leadingIconColor = PrimaryBlue,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MediumGray
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Emergency Contacts Section
            Text(
                text = stringResource(R.string.emergency),
                style = MaterialTheme.typography.titleLarge,
                color = EmergencyRed,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LargeListItem(
                title = stringResource(R.string.emergency_contacts),
                subtitle = stringResource(R.string.manage_emergency_contacts),
                onClick = {
                    context.startActivity(Intent(context, EmergencyContactsSettingsActivity::class.java))
                },
                leadingIcon = Icons.Default.ContactPhone,
                leadingIconColor = EmergencyRed,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MediumGray
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Guardian Integration Section
            Text(
                text = stringResource(R.string.family_guardian),
                style = MaterialTheme.typography.titleLarge,
                color = PrimaryBlue,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            val setNameAgeText = stringResource(R.string.set_name_age)
            LargeListItem(
                title = stringResource(R.string.my_profile),
                subtitle = if (elderName.isNotEmpty()) "$elderName${elderAge?.let { ", Age $it" } ?: ""}" else setNameAgeText,
                onClick = { showProfileDialog = true },
                leadingIcon = Icons.Default.Person,
                leadingIconColor = PrimaryBlue,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MediumGray
                    )
                }
            )

            LargeListItem(
                title = stringResource(R.string.pair_with_guardian),
                subtitle = stringResource(R.string.connect_family_caregiver),
                onClick = {
                    context.startActivity(Intent(context, PairingActivity::class.java))
                },
                leadingIcon = Icons.Default.FamilyRestroom,
                leadingIconColor = PrimaryBlue,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MediumGray
                    )
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // About
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = stringResource(R.string.version),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.designed_with_care),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Language Dialog
    if (showLanguageDialog) {
        val languages = listOf(
            "en" to "English",
            "hi" to "हिंदी (Hindi)",
            "ta" to "தமிழ் (Tamil)",
            "te" to "తెలుగు (Telugu)"
        )

        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.select_language), style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column {
                    languages.forEach { (code, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = language == code,
                                onClick = {
                                    scope.launch {
                                        prefsManager.setLanguage(code)
                                    }
                                    // Save to LocaleHelper
                                    LocaleHelper.saveLanguageCode(context, code)
                                    showLanguageDialog = false
                                    // Restart the app to apply language change
                                    (context as? Activity)?.let { activity ->
                                        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
                                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                        activity.startActivity(intent)
                                        activity.finish()
                                        Runtime.getRuntime().exit(0)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Profile Dialog
    if (showProfileDialog) {
        var nameInput by remember { mutableStateOf(elderName) }
        var ageInput by remember { mutableStateOf(elderAge?.toString() ?: "") }

        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text(stringResource(R.string.my_profile), style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.profile_info_shared),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(stringResource(R.string.your_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ageInput,
                        onValueChange = { input ->
                            // Only allow numbers
                            if (input.isEmpty() || input.all { it.isDigit() }) {
                                ageInput = input.take(3) // Max 3 digits
                            }
                        },
                        label = { Text(stringResource(R.string.your_age)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            prefsManager.setElderName(nameInput.trim())
                            ageInput.toIntOrNull()?.let { prefsManager.setElderAge(it) }
                        }
                        showProfileDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun getLanguageName(code: String): String = when (code) {
    "en" -> "English"
    "hi" -> "हिंदी (Hindi)"
    "ta" -> "தமிழ் (Tamil)"
    "te" -> "తెలుగు (Telugu)"
    else -> "English"
}

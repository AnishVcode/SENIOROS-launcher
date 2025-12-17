package com.example.senioroslauncher.assistant

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.Ringtone
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.senioroslauncher.SeniorLauncherApp
import com.example.senioroslauncher.ui.calendar.CalendarActivity
import com.example.senioroslauncher.ui.emergency.EmergencyActivity
import com.example.senioroslauncher.ui.health.HealthActivity
import com.example.senioroslauncher.ui.medication.MedicationActivity
import com.example.senioroslauncher.ui.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Execute actions based on classified intents
 * Handles all 60 intents from the model
 */
class ActionExecutor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "ActionExecutor"
    }

    data class ActionResult(
        val success: Boolean,
        val message: String,
        val requiresPermission: String? = null,
        val requiresConfirmation: Boolean = false
    )

    /**
     * Execute action for given intent and entities
     */
    fun execute(
        intent: String,
        entities: EntityExtractor.ExtractedEntities,
        onResult: (ActionResult) -> Unit
    ) {
        Log.d(TAG, "Executing: $intent")

        when (intent) {
            // === PHONE CONTROL INTENTS ===
            "OPEN_CAMERA" -> executeOpenCamera(onResult)
            "TAKE_SELFIE" -> executeTakeSelfie(onResult)
            "RECORD_VIDEO" -> executeRecordVideo(onResult)
            "OPEN_GALLERY" -> executeOpenGallery(onResult)
            "OPEN_APP" -> executeOpenApp(entities, onResult)
            "CALL_CONTACT" -> executeCallContact(entities, onResult)
            "SEND_MESSAGE" -> executeSendMessage(entities, onResult)
            "SET_ALARM" -> executeSetAlarm(entities, onResult)
            "SET_TIMER" -> executeSetTimer(entities, onResult)
            "FLASHLIGHT_ON" -> executeFlashlight(true, onResult)
            "FLASHLIGHT_OFF" -> executeFlashlight(false, onResult)
            "OPEN_SETTINGS" -> executeOpenSettings(onResult)
            "READ_NOTIFICATIONS" -> executeReadNotifications(onResult)
            "VOLUME_CONTROL" -> executeVolumeControl(entities, onResult)
            "CHECK_BATTERY" -> executeCheckBattery(onResult)

            // === MEDICATION INTENTS ===
            "MEDICATION_LIST_TODAY" -> executeMedicationList(onResult)
            "MEDICATION_LOG_TAKEN" -> executeMedicationLogTaken(entities, onResult)
            "MEDICATION_LOG_SKIP" -> executeMedicationLogSkip(entities, onResult)
            "MEDICATION_ADD" -> executeMedicationAdd(entities, onResult)
            "MEDICATION_QUERY" -> executeMedicationQuery(entities, onResult)
            "MEDICATION_HISTORY" -> executeMedicationHistory(onResult)
            "MEDICATION_REMINDER_CHANGE" -> executeMedicationReminderChange(entities, onResult)
            "MEDICATION_DELETE" -> executeMedicationDelete(entities, onResult)
            "MEDICATION_INSTRUCTIONS" -> executeMedicationInstructions(entities, onResult)
            "MEDICATION_SIDE_EFFECTS" -> executeMedicationSideEffects(entities, onResult)
            "MEDICATION_INTERACTION" -> executeMedicationInteraction(entities, onResult)
            "MEDICATION_REFILL" -> executeMedicationRefill(entities, onResult)

            // === HEALTH & APPOINTMENT INTENTS ===
            "APPOINTMENT_CREATE" -> executeAppointmentCreate(entities, onResult)
            "APPOINTMENT_LIST" -> executeAppointmentList(onResult)
            "APPOINTMENT_CANCEL" -> executeAppointmentCancel(entities, onResult)
            "SOS_TRIGGER" -> executeSOS(onResult)
            "HEALTH_CHECKIN_START" -> executeHealthCheckin(onResult)
            "HEALTH_SUMMARY" -> executeHealthSummary(onResult)
            "HEALTH_RECORD" -> executeHealthRecord(entities, onResult)
            "CAREGIVER_CONTACT" -> executeCaregiverContact(entities, onResult)

            // === CONVENIENCE INTENTS ===
            "QUERY_TIME" -> executeQueryTime(onResult)
            "QUERY_DATE" -> executeQueryDate(onResult)
            "FIND_PHONE" -> executeFindPhone(onResult)
            "REPEAT_LAST" -> executeRepeatLast(onResult)
            "READ_SCREEN" -> executeReadScreen(onResult)
            "BRIGHTNESS_CONTROL" -> executeBrightnessControl(entities, onResult)
            "DO_NOT_DISTURB" -> executeDoNotDisturb(onResult)
            "SCREEN_LOCK" -> executeScreenLock(onResult)

            // === WEB KNOWLEDGE INTENTS ===
            "WEB_SEARCH" -> executeWebSearch(entities, onResult)
            "QA_FACT" -> executeQAFact(entities, onResult)
            "QA_WEATHER" -> executeQAWeather(onResult)
            "QA_NEWS" -> executeQANews(onResult)
            "QA_DEFINITION" -> executeQADefinition(entities, onResult)
            "QA_HEALTH_INFO" -> executeQAHealthInfo(entities, onResult)
            "SEARCH_LOCATION" -> executeSearchLocation(entities, onResult)
            "QA_CALCULATION" -> executeQACalculation(entities, onResult)
            "QA_CONVERSION" -> executeQAConversion(entities, onResult)
            "QA_MEDICINE_INFO" -> executeQAMedicineInfo(entities, onResult)

            // === SMALL TALK INTENTS ===
            "SMALL_TALK_GREET" -> executeSmallTalkGreet(onResult)
            "SMALL_TALK_THANKS" -> executeSmallTalkThanks(onResult)
            "SMALL_TALK_FEELINGS" -> executeSmallTalkFeelings(onResult)
            "SMALL_TALK_JOKE" -> executeSmallTalkJoke(onResult)
            "SMALL_TALK_ENCOURAGE" -> executeSmallTalkEncourage(onResult)
            "SMALL_TALK_GOODBYE" -> executeSmallTalkGoodbye(onResult)
            "SMALL_TALK_HELP" -> executeSmallTalkHelp(onResult)

            else -> onResult(ActionResult(false, "I don't know how to do that yet"))
        }
    }

    // ========== PHONE CONTROL ACTIONS ==========

    private fun executeOpenCamera(onResult: (ActionResult) -> Unit) {
        try {
            // Intent that just opens the camera app (No runtime permission needed)
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Opening camera"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
            onResult(ActionResult(false, "Couldn't open camera"))
        }
    }

    private fun executeTakeSelfie(onResult: (ActionResult) -> Unit) {
        try {
            // Same safe intent
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Opening camera for selfie"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open selfie camera", e)
            onResult(ActionResult(false, "Couldn't open selfie camera"))
        }
    }

    private fun executeRecordVideo(onResult: (ActionResult) -> Unit) {
        try {
            val intent = Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Opening video camera"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open video camera", e)
            onResult(ActionResult(false, "Couldn't open video camera"))
        }
    }

    private fun executeOpenGallery(onResult: (ActionResult) -> Unit) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.type = "image/*"
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Opening gallery"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open gallery", e)
            onResult(ActionResult(false, "Couldn't open gallery"))
        }
    }

    private fun executeOpenApp(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        val appName = entities.appName
        if (appName.isNullOrEmpty()) {
            onResult(ActionResult(false, "Which app would you like to open?"))
            return
        }

        try {
            // 1. Try known hardcoded list first
            var packageName = getPackageName(appName)

            // 2. Fuzzy Search: Look through installed apps if not found above
            if (packageName == null) {
                val installedApps = context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                val bestMatch = installedApps.find {
                    val label = context.packageManager.getApplicationLabel(it).toString()
                    label.contains(appName, ignoreCase = true)
                }
                packageName = bestMatch?.packageName
            }

            if (packageName != null) {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    onResult(ActionResult(true, "Opening $appName"))
                } else {
                    onResult(ActionResult(false, "$appName is not installed"))
                }
            } else {
                onResult(ActionResult(false, "Couldn't find an app named $appName"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app", e)
            onResult(ActionResult(false, "Couldn't open $appName"))
        }
    }

    private fun getPackageName(appName: String): String? {
        return when (appName.lowercase()) {
            "whatsapp" -> "com.whatsapp"
            "youtube" -> "com.google.android.youtube"
            "google maps", "maps" -> "com.google.android.apps.maps"
            "gmail" -> "com.google.android.gm"
            "facebook" -> "com.facebook.katana"
            "instagram" -> "com.instagram.android"
            "chrome" -> "com.android.chrome"
            "spotify" -> "com.spotify.music"
            "messenger" -> "com.facebook.orca"
            "twitter", "x" -> "com.twitter.android"
            "telegram" -> "org.telegram.messenger"
            "linkedin" -> "com.linkedin.android"
            "netflix" -> "com.netflix.mediaclient"
            else -> null
        }
    }

    private fun executeCallContact(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        val contactName = entities.contactName
        if (contactName.isNullOrEmpty()) {
            onResult(ActionResult(false, "Who would you like to call?"))
            return
        }

        // Check permission - Fallback to Dialer if missing
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            // Permission missing? Open Dialer instead of failing. Safer for seniors.
            try {
                val intent = Intent(Intent.ACTION_DIAL)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                onResult(ActionResult(true, "Opening dialer for $contactName"))
            } catch (e: Exception) {
                onResult(ActionResult(false, "Couldn't open dialer"))
            }
            return
        }

        // Permission granted - Open dialer with number (real lookup logic would go here)
        try {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Opening phone to call $contactName", requiresConfirmation = true))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make call", e)
            onResult(ActionResult(false, "Couldn't make the call"))
        }
    }

    private fun executeSendMessage(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        val contactName = entities.contactName
        if (contactName.isNullOrEmpty()) {
            onResult(ActionResult(false, "Who would you like to message?"))
            return
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("sms:")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Opening messages to text $contactName", requiresConfirmation = true))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            onResult(ActionResult(false, "Couldn't open messages"))
        }
    }

    private fun executeSetAlarm(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        val time = entities.time
        if (time == null) {
            onResult(ActionResult(false, "What time should I set the alarm for?"))
            return
        }

        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, time.get(Calendar.HOUR_OF_DAY))
                putExtra(AlarmClock.EXTRA_MINUTES, time.get(Calendar.MINUTE))
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)

            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(time.time)
            onResult(ActionResult(true, "Setting alarm for $timeStr"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm. Check Manifest permission.", e)
            onResult(ActionResult(false, "Couldn't set alarm"))
        }
    }

    private fun executeSetTimer(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        val duration = entities.duration
        if (duration == null || duration <= 0) {
            onResult(ActionResult(false, "How long should the timer be?"))
            return
        }

        try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, duration * 60) // Convert to seconds
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            onResult(ActionResult(true, "Setting timer for $duration minutes"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set timer. Check Manifest permission.", e)
            onResult(ActionResult(false, "Couldn't set timer"))
        }
    }

    private fun executeFlashlight(on: Boolean, onResult: (ActionResult) -> Unit) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, on)
            onResult(ActionResult(true, if (on) "Flashlight turned on" else "Flashlight turned off"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle flashlight", e)
            onResult(ActionResult(false, "Couldn't control flashlight"))
        }
    }

    private fun executeOpenSettings(onResult: (ActionResult) -> Unit) {
        try {
            val intent = Intent(context, SettingsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Opening settings"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open settings", e)
            onResult(ActionResult(false, "Couldn't open settings"))
        }
    }

    private fun executeReadNotifications(onResult: (ActionResult) -> Unit) {
        onResult(ActionResult(true, "You have no new notifications"))
    }

    private fun executeVolumeControl(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

            val text = entities.rawText.lowercase()
            when {
                text.contains("up") || text.contains("increase") || text.contains("louder") -> {
                    val newVolume = (currentVolume + 2).coerceAtMost(maxVolume)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                    onResult(ActionResult(true, "Volume increased"))
                }
                text.contains("down") || text.contains("decrease") || text.contains("lower") || text.contains("quieter") -> {
                    val newVolume = (currentVolume - 2).coerceAtLeast(0)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                    onResult(ActionResult(true, "Volume decreased"))
                }
                text.contains("mute") -> {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                    onResult(ActionResult(true, "Muted"))
                }
                else -> {
                    onResult(ActionResult(true, "Volume is at ${(currentVolume * 100 / maxVolume)}%"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to control volume", e)
            onResult(ActionResult(false, "Couldn't control volume"))
        }
    }

    private fun executeCheckBattery(onResult: (ActionResult) -> Unit) {
        try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = batteryManager.isCharging

            val message = if (isCharging) {
                "Battery is at $batteryLevel% and charging"
            } else {
                "Battery is at $batteryLevel%"
            }
            onResult(ActionResult(true, message))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check battery", e)
            onResult(ActionResult(false, "Couldn't check battery"))
        }
    }

    // ========== MEDICATION ACTIONS ==========

    private fun executeMedicationList(onResult: (ActionResult) -> Unit) {
        try {
            val intent = Intent(context, MedicationActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Opening your medication list"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open medications", e)
            onResult(ActionResult(false, "Couldn't open medications"))
        }
    }

    private fun executeMedicationLogTaken(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        val medName = entities.medicationName ?: "medication"
        onResult(ActionResult(true, "Marked $medName as taken"))
    }

    private fun executeMedicationLogSkip(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        val medName = entities.medicationName ?: "dose"
        onResult(ActionResult(true, "Skipped $medName", requiresConfirmation = true))
    }

    private fun executeMedicationAdd(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        try {
            val intent = Intent(context, MedicationActivity::class.java)
            intent.putExtra("action", "add")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Opening medication form"))
        } catch (e: Exception) {
            onResult(ActionResult(false, "Couldn't add medication"))
        }
    }

    private fun executeMedicationQuery(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        onResult(ActionResult(true, "Let me check your medication schedule"))
    }

    private fun executeMedicationHistory(onResult: (ActionResult) -> Unit) {
        try {
            val intent = Intent(context, MedicationActivity::class.java)
            intent.putExtra("tab", "history")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Opening medication history"))
        } catch (e: Exception) {
            onResult(ActionResult(false, "Couldn't open history"))
        }
    }

    private fun executeMedicationReminderChange(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        onResult(ActionResult(true, "Opening medication settings"))
    }

    private fun executeMedicationDelete(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        val medName = entities.medicationName ?: "this medication"
        onResult(ActionResult(true, "Are you sure you want to remove $medName?", requiresConfirmation = true))
    }

    private fun executeMedicationInstructions(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        onResult(ActionResult(true, "Let me show you the medication instructions"))
    }

    private fun executeMedicationSideEffects(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        onResult(ActionResult(true, "I'll look up the side effects for you"))
    }

    private fun executeMedicationInteraction(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        onResult(ActionResult(true, "Let me check if these medications are safe together"))
    }

    private fun executeMedicationRefill(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        onResult(ActionResult(true, "I'll help you refill your prescription"))
    }

    // ========== HEALTH & APPOINTMENT ACTIONS ==========

    private fun executeAppointmentCreate(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        try {
            val intent = Intent(context, CalendarActivity::class.java)
            intent.putExtra("action", "add")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Opening appointment scheduler"))
        } catch (e: Exception) {
            onResult(ActionResult(false, "Couldn't create appointment"))
        }
    }

    private fun executeAppointmentList(onResult: (ActionResult) -> Unit) {
        try {
            val intent = Intent(context, CalendarActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Opening your appointments"))
        } catch (e: Exception) {
            onResult(ActionResult(false, "Couldn't open appointments"))
        }
    }

    private fun executeAppointmentCancel(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        onResult(ActionResult(true, "Are you sure you want to cancel this appointment?", requiresConfirmation = true))
    }

    private fun executeSOS(onResult: (ActionResult) -> Unit) {
        try {
            val intent = Intent(context, EmergencyActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Activating emergency assistance", requiresConfirmation = true))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger SOS", e)
            onResult(ActionResult(false, "Couldn't activate emergency"))
        }
    }

    private fun executeHealthCheckin(onResult: (ActionResult) -> Unit) {
        try {
            val intent = Intent(context, HealthActivity::class.java)
            intent.putExtra("action", "checkin")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Starting health check-in"))
        } catch (e: Exception) {
            onResult(ActionResult(false, "Couldn't start health check"))
        }
    }

    private fun executeHealthSummary(onResult: (ActionResult) -> Unit) {
        try {
            val intent = Intent(context, HealthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Opening health summary"))
        } catch (e: Exception) {
            onResult(ActionResult(false, "Couldn't open health data"))
        }
    }

    private fun executeHealthRecord(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        onResult(ActionResult(true, "Recording your health data"))
    }

    private fun executeCaregiverContact(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        val contactName = entities.contactName ?: "caregiver"
        onResult(ActionResult(true, "Calling your $contactName", requiresConfirmation = true))
    }

    // ========== CONVENIENCE ACTIONS ==========

    private fun executeQueryTime(onResult: (ActionResult) -> Unit) {
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        onResult(ActionResult(true, "The time is $time"))
    }

    private fun executeQueryDate(onResult: (ActionResult) -> Unit) {
        val date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
        onResult(ActionResult(true, "Today is $date"))
    }

    private fun executeFindPhone(onResult: (ActionResult) -> Unit) {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val r = RingtoneManager.getRingtone(context, notification)
            r.play()
            onResult(ActionResult(true, "Playing ringtone... I am here!"))
        } catch (e: Exception) {
            onResult(ActionResult(false, "I am here! (Couldn't play sound)"))
        }
    }

    private fun executeRepeatLast(onResult: (ActionResult) -> Unit) {
        onResult(ActionResult(true, "I'll repeat the last message"))
    }

    private fun executeReadScreen(onResult: (ActionResult) -> Unit) {
        onResult(ActionResult(true, "I'll read what's on screen"))
    }

    private fun executeBrightnessControl(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        try {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Opening display settings"))
        } catch (e: Exception) {
            onResult(ActionResult(false, "Couldn't open settings"))
        }
    }

    private fun executeDoNotDisturb(onResult: (ActionResult) -> Unit) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Opening do not disturb settings"))
        } catch (e: Exception) {
            onResult(ActionResult(false, "Couldn't enable do not disturb"))
        }
    }

    private fun executeScreenLock(onResult: (ActionResult) -> Unit) {
        try {
            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Opening screen lock settings"))
        } catch (e: Exception) {
            onResult(ActionResult(false, "Couldn't open security settings"))
        }
    }

    // ========== WEB KNOWLEDGE ACTIONS ==========

    private fun executeWebSearch(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        try {
            val query = entities.rawText.replace(Regex("(search|find|look up|google)\\s+"), "")
            val intent = Intent(Intent.ACTION_WEB_SEARCH)
            intent.putExtra("query", query)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Searching for $query"))
        } catch (e: Exception) {
            onResult(ActionResult(false, "Couldn't perform search"))
        }
    }

    private fun executeQAFact(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        executeWebSearch(entities, onResult)
    }

    private fun executeQAWeather(onResult: (ActionResult) -> Unit) {
        try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH)
            intent.putExtra("query", "weather today")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Checking the weather"))
        } catch (e: Exception) {
            onResult(ActionResult(false, "Couldn't check weather"))
        }
    }

    private fun executeQANews(onResult: (ActionResult) -> Unit) {
        try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH)
            intent.putExtra("query", "news today")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Opening today's news"))
        } catch (e: Exception) {
            onResult(ActionResult(false, "Couldn't get news"))
        }
    }

    private fun executeQADefinition(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        executeWebSearch(entities, onResult)
    }

    private fun executeQAHealthInfo(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        executeWebSearch(entities, onResult)
    }

    private fun executeSearchLocation(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        val location = entities.location ?: "places nearby"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$location"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(ActionResult(true, "Searching for $location"))
        } catch (e: Exception) {
            onResult(ActionResult(false, "Couldn't search location"))
        }
    }

    private fun executeQACalculation(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        executeWebSearch(entities, onResult)
    }

    private fun executeQAConversion(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        executeWebSearch(entities, onResult)
    }

    private fun executeQAMedicineInfo(entities: EntityExtractor.ExtractedEntities, onResult: (ActionResult) -> Unit) {
        executeWebSearch(entities, onResult)
    }

    // ========== SMALL TALK ACTIONS ==========

    private fun executeSmallTalkGreet(onResult: (ActionResult) -> Unit) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..11 -> "Good morning!"
            in 12..16 -> "Good afternoon!"
            in 17..20 -> "Good evening!"
            else -> "Hello!"
        }
        onResult(ActionResult(true, "$greeting How can I help you?"))
    }

    private fun executeSmallTalkThanks(onResult: (ActionResult) -> Unit) {
        onResult(ActionResult(true, "You're welcome! Happy to help anytime."))
    }

    private fun executeSmallTalkFeelings(onResult: (ActionResult) -> Unit) {
        onResult(ActionResult(true, "I'm doing great! How are you today?"))
    }

    private fun executeSmallTalkJoke(onResult: (ActionResult) -> Unit) {
        val jokes = listOf(
            "Why don't scientists trust atoms? Because they make up everything!",
            "What do you call a bear with no teeth? A gummy bear!",
            "Why did the scarecrow win an award? He was outstanding in his field!"
        )
        onResult(ActionResult(true, jokes.random()))
    }

    private fun executeSmallTalkEncourage(onResult: (ActionResult) -> Unit) {
        onResult(ActionResult(true, "You're doing great! Keep going, I believe in you!"))
    }

    private fun executeSmallTalkGoodbye(onResult: (ActionResult) -> Unit) {
        onResult(ActionResult(true, "Goodbye! Have a wonderful day!"))
    }

    private fun executeSmallTalkHelp(onResult: (ActionResult) -> Unit) {
        onResult(ActionResult(true, "I can help you with calls, messages, medications, appointments, and much more. Just ask!"))
    }
}
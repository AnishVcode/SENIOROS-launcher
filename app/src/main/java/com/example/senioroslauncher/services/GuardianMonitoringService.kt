package com.example.senioroslauncher.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.senioroslauncher.MainActivity
import com.example.senioroslauncher.R
import com.example.senioroslauncher.SeniorLauncherApp
import com.example.senioroslauncher.data.database.AppDatabase
import com.example.senioroslauncher.data.database.entity.AlertEntity
import com.example.senioroslauncher.data.database.entity.AlertType
import com.example.senioroslauncher.data.database.entity.PairedGuardianEntity
import com.example.senioroslauncher.data.guardian.ElderIdentity
import com.example.senioroslauncher.data.guardian.WebSocketManager
import com.example.senioroslauncher.data.guardian.handlers.GetAlertHistoryHandler
import com.example.senioroslauncher.data.guardian.handlers.GetHealthHistoryHandler
import com.example.senioroslauncher.data.guardian.handlers.GetMedicationsHandler
import com.example.senioroslauncher.data.guardian.handlers.GetStateHandler
import com.example.senioroslauncher.data.guardian.handlers.MedicationCommandHandler
import com.example.senioroslauncher.data.guardian.handlers.NotificationCommandHandler
import com.example.senioroslauncher.data.guardian.handlers.EmergencyContactCommandHandler
import com.example.senioroslauncher.data.guardian.models.AlertEventPayload
import com.example.senioroslauncher.data.guardian.models.GuardianPairedPayload
import com.example.senioroslauncher.data.guardian.models.GuardianUnpairedPayload
import com.example.senioroslauncher.data.guardian.models.WebSocketMessage
import com.example.senioroslauncher.data.guardian.models.LocationInfo
import com.example.senioroslauncher.data.guardian.models.OutgoingMessageTypes
import com.example.senioroslauncher.data.preferences.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.Calendar
import java.util.Date

/**
 * Foreground service that maintains WebSocket connection to Guardian relay server.
 * Handles incoming requests from guardians and monitors for alerts.
 */
class GuardianMonitoringService : Service() {
    companion object {
        private const val TAG = "GuardianMonitoringService"
        private const val NOTIFICATION_ID = 2001
        private const val BATTERY_CHECK_INTERVAL = 30 * 60 * 1000L // 30 minutes
        private const val INACTIVITY_CHECK_INTERVAL = 60 * 60 * 1000L // 1 hour
        private const val LOW_BATTERY_THRESHOLD = 20

        const val ACTION_START = "com.example.senioroslauncher.guardian.START"
        const val ACTION_STOP = "com.example.senioroslauncher.guardian.STOP"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var database: AppDatabase
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var webSocketManager: WebSocketManager
    private lateinit var getStateHandler: GetStateHandler
    private lateinit var getMedicationsHandler: GetMedicationsHandler
    private lateinit var getAlertHistoryHandler: GetAlertHistoryHandler
    private lateinit var getHealthHistoryHandler: GetHealthHistoryHandler
    private lateinit var medicationCommandHandler: MedicationCommandHandler
    private lateinit var notificationCommandHandler: NotificationCommandHandler
    private lateinit var emergencyContactCommandHandler: EmergencyContactCommandHandler

    private var lastLowBatteryAlertTime: Long = 0

    private val batteryCheckRunnable = object : Runnable {
        override fun run() {
            checkBatteryLevel()
            handler.postDelayed(this, BATTERY_CHECK_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        database = AppDatabase.getDatabase(this)
        preferencesManager = PreferencesManager(this)

        initializeWebSocket()
        initializeHandlers()
    }

    private fun initializeWebSocket() {
        webSocketManager = WebSocketManager(
            context = this,
            onGetState = { message ->
                getStateHandler.handle(message)
            },
            onGetMedications = { message ->
                getMedicationsHandler.handle(message)
            },
            onGetAlertHistory = { message ->
                getAlertHistoryHandler.handle(message)
            },
            onGetHealthHistory = { message ->
                getHealthHistoryHandler.handle(message)
            },
            onGuardianPaired = { message ->
                handleGuardianPaired(message)
            },
            onGuardianUnpaired = { message ->
                handleGuardianUnpaired(message)
            },
            onAddMedication = { message ->
                handleAddMedication(message)
            },
            onUpdateMedication = { message ->
                handleUpdateMedication(message)
            },
            onDeleteMedication = { message ->
                handleDeleteMedication(message)
            },
            onSendReminder = { message ->
                handleSendReminder(message)
            },
            onSendMessage = { message ->
                handleSendMessage(message)
            },
            onUpdateEmergencyContact = { message ->
                handleUpdateEmergencyContact(message)
            },
            onDeleteEmergencyContact = { message ->
                handleDeleteEmergencyContact(message)
            }
        )
    }

    private fun initializeHandlers() {
        getStateHandler = GetStateHandler(this, webSocketManager, preferencesManager, database)
        getMedicationsHandler = GetMedicationsHandler(this, webSocketManager, database)
        getAlertHistoryHandler = GetAlertHistoryHandler(this, webSocketManager, database)
        getHealthHistoryHandler = GetHealthHistoryHandler(this, webSocketManager, database)
        medicationCommandHandler = MedicationCommandHandler(this)
        notificationCommandHandler = NotificationCommandHandler(this)
        emergencyContactCommandHandler = EmergencyContactCommandHandler(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            "MEDICATION_UPDATED" -> {
                handleMedicationUpdated(intent)
                return START_STICKY
            }
        }

        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())

        // Connect WebSocket
        webSocketManager.connect()

        // Start monitoring tasks
        startBatteryMonitoring()

        return START_STICKY
    }

    private fun handleMedicationUpdated(intent: Intent) {
        serviceScope.launch {
            try {
                val payload = intent.getStringExtra("payload") ?: return@launch
                val guardianId = intent.getStringExtra("guardian_id") ?: return@launch
                val elderId = ElderIdentity.getOrCreateElderId(this@GuardianMonitoringService)

                val payloadElement = json.parseToJsonElement(payload)
                val message = WebSocketMessage(
                    type = "MEDICATION_UPDATED",
                    from = elderId,
                    to = guardianId,
                    requestId = "med-update-${System.currentTimeMillis()}",
                    payload = payloadElement,
                    timestamp = java.time.Instant.now().toString()
                )

                webSocketManager.sendMessage(message)
                Log.d(TAG, "Sent medication update to guardian: $guardianId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle medication updated", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")

        handler.removeCallbacks(batteryCheckRunnable)
        webSocketManager.disconnect()
        serviceScope.cancel()
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SeniorLauncherApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Guardian Connected")
            .setContentText("Your family can see your status")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startBatteryMonitoring() {
        handler.postDelayed(batteryCheckRunnable, BATTERY_CHECK_INTERVAL)
    }

    private fun checkBatteryLevel() {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        Log.d(TAG, "Battery level: $batteryLevel%")

        if (batteryLevel <= LOW_BATTERY_THRESHOLD) {
            val currentTime = System.currentTimeMillis()
            // Only alert once every 4 hours
            if (currentTime - lastLowBatteryAlertTime > 4 * 60 * 60 * 1000L) {
                lastLowBatteryAlertTime = currentTime
                triggerAlert(AlertType.LOW_BATTERY, "Battery is low: $batteryLevel%", batteryLevel)
            }
        }
    }

    private suspend fun handleGuardianPaired(message: WebSocketMessage) {
        try {
            val payload = message.payload?.let {
                json.decodeFromJsonElement<GuardianPairedPayload>(it)
            } ?: return

            Log.d(TAG, "Guardian paired: ${payload.guardianId}")

            // Save to database
            val guardian = PairedGuardianEntity(
                guardianId = payload.guardianId,
                guardianName = payload.guardianName,
                pairedAt = Date()
            )
            database.pairedGuardianDao().insert(guardian)

            // Update notification
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling guardian paired", e)
        }
    }

    private suspend fun handleGuardianUnpaired(message: WebSocketMessage) {
        try {
            val payload = message.payload?.let {
                json.decodeFromJsonElement<GuardianUnpairedPayload>(it)
            } ?: return

            Log.d(TAG, "Guardian unpaired: ${payload.guardianId}")

            // Remove from database
            database.pairedGuardianDao().deleteById(payload.guardianId)

            // Update notification
            updateNotification()

            // If no more guardians, consider stopping service
            val remainingCount = database.pairedGuardianDao().getGuardianCountSync()
            if (remainingCount == 0) {
                Log.d(TAG, "No more paired guardians, stopping service")
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling guardian unpaired", e)
        }
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Trigger an alert and broadcast to all paired guardians.
     */
    fun triggerAlert(
        type: AlertType,
        notes: String,
        batteryLevel: Int? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        serviceScope.launch {
            try {
                val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val currentBattery = batteryLevel
                    ?: batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

                // Create alert entity
                val alert = AlertEntity(
                    type = type,
                    triggeredAt = Date(),
                    latitude = latitude,
                    longitude = longitude,
                    batteryLevel = currentBattery,
                    notes = notes
                )

                // Save to database
                val alertId = database.alertDao().insert(alert)

                Log.d(TAG, "Alert triggered: $type - $notes (id: $alertId)")

                // Broadcast to all guardians
                val guardians = database.pairedGuardianDao().getAllGuardiansSync()
                val elderId = ElderIdentity.getOrCreateElderId(this@GuardianMonitoringService)

                val payload = AlertEventPayload(
                    id = alertId.toString(),
                    elderId = elderId,
                    type = type.name,
                    triggeredAt = alert.triggeredAt.toInstant().toString(),
                    location = if (latitude != null && longitude != null) {
                        LocationInfo(latitude, longitude)
                    } else null,
                    batteryLevel = currentBattery,
                    resolved = false,
                    notes = notes
                )

                val payloadJson = json.encodeToString(payload)

                guardians.forEach { guardian ->
                    webSocketManager.sendResponse(
                        type = OutgoingMessageTypes.ALERT_EVENT,
                        to = guardian.guardianId,
                        requestId = java.util.UUID.randomUUID().toString(),
                        payload = payloadJson
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering alert", e)
            }
        }
    }

    /**
     * Get the WebSocket manager for external access.
     */
    fun getWebSocketManager(): WebSocketManager = webSocketManager

    // ============== Command Handlers ==============

    private suspend fun handleAddMedication(message: WebSocketMessage) {
        serviceScope.launch {
            try {
                val response = medicationCommandHandler.handleAddMedication(message)
                webSocketManager.sendMessage(response)
                Log.d(TAG, "Add medication command handled")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling add medication", e)
            }
        }
    }

    private suspend fun handleUpdateMedication(message: WebSocketMessage) {
        serviceScope.launch {
            try {
                val response = medicationCommandHandler.handleUpdateMedication(message)
                webSocketManager.sendMessage(response)
                Log.d(TAG, "Update medication command handled")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling update medication", e)
            }
        }
    }

    private suspend fun handleDeleteMedication(message: WebSocketMessage) {
        serviceScope.launch {
            try {
                val response = medicationCommandHandler.handleDeleteMedication(message)
                webSocketManager.sendMessage(response)
                Log.d(TAG, "Delete medication command handled")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling delete medication", e)
            }
        }
    }

    private suspend fun handleSendReminder(message: WebSocketMessage) {
        serviceScope.launch {
            try {
                val response = notificationCommandHandler.handleSendReminder(message)
                webSocketManager.sendMessage(response)
                Log.d(TAG, "Send reminder command handled")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling send reminder", e)
            }
        }
    }

    private suspend fun handleSendMessage(message: WebSocketMessage) {
        serviceScope.launch {
            try {
                val response = notificationCommandHandler.handleSendMessage(message)
                webSocketManager.sendMessage(response)
                Log.d(TAG, "Send message command handled")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling send message", e)
            }
        }
    }

    private suspend fun handleUpdateEmergencyContact(message: WebSocketMessage) {
        serviceScope.launch {
            try {
                val response = emergencyContactCommandHandler.handleUpdateEmergencyContact(message)
                webSocketManager.sendMessage(response)
                Log.d(TAG, "Update emergency contact command handled")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling update emergency contact", e)
            }
        }
    }

    private suspend fun handleDeleteEmergencyContact(message: WebSocketMessage) {
        serviceScope.launch {
            try {
                val response = emergencyContactCommandHandler.handleDeleteEmergencyContact(message)
                webSocketManager.sendMessage(response)
                Log.d(TAG, "Delete emergency contact command handled")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling delete emergency contact", e)
            }
        }
    }
}

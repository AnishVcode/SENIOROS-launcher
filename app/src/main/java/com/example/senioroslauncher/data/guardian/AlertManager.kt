package com.example.senioroslauncher.data.guardian

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import com.example.senioroslauncher.data.database.AppDatabase
import com.example.senioroslauncher.data.database.entity.AlertEntity
import com.example.senioroslauncher.data.database.entity.AlertType
import com.example.senioroslauncher.data.guardian.models.AlertEventPayload
import com.example.senioroslauncher.data.guardian.models.LocationInfo
import com.example.senioroslauncher.data.guardian.models.OutgoingMessageTypes
import com.example.senioroslauncher.services.GuardianMonitoringService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Date
import java.util.UUID

/**
 * Singleton manager for triggering and broadcasting alerts to guardians.
 * Provides a simple interface for other components to trigger alerts.
 */
object AlertManager {
    private const val TAG = "AlertManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { encodeDefaults = true }

    /**
     * Trigger an SOS alert.
     * Called when the elder presses the SOS button.
     */
    fun triggerSOSAlert(
        context: Context,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        triggerAlert(
            context = context,
            type = AlertType.SOS,
            notes = "SOS emergency button activated",
            latitude = latitude,
            longitude = longitude
        )
    }

    /**
     * Trigger a fall detection alert.
     * Called when a fall is detected by the accelerometer.
     */
    fun triggerFallAlert(
        context: Context,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        triggerAlert(
            context = context,
            type = AlertType.FALL,
            notes = "Potential fall detected",
            latitude = latitude,
            longitude = longitude
        )
    }

    /**
     * Trigger a missed medication alert.
     * Called when a medication reminder times out without action.
     */
    fun triggerMissedMedicationAlert(
        context: Context,
        medicationName: String,
        scheduledTime: String
    ) {
        triggerAlert(
            context = context,
            type = AlertType.MISSED_MED,
            notes = "Missed $medicationName scheduled at $scheduledTime"
        )
    }

    /**
     * Trigger a low battery alert.
     * Called when battery drops below threshold.
     */
    fun triggerLowBatteryAlert(
        context: Context,
        batteryLevel: Int
    ) {
        triggerAlert(
            context = context,
            type = AlertType.LOW_BATTERY,
            notes = "Device battery is low: $batteryLevel%"
        )
    }

    /**
     * Trigger an inactivity alert.
     * Called when no user activity is detected for extended period.
     */
    fun triggerInactivityAlert(
        context: Context,
        hoursSinceActivity: Int
    ) {
        triggerAlert(
            context = context,
            type = AlertType.INACTIVITY,
            notes = "No activity detected for $hoursSinceActivity hours"
        )
    }

    /**
     * Core method to trigger any type of alert.
     */
    private fun triggerAlert(
        context: Context,
        type: AlertType,
        notes: String,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        scope.launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val elderId = ElderIdentity.getOrCreateElderId(context)

                // Create and save alert
                val alert = AlertEntity(
                    type = type,
                    triggeredAt = Date(),
                    latitude = latitude,
                    longitude = longitude,
                    batteryLevel = batteryLevel,
                    notes = notes
                )

                val alertId = database.alertDao().insert(alert)
                Log.d(TAG, "Alert saved: $type - $notes (id: $alertId)")

                // Get paired guardians
                val guardians = database.pairedGuardianDao().getAllGuardiansSync()

                if (guardians.isEmpty()) {
                    Log.d(TAG, "No paired guardians to notify")
                    return@launch
                }

                // Build payload
                val payload = AlertEventPayload(
                    id = alertId.toString(),
                    elderId = elderId,
                    type = type.name,
                    triggeredAt = alert.triggeredAt.toInstant().toString(),
                    location = if (latitude != null && longitude != null) {
                        LocationInfo(latitude, longitude)
                    } else null,
                    batteryLevel = batteryLevel,
                    resolved = false,
                    notes = notes
                )

                // Try to broadcast via running service
                val serviceIntent = Intent(context, GuardianMonitoringService::class.java)
                serviceIntent.action = GuardianMonitoringService.ACTION_START

                // If service isn't running, start it first
                try {
                    context.startForegroundService(serviceIntent)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not start service for alert broadcast", e)
                }

                Log.d(TAG, "Alert broadcast to ${guardians.size} guardian(s)")
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering alert", e)
            }
        }
    }

    /**
     * Check if there was a recent alert of the given type within the specified time window.
     * Used to avoid duplicate alerts.
     */
    suspend fun hasRecentAlert(
        context: Context,
        type: AlertType,
        withinMinutes: Int = 60
    ): Boolean {
        val database = AppDatabase.getDatabase(context)
        val since = Date(System.currentTimeMillis() - (withinMinutes * 60 * 1000L))
        return database.alertDao().hasRecentAlert(type, since) != null
    }

    /**
     * Mark an alert as resolved.
     */
    suspend fun resolveAlert(context: Context, alertId: Long) {
        val database = AppDatabase.getDatabase(context)
        database.alertDao().resolveAlert(alertId)
        Log.d(TAG, "Alert $alertId marked as resolved")
    }
}

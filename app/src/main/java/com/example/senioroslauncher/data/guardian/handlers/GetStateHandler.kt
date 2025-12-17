package com.example.senioroslauncher.data.guardian.handlers

import android.content.Context
import android.os.BatteryManager
import com.example.senioroslauncher.data.database.AppDatabase
import com.example.senioroslauncher.data.database.entity.MedicationAction
import com.example.senioroslauncher.data.guardian.ElderIdentity
import com.example.senioroslauncher.data.guardian.WebSocketManager
import com.example.senioroslauncher.data.guardian.models.AlertInfo
import com.example.senioroslauncher.data.guardian.models.ElderInfo
import com.example.senioroslauncher.data.guardian.models.LocationInfo
import com.example.senioroslauncher.data.guardian.models.MedicationSummary
import com.example.senioroslauncher.data.guardian.models.OutgoingMessageTypes
import com.example.senioroslauncher.data.guardian.models.StateResponsePayload
import com.example.senioroslauncher.data.guardian.models.WebSocketMessage
import com.example.senioroslauncher.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Handles GET_STATE requests from Guardian app.
 * Returns current elder state including profile, battery, recent alerts, and medication summary.
 */
class GetStateHandler(
    private val context: Context,
    private val webSocketManager: WebSocketManager,
    private val preferencesManager: PreferencesManager,
    private val database: AppDatabase
) {
    private val json = Json { encodeDefaults = true }
    private val elderId = ElderIdentity.getOrCreateElderId(context)

    suspend fun handle(message: WebSocketMessage) {
        val elderName = preferencesManager.elderName.first().ifEmpty {
            preferencesManager.userName.first()
        }
        val elderAge = preferencesManager.elderAge.first()
        val batteryLevel = getBatteryLevel()

        // Get recent alerts (last 5)
        val recentAlerts = database.alertDao().getRecentAlertsSync(5).map { alert ->
            AlertInfo(
                id = alert.id.toString(),
                elderId = elderId,
                type = alert.type.name,
                triggeredAt = alert.triggeredAt.toInstant().toString(),
                location = if (alert.latitude != null && alert.longitude != null) {
                    LocationInfo(alert.latitude, alert.longitude)
                } else null,
                batteryLevel = alert.batteryLevel,
                resolved = alert.resolved,
                notes = alert.notes
            )
        }

        // Calculate today's medication stats
        val medicationSummary = calculateTodayMedicationStats()

        val payload = StateResponsePayload(
            elder = ElderInfo(
                name = elderName,
                age = elderAge,
                batteryLevel = batteryLevel,
                lastHeartbeat = Instant.now().toString()
            ),
            recentAlerts = recentAlerts,
            medicationSummary = medicationSummary
        )

        webSocketManager.sendResponse(
            type = OutgoingMessageTypes.STATE_RESPONSE,
            to = message.from,
            requestId = message.requestId,
            payload = json.encodeToString(payload)
        )
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private suspend fun calculateTodayMedicationStats(): MedicationSummary {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.time

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.time

        // Get today's logs
        val todayLogs = database.medicationLogDao()
            .getLogsBetweenDates(startOfDay, endOfDay)
            .first()

        // Get total scheduled medications for today
        val activeMedications = database.medicationDao().getAllActiveMedications().first()
        val todayDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

        var totalScheduled = 0
        for (medication in activeMedications) {
            val schedules = database.medicationScheduleDao()
                .getSchedulesForMedicationSync(medication.id)
                .filter { it.isEnabled && it.daysOfWeek.contains(todayDayOfWeek) }
            totalScheduled += schedules.size
        }

        val taken = todayLogs.count { it.action == MedicationAction.TAKEN }
        val missed = todayLogs.count {
            it.action == MedicationAction.SKIPPED || it.action == MedicationAction.MISSED
        }

        return MedicationSummary(
            todayTotal = totalScheduled,
            takenToday = taken,
            missedToday = missed
        )
    }
}

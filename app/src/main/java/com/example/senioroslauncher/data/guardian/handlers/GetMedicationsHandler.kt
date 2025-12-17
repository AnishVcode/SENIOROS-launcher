package com.example.senioroslauncher.data.guardian.handlers

import android.content.Context
import com.example.senioroslauncher.data.database.AppDatabase
import com.example.senioroslauncher.data.database.entity.MedicationAction
import com.example.senioroslauncher.data.guardian.WebSocketManager
import com.example.senioroslauncher.data.guardian.models.MedicationInfo
import com.example.senioroslauncher.data.guardian.models.MedicationLogInfo
import com.example.senioroslauncher.data.guardian.models.MedicationsResponsePayload
import com.example.senioroslauncher.data.guardian.models.OutgoingMessageTypes
import com.example.senioroslauncher.data.guardian.models.ScheduleInfo
import com.example.senioroslauncher.data.guardian.models.WebSocketMessage
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.Date

/**
 * Handles GET_MEDICATIONS requests from Guardian app.
 * Returns all medications, schedules, and recent logs.
 */
class GetMedicationsHandler(
    private val context: Context,
    private val webSocketManager: WebSocketManager,
    private val database: AppDatabase
) {
    private val json = Json { encodeDefaults = true }

    suspend fun handle(message: WebSocketMessage) {
        // Get all active medications
        val medications = database.medicationDao().getAllActiveMedications().first().map { med ->
            MedicationInfo(
                id = med.id.toString(),
                name = med.name,
                dosage = med.dosage,
                instructions = med.notes
            )
        }

        // Get all schedules
        val schedules = mutableListOf<ScheduleInfo>()
        for (med in database.medicationDao().getAllActiveMedications().first()) {
            val medSchedules = database.medicationScheduleDao()
                .getSchedulesForMedicationSync(med.id)
                .map { schedule ->
                    ScheduleInfo(
                        id = schedule.id.toString(),
                        medicationId = schedule.medicationId.toString(),
                        time = formatTime(schedule.hour, schedule.minute),
                        // Convert from 1-7 (Sunday=1) to 0-6 (Sunday=0)
                        daysOfWeek = schedule.daysOfWeek.map { (it - 1).coerceIn(0, 6) },
                        enabled = schedule.isEnabled
                    )
                }
            schedules.addAll(medSchedules)
        }

        // Get recent logs (last 7 days)
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -7)
        val sevenDaysAgo = calendar.time

        val logs = database.medicationLogDao()
            .getLogsBetweenDates(sevenDaysAgo, Date())
            .first()
            .map { log ->
                MedicationLogInfo(
                    id = log.id.toString(),
                    medicationId = log.medicationId.toString(),
                    scheduleId = "sched-${log.medicationId}",
                    scheduledTime = log.scheduledTime.toInstant().toString(),
                    takenAt = if (log.action == MedicationAction.TAKEN) {
                        log.actionTime.toInstant().toString()
                    } else null,
                    status = when (log.action) {
                        MedicationAction.TAKEN -> "taken"
                        MedicationAction.SKIPPED -> "skipped"
                        MedicationAction.MISSED -> "missed"
                        MedicationAction.SNOOZED -> "snoozed"
                    }
                )
            }

        val payload = MedicationsResponsePayload(
            medications = medications,
            schedules = schedules,
            logs = logs
        )

        webSocketManager.sendResponse(
            type = OutgoingMessageTypes.MEDICATIONS_RESPONSE,
            to = message.from,
            requestId = message.requestId,
            payload = json.encodeToString(payload)
        )
    }

    private fun formatTime(hour: Int, minute: Int): String {
        return String.format("%02d:%02d", hour, minute)
    }
}

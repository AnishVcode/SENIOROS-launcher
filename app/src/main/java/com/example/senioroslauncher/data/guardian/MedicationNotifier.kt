package com.example.senioroslauncher.data.guardian

import android.content.Context
import android.util.Log
import com.example.senioroslauncher.data.database.AppDatabase
import com.example.senioroslauncher.data.database.entity.MedicationEntity
import com.example.senioroslauncher.data.guardian.models.MedicationInfo
import com.example.senioroslauncher.data.guardian.models.MedicationUpdatedPayload
import com.example.senioroslauncher.data.guardian.models.OutgoingMessageTypes
import com.example.senioroslauncher.data.guardian.models.ScheduleInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Notifies all paired guardians when medications are added, updated, or deleted.
 */
object MedicationNotifier {
    private const val TAG = "MedicationNotifier"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { encodeDefaults = true }

    /**
     * Notify guardians when a medication is added.
     */
    fun notifyMedicationAdded(
        context: Context,
        medication: MedicationEntity
    ) {
        scope.launch {
            try {
                val elderId = ElderIdentity.getOrCreateElderId(context)
                val database = AppDatabase.getDatabase(context)

                // Get schedules for this medication
                val schedules = database.medicationScheduleDao()
                    .getSchedulesForMedicationSync(medication.id)
                    .map { schedule ->
                        ScheduleInfo(
                            id = schedule.id.toString(),
                            medicationId = schedule.medicationId.toString(),
                            time = String.format("%02d:%02d", schedule.hour, schedule.minute),
                            daysOfWeek = schedule.daysOfWeek.map { (it - 1).coerceIn(0, 6) },
                            enabled = schedule.isEnabled
                        )
                    }

                val payload = MedicationUpdatedPayload(
                    elderId = elderId,
                    action = "added",
                    medication = MedicationInfo(
                        id = medication.id.toString(),
                        name = medication.name,
                        dosage = medication.dosage,
                        instructions = medication.notes
                    ),
                    schedules = schedules
                )

                broadcastToAllGuardians(context, payload)
                Log.d(TAG, "Notified guardians: medication added - ${medication.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify medication added", e)
            }
        }
    }

    /**
     * Notify guardians when a medication is updated.
     */
    fun notifyMedicationUpdated(
        context: Context,
        medication: MedicationEntity
    ) {
        scope.launch {
            try {
                val elderId = ElderIdentity.getOrCreateElderId(context)
                val database = AppDatabase.getDatabase(context)

                val schedules = database.medicationScheduleDao()
                    .getSchedulesForMedicationSync(medication.id)
                    .map { schedule ->
                        ScheduleInfo(
                            id = schedule.id.toString(),
                            medicationId = schedule.medicationId.toString(),
                            time = String.format("%02d:%02d", schedule.hour, schedule.minute),
                            daysOfWeek = schedule.daysOfWeek.map { (it - 1).coerceIn(0, 6) },
                            enabled = schedule.isEnabled
                        )
                    }

                val payload = MedicationUpdatedPayload(
                    elderId = elderId,
                    action = "updated",
                    medication = MedicationInfo(
                        id = medication.id.toString(),
                        name = medication.name,
                        dosage = medication.dosage,
                        instructions = medication.notes
                    ),
                    schedules = schedules
                )

                broadcastToAllGuardians(context, payload)
                Log.d(TAG, "Notified guardians: medication updated - ${medication.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify medication updated", e)
            }
        }
    }

    /**
     * Notify guardians when a medication is deleted.
     */
    fun notifyMedicationDeleted(
        context: Context,
        medication: MedicationEntity
    ) {
        scope.launch {
            try {
                val elderId = ElderIdentity.getOrCreateElderId(context)

                val payload = MedicationUpdatedPayload(
                    elderId = elderId,
                    action = "deleted",
                    medication = MedicationInfo(
                        id = medication.id.toString(),
                        name = medication.name,
                        dosage = medication.dosage,
                        instructions = medication.notes
                    ),
                    schedules = emptyList()
                )

                broadcastToAllGuardians(context, payload)
                Log.d(TAG, "Notified guardians: medication deleted - ${medication.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify medication deleted", e)
            }
        }
    }

    /**
     * Broadcast to all paired guardians via the GuardianMonitoringService.
     */
    private suspend fun broadcastToAllGuardians(
        context: Context,
        payload: MedicationUpdatedPayload
    ) {
        try {
            val database = AppDatabase.getDatabase(context)
            val guardians = database.pairedGuardianDao().getAllGuardiansSync()

            if (guardians.isEmpty()) {
                Log.d(TAG, "No paired guardians to notify")
                return
            }

            // Send broadcast intent to GuardianMonitoringService
            val intent = android.content.Intent(context, com.example.senioroslauncher.services.GuardianMonitoringService::class.java)
            intent.action = "MEDICATION_UPDATED"
            intent.putExtra("payload", json.encodeToString(payload))

            guardians.forEach { guardian ->
                intent.putExtra("guardian_id", guardian.guardianId)
                try {
                    context.startService(intent)
                    Log.d(TAG, "Sent medication update intent to guardian: ${guardian.guardianId}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send intent for guardian ${guardian.guardianId}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast to guardians", e)
        }
    }
}

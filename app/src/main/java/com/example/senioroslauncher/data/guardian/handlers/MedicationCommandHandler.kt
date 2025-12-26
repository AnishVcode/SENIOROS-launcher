package com.example.senioroslauncher.data.guardian.handlers

import android.content.Context
import com.example.senioroslauncher.data.database.AppDatabase
import com.example.senioroslauncher.data.database.entity.MedicationEntity
import com.example.senioroslauncher.data.database.entity.MedicationFrequency
import com.example.senioroslauncher.data.database.entity.MedicationScheduleEntity
import com.example.senioroslauncher.data.guardian.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

/**
 * Handles medication-related commands from Guardian app:
 * - ADD_MEDICATION
 * - UPDATE_MEDICATION
 * - DELETE_MEDICATION
 */
class MedicationCommandHandler(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val medicationDao = database.medicationDao()
    private val scheduleDao = database.medicationScheduleDao()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Handle ADD_MEDICATION command
     */
    suspend fun handleAddMedication(message: WebSocketMessage): WebSocketMessage {
        return withContext(Dispatchers.IO) {
            try {
                val payload = json.decodeFromString<AddMedicationPayload>(message.payload.toString())

                // Validate input
                if (payload.name.isBlank()) {
                    return@withContext createErrorResponse(message, "Medication name cannot be empty")
                }
                if (payload.schedules.isEmpty()) {
                    return@withContext createErrorResponse(message, "At least one schedule is required")
                }

                // Insert medication (using DAILY as default frequency for guardian-added meds)
                val medication = MedicationEntity(
                    name = payload.name.trim(),
                    dosage = payload.dosage.trim(),
                    frequency = MedicationFrequency.DAILY,
                    notes = payload.instructions.trim()
                )
                val medicationId = medicationDao.insert(medication)

                // Insert schedules
                payload.schedules.forEach { schedulePayload ->
                    // Parse time "HH:mm" format
                    val timeParts = schedulePayload.time.split(":")
                    val hour = timeParts[0].toIntOrNull() ?: 0
                    val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

                    val schedule = MedicationScheduleEntity(
                        medicationId = medicationId,
                        hour = hour,
                        minute = minute,
                        daysOfWeek = schedulePayload.daysOfWeek,
                        isEnabled = schedulePayload.enabled
                    )
                    scheduleDao.insert(schedule)
                }

                createSuccessResponse(
                    message,
                    "Medication added successfully",
                    mapOf("medicationId" to medicationId.toString())
                )
            } catch (e: Exception) {
                createErrorResponse(message, "Failed to add medication: ${e.message}")
            }
        }
    }

    /**
     * Handle UPDATE_MEDICATION command
     */
    suspend fun handleUpdateMedication(message: WebSocketMessage): WebSocketMessage {
        return withContext(Dispatchers.IO) {
            try {
                val payload = json.decodeFromString<UpdateMedicationPayload>(message.payload.toString())
                val medicationId = payload.medicationId.toLongOrNull()
                    ?: return@withContext createErrorResponse(message, "Invalid medication ID")

                // Get existing medication
                val medication = medicationDao.getMedicationById(medicationId)
                    ?: return@withContext createErrorResponse(message, "Medication not found")

                // Update medication fields
                val updatedMedication = medication.copy(
                    name = payload.name?.trim() ?: medication.name,
                    dosage = payload.dosage?.trim() ?: medication.dosage,
                    notes = payload.instructions?.trim() ?: medication.notes,
                    updatedAt = Date()
                )
                medicationDao.update(updatedMedication)

                // Update schedules if provided
                payload.schedules?.let { newSchedules ->
                    // Delete old schedules
                    val oldSchedules = scheduleDao.getSchedulesForMedicationSync(medicationId)
                    oldSchedules.forEach { scheduleDao.delete(it) }

                    // Insert new schedules
                    newSchedules.forEach { schedulePayload ->
                        val timeParts = schedulePayload.time.split(":")
                        val hour = timeParts[0].toIntOrNull() ?: 0
                        val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

                        val schedule = MedicationScheduleEntity(
                            medicationId = medicationId,
                            hour = hour,
                            minute = minute,
                            daysOfWeek = schedulePayload.daysOfWeek,
                            isEnabled = schedulePayload.enabled
                        )
                        scheduleDao.insert(schedule)
                    }
                }

                createSuccessResponse(message, "Medication updated successfully")
            } catch (e: Exception) {
                createErrorResponse(message, "Failed to update medication: ${e.message}")
            }
        }
    }

    /**
     * Handle DELETE_MEDICATION command
     */
    suspend fun handleDeleteMedication(message: WebSocketMessage): WebSocketMessage {
        return withContext(Dispatchers.IO) {
            try {
                val payload = json.decodeFromString<DeleteMedicationPayload>(message.payload.toString())
                val medicationId = payload.medicationId.toLongOrNull()
                    ?: return@withContext createErrorResponse(message, "Invalid medication ID")

                // Delete schedules
                val schedules = scheduleDao.getSchedulesForMedicationSync(medicationId)
                schedules.forEach { scheduleDao.delete(it) }

                // Delete medication
                medicationDao.deleteById(medicationId)

                createSuccessResponse(message, "Medication deleted successfully")
            } catch (e: Exception) {
                createErrorResponse(message, "Failed to delete medication: ${e.message}")
            }
        }
    }

    private fun createSuccessResponse(
        originalMessage: WebSocketMessage,
        message: String,
        data: Map<String, String>? = null
    ): WebSocketMessage {
        val payload = CommandSuccessPayload(message, data)
        return WebSocketMessage(
            type = OutgoingMessageTypes.COMMAND_SUCCESS,
            from = originalMessage.to,
            to = originalMessage.from,
            requestId = originalMessage.requestId,
            payload = Json.parseToJsonElement(json.encodeToString(payload)),
            timestamp = Date().toInstant().toString()
        )
    }

    private fun createErrorResponse(originalMessage: WebSocketMessage, error: String): WebSocketMessage {
        val payload = CommandErrorPayload(error)
        return WebSocketMessage(
            type = OutgoingMessageTypes.COMMAND_ERROR,
            from = originalMessage.to,
            to = originalMessage.from,
            requestId = originalMessage.requestId,
            payload = Json.parseToJsonElement(json.encodeToString(payload)),
            timestamp = Date().toInstant().toString()
        )
    }
}

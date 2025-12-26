package com.example.senioroslauncher.data.guardian.handlers

import android.content.Context
import com.example.senioroslauncher.data.database.AppDatabase
import com.example.senioroslauncher.data.database.entity.EmergencyContactEntity
import com.example.senioroslauncher.data.guardian.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

/**
 * Handles emergency contact commands from Guardian app:
 * - UPDATE_EMERGENCY_CONTACT (add or update)
 * - DELETE_EMERGENCY_CONTACT
 */
class EmergencyContactCommandHandler(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val emergencyContactDao = database.emergencyContactDao()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Handle UPDATE_EMERGENCY_CONTACT command (add or update)
     */
    suspend fun handleUpdateEmergencyContact(message: WebSocketMessage): WebSocketMessage {
        return withContext(Dispatchers.IO) {
            try {
                val payload = json.decodeFromString<UpdateEmergencyContactPayload>(message.payload.toString())

                // Validate input
                if (payload.name.isBlank()) {
                    return@withContext createErrorResponse(message, "Contact name cannot be empty")
                }
                if (payload.phoneNumber.isBlank()) {
                    return@withContext createErrorResponse(message, "Phone number cannot be empty")
                }

                val contactId = payload.contactId?.toLongOrNull()

                if (contactId != null) {
                    // Update existing contact
                    val existing = emergencyContactDao.getContactById(contactId)
                        ?: return@withContext createErrorResponse(message, "Contact not found")

                    val updated = existing.copy(
                        name = payload.name.trim(),
                        phoneNumber = payload.phoneNumber.trim(),
                        relationship = payload.relationship.trim()
                    )
                    emergencyContactDao.update(updated)

                    createSuccessResponse(
                        message,
                        "Emergency contact updated successfully",
                        mapOf("contactId" to contactId.toString())
                    )
                } else {
                    // Add new contact
                    val newContact = EmergencyContactEntity(
                        name = payload.name.trim(),
                        phoneNumber = payload.phoneNumber.trim(),
                        relationship = payload.relationship.trim()
                    )
                    val newId = emergencyContactDao.insert(newContact)

                    createSuccessResponse(
                        message,
                        "Emergency contact added successfully",
                        mapOf("contactId" to newId.toString())
                    )
                }
            } catch (e: Exception) {
                createErrorResponse(message, "Failed to update emergency contact: ${e.message}")
            }
        }
    }

    /**
     * Handle DELETE_EMERGENCY_CONTACT command
     */
    suspend fun handleDeleteEmergencyContact(message: WebSocketMessage): WebSocketMessage {
        return withContext(Dispatchers.IO) {
            try {
                val payload = json.decodeFromString<DeleteEmergencyContactPayload>(message.payload.toString())
                val contactId = payload.contactId.toLongOrNull()
                    ?: return@withContext createErrorResponse(message, "Invalid contact ID")

                // Check if contact exists
                val contact = emergencyContactDao.getContactById(contactId)
                    ?: return@withContext createErrorResponse(message, "Contact not found")

                // Delete contact
                emergencyContactDao.delete(contact)

                createSuccessResponse(message, "Emergency contact deleted successfully")
            } catch (e: Exception) {
                createErrorResponse(message, "Failed to delete emergency contact: ${e.message}")
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

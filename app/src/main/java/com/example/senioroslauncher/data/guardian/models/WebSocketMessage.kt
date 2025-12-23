package com.example.senioroslauncher.data.guardian.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Base WebSocket message structure for Guardian integration.
 * All messages between Elder and Guardian apps follow this format.
 */
@Serializable
data class WebSocketMessage(
    val type: String,
    val from: String,
    val to: String,
    val requestId: String,
    val payload: JsonElement? = null,
    val timestamp: String
)

/**
 * Message types sent FROM Guardian TO Elder (requests and commands)
 */
object IncomingMessageTypes {
    // Query requests
    const val GET_STATE = "GET_STATE"
    const val GET_MEDICATIONS = "GET_MEDICATIONS"
    const val GET_ALERT_HISTORY = "GET_ALERT_HISTORY"
    const val GET_HEALTH_HISTORY = "GET_HEALTH_HISTORY"

    // Pairing events
    const val GUARDIAN_PAIRED = "GUARDIAN_PAIRED"
    const val GUARDIAN_UNPAIRED = "GUARDIAN_UNPAIRED"

    // Medication management commands
    const val ADD_MEDICATION = "ADD_MEDICATION"
    const val UPDATE_MEDICATION = "UPDATE_MEDICATION"
    const val DELETE_MEDICATION = "DELETE_MEDICATION"

    // Notification/reminder commands
    const val SEND_REMINDER = "SEND_REMINDER"
    const val SEND_MESSAGE = "SEND_MESSAGE"

    // Emergency contact management
    const val UPDATE_EMERGENCY_CONTACT = "UPDATE_EMERGENCY_CONTACT"
    const val DELETE_EMERGENCY_CONTACT = "DELETE_EMERGENCY_CONTACT"
}

/**
 * Message types sent FROM Elder TO Guardian (responses and events)
 */
object OutgoingMessageTypes {
    // Query responses
    const val STATE_RESPONSE = "STATE_RESPONSE"
    const val MEDICATIONS_RESPONSE = "MEDICATIONS_RESPONSE"
    const val ALERT_HISTORY_RESPONSE = "ALERT_HISTORY_RESPONSE"
    const val HEALTH_HISTORY_RESPONSE = "HEALTH_HISTORY_RESPONSE"

    // Real-time events
    const val ALERT_EVENT = "ALERT_EVENT"
    const val MEDICATION_UPDATED = "MEDICATION_UPDATED"

    // Command responses
    const val COMMAND_SUCCESS = "COMMAND_SUCCESS"
    const val COMMAND_ERROR = "COMMAND_ERROR"
    const val ERROR = "ERROR"
}

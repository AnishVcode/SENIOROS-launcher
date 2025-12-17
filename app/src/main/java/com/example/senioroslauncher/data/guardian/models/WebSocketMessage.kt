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
 * Message types sent FROM Guardian TO Elder (requests)
 */
object IncomingMessageTypes {
    const val GET_STATE = "GET_STATE"
    const val GET_MEDICATIONS = "GET_MEDICATIONS"
    const val GET_ALERT_HISTORY = "GET_ALERT_HISTORY"
    const val GET_HEALTH_HISTORY = "GET_HEALTH_HISTORY"
    const val GUARDIAN_PAIRED = "GUARDIAN_PAIRED"
    const val GUARDIAN_UNPAIRED = "GUARDIAN_UNPAIRED"
}

/**
 * Message types sent FROM Elder TO Guardian (responses and events)
 */
object OutgoingMessageTypes {
    const val STATE_RESPONSE = "STATE_RESPONSE"
    const val MEDICATIONS_RESPONSE = "MEDICATIONS_RESPONSE"
    const val ALERT_HISTORY_RESPONSE = "ALERT_HISTORY_RESPONSE"
    const val HEALTH_HISTORY_RESPONSE = "HEALTH_HISTORY_RESPONSE"
    const val ALERT_EVENT = "ALERT_EVENT"
    const val MEDICATION_UPDATED = "MEDICATION_UPDATED"
    const val ERROR = "ERROR"
}

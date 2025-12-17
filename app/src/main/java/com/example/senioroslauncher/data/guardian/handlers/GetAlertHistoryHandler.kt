package com.example.senioroslauncher.data.guardian.handlers

import android.content.Context
import com.example.senioroslauncher.data.database.AppDatabase
import com.example.senioroslauncher.data.guardian.ElderIdentity
import com.example.senioroslauncher.data.guardian.WebSocketManager
import com.example.senioroslauncher.data.guardian.models.AlertHistoryResponsePayload
import com.example.senioroslauncher.data.guardian.models.AlertInfo
import com.example.senioroslauncher.data.guardian.models.LocationInfo
import com.example.senioroslauncher.data.guardian.models.OutgoingMessageTypes
import com.example.senioroslauncher.data.guardian.models.WebSocketMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Handles GET_ALERT_HISTORY requests from Guardian app.
 * Returns all historical alerts.
 */
class GetAlertHistoryHandler(
    private val context: Context,
    private val webSocketManager: WebSocketManager,
    private val database: AppDatabase
) {
    private val json = Json { encodeDefaults = true }
    private val elderId = ElderIdentity.getOrCreateElderId(context)

    suspend fun handle(message: WebSocketMessage) {
        val alerts = database.alertDao().getAllAlertsSync().map { alert ->
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

        val payload = AlertHistoryResponsePayload(alerts = alerts)

        webSocketManager.sendResponse(
            type = OutgoingMessageTypes.ALERT_HISTORY_RESPONSE,
            to = message.from,
            requestId = message.requestId,
            payload = json.encodeToString(payload)
        )
    }
}

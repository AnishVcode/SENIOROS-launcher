package com.example.senioroslauncher.data.guardian.handlers

import android.content.Context
import com.example.senioroslauncher.data.database.AppDatabase
import com.example.senioroslauncher.data.guardian.ElderIdentity
import com.example.senioroslauncher.data.guardian.WebSocketManager
import com.example.senioroslauncher.data.guardian.models.HealthCheckInInfo
import com.example.senioroslauncher.data.guardian.models.HealthHistoryResponsePayload
import com.example.senioroslauncher.data.guardian.models.OutgoingMessageTypes
import com.example.senioroslauncher.data.guardian.models.WebSocketMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Handles GET_HEALTH_HISTORY requests from Guardian app.
 * Returns all health check-ins.
 */
class GetHealthHistoryHandler(
    private val context: Context,
    private val webSocketManager: WebSocketManager,
    private val database: AppDatabase
) {
    private val json = Json { encodeDefaults = true }
    private val elderId = ElderIdentity.getOrCreateElderId(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    suspend fun handle(message: WebSocketMessage) {
        val checkIns = database.healthCheckInDao().getAllCheckInsSync().map { checkIn ->
            HealthCheckInInfo(
                id = checkIn.id.toString(),
                elderId = elderId,
                date = dateFormat.format(checkIn.date),
                mood = checkIn.mood,
                painLevel = checkIn.painLevel,
                sleepQuality = checkIn.sleepQuality,
                symptoms = checkIn.symptoms,
                notes = checkIn.notes
            )
        }

        val payload = HealthHistoryResponsePayload(checkIns = checkIns)

        webSocketManager.sendResponse(
            type = OutgoingMessageTypes.HEALTH_HISTORY_RESPONSE,
            to = message.from,
            requestId = message.requestId,
            payload = json.encodeToString(payload)
        )
    }
}

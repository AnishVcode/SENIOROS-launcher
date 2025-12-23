package com.example.senioroslauncher.data.guardian

import android.content.Context
import android.util.Log
import com.example.senioroslauncher.BuildConfig
import com.example.senioroslauncher.data.guardian.models.IncomingMessageTypes
import com.example.senioroslauncher.data.guardian.models.OutgoingMessageTypes
import com.example.senioroslauncher.data.guardian.models.WebSocketMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Manages WebSocket connection to the Guardian relay server.
 * Handles connection lifecycle, auto-reconnection, and message routing.
 */
class WebSocketManager(
    private val context: Context,
    private val onGetState: suspend (WebSocketMessage) -> Unit,
    private val onGetMedications: suspend (WebSocketMessage) -> Unit,
    private val onGetAlertHistory: suspend (WebSocketMessage) -> Unit,
    private val onGetHealthHistory: suspend (WebSocketMessage) -> Unit,
    private val onGuardianPaired: suspend (WebSocketMessage) -> Unit,
    private val onGuardianUnpaired: suspend (WebSocketMessage) -> Unit,
    private val onAddMedication: suspend (WebSocketMessage) -> Unit,
    private val onUpdateMedication: suspend (WebSocketMessage) -> Unit,
    private val onDeleteMedication: suspend (WebSocketMessage) -> Unit,
    private val onSendReminder: suspend (WebSocketMessage) -> Unit,
    private val onSendMessage: suspend (WebSocketMessage) -> Unit,
    private val onUpdateEmergencyContact: suspend (WebSocketMessage) -> Unit,
    private val onDeleteEmergencyContact: suspend (WebSocketMessage) -> Unit
) {
    companion object {
        private const val TAG = "WebSocketManager"
        private const val INITIAL_RECONNECT_DELAY = 5000L // 5 seconds
        private const val MAX_RECONNECT_DELAY = 300000L // 5 minutes
        private const val RECONNECT_MULTIPLIER = 2.0
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
        .pingInterval(30, TimeUnit.SECONDS) // Keep connection alive
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectAttempts = 0
    private var shouldReconnect = true

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val elderId: String by lazy {
        ElderIdentity.getOrCreateElderId(context)
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING
    }

    /**
     * Connect to the WebSocket relay server.
     */
    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING) {
            Log.d(TAG, "Already connected or connecting")
            return
        }

        shouldReconnect = true
        _connectionState.value = ConnectionState.CONNECTING

        val serverUrl = "ws://${BuildConfig.GUARDIAN_SERVER_IP}:${BuildConfig.GUARDIAN_SERVER_PORT}" +
                "?deviceId=$elderId&type=elder"

        Log.d(TAG, "Connecting to: $serverUrl")

        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, createWebSocketListener())
    }

    /**
     * Disconnect from the WebSocket server.
     */
    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        Log.d(TAG, "Disconnected from relay server")
    }

    /**
     * Send a message to the relay server.
     */
    fun sendMessage(message: WebSocketMessage): Boolean {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send message - not connected")
            return false
        }

        return try {
            val jsonString = json.encodeToString(message)
            Log.d(TAG, "Sending message: $jsonString")
            webSocket?.send(jsonString) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            false
        }
    }

    /**
     * Send a response to a specific request.
     */
    fun sendResponse(
        type: String,
        to: String,
        requestId: String,
        payload: String
    ): Boolean {
        val payloadElement = json.parseToJsonElement(payload)
        val message = WebSocketMessage(
            type = type,
            from = elderId,
            to = to,
            requestId = requestId,
            payload = payloadElement,
            timestamp = Instant.now().toString()
        )
        return sendMessage(message)
    }

    /**
     * Broadcast a message to all paired guardians.
     */
    fun broadcastToGuardians(
        type: String,
        payload: String,
        guardianIds: List<String>
    ) {
        guardianIds.forEach { guardianId ->
            sendResponse(
                type = type,
                to = guardianId,
                requestId = UUID.randomUUID().toString(),
                payload = payload
            )
        }
    }

    private fun createWebSocketListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
            _connectionState.value = ConnectionState.CONNECTED
            reconnectAttempts = 0
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Received message: $text")
            scope.launch {
                handleIncomingMessage(text)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code - $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code - $reason")
            _connectionState.value = ConnectionState.DISCONNECTED
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure", t)
            _connectionState.value = ConnectionState.DISCONNECTED
            scheduleReconnect()
        }
    }

    private suspend fun handleIncomingMessage(jsonMessage: String) {
        try {
            val message = json.decodeFromString<WebSocketMessage>(jsonMessage)

            when (message.type) {
                // Query requests
                IncomingMessageTypes.GET_STATE -> onGetState(message)
                IncomingMessageTypes.GET_MEDICATIONS -> onGetMedications(message)
                IncomingMessageTypes.GET_ALERT_HISTORY -> onGetAlertHistory(message)
                IncomingMessageTypes.GET_HEALTH_HISTORY -> onGetHealthHistory(message)

                // Pairing events
                IncomingMessageTypes.GUARDIAN_PAIRED -> onGuardianPaired(message)
                IncomingMessageTypes.GUARDIAN_UNPAIRED -> onGuardianUnpaired(message)

                // Medication commands
                IncomingMessageTypes.ADD_MEDICATION -> onAddMedication(message)
                IncomingMessageTypes.UPDATE_MEDICATION -> onUpdateMedication(message)
                IncomingMessageTypes.DELETE_MEDICATION -> onDeleteMedication(message)

                // Notification commands
                IncomingMessageTypes.SEND_REMINDER -> onSendReminder(message)
                IncomingMessageTypes.SEND_MESSAGE -> onSendMessage(message)

                // Emergency contact commands
                IncomingMessageTypes.UPDATE_EMERGENCY_CONTACT -> onUpdateEmergencyContact(message)
                IncomingMessageTypes.DELETE_EMERGENCY_CONTACT -> onDeleteEmergencyContact(message)

                else -> Log.w(TAG, "Unknown message type: ${message.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message", e)
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) {
            Log.d(TAG, "Reconnection disabled")
            return
        }

        _connectionState.value = ConnectionState.RECONNECTING

        val delay = calculateReconnectDelay()
        reconnectAttempts++

        Log.d(TAG, "Scheduling reconnect in ${delay}ms (attempt $reconnectAttempts)")

        scope.launch {
            delay(delay)
            if (shouldReconnect && _connectionState.value != ConnectionState.CONNECTED) {
                connect()
            }
        }
    }

    private fun calculateReconnectDelay(): Long {
        val delay = INITIAL_RECONNECT_DELAY * Math.pow(RECONNECT_MULTIPLIER, reconnectAttempts.toDouble())
        return delay.toLong().coerceAtMost(MAX_RECONNECT_DELAY)
    }

    fun isConnected(): Boolean = _connectionState.value == ConnectionState.CONNECTED
}

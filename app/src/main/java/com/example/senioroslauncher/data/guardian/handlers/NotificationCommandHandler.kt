package com.example.senioroslauncher.data.guardian.handlers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.senioroslauncher.MainActivity
import com.example.senioroslauncher.R
import com.example.senioroslauncher.data.guardian.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

/**
 * Handles notification/message commands from Guardian app:
 * - SEND_REMINDER
 * - SEND_MESSAGE
 */
class NotificationCommandHandler(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val GUARDIAN_CHANNEL_ID = "guardian_messages"
        private const val GUARDIAN_CHANNEL_NAME = "Guardian Messages"
        private const val REMINDER_CHANNEL_ID = "guardian_reminders"
        private const val REMINDER_CHANNEL_NAME = "Guardian Reminders"
    }

    init {
        createNotificationChannels()
    }

    /**
     * Handle SEND_REMINDER command
     */
    suspend fun handleSendReminder(message: WebSocketMessage): WebSocketMessage {
        return withContext(Dispatchers.IO) {
            try {
                val payload = json.decodeFromString<SendReminderPayload>(message.payload.toString())

                // Validate input
                if (payload.title.isBlank() || payload.message.isBlank()) {
                    return@withContext createErrorResponse(message, "Title and message cannot be empty")
                }

                // Show notification
                showReminderNotification(payload)

                createSuccessResponse(message, "Reminder sent successfully")
            } catch (e: Exception) {
                createErrorResponse(message, "Failed to send reminder: ${e.message}")
            }
        }
    }

    /**
     * Handle SEND_MESSAGE command
     */
    suspend fun handleSendMessage(message: WebSocketMessage): WebSocketMessage {
        return withContext(Dispatchers.IO) {
            try {
                val payload = json.decodeFromString<SendMessagePayload>(message.payload.toString())

                // Validate input
                if (payload.message.isBlank()) {
                    return@withContext createErrorResponse(message, "Message cannot be empty")
                }

                // Show notification
                showMessageNotification(payload)

                createSuccessResponse(message, "Message sent successfully")
            } catch (e: Exception) {
                createErrorResponse(message, "Failed to send message: ${e.message}")
            }
        }
    }

    private fun showReminderNotification(payload: SendReminderPayload) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val priority = when (payload.priority) {
            "urgent" -> NotificationCompat.PRIORITY_MAX
            "high" -> NotificationCompat.PRIORITY_HIGH
            "low" -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(payload.title)
            .setContentText(payload.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.message))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun showMessageNotification(payload: SendMessagePayload) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = "Message from ${payload.guardianName}"
        val notification = NotificationCompat.Builder(context, GUARDIAN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(payload.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannels() {
        // Guardian messages channel
        val messagesChannel = NotificationChannel(
            GUARDIAN_CHANNEL_ID,
            GUARDIAN_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Messages from family guardians"
            enableVibration(true)
            enableLights(true)
        }
        notificationManager.createNotificationChannel(messagesChannel)

        // Guardian reminders channel
        val remindersChannel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            REMINDER_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminders from family guardians"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(remindersChannel)
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

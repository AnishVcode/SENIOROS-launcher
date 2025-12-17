package com.example.senioroslauncher.assistant

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Extract entities from user commands (names, times, numbers, etc.)
 */
class EntityExtractor {

    companion object {
        private const val TAG = "EntityExtractor"
    }

    data class ExtractedEntities(
        val contactName: String? = null,
        val phoneNumber: String? = null,
        val time: Calendar? = null,
        val duration: Int? = null, // in minutes
        val appName: String? = null,
        val medicationName: String? = null,
        val number: Int? = null,
        val location: String? = null,
        val rawText: String
    )

    /**
     * Extract entities based on intent
     */
    fun extract(text: String, intent: String): ExtractedEntities {
        Log.d(TAG, "Extracting entities from: '$text' for intent: $intent")

        return when (intent) {
            "CALL_CONTACT", "SEND_MESSAGE", "CAREGIVER_CONTACT" -> {
                ExtractedEntities(
                    contactName = extractContactName(text),
                    rawText = text
                )
            }
            "SET_ALARM" -> {
                ExtractedEntities(
                    time = extractTime(text),
                    rawText = text
                )
            }
            "SET_TIMER" -> {
                ExtractedEntities(
                    duration = extractDuration(text),
                    rawText = text
                )
            }
            "OPEN_APP" -> {
                ExtractedEntities(
                    appName = extractAppName(text),
                    rawText = text
                )
            }
            "MEDICATION_ADD", "MEDICATION_LOG_TAKEN", "MEDICATION_QUERY" -> {
                ExtractedEntities(
                    medicationName = extractMedicationName(text),
                    rawText = text
                )
            }
            "SEARCH_LOCATION", "APPOINTMENT_CREATE" -> {
                ExtractedEntities(
                    location = extractLocation(text),
                    time = if (intent == "APPOINTMENT_CREATE") extractTime(text) else null,
                    rawText = text
                )
            }
            "HEALTH_RECORD" -> {
                ExtractedEntities(
                    number = extractNumber(text),
                    rawText = text
                )
            }
            else -> ExtractedEntities(rawText = text)
        }
    }

    /**
     * Extract contact name from text
     * Examples: "call john", "phone my daughter", "dial sarah"
     */
    private fun extractContactName(text: String): String? {
        val lowerText = text.lowercase()

        // Remove trigger words
        val cleaned = lowerText
            .replace(Regex("(call|phone|dial|ring|message|text|whatsapp|sms|contact)\\s+"), "")
            .replace(Regex("\\s+(please|now)"), "")
            .trim()

        // Extract name (first word after cleaning, or "my X" pattern)
        val myPattern = Regex("my\\s+(\\w+)")
        val myMatch = myPattern.find(cleaned)
        if (myMatch != null) {
            return myMatch.groupValues[1].capitalize()
        }

        // Just get the first word as name
        val words = cleaned.split(" ")
        return words.firstOrNull()?.capitalize()
    }

    /**
     * Extract time from text
     * Examples: "7 am", "6 30", "tomorrow morning", "8 o'clock"
     */
    private fun extractTime(text: String): Calendar? {
        val lowerText = text.lowercase()

        // Pattern for "X am/pm"
        val timePattern = Regex("(\\d{1,2})\\s*(am|pm)")
        val timeMatch = timePattern.find(lowerText)
        if (timeMatch != null) {
            val hour = timeMatch.groupValues[1].toInt()
            val amPm = timeMatch.groupValues[2]

            val calendar = Calendar.getInstance()
            var finalHour = hour
            if (amPm == "pm" && hour != 12) finalHour += 12
            if (amPm == "am" && hour == 12) finalHour = 0

            calendar.set(Calendar.HOUR_OF_DAY, finalHour)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)

            return calendar
        }

        // Pattern for "X:YY" or "X YY"
        val timePattern2 = Regex("(\\d{1,2})[:\\s](\\d{2})")
        val timeMatch2 = timePattern2.find(lowerText)
        if (timeMatch2 != null) {
            val hour = timeMatch2.groupValues[1].toInt()
            val minute = timeMatch2.groupValues[2].toInt()

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)

            return calendar
        }

        // Pattern for just hour
        val hourPattern = Regex("(\\d{1,2})\\s*(?:o'?clock)?")
        val hourMatch = hourPattern.find(lowerText)
        if (hourMatch != null) {
            val hour = hourMatch.groupValues[1].toInt()

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)

            return calendar
        }

        return null
    }

    /**
     * Extract duration in minutes
     * Examples: "5 minutes", "30 seconds", "1 hour"
     */
    private fun extractDuration(text: String): Int? {
        val lowerText = text.lowercase()

        // Minutes
        val minutePattern = Regex("(\\d+)\\s*(?:minute|min)")
        val minuteMatch = minutePattern.find(lowerText)
        if (minuteMatch != null) {
            return minuteMatch.groupValues[1].toInt()
        }

        // Seconds
        val secondPattern = Regex("(\\d+)\\s*(?:second|sec)")
        val secondMatch = secondPattern.find(lowerText)
        if (secondMatch != null) {
            return (secondMatch.groupValues[1].toInt() / 60).coerceAtLeast(1)
        }

        // Hours
        val hourPattern = Regex("(\\d+)\\s*(?:hour|hr)")
        val hourMatch = hourPattern.find(lowerText)
        if (hourMatch != null) {
            return hourMatch.groupValues[1].toInt() * 60
        }

        // Just a number (assume minutes)
        val numberPattern = Regex("(\\d+)")
        val numberMatch = numberPattern.find(lowerText)
        if (numberMatch != null) {
            return numberMatch.groupValues[1].toInt()
        }

        return null
    }

    /**
     * Extract app name from text
     * Examples: "open whatsapp", "launch youtube", "start google maps"
     */
    private fun extractAppName(text: String): String? {
        val lowerText = text.lowercase()

        // Remove trigger words
        val cleaned = lowerText
            .replace(Regex("(open|launch|start|run)\\s+"), "")
            .trim()

        // Map common app names
        return when {
            cleaned.contains("whatsapp") -> "WhatsApp"
            cleaned.contains("youtube") -> "YouTube"
            cleaned.contains("maps") || cleaned.contains("google maps") -> "Google Maps"
            cleaned.contains("gmail") -> "Gmail"
            cleaned.contains("facebook") -> "Facebook"
            cleaned.contains("instagram") -> "Instagram"
            cleaned.contains("chrome") -> "Chrome"
            cleaned.contains("spotify") -> "Spotify"
            cleaned.contains("messenger") -> "Messenger"
            cleaned.contains("twitter") -> "Twitter"
            cleaned.contains("telegram") -> "Telegram"
            cleaned.contains("linkedin") -> "LinkedIn"
            cleaned.contains("netflix") -> "Netflix"
            else -> cleaned.split(" ").firstOrNull()?.capitalize()
        }
    }

    /**
     * Extract medication name from text
     */
    private fun extractMedicationName(text: String): String? {
        val lowerText = text.lowercase()

        // Remove common words
        val cleaned = lowerText
            .replace(Regex("(i took|took|take|medicine|pill|medication|tablet|my)\\s+"), "")
            .trim()

        return cleaned.split(" ").firstOrNull()?.capitalize()
    }

    /**
     * Extract location from text
     */
    private fun extractLocation(text: String): String? {
        val lowerText = text.lowercase()

        // Remove trigger words
        val cleaned = lowerText
            .replace(Regex("(nearest|nearby|find|search|locate)\\s+"), "")
            .trim()

        // Common locations
        return when {
            cleaned.contains("hospital") -> "hospital"
            cleaned.contains("pharmacy") || cleaned.contains("medical store") -> "pharmacy"
            cleaned.contains("clinic") -> "clinic"
            cleaned.contains("doctor") -> "doctor"
            cleaned.contains("emergency") -> "emergency room"
            else -> cleaned
        }
    }

    /**
     * Extract number from text
     */
    private fun extractNumber(text: String): Int? {
        val numberPattern = Regex("(\\d+)")
        val match = numberPattern.find(text)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Capitalize first letter
     */
    private fun String.capitalize(): String {
        return replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
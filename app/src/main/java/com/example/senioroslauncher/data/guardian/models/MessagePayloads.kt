package com.example.senioroslauncher.data.guardian.models

import kotlinx.serialization.Serializable

// ============== GET_STATE Response Payloads ==============

@Serializable
data class StateResponsePayload(
    val elder: ElderInfo,
    val recentAlerts: List<AlertInfo>,
    val medicationSummary: MedicationSummary
)

@Serializable
data class ElderInfo(
    val name: String,
    val age: Int?,
    val batteryLevel: Int,
    val lastHeartbeat: String
)

@Serializable
data class AlertInfo(
    val id: String,
    val elderId: String,
    val type: String,
    val triggeredAt: String,
    val location: LocationInfo? = null,
    val batteryLevel: Int? = null,
    val resolved: Boolean,
    val notes: String
)

@Serializable
data class LocationInfo(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class MedicationSummary(
    val todayTotal: Int,
    val takenToday: Int,
    val missedToday: Int
)

// ============== GET_MEDICATIONS Response Payloads ==============

@Serializable
data class MedicationsResponsePayload(
    val medications: List<MedicationInfo>,
    val schedules: List<ScheduleInfo>,
    val logs: List<MedicationLogInfo>
)

@Serializable
data class MedicationInfo(
    val id: String,
    val name: String,
    val dosage: String,
    val instructions: String
)

@Serializable
data class ScheduleInfo(
    val id: String,
    val medicationId: String,
    val time: String,  // "HH:mm" format
    val daysOfWeek: List<Int>,  // 0-6 (Sunday=0)
    val enabled: Boolean
)

@Serializable
data class MedicationLogInfo(
    val id: String,
    val medicationId: String,
    val scheduleId: String,
    val scheduledTime: String,  // ISO timestamp
    val takenAt: String? = null,  // ISO timestamp
    val status: String  // "taken", "missed", "skipped"
)

// ============== GET_ALERT_HISTORY Response Payloads ==============

@Serializable
data class AlertHistoryResponsePayload(
    val alerts: List<AlertInfo>
)

// ============== GET_HEALTH_HISTORY Response Payloads ==============

@Serializable
data class HealthHistoryResponsePayload(
    val checkIns: List<HealthCheckInInfo>
)

@Serializable
data class HealthCheckInInfo(
    val id: String,
    val elderId: String,
    val date: String,  // "yyyy-MM-dd" format
    val mood: Int? = null,
    val painLevel: Int? = null,
    val sleepQuality: Int? = null,
    val symptoms: List<String>,
    val notes: String
)

// ============== ALERT_EVENT Payload (Real-time alerts) ==============

@Serializable
data class AlertEventPayload(
    val id: String,
    val elderId: String,
    val type: String,
    val triggeredAt: String,
    val location: LocationInfo? = null,
    val batteryLevel: Int? = null,
    val resolved: Boolean,
    val notes: String
)

// ============== MEDICATION_UPDATED Payload (Real-time medication changes) ==============

@Serializable
data class MedicationUpdatedPayload(
    val elderId: String,
    val action: String,  // "added", "updated", "deleted"
    val medication: MedicationInfo,
    val schedules: List<ScheduleInfo> = listOf()
)

// ============== Error Payload ==============

@Serializable
data class ErrorPayload(
    val code: String,
    val message: String
)

// ============== Guardian Pairing Payloads ==============

@Serializable
data class GuardianPairedPayload(
    val guardianId: String,
    val guardianName: String
)

@Serializable
data class GuardianUnpairedPayload(
    val guardianId: String
)

// ============== Guardian Commands - Medication Management ==============

@Serializable
data class AddMedicationPayload(
    val name: String,
    val dosage: String,
    val instructions: String,
    val schedules: List<MedicationSchedulePayload>
)

@Serializable
data class MedicationSchedulePayload(
    val time: String,  // "HH:mm" format
    val daysOfWeek: List<Int>,  // 0-6 (Sunday=0)
    val enabled: Boolean = true
)

@Serializable
data class UpdateMedicationPayload(
    val medicationId: String,
    val name: String? = null,
    val dosage: String? = null,
    val instructions: String? = null,
    val schedules: List<MedicationSchedulePayload>? = null
)

@Serializable
data class DeleteMedicationPayload(
    val medicationId: String
)

// ============== Guardian Commands - Notifications ==============

@Serializable
data class SendReminderPayload(
    val title: String,
    val message: String,
    val priority: String = "normal"  // "low", "normal", "high", "urgent"
)

@Serializable
data class SendMessagePayload(
    val guardianName: String,
    val message: String,
    val requiresAcknowledgment: Boolean = false
)

// ============== Guardian Commands - Emergency Contacts ==============

@Serializable
data class UpdateEmergencyContactPayload(
    val contactId: String? = null,  // null for new contact
    val name: String,
    val phoneNumber: String,
    val relationship: String
)

@Serializable
data class DeleteEmergencyContactPayload(
    val contactId: String
)

// ============== Command Response Payloads ==============

@Serializable
data class CommandSuccessPayload(
    val message: String,
    val data: Map<String, String>? = null  // Optional data (e.g., new medication ID)
)

@Serializable
data class CommandErrorPayload(
    val error: String,
    val details: String? = null
)

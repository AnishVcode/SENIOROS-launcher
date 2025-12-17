package com.example.senioroslauncher.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: AlertType,
    val triggeredAt: Date = Date(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val batteryLevel: Int? = null,
    val resolved: Boolean = false,
    val resolvedAt: Date? = null,
    val notes: String = ""
)

enum class AlertType {
    SOS,           // Emergency button pressed (critical)
    FALL,          // Fall detected (critical)
    MISSED_MED,    // Medication not taken on time (warning)
    INACTIVITY,    // No phone activity for extended period (warning)
    LOW_BATTERY    // Battery below threshold (info)
}

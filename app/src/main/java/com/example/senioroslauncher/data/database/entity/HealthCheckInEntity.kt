package com.example.senioroslauncher.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "health_checkins")
data class HealthCheckInEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Date = Date(),
    val mood: Int? = null,          // 1-5 scale (1=very bad, 5=very good)
    val painLevel: Int? = null,     // 1-10 scale (1=no pain, 10=severe pain)
    val sleepQuality: Int? = null,  // 1-5 scale (1=very poor, 5=very good)
    val symptoms: List<String> = listOf(),
    val notes: String = "",
    val createdAt: Date = Date()
)

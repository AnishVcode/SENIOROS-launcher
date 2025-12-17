package com.example.senioroslauncher.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "paired_guardians")
data class PairedGuardianEntity(
    @PrimaryKey
    val guardianId: String,
    val guardianName: String = "",
    val pairedAt: Date = Date()
)

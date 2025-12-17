package com.example.senioroslauncher.data.database.dao

import androidx.room.*
import com.example.senioroslauncher.data.database.entity.AlertEntity
import com.example.senioroslauncher.data.database.entity.AlertType
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY triggeredAt DESC")
    fun getAllAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts ORDER BY triggeredAt DESC LIMIT :limit")
    fun getRecentAlerts(limit: Int = 50): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE resolved = 0 ORDER BY triggeredAt DESC")
    fun getUnresolvedAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE type = :type ORDER BY triggeredAt DESC")
    fun getAlertsByType(type: AlertType): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE triggeredAt BETWEEN :startDate AND :endDate ORDER BY triggeredAt DESC")
    fun getAlertsBetweenDates(startDate: Date, endDate: Date): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE id = :id")
    suspend fun getAlertById(id: Long): AlertEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: AlertEntity): Long

    @Update
    suspend fun update(alert: AlertEntity)

    @Delete
    suspend fun delete(alert: AlertEntity)

    @Query("UPDATE alerts SET resolved = 1, resolvedAt = :resolvedAt WHERE id = :id")
    suspend fun resolveAlert(id: Long, resolvedAt: Date = Date())

    @Query("SELECT * FROM alerts WHERE type = :type AND triggeredAt > :since LIMIT 1")
    suspend fun hasRecentAlert(type: AlertType, since: Date): AlertEntity?

    // For sync - get all alerts as a list (not Flow)
    @Query("SELECT * FROM alerts ORDER BY triggeredAt DESC")
    suspend fun getAllAlertsSync(): List<AlertEntity>

    @Query("SELECT * FROM alerts ORDER BY triggeredAt DESC LIMIT :limit")
    suspend fun getRecentAlertsSync(limit: Int = 50): List<AlertEntity>
}

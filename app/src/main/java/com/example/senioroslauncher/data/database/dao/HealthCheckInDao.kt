package com.example.senioroslauncher.data.database.dao

import androidx.room.*
import com.example.senioroslauncher.data.database.entity.HealthCheckInEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface HealthCheckInDao {
    @Query("SELECT * FROM health_checkins ORDER BY date DESC")
    fun getAllCheckIns(): Flow<List<HealthCheckInEntity>>

    @Query("SELECT * FROM health_checkins ORDER BY date DESC LIMIT :limit")
    fun getRecentCheckIns(limit: Int = 30): Flow<List<HealthCheckInEntity>>

    @Query("SELECT * FROM health_checkins WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getCheckInsBetweenDates(startDate: Date, endDate: Date): Flow<List<HealthCheckInEntity>>

    @Query("SELECT * FROM health_checkins WHERE id = :id")
    suspend fun getCheckInById(id: Long): HealthCheckInEntity?

    @Query("SELECT * FROM health_checkins WHERE date >= :startOfDay AND date < :endOfDay LIMIT 1")
    suspend fun getCheckInForDate(startOfDay: Date, endOfDay: Date): HealthCheckInEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkIn: HealthCheckInEntity): Long

    @Update
    suspend fun update(checkIn: HealthCheckInEntity)

    @Delete
    suspend fun delete(checkIn: HealthCheckInEntity)

    @Query("DELETE FROM health_checkins WHERE id = :id")
    suspend fun deleteById(id: Long)

    // For sync - get all check-ins as a list (not Flow)
    @Query("SELECT * FROM health_checkins ORDER BY date DESC")
    suspend fun getAllCheckInsSync(): List<HealthCheckInEntity>

    @Query("SELECT * FROM health_checkins ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentCheckInsSync(limit: Int = 30): List<HealthCheckInEntity>
}

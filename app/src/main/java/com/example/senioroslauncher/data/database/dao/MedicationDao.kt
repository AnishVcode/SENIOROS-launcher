package com.example.senioroslauncher.data.database.dao

import androidx.room.*
import com.example.senioroslauncher.data.database.entity.MedicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveMedications(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications ORDER BY name ASC")
    fun getAllMedications(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedicationById(id: Long): MedicationEntity?

    @Query("SELECT * FROM medications WHERE id = :id")
    fun getMedicationByIdSync(id: Long): MedicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(medication: MedicationEntity): Long

    @Update
    suspend fun update(medication: MedicationEntity)

    @Delete
    suspend fun delete(medication: MedicationEntity)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteById(id: Long)
}

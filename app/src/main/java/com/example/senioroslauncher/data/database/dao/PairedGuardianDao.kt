package com.example.senioroslauncher.data.database.dao

import androidx.room.*
import com.example.senioroslauncher.data.database.entity.PairedGuardianEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PairedGuardianDao {
    @Query("SELECT * FROM paired_guardians ORDER BY pairedAt DESC")
    fun getAllGuardians(): Flow<List<PairedGuardianEntity>>

    @Query("SELECT * FROM paired_guardians ORDER BY pairedAt DESC")
    suspend fun getAllGuardiansSync(): List<PairedGuardianEntity>

    @Query("SELECT * FROM paired_guardians WHERE guardianId = :guardianId")
    suspend fun getGuardianById(guardianId: String): PairedGuardianEntity?

    @Query("SELECT COUNT(*) FROM paired_guardians")
    fun getGuardianCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM paired_guardians")
    suspend fun getGuardianCountSync(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(guardian: PairedGuardianEntity)

    @Update
    suspend fun update(guardian: PairedGuardianEntity)

    @Delete
    suspend fun delete(guardian: PairedGuardianEntity)

    @Query("DELETE FROM paired_guardians WHERE guardianId = :guardianId")
    suspend fun deleteById(guardianId: String)

    @Query("DELETE FROM paired_guardians")
    suspend fun deleteAll()
}

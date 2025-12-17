package com.example.senioroslauncher.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.senioroslauncher.data.database.dao.*
import com.example.senioroslauncher.data.database.entity.*

@Database(
    entities = [
        MedicationEntity::class,
        MedicationScheduleEntity::class,
        MedicationLogEntity::class,
        EmergencyContactEntity::class,
        AppointmentEntity::class,
        NoteEntity::class,
        SpeedDialContactEntity::class,
        MedicalProfileEntity::class,
        HydrationLogEntity::class,
        // Guardian Integration entities
        AlertEntity::class,
        HealthCheckInEntity::class,
        PairedGuardianEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao
    abstract fun medicationScheduleDao(): MedicationScheduleDao
    abstract fun medicationLogDao(): MedicationLogDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun noteDao(): NoteDao
    abstract fun speedDialContactDao(): SpeedDialContactDao
    abstract fun medicalProfileDao(): MedicalProfileDao
    abstract fun hydrationLogDao(): HydrationLogDao
    // Guardian Integration DAOs
    abstract fun alertDao(): AlertDao
    abstract fun healthCheckInDao(): HealthCheckInDao
    abstract fun pairedGuardianDao(): PairedGuardianDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "senior_launcher_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

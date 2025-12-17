package com.example.senioroslauncher.data.database

import androidx.room.TypeConverter
import com.example.senioroslauncher.data.database.entity.AlertType
import com.example.senioroslauncher.data.database.entity.MedicationAction
import com.example.senioroslauncher.data.database.entity.MedicationFrequency
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        if (value == null) return emptyList()
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun toStringList(list: List<String>?): String {
        return gson.toJson(list ?: emptyList<String>())
    }

    @TypeConverter
    fun fromIntList(value: String?): List<Int> {
        if (value == null) return emptyList()
        val listType = object : TypeToken<List<Int>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun toIntList(list: List<Int>?): String {
        return gson.toJson(list ?: emptyList<Int>())
    }

    @TypeConverter
    fun fromMedicationFrequency(frequency: MedicationFrequency): String {
        return frequency.name
    }

    @TypeConverter
    fun toMedicationFrequency(value: String): MedicationFrequency {
        return MedicationFrequency.valueOf(value)
    }

    @TypeConverter
    fun fromMedicationAction(action: MedicationAction): String {
        return action.name
    }

    @TypeConverter
    fun toMedicationAction(value: String): MedicationAction {
        return MedicationAction.valueOf(value)
    }

    @TypeConverter
    fun fromAlertType(type: AlertType): String {
        return type.name
    }

    @TypeConverter
    fun toAlertType(value: String): AlertType {
        return AlertType.valueOf(value)
    }
}

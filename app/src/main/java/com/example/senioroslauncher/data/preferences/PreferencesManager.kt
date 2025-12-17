package com.example.senioroslauncher.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "senior_launcher_prefs")

class PreferencesManager(private val context: Context) {

    // Keys
    private object Keys {
        val HEARING_AID_MODE = booleanPreferencesKey("hearing_aid_mode")
        val VOICE_FEEDBACK = booleanPreferencesKey("voice_feedback")
        val ANTI_SHAKE = booleanPreferencesKey("anti_shake")
        val DOUBLE_TAP_CONFIRM = booleanPreferencesKey("double_tap_confirm")
        val TOUCH_VIBRATION = booleanPreferencesKey("touch_vibration")
        val FALL_DETECTION = booleanPreferencesKey("fall_detection")
        val LOCATION_SHARING = booleanPreferencesKey("location_sharing")
        val AUTO_ANSWER_CALLS = booleanPreferencesKey("auto_answer_calls")
        val LANGUAGE = stringPreferencesKey("language")
        val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        val USER_NAME = stringPreferencesKey("user_name")
        val HYDRATION_GOAL = intPreferencesKey("hydration_goal")
        val HYDRATION_REMINDER_ENABLED = booleanPreferencesKey("hydration_reminder_enabled")
        val HYDRATION_REMINDER_INTERVAL = intPreferencesKey("hydration_reminder_interval")
        val HOME_ADDRESS = stringPreferencesKey("home_address")
        val DOCTOR_ADDRESS = stringPreferencesKey("doctor_address")
        val PHARMACY_ADDRESS = stringPreferencesKey("pharmacy_address")
        // Guardian Integration - Elder Profile
        val ELDER_NAME = stringPreferencesKey("elder_name")
        val ELDER_AGE = intPreferencesKey("elder_age")
    }

    // Settings Flows
    val hearingAidMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.HEARING_AID_MODE] ?: false }
    val voiceFeedback: Flow<Boolean> = context.dataStore.data.map { it[Keys.VOICE_FEEDBACK] ?: false }
    val antiShake: Flow<Boolean> = context.dataStore.data.map { it[Keys.ANTI_SHAKE] ?: false }
    val doubleTapConfirm: Flow<Boolean> = context.dataStore.data.map { it[Keys.DOUBLE_TAP_CONFIRM] ?: false }
    val touchVibration: Flow<Boolean> = context.dataStore.data.map { it[Keys.TOUCH_VIBRATION] ?: true }
    val fallDetection: Flow<Boolean> = context.dataStore.data.map { it[Keys.FALL_DETECTION] ?: false }
    val locationSharing: Flow<Boolean> = context.dataStore.data.map { it[Keys.LOCATION_SHARING] ?: false }
    val autoAnswerCalls: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_ANSWER_CALLS] ?: false }
    val language: Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE] ?: "en" }
    val isFirstLaunch: Flow<Boolean> = context.dataStore.data.map { it[Keys.FIRST_LAUNCH] ?: true }
    val userName: Flow<String> = context.dataStore.data.map { it[Keys.USER_NAME] ?: "" }
    val hydrationGoal: Flow<Int> = context.dataStore.data.map { it[Keys.HYDRATION_GOAL] ?: 8 }
    val hydrationReminderEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.HYDRATION_REMINDER_ENABLED] ?: true }
    val hydrationReminderInterval: Flow<Int> = context.dataStore.data.map { it[Keys.HYDRATION_REMINDER_INTERVAL] ?: 60 }
    val homeAddress: Flow<String> = context.dataStore.data.map { it[Keys.HOME_ADDRESS] ?: "" }
    val doctorAddress: Flow<String> = context.dataStore.data.map { it[Keys.DOCTOR_ADDRESS] ?: "" }
    val pharmacyAddress: Flow<String> = context.dataStore.data.map { it[Keys.PHARMACY_ADDRESS] ?: "" }
    // Guardian Integration - Elder Profile
    val elderName: Flow<String> = context.dataStore.data.map { it[Keys.ELDER_NAME] ?: "" }
    val elderAge: Flow<Int?> = context.dataStore.data.map { it[Keys.ELDER_AGE] }

    // Setters
    suspend fun setHearingAidMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HEARING_AID_MODE] = enabled }
    }

    suspend fun setVoiceFeedback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VOICE_FEEDBACK] = enabled }
    }

    suspend fun setAntiShake(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ANTI_SHAKE] = enabled }
    }

    suspend fun setDoubleTapConfirm(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DOUBLE_TAP_CONFIRM] = enabled }
    }

    suspend fun setTouchVibration(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TOUCH_VIBRATION] = enabled }
    }

    suspend fun setFallDetection(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FALL_DETECTION] = enabled }
    }

    suspend fun setLocationSharing(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOCATION_SHARING] = enabled }
    }

    suspend fun setAutoAnswerCalls(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_ANSWER_CALLS] = enabled }
    }

    suspend fun setLanguage(languageCode: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = languageCode }
    }

    suspend fun setFirstLaunch(isFirst: Boolean) {
        context.dataStore.edit { it[Keys.FIRST_LAUNCH] = isFirst }
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[Keys.USER_NAME] = name }
    }

    suspend fun setHydrationGoal(goal: Int) {
        context.dataStore.edit { it[Keys.HYDRATION_GOAL] = goal }
    }

    suspend fun setHydrationReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HYDRATION_REMINDER_ENABLED] = enabled }
    }

    suspend fun setHydrationReminderInterval(minutes: Int) {
        context.dataStore.edit { it[Keys.HYDRATION_REMINDER_INTERVAL] = minutes }
    }

    suspend fun setHomeAddress(address: String) {
        context.dataStore.edit { it[Keys.HOME_ADDRESS] = address }
    }

    suspend fun setDoctorAddress(address: String) {
        context.dataStore.edit { it[Keys.DOCTOR_ADDRESS] = address }
    }

    suspend fun setPharmacyAddress(address: String) {
        context.dataStore.edit { it[Keys.PHARMACY_ADDRESS] = address }
    }

    // Guardian Integration - Elder Profile Setters
    suspend fun setElderName(name: String) {
        context.dataStore.edit { it[Keys.ELDER_NAME] = name }
    }

    suspend fun setElderAge(age: Int) {
        context.dataStore.edit { it[Keys.ELDER_AGE] = age }
    }
}

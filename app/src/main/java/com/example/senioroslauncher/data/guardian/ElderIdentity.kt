package com.example.senioroslauncher.data.guardian

import android.content.Context
import java.util.UUID

/**
 * Manages the unique Elder Device ID used for Guardian integration.
 * The ID is generated once on first launch and persisted permanently.
 */
object ElderIdentity {
    private const val PREF_NAME = "elder_identity"
    private const val KEY_ELDER_ID = "elder_device_id"

    /**
     * Gets the existing Elder ID or creates a new one if none exists.
     * Format: elder_[UUID] (e.g., elder_a1b2c3d4-e5f6-7890-abcd-ef1234567890)
     */
    fun getOrCreateElderId(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        var elderId = prefs.getString(KEY_ELDER_ID, null)

        if (elderId == null) {
            elderId = "elder_${UUID.randomUUID()}"
            prefs.edit().putString(KEY_ELDER_ID, elderId).apply()
        }

        return elderId
    }

    /**
     * Checks if an Elder ID has already been generated.
     */
    fun hasElderId(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ELDER_ID, null) != null
    }

    /**
     * Gets the Elder ID without creating one if it doesn't exist.
     * Returns null if no ID has been generated yet.
     */
    fun getElderId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ELDER_ID, null)
    }
}

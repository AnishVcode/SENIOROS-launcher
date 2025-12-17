package com.example.senioroslauncher.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "language_code"

    /**
     * Apply the selected language to the context.
     * Call this in attachBaseContext of each Activity.
     */
    fun applyLanguage(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }

    /**
     * Update the app's locale and recreate the activity.
     * Call this when user changes language in settings.
     */
    fun setLocale(activity: Activity, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(activity.resources.configuration)
        config.setLocale(locale)

        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)

        // Recreate activity to apply changes
        activity.recreate()
    }

    /**
     * Get current locale code from shared preferences.
     * Uses applicationContext to avoid issues during attachBaseContext.
     */
    fun getLanguageCode(context: Context): String {
        // Use applicationContext if available for consistent preference access
        val appContext = context.applicationContext ?: context
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "en") ?: "en"
    }

    /**
     * Save language code to shared preferences.
     * Uses commit() for synchronous save to ensure it's saved before app restart.
     */
    fun saveLanguageCode(context: Context, languageCode: String) {
        val appContext = context.applicationContext ?: context
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Use commit() instead of apply() to ensure synchronous save before restart
        prefs.edit().putString(KEY_LANGUAGE, languageCode).commit()
    }
}

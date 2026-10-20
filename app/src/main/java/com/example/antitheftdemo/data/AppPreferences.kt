package com.example.antitheftdemo.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Atomic settings writes; raw PINs never belong in this store. */
class AppPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("setup", Context.MODE_PRIVATE)
    val language: String? get() = com.example.antitheftdemo.language.LanguageCodes.code(prefs.getString("language", null))
    val introComplete: Boolean get() = prefs.getBoolean("intro", false)
    val permissionsReviewed: Boolean get() = prefs.getBoolean("permissions", false)
    val onboardingComplete: Boolean get() = prefs.getBoolean("complete", false)
    suspend fun saveLanguage(value: String) = write { putString("language", requireNotNull(com.example.antitheftdemo.language.LanguageCodes.code(value))) }
    suspend fun finishIntro() = write { putBoolean("intro", true) }
    suspend fun finishPermissions() = write { putBoolean("permissions", true) }
    suspend fun finishOnboarding() = write { putBoolean("complete", true) }
    private suspend fun write(block: android.content.SharedPreferences.Editor.() -> Unit) =
        withContext(Dispatchers.IO) {
            check(prefs.edit().apply(block).commit()) { "Cannot save application settings" }
        }
}

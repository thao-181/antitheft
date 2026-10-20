package com.example.antitheftdemo.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

class SettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("alarm_settings", Context.MODE_PRIVATE)
    val current get() = AlarmSettings(AlarmTone.fromId(preferences.getInt("tone", 1)),
        preferences.getBoolean("flash", false), preferences.getBoolean("vibrate", true))
    val settings = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(current) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(current)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate().distinctUntilChanged()
    suspend fun setTone(tone: AlarmTone) = save { putInt("tone", tone.id) }
    suspend fun setFlash(enabled: Boolean) = save { putBoolean("flash", enabled) }
    suspend fun setVibrate(enabled: Boolean) = save { putBoolean("vibrate", enabled) }
    private suspend fun save(change: SharedPreferences.Editor.() -> Unit) = withContext(Dispatchers.IO) {
        check(preferences.edit().apply(change).commit()) { "Cannot save alarm settings" }
    }
}

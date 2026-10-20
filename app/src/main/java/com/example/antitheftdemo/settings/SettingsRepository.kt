package com.example.antitheftdemo.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        writeMutex.withLock {
            val before = current
            if (!preferences.edit().apply(change).commit()) {
                // commit() updates SharedPreferences memory even when disk persistence fails.
                // Roll back that memory too, so observers do not claim an unsaved value.
                preferences.edit().putInt("tone", before.tone.id).putBoolean("flash", before.flash)
                    .putBoolean("vibrate", before.vibrate).commit()
                error("Cannot save alarm settings")
            }
        }
    }
    companion object { private val writeMutex = Mutex() }
}

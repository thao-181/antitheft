package com.example.antitheftdemo.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    val state = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.current)
    var error by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    fun previewError(message: String) { error = message }
    fun tone(value: AlarmTone, onSaved: () -> Unit) = save(onSaved) { repository.setTone(value) }
    fun flash(enabled: Boolean) = save { repository.setFlash(enabled) }
    fun vibrate(enabled: Boolean) = save { repository.setVibrate(enabled) }
    private fun save(onSaved: () -> Unit = {}, operation: suspend () -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            try { operation(); error = null; onSaved() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Cannot save settings. Please retry." }
            finally { saving = false }
        }
    }
}

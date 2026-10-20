package com.example.antitheftdemo.alarm

import com.example.antitheftdemo.settings.AlarmSettings

enum class AlarmStatus { IDLE, PREPARING, PLAYING, SUSPENDED, FAILED }

interface AlarmPlayback {
    fun start(onState: (AlarmStatus) -> Unit, onFailure: (String) -> Unit)
    fun stop()
    fun applySettings(value: AlarmSettings) {}
}

/** One playback owner; a stopped or stale session can never start an alarm. */
class AlarmController(private val playback: AlarmPlayback) {
    private var session: String? = null
    fun trigger(id: String, secured: Boolean, onState: (AlarmStatus) -> Unit = {}, onFailure: (String) -> Unit) {
        if (!secured || session == id) return
        stop()
        session = id
        try { playback.start(onState, onFailure) }
        catch (_: Exception) { stop(); onFailure("Cannot play alarm audio.") }
    }
    fun applySettings(settings: AlarmSettings) { playback.applySettings(settings) }
    fun stop() {
        session = null
        playback.stop()
    }
}

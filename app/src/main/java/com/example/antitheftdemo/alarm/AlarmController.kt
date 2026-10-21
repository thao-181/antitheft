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
    private var generation = 0
    fun trigger(id: String, secured: Boolean, onState: (AlarmStatus) -> Unit = {}, onFailure: (String) -> Unit) {
        if (!secured || session == id) return
        stop()
        session = id
        val current = generation
        try { playback.start(
            { if (session == id && generation == current) onState(it) },
            { if (session == id && generation == current) onFailure(it) }) }
        catch (_: Exception) { stop(); onFailure("@string/audio_unavailable") }
    }
    fun applySettings(settings: AlarmSettings) { playback.applySettings(settings) }
    fun stop() {
        session = null
        generation++
        playback.stop()
    }
}

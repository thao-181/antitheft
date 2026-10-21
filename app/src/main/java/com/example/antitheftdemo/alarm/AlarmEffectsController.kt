package com.example.antitheftdemo.alarm

import com.example.antitheftdemo.settings.AlarmSettings

interface AlarmEffectOutput {
    fun vibration(enabled: Boolean)
    fun flash(enabled: Boolean)
}
/** Optional effects are independent of sound availability; updating settings never disarms. */
class AlarmEffectsController(private val output: AlarmEffectOutput) {
    private var active = false
    private var applied = AlarmSettings(vibrate = false)
    fun start(settings: AlarmSettings) { active = true; update(settings) }
    fun update(settings: AlarmSettings) {
        if (!active) return
        if (settings.vibrate != applied.vibrate) runCatching { output.vibration(settings.vibrate) }
        if (settings.flash != applied.flash) runCatching { output.flash(settings.flash) }
        applied = settings
    }
    fun stop() {
        active = false
        runCatching { output.vibration(false) }
        runCatching { output.flash(false) }
        applied = AlarmSettings(vibrate = false)
    }
}

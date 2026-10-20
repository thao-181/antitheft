package com.example.antitheftdemo.alarm

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class AndroidAlarmEffects(context: Context, private val onWarning: (String) -> Unit) : AlarmEffectOutput {
    private val flashController = FlashController(context, onWarning)
    private val vibrator = if (Build.VERSION.SDK_INT >= 31)
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    override fun flash(enabled: Boolean) {
        if (enabled) flashController.start() else flashController.stop()
    }
    override fun vibration(enabled: Boolean) {
        if (!enabled) { runCatching { vibrator.cancel() }; return }
        if (!vibrator.hasVibrator()) { onWarning("This device does not support vibration."); return }
        try {
            val pattern = longArrayOf(0, 500, 250, 500, 250)
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
        } catch (_: Exception) { onWarning("Vibration is unavailable. Monitoring remains active.") }
    }
}

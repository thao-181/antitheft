package com.example.antitheftdemo.alarm

import android.content.Context
import android.media.AudioAttributes
import android.os.VibrationAttributes
import android.app.NotificationManager
import android.util.Log
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class AndroidAlarmEffects(context: Context, private val onWarning: (String) -> Unit) : AlarmEffectOutput {
    private val notifications = context.getSystemService(NotificationManager::class.java)
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
        if (!enabled) { runCatching { vibrator.cancel() }; Log.i("AntiTheftEffects", "vibration canceled"); return }
        if (!vibrator.hasVibrator()) { Log.w("AntiTheftEffects", "No vibrator hardware"); onWarning("@string/no_vibrator"); return }
        try {
            val pattern = longArrayOf(0, 500, 250, 500, 250)
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0),
                    VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build())
            } else if (Build.VERSION.SDK_INT >= 26) {
                @Suppress("DEPRECATION")
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0), attributes)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0, attributes)
            }
            Log.i("AntiTheftEffects", "Repeating vibration requested: usage=ALARM sdk=${Build.VERSION.SDK_INT}")
            if (notifications.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE)
                onWarning("@string/vibration_unavailable")
        } catch (error: Exception) { Log.w("AntiTheftEffects", "Vibration request failed", error); onWarning("@string/vibration_unavailable") }
    }
}

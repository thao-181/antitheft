package com.example.antitheftdemo.alarm

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.antitheftdemo.detection.DetectionRepository
import com.example.antitheftdemo.settings.AlarmSettings
import com.example.antitheftdemo.settings.AlarmTone

/** Main-thread owner. Starting protection always preempts this short-lived preview. */
object AlarmPreview {
    @SuppressLint("StaticFieldLeak") // Playback receives applicationContext only; never an Activity.
    private var playback: AndroidAlarmPlayback? = null
    private val handler = Handler(Looper.getMainLooper())
    fun play(context: Context, tone: AlarmTone, onFailure: (String) -> Unit) {
        stop()
        if (DetectionRepository.state.value.session != null) return
        val preview = AndroidAlarmPlayback(context.applicationContext, AlarmSettings(tone = tone, vibrate = false), loop = false)
        playback = preview
        preview.start({}, { stop(); onFailure("@string/preview_error") })
        handler.postDelayed({ if (playback === preview) stop() }, 3000)
    }
    fun stop() {
        handler.removeCallbacksAndMessages(null)
        playback?.stop()
        playback = null
    }
}

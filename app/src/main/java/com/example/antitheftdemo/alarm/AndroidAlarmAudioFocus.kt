package com.example.antitheftdemo.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

internal fun alarmAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

internal interface AlarmAudioFocus {
    fun request(onChange: (FocusChange) -> Unit): FocusRequestResult
    fun abandon()
    fun recoveryAllowed(): Boolean
}

/** Public focus API only. A quiet media stream is a conservative hint, never ownership of focus. */
internal class AndroidAlarmAudioFocus(context: Context) : AlarmAudioFocus {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null
    private var listener: AudioManager.OnAudioFocusChangeListener? = null
    override fun request(onChange: (FocusChange) -> Unit): FocusRequestResult {
        val callback = AudioManager.OnAudioFocusChangeListener { value ->
            when (value) {
                AudioManager.AUDIOFOCUS_GAIN -> onChange(FocusChange.GAIN)
                AudioManager.AUDIOFOCUS_LOSS -> onChange(FocusChange.PERMANENT_LOSS)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onChange(FocusChange.TRANSIENT_LOSS)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onChange(FocusChange.DUCK)
            }
        }
        listener = callback
        val result = if (Build.VERSION.SDK_INT >= 26) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(alarmAudioAttributes()).setWillPauseWhenDucked(true)
                .setAcceptsDelayedFocusGain(true).setOnAudioFocusChangeListener(callback, Handler(Looper.getMainLooper()))
                .build().also { request = it }.let(audio::requestAudioFocus)
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(callback, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
        return when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> FocusRequestResult.GRANTED
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> FocusRequestResult.DELAYED
            else -> FocusRequestResult.DENIED
        }
    }
    override fun recoveryAllowed() = audio.mode == AudioManager.MODE_NORMAL && !audio.isMusicActive
    override fun abandon() {
        val oldRequest = request
        val oldListener = listener
        request = null
        listener = null
        if (Build.VERSION.SDK_INT >= 26) oldRequest?.let { audio.abandonAudioFocusRequest(it) }
        else {
            @Suppress("DEPRECATION")
            oldListener?.let { audio.abandonAudioFocus(it) }
        }
    }
}

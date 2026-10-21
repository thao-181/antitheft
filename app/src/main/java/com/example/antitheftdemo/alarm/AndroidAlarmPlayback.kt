package com.example.antitheftdemo.alarm

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.antitheftdemo.settings.AlarmSettings
import com.example.antitheftdemo.util.releaseResources

/** A single looping resource on the alarm stream. Never writes volume or DND policy. */
class AndroidAlarmPlayback(context: Context, initial: AlarmSettings = AlarmSettings(), private val loop: Boolean = true) : AlarmPlayback {
    private val context = context.applicationContext
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private var settings = initial
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var focusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var active = false
    private var prepared = false
    private var focused = false
    private var generation = 0
    private var stateCallback: (AlarmStatus) -> Unit = {}
    private var failureCallback: (String) -> Unit = {}
    private var lastStatus: AlarmStatus? = null
    private val health = object : Runnable {
        override fun run() {
            if (!active) return
            try { publishStatus(); handler.postDelayed(this, 1000) }
            catch (_: Exception) { fail("@string/audio_unavailable") }
        }
    }
    override fun start(onState: (AlarmStatus) -> Unit, onFailure: (String) -> Unit) {
        stop()
        active = true
        stateCallback = onState
        failureCallback = onFailure
        val current = generation
        try {
            val listener = AudioManager.OnAudioFocusChangeListener { change ->
                if (!active || current != generation) return@OnAudioFocusChangeListener
                try {
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS -> fail("@string/audio_unavailable")
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            focused = false
                            if (prepared && player?.isPlaying == true) player?.pause()
                            publishStatus()
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            focused = true
                            if (prepared) player?.start()
                            publishStatus()
                        }
                    }
                } catch (_: Exception) { fail("@string/audio_unavailable") }
            }
            focusListener = listener
            val granted = if (Build.VERSION.SDK_INT >= 26) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes).setWillPauseWhenDucked(true).setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(listener, handler).build()
                focusRequest = request
                audio.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audio.requestAudioFocus(listener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
            if (granted == AudioManager.AUDIOFOCUS_REQUEST_FAILED) { fail("@string/audio_unavailable"); return }
            focused = granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            preparePlayer()
            handler.post(health)
        } catch (_: Exception) { fail("@string/audio_unavailable") }
    }
    private fun preparePlayer() {
        releasePlayer()
        prepared = false
        val media = MediaPlayer()
        player = media
        media.setAudioAttributes(attributes)
        media.isLooping = loop
        media.setOnPreparedListener {
            if (player !== it || !active) return@setOnPreparedListener
            try { prepared = true; if (focused) it.start(); publishStatus() }
            catch (_: Exception) { fail("@string/audio_unavailable") }
        }
        media.setOnErrorListener { failed, _, _ ->
            if (player === failed) fail("@string/audio_unavailable")
            true
        }
        media.setOnCompletionListener { if (!loop && player === it) stop() }
        context.resources.openRawResourceFd(settings.tone.resource).use { resource ->
            media.setDataSource(resource.fileDescriptor, resource.startOffset, resource.length)
        }
        media.prepareAsync()
        publishStatus()
    }
    override fun applySettings(value: AlarmSettings) {
        val changed = settings.tone != value.tone
        settings = value
        if (active && changed) {
            try { preparePlayer() } catch (_: Exception) { fail("@string/audio_unavailable") }
        }
    }
    private fun publishStatus() {
        if (!active) return
        val silenced = audio.isStreamMute(AudioManager.STREAM_ALARM) ||
            notifications.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
        val status = AlarmAudibility.status(prepared, focused, audio.getStreamVolume(AudioManager.STREAM_ALARM), silenced)
        if (lastStatus != status) { lastStatus = status; stateCallback(status) }
    }
    private fun fail(message: String) {
        val callback = failureCallback
        stop()
        callback(message)
    }
    private fun releasePlayer() {
        val media = player
        player = null
        // Listener identity checks also reject already-queued callbacks after this reference is cleared.
        media?.release()
    }
    override fun stop() {
        active = false
        generation++
        handler.removeCallbacksAndMessages(null)
        prepared = false
        focused = false
        val oldRequest = focusRequest
        val oldListener = focusListener
        focusRequest = null
        focusListener = null
        stateCallback = {}
        failureCallback = {}
        lastStatus = null
        releaseResources({ resource, error -> Log.w("AntiTheftAudio", "Could not release $resource", error) },
            "player" to { releasePlayer() },
            "audio focus" to {
                if (Build.VERSION.SDK_INT >= 26) oldRequest?.let { audio.abandonAudioFocusRequest(it) }
                else {
                    @Suppress("DEPRECATION")
                    oldListener?.let { audio.abandonAudioFocus(it) }
                }
                Unit
            })
    }
}

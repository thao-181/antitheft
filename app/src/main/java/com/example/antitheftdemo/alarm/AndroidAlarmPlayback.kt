package com.example.antitheftdemo.alarm

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.antitheftdemo.settings.AlarmSettings
import com.example.antitheftdemo.util.releaseResources

/** A single looping resource on the alarm stream. Never writes volume or DND policy. */
class AndroidAlarmPlayback internal constructor(
    context: Context, initial: AlarmSettings, private val loop: Boolean,
    private val focus: AlarmAudioFocus, private val playerFactory: () -> MediaPlayer = ::MediaPlayer
) : AlarmPlayback {
    constructor(context: Context, initial: AlarmSettings = AlarmSettings(), loop: Boolean = true) :
        this(context, initial, loop, AndroidAlarmAudioFocus(context))
    private val context = context.applicationContext
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = alarmAudioAttributes()
    private val recovery = AlarmFocusRecovery(SystemClock::elapsedRealtime)
    private var settings = initial
    private var player: MediaPlayer? = null
    private var active = false
    private var prepared = false
    private var generation = 0
    private var focusGeneration = 0
    private var focusGrantedAt: Long? = null
    private var stateCallback: (AlarmStatus) -> Unit = {}
    private var failureCallback: (String) -> Unit = {}
    private var lastStatus: AlarmStatus? = null
    private val health = object : Runnable {
        override fun run() {
            if (!active) return
            try {
                if (loop && recovery.recoveryPending && recovery.requestRecoveryIfEligible(focus.recoveryAllowed())) requestFocus()
                resumeIfAllowed()
                publishStatus()
                handler.postDelayed(this, 500)
            }
            catch (_: Exception) { fail("@string/audio_unavailable") }
        }
    }
    override fun start(onState: (AlarmStatus) -> Unit, onFailure: (String) -> Unit) {
        stop()
        active = true
        stateCallback = onState
        failureCallback = onFailure
        recovery.start()
        try {
            requestFocus()
            if (!active) return
            handler.post(health)
        } catch (_: Exception) { fail("@string/audio_unavailable") }
    }
    private fun abandonFocus() { focusGeneration++; focus.abandon() }
    private fun requestFocus() {
        abandonFocus()
        val current = generation
        val focusEpoch = focusGeneration
        val result = focus.request { change ->
            if (active && generation == current && focusGeneration == focusEpoch) {
                try {
                    focusGrantedAt = if (change == FocusChange.GAIN) SystemClock.elapsedRealtimeNanos() else null
                    recovery.changed(change)
                    if (!recovery.focused && prepared && player?.isPlaying == true) player?.pause()
                    if (change == FocusChange.PERMANENT_LOSS) abandonFocus()
                    resumeIfAllowed()
                    publishStatus()
                } catch (_: Exception) { fail("@string/audio_unavailable") }
            }
        }
        recovery.requested(result)
        if (result == FocusRequestResult.GRANTED && recovery.focused) focusGrantedAt = SystemClock.elapsedRealtimeNanos()
        if (result == FocusRequestResult.DENIED && !loop) { fail("@string/audio_unavailable"); return }
        if (player == null && result != FocusRequestResult.DENIED) preparePlayer()
        resumeIfAllowed()
        publishStatus()
    }
    private fun resumeIfAllowed() {
        if (active && prepared && recovery.focused && player?.isPlaying == false) {
            val granted = focusGrantedAt
            player?.start()
            focusGrantedAt = null
            if (granted != null && Log.isLoggable("AntiTheftAudioTiming", Log.DEBUG)) Log.d("AntiTheftAudioTiming",
                "FOCUS_TO_START duration_ms=${(SystemClock.elapsedRealtimeNanos() - granted) / 1_000_000.0}")
        }
    }
    private fun preparePlayer() {
        releasePlayer()
        prepared = false
        val media = playerFactory()
        player = media
        media.setAudioAttributes(attributes)
        media.isLooping = loop
        media.setOnPreparedListener {
            if (player !== it || !active) return@setOnPreparedListener
            try { prepared = true; resumeIfAllowed(); publishStatus() }
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
        val predicted = AlarmAudibility.status(prepared, recovery.focused, audio.getStreamVolume(AudioManager.STREAM_ALARM), silenced)
        val status = if (predicted == AlarmStatus.PLAYING && player?.isPlaying != true) AlarmStatus.SUSPENDED else predicted
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
        focusGeneration++
        recovery.stop()
        focusGrantedAt = null
        handler.removeCallbacksAndMessages(null)
        prepared = false
        stateCallback = {}
        failureCallback = {}
        lastStatus = null
        releaseResources({ resource, error -> Log.w("AntiTheftAudio", "Could not release $resource", error) },
            "player" to { releasePlayer() },
            "audio focus" to {
                focus.abandon()
            })
    }
}

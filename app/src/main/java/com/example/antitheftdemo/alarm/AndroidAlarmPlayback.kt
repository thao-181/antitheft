package com.example.antitheftdemo.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Uses the user's system alarm sound until a project-specific raw asset is supplied. */
class AndroidAlarmPlayback(context: Context) : AlarmPlayback {
    private val context = context.applicationContext
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var focusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var vibrator: Vibrator? = null
    private var generation = 0
    private var prepared = false
    private var suspended = false
    override fun start(onState: (AlarmStatus) -> Unit, onFailure: (String) -> Unit) {
        stop()
        val current = generation
        onState(AlarmStatus.PREPARING)
        check(audio.getStreamVolume(AudioManager.STREAM_ALARM) > 0) { "Alarm volume is muted" }
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            if (current != generation) return@OnAudioFocusChangeListener
            if (change == AudioManager.AUDIOFOCUS_LOSS) {
                stop()
                onFailure("Alarm audio focus was lost. Monitoring stopped.")
            } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                suspended = true
                if (prepared) player?.let { if (it.isPlaying) it.pause() }
                onState(AlarmStatus.SUSPENDED)
            } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                suspended = false
                if (prepared) {
                    try { player?.start(); onState(AlarmStatus.PLAYING) }
                    catch (_: Exception) { stop(); onFailure("Cannot resume alarm audio.") }
                }
            }
        }
        focusListener = listener
        val granted = if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener(listener).build()
            focus = request
            audio.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(listener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
        check(granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Alarm audio focus unavailable" }
        val media = MediaPlayer()
        player = media
        media.setAudioAttributes(attributes)
        media.isLooping = true
        media.setOnPreparedListener {
            if (player === it) {
                try {
                    prepared = true
                    if (!suspended) { it.start(); onState(AlarmStatus.PLAYING) }
                    else onState(AlarmStatus.SUSPENDED)
                    vibrate()
                }
                catch (_: Exception) { stop(); onFailure("Alarm playback failed. Monitoring stopped.") }
            }
        }
        media.setOnErrorListener { _, _, _ ->
            stop(); onFailure("Alarm playback failed. Monitoring stopped."); true
        }
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        media.setDataSource(context, uri)
        media.prepareAsync()
    }
    private fun vibrate() {
        val device = if (Build.VERSION.SDK_INT >= 31)
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (!device.hasVibrator()) return
        vibrator = device
        val pattern = longArrayOf(0, 500, 250, 500, 250)
        if (Build.VERSION.SDK_INT >= 26) device.vibrate(VibrationEffect.createWaveform(pattern, 0))
        else {
            @Suppress("DEPRECATION")
            device.vibrate(pattern, 0)
        }
    }
    override fun stop() {
        generation++
        prepared = false
        suspended = false
        val media = player
        player = null
        media?.setOnPreparedListener(null)
        media?.setOnErrorListener(null)
        media?.release()
        vibrator?.cancel()
        vibrator = null
        if (Build.VERSION.SDK_INT >= 26) focus?.let { audio.abandonAudioFocusRequest(it) }
        else {
            @Suppress("DEPRECATION")
            focusListener?.let { audio.abandonAudioFocus(it) }
        }
        focus = null
        focusListener = null
    }
}

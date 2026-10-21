package com.example.antitheftdemo.alarm

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.antitheftdemo.settings.AlarmSettings
import com.example.antitheftdemo.settings.AlarmTone
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Real Android focus arbitration with a second media owner. Does not launch or control personal apps. */
class SystemAudioFocusTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync { block() }
    private fun waitFor(block: () -> Boolean) {
        var ready = false
        val end = SystemClock.elapsedRealtime() + 12_000
        while (!ready && SystemClock.elapsedRealtime() < end) { main { ready = block() }; if (!ready) SystemClock.sleep(25) }
        assertTrue("System focus transition timed out", ready)
    }
    @Test fun competingMediaPausesAlarmAndQuietRecoveryResumesWithoutRecreatingPlayer() {
        assumeTrue(Build.VERSION.SDK_INT >= 26)
        val context = instrumentation.targetContext
        val audio = context.getSystemService(AudioManager::class.java)
        val system = AndroidAlarmAudioFocus(context)
        var requests = 0
        val focus = object : AlarmAudioFocus by system {
            override fun request(onChange: (FocusChange) -> Unit): FocusRequestResult { requests++; return system.request(onChange) }
        }
        var alarmPlayer: MediaPlayer? = null
        var competingPlayer: MediaPlayer? = null
        var playback: AndroidAlarmPlayback? = null
        var created = 0
        var failures = 0
        val competingRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener { }.build()
        try {
            main {
                playback = AndroidAlarmPlayback(context, AlarmSettings(), true, focus) {
                    created++; MediaPlayer().also { alarmPlayer = it }
                }
                playback!!.start({}, { failures++ })
            }
            waitFor { alarmPlayer?.isPlaying == true }
            main {
                competingPlayer = MediaPlayer.create(context, AlarmTone.TONE_2.resource).apply { isLooping = true }
                assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(competingRequest))
                competingPlayer!!.start()
            }
            waitFor { alarmPlayer?.isPlaying == false && audio.isMusicActive }
            SystemClock.sleep(3_000)
            main {
                assertEquals(1, requests)
                assertFalse(alarmPlayer!!.isPlaying)
                competingPlayer!!.release(); competingPlayer = null
                audio.abandonAudioFocusRequest(competingRequest)
            }
            val quietAt = SystemClock.elapsedRealtimeNanos()
            waitFor { alarmPlayer?.isPlaying == true }
            Log.i("AntiTheftAudioTiming", "QUIET_TO_PLAYER_PLAYING duration_ms=${(SystemClock.elapsedRealtimeNanos() - quietAt) / 1_000_000.0}")
            main { assertEquals(2, requests); assertEquals(1, created); assertEquals(0, failures) }
        } finally {
            main {
                playback?.stop()
                competingPlayer?.release()
                audio.abandonAudioFocusRequest(competingRequest)
            }
        }
    }
}

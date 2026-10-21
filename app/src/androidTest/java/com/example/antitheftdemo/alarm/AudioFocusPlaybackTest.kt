package com.example.antitheftdemo.alarm

import android.media.MediaPlayer
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.antitheftdemo.settings.AlarmSettings
import com.example.antitheftdemo.settings.AlarmTone
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Native MediaPlayer, deterministic focus injection; no user preferences, volume or DND edits. */
class AudioFocusPlaybackTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync { block() }
    private fun waitFor(block: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 12_000
        var ready = false
        while (!ready && SystemClock.elapsedRealtime() < end) { main { ready = block() }; if (!ready) SystemClock.sleep(25) }
        assertTrue("Audio transition timed out", ready)
    }
    private class Focus : AlarmAudioFocus {
        var result = FocusRequestResult.GRANTED
        var allowed = true
        var requests = 0
        var abandoned = 0
        val callbacks = mutableListOf<(FocusChange) -> Unit>()
        override fun request(onChange: (FocusChange) -> Unit): FocusRequestResult { requests++; callbacks += onChange; return result }
        override fun abandon() { abandoned++ }
        override fun recoveryAllowed() = allowed
        fun signal(change: FocusChange) { callbacks.last()(change) }
    }
    private class Player : MediaPlayer() {
        var error: OnErrorListener? = null
        override fun setOnErrorListener(listener: OnErrorListener?) { error = listener; super.setOnErrorListener(listener) }
    }
    private val focus = Focus()
    private val players = mutableListOf<Player>()
    private var playback: AndroidAlarmPlayback? = null
    private var status = AlarmStatus.IDLE
    private var failures = 0
    private fun start() = main {
        playback = AndroidAlarmPlayback(context, AlarmSettings(), true, focus) { Player().also(players::add) }
        playback!!.start({ status = it }, { failures++ })
    }
    @After fun cleanup() = main { playback?.stop() }

    @Test fun grantedTransientDuckAndGainResumeTheSameNativePlayerPromptly() {
        start()
        waitFor { players.singleOrNull()?.isPlaying == true }
        val times = mutableListOf<Long>()
        repeat(10) { index ->
            main {
                focus.signal(if (index % 2 == 0) FocusChange.TRANSIENT_LOSS else FocusChange.DUCK)
                assertFalse(players.single().isPlaying)
                assertEquals(AlarmStatus.SUSPENDED, status)
                val before = SystemClock.elapsedRealtimeNanos()
                focus.signal(FocusChange.GAIN)
                assertTrue(players.single().isPlaying)
                times += SystemClock.elapsedRealtimeNanos() - before
                assertEquals(1, focus.requests)
                assertEquals(0, failures)
            }
        }
        times.sort()
        Log.i("AntiTheftAudioTiming", "GAIN_TO_PLAYER_PLAYING_MEDIAN duration_ms=${times[4] / 1_000_000.0}")
        Log.i("AntiTheftAudioTiming", "GAIN_TO_PLAYER_PLAYING_P95 duration_ms=${times[9] / 1_000_000.0}")
    }

    @Test fun deniedAndDelayedRequestsSuspendAndPermanentLossRecoversOnlyWhenQuiet() {
        focus.result = FocusRequestResult.DENIED
        start()
        main { assertEquals(AlarmStatus.SUSPENDED, status); assertTrue(players.isEmpty()); focus.result = FocusRequestResult.DELAYED }
        waitFor { focus.requests == 2 && players.isNotEmpty() }
        main { assertFalse(players.single().isPlaying); focus.signal(FocusChange.GAIN) }
        waitFor { players.single().isPlaying }
        main { focus.allowed = false; focus.signal(FocusChange.PERMANENT_LOSS); assertFalse(players.single().isPlaying) }
        SystemClock.sleep(2_500)
        main { assertEquals(2, focus.requests); focus.result = FocusRequestResult.GRANTED; focus.allowed = true }
        waitFor { focus.requests == 3 && players.single().isPlaying }
        main { assertEquals(0, failures); assertEquals(1, players.size) }
    }

    @Test fun stopAndChangedSessionRejectPendingFocusCallbacksAndRepeatedSensorTriggers() {
        start()
        val controller = AlarmController(playback!!)
        main { controller.trigger("old", true, { status = it }, { failures++ }) }
        waitFor { players.lastOrNull()?.isPlaying == true }
        lateinit var old: (FocusChange) -> Unit
        main {
            old = focus.callbacks.last()
            controller.stop()
            controller.trigger("new", true, { status = it }, { failures++ })
        }
        waitFor { players.last().isPlaying }
        main {
            val count = players.size
            repeat(10) { controller.trigger("new", true) { failures++ } }
            old(FocusChange.GAIN); old(FocusChange.PERMANENT_LOSS)
            assertEquals(count, players.size)
            assertTrue(players.last().isPlaying)
            playback!!.applySettings(AlarmSettings(tone = AlarmTone.TONE_2))
        }
        waitFor { players.last().isPlaying }
        val count = players.size
        var requests = 0
        main { focus.signal(FocusChange.PERMANENT_LOSS); requests = focus.requests; controller.stop() }
        SystemClock.sleep(2_500)
        main { old(FocusChange.GAIN); assertEquals(count, players.size); assertEquals(requests, focus.requests) }
    }

    @Test fun mediaErrorReleasesPlayerAndFocusWithoutStartingRecovery() {
        start()
        waitFor { players.singleOrNull()?.isPlaying == true }
        main {
            val player = players.single()
            assertTrue(player.error!!.onError(player, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0))
            assertEquals(1, failures)
        }
        val requests = focus.requests
        SystemClock.sleep(2_500)
        main { focus.signal(FocusChange.GAIN); assertEquals(requests, focus.requests); assertEquals(1, players.size) }
    }
}

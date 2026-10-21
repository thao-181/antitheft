package com.example.antitheftdemo.alarm

import org.junit.Assert.*
import org.junit.Test
import com.example.antitheftdemo.settings.AlarmSettings
import com.example.antitheftdemo.settings.AlarmTone

class AlarmControllerTest {
    @Test fun callbacksFromStoppedPlaybackCannotAffectRearmedSession() {
        val states = mutableListOf<(AlarmStatus) -> Unit>()
        val failures = mutableListOf<(String) -> Unit>()
        val alarm = AlarmController(object : AlarmPlayback {
            override fun start(onState: (AlarmStatus) -> Unit, onFailure: (String) -> Unit) {
                states += onState
                failures += onFailure
            }
            override fun stop() {}
        })
        val observed = mutableListOf<AlarmStatus>()
        var failed = false
        alarm.trigger("old", true, observed::add) { failed = true }
        alarm.stop()
        alarm.stop()
        alarm.trigger("new", true, observed::add) { failed = true }
        states[0](AlarmStatus.PLAYING)
        failures[0]("late failure")
        assertTrue(observed.isEmpty())
        assertFalse(failed)
        states[1](AlarmStatus.SUSPENDED)
        assertEquals(listOf(AlarmStatus.SUSPENDED), observed)
    }
    private class FakePlayback : AlarmPlayback {
        var starts = 0
        var playing = false
        var settings = AlarmSettings()
        override fun applySettings(value: AlarmSettings) { settings = value }
        override fun start(onState: (AlarmStatus) -> Unit, onFailure: (String) -> Unit) { starts++; playing = true }
        override fun stop() { playing = false }
    }
    @Test fun toneChangedBeforeFirstEventIsUsedWithoutStartingPlayback() {
        val fake = FakePlayback()
        val alarm = AlarmController(fake)
        alarm.applySettings(AlarmSettings(tone = AlarmTone.fromId(2)))
        assertEquals(2, fake.settings.tone.id)
        assertEquals(0, fake.starts)
        alarm.trigger("one", true) { fail(it) }
        assertEquals(2, fake.settings.tone.id)
        assertEquals(1, fake.starts)
    }
    @Test fun onlySecuredCanTriggerAndRepeatedEventsDoNotOverlap() {
        val fake = FakePlayback()
        val alarm = AlarmController(fake)
        alarm.trigger("one", false) { fail(it) }
        assertEquals(0, fake.starts)
        alarm.trigger("one", true) { fail(it) }
        alarm.trigger("one", true) { fail(it) }
        assertEquals(1, fake.starts)
        assertTrue(fake.playing)
        alarm.stop()
        assertFalse(fake.playing)
        alarm.trigger("one", false) { fail(it) }
        assertFalse(fake.playing)
    }
    @Test fun playbackFailureReleasesAndReports() {
        var released = false
        var failed = false
        val alarm = AlarmController(object : AlarmPlayback {
            override fun start(onState: (AlarmStatus) -> Unit, onFailure: (String) -> Unit) { error("No sound") }
            override fun stop() { released = true }
        })
        alarm.trigger("one", true) { failed = true }
        assertTrue(released)
        assertTrue(failed)
    }
}

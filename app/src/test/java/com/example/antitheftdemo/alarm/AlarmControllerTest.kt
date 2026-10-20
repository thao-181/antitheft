package com.example.antitheftdemo.alarm

import org.junit.Assert.*
import org.junit.Test

class AlarmControllerTest {
    private class FakePlayback : AlarmPlayback {
        var starts = 0
        var playing = false
        override fun start(onState: (AlarmStatus) -> Unit, onFailure: (String) -> Unit) { starts++; playing = true }
        override fun stop() { playing = false }
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

package com.example.antitheftdemo.detection

import com.example.antitheftdemo.home.HomeItemType.*
import org.junit.Assert.*
import org.junit.Test

class DetectionControllerTest {
    @Test fun securedRequiresMonitoringAcknowledgement() {
        val c = DetectionController()
        val id = c.request(MOTION_DETECTION)!!
        assertEquals(DetectionState.ACTIVE, c.state.value.states[MOTION_DETECTION])
        c.alarm(id)
        assertFalse(c.state.value.alarming)
        c.started(id)
        assertEquals(DetectionState.SECURED, c.state.value.states[MOTION_DETECTION])
        c.alarm(id)
        assertTrue(c.state.value.alarming)
        c.ended(id)
        c.alarm(id)
        assertFalse(c.state.value.alarming)
        assertEquals(DetectionState.STOP, c.state.value.states[MOTION_DETECTION])
        assertNull(c.request(MOTION_DETECTION))
        assertEquals(DetectionState.ACTIVE, c.state.value.states[MOTION_DETECTION])
    }
    @Test fun cannotReplaceSecuredModeAndProofIsOneUse() {
        val c = DetectionController()
        val id = c.request(WIFI_DETECTION)!!
        c.started(id)
        assertNull(c.request(CHARGER_DETECTION))
        val proof = c.issueProof(id)!!
        assertEquals(id, c.consumeProof(proof))
        assertNull(c.consumeProof(proof))
        c.ended(id)
        val second = c.request(CHARGER_DETECTION)!!
        c.started(second)
        assertNull(c.issueProof(id))
        assertNull(c.consumeProof(proof))
        c.ended(id)
        assertEquals(second, c.state.value.session)
    }
    @Test fun failureIsStoppedAndCannotLeaveSecuredUi() {
        val c = DetectionController()
        val id = c.request(PROXIMITY_DETECTION)!!
        c.ended(id, "No sensor")
        c.started(id)
        assertEquals(DetectionState.STOP, c.state.value.states[PROXIMITY_DETECTION])
        assertEquals("No sensor", c.state.value.message)
    }
    @Test fun repeatedAlarmEventsDoNotResetActualPlaybackStatus() {
        val c = DetectionController()
        val id = c.request(WIFI_DETECTION)!!
        c.started(id)
        c.alarm(id)
        c.playback(id, com.example.antitheftdemo.alarm.AlarmStatus.PLAYING)
        c.alarm(id)
        assertEquals(com.example.antitheftdemo.alarm.AlarmStatus.PLAYING, c.state.value.alarmStatus)
        c.ended(id)
        c.playback(id, com.example.antitheftdemo.alarm.AlarmStatus.PLAYING)
        assertEquals(com.example.antitheftdemo.alarm.AlarmStatus.IDLE, c.state.value.alarmStatus)
    }
    @Test fun audioFailureDoesNotAuthorizeStoppingProtection() {
        val c = DetectionController()
        val id = c.request(WIFI_DETECTION)!!
        c.started(id)
        c.alarm(id)
        c.playback(id, com.example.antitheftdemo.alarm.AlarmStatus.FAILED)
        assertEquals(DetectionState.SECURED, c.state.value.states[WIFI_DETECTION])
        assertEquals(id, c.state.value.session)
        assertNull(c.consumeProof("not-authorized"))
    }
}

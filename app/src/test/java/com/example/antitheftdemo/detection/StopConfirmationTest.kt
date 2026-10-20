package com.example.antitheftdemo.detection

import com.example.antitheftdemo.home.HomeItemType.PROXIMITY_DETECTION
import org.junit.Assert.*
import org.junit.Test

class StopConfirmationTest {
    @Test fun confirmationAndBackDoNotDisarmOrSilenceAlarm() {
        val c = DetectionController()
        val id = c.request(PROXIMITY_DETECTION)!!
        c.started(id)
        c.alarm(id)
        assertEquals(DetectionPresentation.STOP_CONFIRMATION, detectionPresentation(c.state.value, PROXIMITY_DETECTION, id))
        assertEquals(DetectionState.SECURED, c.state.value.states[PROXIMITY_DETECTION])
        assertTrue(c.state.value.alarming)
        // Back/cancel discards the presentation route; the monitoring snapshot remains the same.
        assertEquals(DetectionPresentation.SECURED, detectionPresentation(c.state.value, PROXIMITY_DETECTION))
        assertEquals(id, c.state.value.session)
        assertNull(c.consumeProof("wrong"))
        assertTrue(c.state.value.alarming)
    }
    @Test fun staleConfirmationAndProofCannotStopNewSession() {
        val c = DetectionController()
        val id = c.request(PROXIMITY_DETECTION)!!
        c.started(id)
        val proof = c.issueProof(id)!!
        assertEquals(id, c.consumeProof(proof))
        c.ended(id)
        assertEquals(DetectionState.STOP, c.state.value.states[PROXIMITY_DETECTION])
        c.request(PROXIMITY_DETECTION) // Original STOP -> ACTIVE requires an explicit tap.
        val next = c.request(PROXIMITY_DETECTION)!!
        c.started(next)
        assertNull(c.consumeProof(proof))
        assertEquals(DetectionPresentation.SECURED, detectionPresentation(c.state.value, PROXIMITY_DETECTION, id))
    }
}

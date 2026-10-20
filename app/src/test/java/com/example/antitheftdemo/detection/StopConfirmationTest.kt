package com.example.antitheftdemo.detection

import com.example.antitheftdemo.home.HomeItemType.PROXIMITY_DETECTION
import org.junit.Assert.*
import org.junit.Test

class StopConfirmationTest {
    @Test fun outgoingStoppedRouteStaysRedButReopenedRouteIsActive() {
        val c = DetectionController()
        val id = c.request(PROXIMITY_DETECTION)!!
        c.started(id)
        val proof = c.issueProof(id)!!
        assertEquals(id, c.consumeProof(proof))
        c.ended(id)
        assertEquals(DetectionPresentation.AUTHORIZED_STOP,
            detectionPresentation(c.state.value, PROXIMITY_DETECTION, stopCompleted = true))
        assertEquals(DetectionPresentation.ACTIVE, detectionPresentation(c.state.value, PROXIMITY_DETECTION))
        assertNull(c.state.value.session)
    }
    @Test fun authorizedStopAndBackDoNotDisarmOrSilenceAlarm() {
        val c = DetectionController()
        val id = c.request(PROXIMITY_DETECTION)!!
        c.started(id)
        c.alarm(id)
        val proof = c.issueProof(id)!!
        assertTrue(c.validProof(proof))
        assertEquals(DetectionPresentation.AUTHORIZED_STOP, detectionPresentation(c.state.value, PROXIMITY_DETECTION, id))
        assertEquals(DetectionState.SECURED, c.state.value.states[PROXIMITY_DETECTION])
        assertTrue(c.state.value.alarming)
        // Back discards authorization; the monitoring snapshot remains the same.
        c.revokeProof(proof)
        assertNull(c.consumeProof(proof))
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
        assertEquals(DetectionState.ACTIVE, c.state.value.states[PROXIMITY_DETECTION])
        val next = c.request(PROXIMITY_DETECTION)!!
        c.started(next)
        assertNull(c.consumeProof(proof))
        assertEquals(DetectionPresentation.SECURED, detectionPresentation(c.state.value, PROXIMITY_DETECTION, id))
    }
}

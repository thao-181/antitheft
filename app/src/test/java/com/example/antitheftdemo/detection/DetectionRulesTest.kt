package com.example.antitheftdemo.detection

import com.example.antitheftdemo.detection.monitor.*
import org.junit.Assert.*
import org.junit.Test

class DetectionRulesTest {
    @Test fun proximityNeedsNearToFarTransition() {
        val rule = ProximityRule()
        assertFalse(rule.update(false))
        assertFalse(rule.update(true))
        assertTrue(rule.update(false))
        assertFalse(rule.update(false))
    }
    @Test fun chargerDoesNotTriggerOnInitialStateOrConnect() {
        val rule = DisconnectRule(true)
        assertFalse(rule.update(true))
        assertTrue(rule.update(false))
        assertFalse(rule.update(false))
        val unplugged = DisconnectRule(false)
        assertFalse(unplugged.update(false))
        assertFalse(unplugged.update(true))
        assertTrue(unplugged.update(false))
    }
    @Test fun wifiSeparatesAdapterAndConnectionTransitions() {
        val rule = WifiRule(true, true)
        assertFalse(rule.adapter(true))
        assertFalse(rule.connection(true))
        assertTrue(rule.connection(false))
        assertFalse(rule.connection(false))
        assertTrue(rule.adapter(false))
        val offline = WifiRule(true, false)
        assertFalse(offline.connection(false))
        assertFalse(offline.connection(true))
        assertTrue(offline.connection(false))
    }
    @Test fun batteryTriggersOnceAndRearmsOnNextCycle() {
        val rule = BatteryFullRule(99)
        assertFalse(rule.update(99, true))
        assertTrue(rule.update(100, true))
        assertFalse(rule.update(100, true))
        assertFalse(rule.update(99, true))
        assertFalse(rule.update(100, true))
        assertFalse(rule.update(95, false))
        assertTrue(rule.update(100, true))
        val alreadyFull = BatteryFullRule(100)
        assertFalse(alreadyFull.update(100, true))
        assertFalse(alreadyFull.update(100, false))
        assertFalse(alreadyFull.update(100, true))
    }
    @Test fun motionRejectsNoiseAndSingleSpikesButAcceptsSustainedMotion() {
        val rule = MotionRule()
        for (time in 0L..3000L step 20) assertFalse(rule.update(0.05f, 0f, 9.81f, time))
        assertFalse(rule.update(8f, 0f, 9.81f, 3020))
        assertFalse(rule.update(0f, 0f, 9.81f, 3040))
        var triggered = false
        for (time in 3060L..3500L step 20) triggered = rule.update(8f, 0f, 9.81f, time) || triggered
        assertTrue(triggered)
    }
    @Test fun headphonesTriggerOnlyWhenTheLastEndpointDisappears() {
        val rule = HeadphonesRule(setOf(1, 2))
        assertFalse(rule.update(setOf(1, 2)))
        assertFalse(rule.update(setOf(2)))
        assertFalse(rule.update(setOf(3))) // Profile handoff still has a headphone output.
        assertTrue(rule.update(emptySet()))
        assertFalse(rule.update(emptySet()))
        assertFalse(HeadphonesRule(emptySet()).update(emptySet()))
    }
}

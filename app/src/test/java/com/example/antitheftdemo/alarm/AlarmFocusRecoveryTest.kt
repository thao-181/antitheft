package com.example.antitheftdemo.alarm

import org.junit.Assert.*
import org.junit.Test

class AlarmFocusRecoveryTest {
    private var now = 0L
    private val policy = AlarmFocusRecovery { now }
    @Test fun grantedDelayedAndDeniedHaveDistinctStates() {
        policy.start(); policy.requested(FocusRequestResult.GRANTED)
        assertTrue(policy.focused)
        policy.start(); policy.requested(FocusRequestResult.DELAYED)
        assertFalse(policy.focused)
        now = 60_000
        assertFalse(policy.requestRecoveryIfEligible(true))
        policy.changed(FocusChange.GAIN)
        assertTrue(policy.focused)
        policy.start(); policy.requested(FocusRequestResult.DENIED)
        assertTrue(policy.recoveryPending)
        assertFalse(policy.focused)
    }
    @Test fun transientLossAndDuckWaitForGainWithoutCompetingRequests() {
        policy.start(); policy.requested(FocusRequestResult.GRANTED)
        for (change in listOf(FocusChange.TRANSIENT_LOSS, FocusChange.DUCK)) {
            policy.changed(change)
            now += 60_000
            assertFalse(policy.focused)
            assertFalse(policy.requestRecoveryIfEligible(true))
            policy.changed(FocusChange.GAIN)
            assertTrue(policy.focused)
        }
    }
    @Test fun permanentLossWaitsForQuietAndHasThreeRequestsAcrossRepeatedLosses() {
        policy.start(); policy.requested(FocusRequestResult.GRANTED)
        repeat(3) {
            policy.changed(FocusChange.PERMANENT_LOSS)
            assertFalse(policy.focused)
            now += 60_000
            assertFalse(policy.requestRecoveryIfEligible(false)) // TikTok/call is still active.
            assertFalse(policy.requestRecoveryIfEligible(true))
            now += 500
            assertFalse(policy.requestRecoveryIfEligible(false)) // Brief media gap is insufficient.
            assertFalse(policy.requestRecoveryIfEligible(true))
            now += 1_000
            assertTrue(policy.requestRecoveryIfEligible(true))
            policy.requested(FocusRequestResult.GRANTED)
            assertTrue(policy.focused)
        }
        policy.changed(FocusChange.PERMANENT_LOSS)
        now += 600_000
        assertFalse(policy.requestRecoveryIfEligible(true))
        assertFalse(policy.recoveryPending)
        assertFalse(policy.focused)
    }
    @Test fun deniedRecoveryUsesBackoffAndDelayedRecoveryDoesNotRepeat() {
        policy.start(); policy.requested(FocusRequestResult.DENIED)
        assertFalse(policy.requestRecoveryIfEligible(true))
        now = 1_999
        assertFalse(policy.requestRecoveryIfEligible(true))
        now = 2_000
        assertTrue(policy.requestRecoveryIfEligible(true))
        policy.requested(FocusRequestResult.DENIED)
        assertFalse(policy.requestRecoveryIfEligible(true))
        now = 6_999
        assertFalse(policy.requestRecoveryIfEligible(true))
        now = 7_000
        assertTrue(policy.requestRecoveryIfEligible(true))
        policy.requested(FocusRequestResult.DELAYED)
        now = 600_000
        assertFalse(policy.requestRecoveryIfEligible(true))
        policy.changed(FocusChange.GAIN)
        assertTrue(policy.focused)
    }
    @Test fun stopInvalidatesPendingRecoveryAndNewPlaybackGetsItsOwnBudget() {
        policy.start(); policy.requested(FocusRequestResult.GRANTED)
        policy.changed(FocusChange.PERMANENT_LOSS)
        policy.stop()
        now = 600_000
        policy.changed(FocusChange.GAIN)
        assertFalse(policy.focused)
        assertFalse(policy.requestRecoveryIfEligible(true))
        policy.start(); policy.requested(FocusRequestResult.GRANTED)
        assertTrue(policy.focused)
    }
}

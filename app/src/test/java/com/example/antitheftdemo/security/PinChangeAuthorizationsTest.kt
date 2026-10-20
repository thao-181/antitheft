package com.example.antitheftdemo.security

import org.junit.Assert.*
import org.junit.Test

class PinChangeAuthorizationsTest {
    @Test fun grantsAreBoundToCurrentVerifierAndConsumedOnce() {
        val grants = PinChangeAuthorizations()
        val proof = grants.issue("encrypted-old-verifier", 100)
        assertTrue(grants.consume(proof, "encrypted-old-verifier", 101))
        assertFalse(grants.consume(proof, "encrypted-old-verifier", 102))
        val stale = grants.issue("encrypted-old-verifier", 100)
        assertFalse(grants.consume(stale, "another-verifier", 101))
    }
    @Test fun expiredAndCancelledGrantsCannotReplacePin() {
        val grants = PinChangeAuthorizations()
        val expired = grants.issue("verifier", 0)
        assertFalse(grants.consume(expired, "verifier", 300_000))
        val cancelled = grants.issue("verifier", 100)
        grants.revoke(cancelled)
        assertFalse(grants.consume(cancelled, "verifier", 101))
    }
}

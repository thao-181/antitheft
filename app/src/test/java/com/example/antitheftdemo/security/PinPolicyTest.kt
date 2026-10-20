package com.example.antitheftdemo.security

import org.junit.Assert.*
import org.junit.Test

class PinPolicyTest {
    @Test fun acceptsOnlyFourAsciiDigits() {
        assertTrue(PinPolicy.valid("0123"))
        listOf("", "123", "12345", "12a4", "????").forEach { assertFalse(PinPolicy.valid(it)) }
    }
    @Test fun confirmationMustMatch() {
        assertTrue(PinPolicy.confirmed("0123", "0123"))
        assertFalse(PinPolicy.confirmed("0123", "3210"))
        assertFalse(PinPolicy.confirmed("123", "123"))
    }
    @Test fun verifiesWithSaltAndRejectsWrongPin() {
        val salt = ByteArray(32) { it.toByte() }
        val verifier = PinPolicy.derive("0123", salt)
        assertTrue(PinPolicy.matches("0123", salt, verifier))
        assertFalse(PinPolicy.matches("0124", salt, verifier))
        assertFalse(PinPolicy.matches("0123", ByteArray(32), verifier))
    }
    @Test fun repeatedFailuresIncreaseDelayAndCapIt() {
        assertEquals(0L, PinPolicy.delayAfterFailures(4))
        assertEquals(30_000L, PinPolicy.delayAfterFailures(5))
        assertEquals(60_000L, PinPolicy.delayAfterFailures(6))
        assertEquals(900_000L, PinPolicy.delayAfterFailures(30))
    }
    @Test fun clockChangesCannotBypassDelayWithinSameBoot() {
        assertEquals(20_000L, PinPolicy.remainingDelay(130_000, 40_000, 10_000, 999_999, 20_000))
        assertEquals(30_000L, PinPolicy.remainingDelay(130_000, 40_000, 10_000, 100_000, 20_000))
        assertEquals(0L, PinPolicy.remainingDelay(130_000, 40_000, 10_000, 150_000, 50_000))
        // Reboot: stale uptime is ignored, wall-clock deadline still applies.
        assertEquals(10_000L, PinPolicy.remainingDelay(130_000, 40_000, 10_000, 120_000, 1000))
    }
}

package com.example.antitheftdemo.security

import android.os.SystemClock
import android.util.Log

/** Fixed step names + durations only. No PIN, verifier, salt, key, session or proof reaches this API. */
internal object PinVerificationTimings {
    private const val TAG = "AntiTheftPinTiming"
    enum class Step {
        CLICK_TO_LAUNCH, IO_DISPATCH, MUTEX_WAIT, PREFERENCES_READ, KEYSTORE_LOOKUP,
        AES_DECRYPT, PBKDF2_MATCH, PREFERENCES_WRITE, IO_RESUME, VERIFY_TOTAL,
        SUCCESS_DELIVERY, NAVIGATION, FIRST_STOP_DRAW, CHECK_TO_STOP
    }
    @Volatile var observer: ((Step, Long) -> Unit)? = null
    fun now() = SystemClock.elapsedRealtimeNanos()
    fun record(step: Step, nanos: Long) {
        observer?.invoke(step, nanos)
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "${step.name} duration_ms=${nanos / 1_000_000.0}")
        }
    }
    inline fun <T> measure(step: Step, block: () -> T): T {
        val start = now()
        return try { block() } finally { record(step, now() - start) }
    }
}

/** A single in-memory UI trace; never contains authentication material. */
internal class PinCheckTrace(val clickedAt: Long = PinVerificationTimings.now()) {
    private var readyAt = clickedAt
    private var navigationAt = clickedAt
    private var navigatedAt = clickedAt
    fun ready() { readyAt = PinVerificationTimings.now() }
    fun delivered() { PinVerificationTimings.record(PinVerificationTimings.Step.SUCCESS_DELIVERY, PinVerificationTimings.now() - readyAt) }
    fun navigating() { navigationAt = PinVerificationTimings.now() }
    fun navigated() {
        navigatedAt = PinVerificationTimings.now()
        PinVerificationTimings.record(PinVerificationTimings.Step.NAVIGATION, navigatedAt - navigationAt)
    }
    fun drawn() {
        val now = PinVerificationTimings.now()
        PinVerificationTimings.record(PinVerificationTimings.Step.FIRST_STOP_DRAW, now - navigatedAt)
        PinVerificationTimings.record(PinVerificationTimings.Step.CHECK_TO_STOP, now - clickedAt)
    }
}

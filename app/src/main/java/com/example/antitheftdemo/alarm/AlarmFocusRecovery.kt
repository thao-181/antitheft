package com.example.antitheftdemo.alarm

internal enum class FocusRequestResult { GRANTED, DELAYED, DENIED }
internal enum class FocusChange { GAIN, TRANSIENT_LOSS, DUCK, PERMANENT_LOSS }

/** One budget per armed playback, never reset by gain/loss or tone changes. No Android side effects. */
internal class AlarmFocusRecovery(private val nowMs: () -> Long) {
    private enum class State { STOPPED, REQUESTING, HELD, WAITING, RECOVERABLE }
    private var state = State.STOPPED
    private var attempts = 0
    private var retryAfter = 0L
    private var quietSince: Long? = null
    val focused get() = state == State.HELD
    val recoveryPending get() = state == State.RECOVERABLE && attempts < BACKOFF_MS.size
    fun start() { stop(); state = State.REQUESTING }
    fun stop() { state = State.STOPPED; attempts = 0; quietSince = null }
    fun requested(result: FocusRequestResult) {
        if (state != State.REQUESTING) return
        state = when (result) {
            FocusRequestResult.GRANTED -> State.HELD
            FocusRequestResult.DELAYED -> State.WAITING
            FocusRequestResult.DENIED -> State.RECOVERABLE
        }
        if (result == FocusRequestResult.DENIED) schedule()
    }
    fun changed(change: FocusChange) {
        if (state == State.STOPPED) return
        state = when (change) {
            FocusChange.GAIN -> State.HELD
            FocusChange.TRANSIENT_LOSS, FocusChange.DUCK -> State.WAITING
            FocusChange.PERMANENT_LOSS -> State.RECOVERABLE
        }
        if (change == FocusChange.PERMANENT_LOSS) schedule()
    }
    private fun schedule() {
        quietSince = null
        retryAfter = nowMs() + BACKOFF_MS[attempts.coerceAtMost(BACKOFF_MS.lastIndex)]
    }
    fun requestRecoveryIfEligible(eligible: Boolean): Boolean {
        if (state != State.RECOVERABLE || attempts >= BACKOFF_MS.size) return false
        if (!eligible) { quietSince = null; return false }
        val now = nowMs()
        val quiet = quietSince ?: now.also { quietSince = it }
        if (now < retryAfter || now - quiet < 750) return false
        attempts++
        state = State.REQUESTING
        quietSince = null
        return true
    }
    companion object { private val BACKOFF_MS = longArrayOf(2_000, 5_000, 15_000) }
}

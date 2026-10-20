package com.example.antitheftdemo.detection

import com.example.antitheftdemo.alarm.AlarmStatus
import com.example.antitheftdemo.home.HomeItemType
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DetectionState { ACTIVE, SECURED, STOP }
data class DetectionSnapshot(
    val states: Map<HomeItemType, DetectionState> = HomeItemType.entries.associateWith { DetectionState.ACTIVE },
    val type: HomeItemType? = null,
    val session: String? = null,
    val starting: Boolean = false,
    val alarming: Boolean = false,
    val alarmStatus: AlarmStatus = AlarmStatus.IDLE,
    val message: String? = null
)

/** Main-thread owner of the one monitoring session. No Android dependencies. */
class DetectionController(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val mutable = MutableStateFlow(DetectionSnapshot())
    val state = mutable.asStateFlow()
    private data class Grant(val session: String, val expires: Long)
    private val proofs = mutableMapOf<String, Grant>()
    fun request(type: HomeItemType): String? {
        val old = mutable.value
        if (old.type != null) {
            mutable.value = old.copy(message = "@string/other_mode")
            return null
        }
        val session = UUID.randomUUID().toString()
        mutable.value = old.copy(type = type, session = session, starting = true, message = null)
        return session
    }
    fun started(session: String) {
        val old = mutable.value
        if (old.session != session || old.type == null) return
        mutable.value = old.copy(starting = false, states = old.states + (old.type to DetectionState.SECURED))
    }
    fun alarm(session: String) {
        val old = mutable.value
        if (old.session == session && !old.starting && !old.alarming && old.type != null && old.states[old.type] == DetectionState.SECURED)
            mutable.value = old.copy(alarming = true, alarmStatus = AlarmStatus.PREPARING)
    }
    fun playback(session: String, status: AlarmStatus) {
        val old = mutable.value
        if (old.session == session && old.alarming) mutable.value = old.copy(alarmStatus = status)
    }
    fun issueProof(session: String): String? {
        if (mutable.value.session != session || mutable.value.starting) return null
        proofs.clear()
        return UUID.randomUUID().toString().also { proofs[it] = Grant(session, now() + 60_000) }
    }
    fun validProof(proof: String): Boolean = proofs[proof]?.let {
        it.session == mutable.value.session && now() < it.expires
    } == true
    fun revokeProof(proof: String) { proofs.remove(proof) }
    fun consumeProof(proof: String): String? {
        val valid = validProof(proof)
        val grant = proofs.remove(proof) ?: return null
        return grant.session.takeIf { valid }
    }
    fun ended(session: String, message: String? = null) {
        val old = mutable.value
        if (old.session != session || old.type == null) return
        proofs.clear()
        mutable.value = old.copy(states = old.states + (old.type to DetectionState.ACTIVE), type = null,
            session = null, starting = false, alarming = false, alarmStatus = AlarmStatus.IDLE, message = message)
    }
    fun message(value: String) { mutable.value = mutable.value.copy(message = value) }
}

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
class DetectionController {
    private val mutable = MutableStateFlow(DetectionSnapshot())
    val state = mutable.asStateFlow()
    private val proofs = mutableMapOf<String, String>()
    fun request(type: HomeItemType): String? {
        val old = mutable.value
        if (old.type != null) {
            mutable.value = old.copy(message = "Deactivate ${old.type.name.replace('_', ' ')} with PIN first.")
            return null
        }
        if (old.states[type] == DetectionState.STOP) {
            mutable.value = old.copy(states = old.states + (type to DetectionState.ACTIVE), message = null)
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
        return UUID.randomUUID().toString().also { proofs[it] = session }
    }
    fun consumeProof(proof: String): String? {
        val session = proofs.remove(proof) ?: return null
        return session.takeIf { it == mutable.value.session }
    }
    fun ended(session: String, message: String? = null) {
        val old = mutable.value
        if (old.session != session || old.type == null) return
        proofs.clear()
        mutable.value = old.copy(states = old.states + (old.type to DetectionState.STOP), type = null,
            session = null, starting = false, alarming = false, alarmStatus = AlarmStatus.IDLE, message = message)
    }
    fun message(value: String) { mutable.value = mutable.value.copy(message = value) }
}

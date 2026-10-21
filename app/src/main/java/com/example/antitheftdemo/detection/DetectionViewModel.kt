package com.example.antitheftdemo.detection

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import com.example.antitheftdemo.home.HomeItemType
import com.example.antitheftdemo.security.PinCheckTrace

class DetectionViewModel(application: Application) : AndroidViewModel(application) {
    private var proof: String? = null
    private var pinTrace: PinCheckTrace? = null
    internal fun recordPinNavigation(trace: PinCheckTrace?) { pinTrace = trace }
    internal fun onStopRendered() {
        if (authorizedSession != null && authorizedSession == state.value.session) pinTrace?.drawn()
        pinTrace = null
    }
    var authorizedSession by mutableStateOf<String?>(null)
        private set
    var stopCompleted by mutableStateOf(false)
        private set
    fun acceptProof(value: String, type: HomeItemType): Boolean {
        val snapshot = state.value
        if (snapshot.type != type || !DetectionRepository.controller.validProof(value)) return false
        if (proof == value) return true
        revokeStop()
        proof = value
        authorizedSession = snapshot.session
        stopCompleted = false
        return true
    }
    fun revokeStop() {
        proof?.let(DetectionRepository.controller::revokeProof)
        proof = null
        authorizedSession = null
        pinTrace = null
    }
    fun confirmStop(): Boolean {
        val value = proof ?: return false
        val stopped = stopDetection(value)
        if (stopped) stopCompleted = true
        revokeStop()
        return stopped
    }
    override fun onCleared() { revokeStop() }
    val state = DetectionRepository.state
    fun onCircleClick(type: HomeItemType) = DetectionRepository.activate(getApplication(), type)
    fun stopDetection(proof: String): Boolean = DetectionRepository.stopVerified(proof)
}

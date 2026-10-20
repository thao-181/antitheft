package com.example.antitheftdemo.detection

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import com.example.antitheftdemo.home.HomeItemType

class DetectionViewModel(application: Application) : AndroidViewModel(application) {
    private var proof: String? = null
    var authorizedSession by mutableStateOf<String?>(null)
        private set
    var stopCompleted by mutableStateOf(false)
        private set
    fun acceptProof(value: String) {
        revokeStop()
        if (DetectionRepository.controller.validProof(value)) {
            proof = value
            authorizedSession = DetectionRepository.state.value.session
        }
    }
    fun revokeStop() {
        proof?.let(DetectionRepository.controller::revokeProof)
        proof = null
        authorizedSession = null
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

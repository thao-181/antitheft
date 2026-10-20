package com.example.antitheftdemo.detection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.antitheftdemo.home.HomeItemType

class DetectionViewModel(application: Application) : AndroidViewModel(application) {
    val state = DetectionRepository.state
    fun onCircleClick(type: HomeItemType) = DetectionRepository.activate(getApplication(), type)
    fun stopDetection(proof: String): Boolean = DetectionRepository.stopVerified(proof)
}

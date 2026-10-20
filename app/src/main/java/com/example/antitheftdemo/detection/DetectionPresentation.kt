package com.example.antitheftdemo.detection

import com.example.antitheftdemo.home.HomeItemType

/** A red confirmation is presentation only; it never disarms the repository. */
enum class DetectionPresentation { ACTIVE, SECURED, STOP_CONFIRMATION, STOP }
fun detectionPresentation(snapshot: DetectionSnapshot, type: HomeItemType,
    confirmationSession: String? = null): DetectionPresentation = when {
    confirmationSession != null && snapshot.session == confirmationSession &&
        snapshot.type == type && snapshot.states[type] == DetectionState.SECURED -> DetectionPresentation.STOP_CONFIRMATION
    snapshot.states[type] == DetectionState.SECURED -> DetectionPresentation.SECURED
    snapshot.states[type] == DetectionState.STOP -> DetectionPresentation.STOP
    else -> DetectionPresentation.ACTIVE
}

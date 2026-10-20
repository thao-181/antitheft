package com.example.antitheftdemo.detection

import com.example.antitheftdemo.home.HomeItemType

/** The red screen is available only after PIN authorization; monitoring remains SECURED. */
enum class DetectionPresentation { ACTIVE, SECURED, AUTHORIZED_STOP }
fun detectionPresentation(snapshot: DetectionSnapshot, type: HomeItemType,
    authorizedSession: String? = null, stopCompleted: Boolean = false): DetectionPresentation = when {
    // Latch red for the outgoing route only; a newly opened route has no completion flag.
    stopCompleted -> DetectionPresentation.AUTHORIZED_STOP
    authorizedSession != null && snapshot.session == authorizedSession &&
        snapshot.type == type && snapshot.states[type] == DetectionState.SECURED -> DetectionPresentation.AUTHORIZED_STOP
    snapshot.states[type] == DetectionState.SECURED -> DetectionPresentation.SECURED
    snapshot.states[type] == DetectionState.STOP -> DetectionPresentation.ACTIVE
    else -> DetectionPresentation.ACTIVE
}

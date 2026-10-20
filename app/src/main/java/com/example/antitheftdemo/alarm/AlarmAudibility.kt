package com.example.antitheftdemo.alarm

object AlarmAudibility {
    fun status(prepared: Boolean, focus: Boolean, volume: Int, muted: Boolean): AlarmStatus = when {
        !focus || volume <= 0 || muted -> AlarmStatus.SUSPENDED
        !prepared -> AlarmStatus.PREPARING
        else -> AlarmStatus.PLAYING
    }
}

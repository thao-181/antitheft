package com.example.antitheftdemo.detection.monitor

import kotlin.math.sqrt

class DisconnectRule(initiallyConnected: Boolean) {
    private var connected = initiallyConnected
    fun update(nowConnected: Boolean): Boolean {
        val trigger = connected && !nowConnected
        connected = nowConnected
        return trigger
    }
}
class ProximityRule {
    private var near: Boolean? = null
    fun update(isNear: Boolean): Boolean {
        val trigger = near == true && !isNear
        near = isNear
        return trigger
    }
}
class BatteryFullRule(initialPercent: Int) {
    private var belowFull = initialPercent in 0..99
    private var fired = false
    fun update(percent: Int, plugged: Boolean): Boolean {
        if (percent !in 0..100) return false
        if (!plugged && percent < 100) { fired = false; belowFull = true }
        if (percent < 100) belowFull = true
        if (plugged && percent == 100 && belowFull && !fired) {
            fired = true
            return true
        }
        return false
    }
}
class WifiRule(enabled: Boolean, connected: Boolean) {
    private val adapter = DisconnectRule(enabled)
    private val connection = DisconnectRule(connected)
    fun adapter(enabled: Boolean) = adapter.update(enabled)
    fun connection(connected: Boolean) = connection.update(connected)
}
class MotionRule {
    private val gravity = FloatArray(3)
    private var firstMs: Long? = null
    private var previousMs = 0L
    private var overSince: Long? = null
    private var count = 0
    fun update(x: Float, y: Float, z: Float, timeMs: Long): Boolean {
        val values = floatArrayOf(x, y, z)
        if (values.any { !it.isFinite() }) return false
        if (firstMs == null) { firstMs = timeMs; values.copyInto(gravity); previousMs = timeMs; return false }
        val dt = (timeMs - previousMs).coerceAtLeast(1)
        if (dt > 500) { overSince = null; count = 0 }
        previousMs = timeMs
        val alpha = (0.8f / (0.8f + dt / 1000f)).coerceIn(0f, 1f)
        var energy = 0f
        for (i in 0..2) {
            gravity[i] = alpha * gravity[i] + (1 - alpha) * values[i]
            val linear = values[i] - gravity[i]
            energy += linear * linear
        }
        if (timeMs - firstMs!! < 2000 || sqrt(energy) < 2.5f) {
            overSince = null; count = 0; return false
        }
        if (overSince == null) overSince = timeMs
        count++
        return timeMs - overSince!! >= 150 && count >= 3
    }
}

class HeadphonesRule(initial: Set<Int>) {
    private val connection = DisconnectRule(initial.isNotEmpty())
    fun update(current: Set<Int>) = connection.update(current.isNotEmpty())
}

package com.example.antitheftdemo.receiver

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

class BroadCastManager(context: Context) {
    private val context = context.applicationContext
    private val receivers = mutableListOf<BroadCastDetection>()
    fun register(filter: IntentFilter, receive: (Intent) -> Unit): Intent? {
        val receiver = BroadCastDetection(receive)
        // Take ownership before registration so a partially failed start is still closed by the owner.
        receivers += receiver
        return ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    fun close() {
        receivers.forEach { receiver -> runCatching { context.unregisterReceiver(receiver) } }
        receivers.clear()
    }
}

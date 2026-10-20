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
        val sticky = ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receivers += receiver
        return sticky
    }
    fun close() {
        receivers.forEach { receiver -> runCatching { context.unregisterReceiver(receiver) } }
        receivers.clear()
    }
}

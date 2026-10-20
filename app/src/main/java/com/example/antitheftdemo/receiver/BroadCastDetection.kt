package com.example.antitheftdemo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Owned and registered only by BroadCastManager; never registered by a screen. */
class BroadCastDetection(private val receive: (Intent) -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) { intent?.let(receive) }
}

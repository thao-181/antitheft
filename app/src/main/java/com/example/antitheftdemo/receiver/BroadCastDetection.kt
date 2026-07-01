package com.example.antitheftdemo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import com.example.antitheftdemo.screen.home.HomeItem
import com.example.antitheftdemo.screen.home.HomeItemType
import com.example.antitheftdemo.screen.stop.DetectionActivity

class BroadCastDetection: BroadcastReceiver() {
    var count = 0

    override fun onReceive(context: Context?, intent: Intent?) {
        println("BroadCastDetection onReceive: ${intent?.action} $context")
        when (intent?.action) {
            WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                if (count++ == 0) return
                context?.startActivity(Intent(context, DetectionActivity::class.java))
            }
        }
    }

    fun listen(context: Context, intent: Intent, homeItem: HomeItem, broadCastDetection: BroadCastDetection) {
        when(homeItem.type) {
            HomeItemType.PROXIMITY_DETECTION -> {
                // implement later
            }
            HomeItemType.MOTION_DETECTION -> {
                // implement later
            }
            HomeItemType.CHARGER_DETECTION -> {
                // implement later
            }
            HomeItemType.HANDSFREE_DETECTION -> {
                // implement later
            }
            HomeItemType.WIFI_DETECTION -> {
                intent.action = WifiManager.WIFI_STATE_CHANGED_ACTION

            }
            HomeItemType.BATTERY_FULL_DETECTION -> {
                // implement later
            }

            else -> { }
        }
        println("BroadCastDetection listen: ${intent.action}")
        if (intent.action == null) return
        val intentFilter = IntentFilter(intent.action)
        context.registerReceiver(broadCastDetection,intentFilter)

    }

    fun stopListening(context: Context, broadCastDetection: BroadCastDetection) {
        context.unregisterReceiver(broadCastDetection)
    }
}
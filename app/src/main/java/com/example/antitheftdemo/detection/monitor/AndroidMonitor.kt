package com.example.antitheftdemo.detection.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.antitheftdemo.detection.MonitorBackend
import com.example.antitheftdemo.home.HomeItemType
import com.example.antitheftdemo.receiver.BroadCastManager

class AndroidMonitor(context: Context) : MonitorBackend {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val broadcasts = BroadCastManager(context)
    private val cleanup = mutableListOf<() -> Unit>()
    private var generation = 0
    override fun start(type: HomeItemType, onAlarm: () -> Unit, onFailure: (String) -> Unit) {
        stop()
        val current = generation
        val alarm = { if (generation == current) onAlarm() }
        val failure = { message: String -> if (generation == current) onFailure(message) }
        try {
            when (type) {
                HomeItemType.PROXIMITY_DETECTION -> sensor(Sensor.TYPE_PROXIMITY, alarm, failure)
                HomeItemType.MOTION_DETECTION -> sensor(Sensor.TYPE_ACCELEROMETER, alarm, failure)
                HomeItemType.CHARGER_DETECTION -> charger(alarm)
                HomeItemType.HANDSFREE_DETECTION -> headphones(alarm)
                HomeItemType.WIFI_DETECTION -> wifi(alarm)
                HomeItemType.BATTERY_FULL_DETECTION -> battery(alarm)
            }
        } catch (error: Exception) { stop(); throw error }
    }
    override fun stop() {
        generation++
        handler.removeCallbacksAndMessages(null)
        broadcasts.close()
        cleanup.asReversed().forEach { runCatching(it) }
        cleanup.clear()
    }
    private fun batteryIntent() = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?: error("Battery information unavailable")
    private fun plugged(intent: Intent) = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    private fun percent(intent: Intent): Int {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else -1
    }
    private fun charger(alarm: () -> Unit) {
        val initial = plugged(batteryIntent())
        check(initial) { "Connect a charger before activating this mode." }
        val rule = DisconnectRule(initial)
        broadcasts.register(IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }) { intent ->
            val connected = when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> true
                Intent.ACTION_POWER_DISCONNECTED -> false
                else -> plugged(intent)
            }
            if (rule.update(connected)) alarm()
        }
        if (rule.update(plugged(batteryIntent()))) handler.post { alarm() }
    }
    private fun battery(alarm: () -> Unit) {
        val initial = percent(batteryIntent())
        check(initial >= 0) { "Battery percentage unavailable" }
        val rule = BatteryFullRule(initial)
        broadcasts.register(IntentFilter(Intent.ACTION_BATTERY_CHANGED)) { intent ->
            if (rule.update(percent(intent), plugged(intent))) alarm()
        }
    }
    private fun sensor(type: Int, alarm: () -> Unit, failure: (String) -> Unit) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager.getDefaultSensor(type) ?: error("This device does not have the required sensor.")
        val proximity = ProximityRule()
        val motion = MotionRule()
        var received = false
        var lastEvent = SystemClock.elapsedRealtime()
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                received = true
                lastEvent = SystemClock.elapsedRealtime()
                val trigger = if (type == Sensor.TYPE_PROXIMITY)
                    proximity.update(event.values[0] < minOf(sensor.maximumRange, 5f))
                else motion.update(event.values[0], event.values[1], event.values[2], event.timestamp / 1_000_000)
                if (trigger) alarm()
            }
        }
        cleanup += { manager.unregisterListener(listener) }
        check(manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME, handler)) {
            "Cannot register the sensor listener."
        }
        handler.postDelayed({ if (!received) failure("Sensor did not deliver data. Monitoring stopped.") }, 5000)
        if (type == Sensor.TYPE_ACCELEROMETER) {
            val expectedGeneration = generation
            val watchdog = object : Runnable {
                override fun run() {
                    if (generation != expectedGeneration) return
                    if (SystemClock.elapsedRealtime() - lastEvent > 5000)
                        failure("Motion sensor stopped delivering data. Monitoring stopped.")
                    else handler.postDelayed(this, 2000)
                }
            }
            handler.postDelayed(watchdog, 6000)
        }
        val callback = object : SensorManager.DynamicSensorCallback() {
            override fun onDynamicSensorDisconnected(disconnected: Sensor) {
                if (disconnected.id == sensor.id) failure("Sensor disconnected. Monitoring stopped.")
            }
        }
        manager.registerDynamicSensorCallback(callback, handler)
        cleanup += { manager.unregisterDynamicSensorCallback(callback) }
    }
    private fun headphones(alarm: () -> Unit) {
        val audio = context.getSystemService(AudioManager::class.java)
        fun headset(device: AudioDeviceInfo): Boolean {
            val supported = mutableSetOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            if (Build.VERSION.SDK_INT >= 26) supported += AudioDeviceInfo.TYPE_USB_HEADSET
            if (Build.VERSION.SDK_INT >= 28) supported += AudioDeviceInfo.TYPE_HEARING_AID
            if (Build.VERSION.SDK_INT >= 31) supported += AudioDeviceInfo.TYPE_BLE_HEADSET
            return device.type in supported
        }
        val initial = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter(::headset).map { it.id }.toSet()
        check(initial.isNotEmpty()) { "Connect wired or Bluetooth headphones before activating this mode." }
        val rule = HeadphonesRule(initial)
        val expectedGeneration = generation
        val recheck = Runnable {
            if (generation == expectedGeneration) {
                val connected = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter(::headset).map { it.id }.toSet()
                if (rule.update(connected)) alarm()
            }
        }
        fun changed() {
            handler.removeCallbacks(recheck)
            handler.postDelayed(recheck, 750)
        }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(devices: Array<out AudioDeviceInfo>) = changed()
            override fun onAudioDevicesAdded(devices: Array<out AudioDeviceInfo>) = changed()
        }
        audio.registerAudioDeviceCallback(callback, handler)
        cleanup += { audio.unregisterAudioDeviceCallback(callback) }
        changed()
    }

    private fun wifi(alarm: () -> Unit) {
        val manager = context.getSystemService(WifiManager::class.java)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        check(manager.isWifiEnabled) { "Enable Wi-Fi before activating this mode." }
        val networks = mutableSetOf<Network>()
        val rule = WifiRule(true, false)
        val registeredGeneration = generation
        fun post(block: () -> Unit) {
            handler.post { if (generation == registeredGeneration) block() }
        }
        broadcasts.register(IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)) { intent ->
            when (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)) {
                WifiManager.WIFI_STATE_DISABLED -> if (rule.adapter(false)) alarm()
                WifiManager.WIFI_STATE_ENABLED -> rule.adapter(true)
            }
        }
        var loss: Runnable? = null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                post {
                    networks += network
                    loss?.let(handler::removeCallbacks)
                    rule.connection(true)
                }
            }
            override fun onLost(network: Network) {
                post {
                    networks -= network
                    if (networks.isEmpty()) {
                        loss?.let(handler::removeCallbacks)
                        loss = Runnable { if (networks.isEmpty() && rule.connection(false)) alarm() }
                        handler.postDelayed(loss!!, 1000)
                    }
                }
            }
        }
        connectivity.registerNetworkCallback(NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback)
        cleanup += { connectivity.unregisterNetworkCallback(callback) }
        post { if (!manager.isWifiEnabled && rule.adapter(false)) alarm() }
    }
}

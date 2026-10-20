package com.example.antitheftdemo.alarm

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/** Public torch API only: never opens a camera or captures frames. */
class FlashController(context: Context, private val onWarning: (String) -> Unit) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var id: String? = null
    private var registered = false
    private var baselinePending = false
    private var ownsTorch = false
    private var on = false
    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == id) unavailable("Flash is unavailable while the camera is in use.")
        }
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId != id || !baselinePending) return
            baselinePending = false
            if (enabled) unavailable("Flash is already in use. Alarm sound and monitoring remain active.")
            else handler.post(blink)
        }
    }
    private val blink = object : Runnable {
        override fun run() {
            val camera = id ?: return
            try {
                on = !on
                manager.setTorchMode(camera, on)
                ownsTorch = true
                handler.postDelayed(this, 500)
            } catch (_: Exception) { unavailable("Flash could not be controlled on this device.") }
        }
    }
    fun start() {
        if (registered) return
        try {
            id = manager.cameraIdList.firstOrNull { camera ->
                manager.getCameraCharacteristics(camera).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (id == null) { onWarning("This device has no available flash."); return }
            baselinePending = true
            registered = true
            manager.registerTorchCallback(callback, handler)
            // A missing baseline must not leave a registered callback indefinitely.
            handler.postDelayed({ if (baselinePending) unavailable("Flash status is unavailable.") }, 3000)
        } catch (_: Exception) { unavailable("Flash is restricted by this device. Monitoring remains active.") }
    }
    private fun unavailable(message: String) { stop(); onWarning(message) }
    fun stop() {
        handler.removeCallbacksAndMessages(null)
        if (ownsTorch) id?.let { runCatching { manager.setTorchMode(it, false) } }
        if (registered) runCatching { manager.unregisterTorchCallback(callback) }
        id = null
        registered = false
        baselinePending = false
        ownsTorch = false
        on = false
    }
}

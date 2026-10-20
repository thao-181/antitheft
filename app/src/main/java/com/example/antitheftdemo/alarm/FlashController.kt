package com.example.antitheftdemo.alarm

import android.content.Context
import android.util.Log
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
            if (cameraId == id) unavailable("@string/flash_busy")
        }
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId != id || !baselinePending) return
            baselinePending = false
            if (enabled) unavailable("@string/flash_owned")
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
            } catch (_: Exception) { unavailable("@string/flash_unavailable") }
        }
    }
    fun start() {
        if (registered) return
        try {
            id = manager.cameraIdList.sortedBy { camera ->
                if (manager.getCameraCharacteristics(camera).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) 0 else 1
            }.firstOrNull { camera ->
                manager.getCameraCharacteristics(camera).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (id == null) { onWarning("@string/no_flash"); return }
            Log.i("AntiTheftEffects", "Alarm torch requested")
            baselinePending = true
            registered = true
            manager.registerTorchCallback(callback, handler)
            // A missing baseline must not leave a registered callback indefinitely.
            handler.postDelayed({ if (baselinePending) unavailable("@string/flash_unavailable") }, 3000)
        } catch (_: Exception) { unavailable("@string/flash_unavailable") }
    }
    private fun unavailable(message: String) { Log.w("AntiTheftEffects", message); stop(); onWarning(message) }
    fun stop() {
        if (registered) Log.i("AntiTheftEffects", "Alarm torch released")
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

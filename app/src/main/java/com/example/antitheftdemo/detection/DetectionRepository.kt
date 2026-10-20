package com.example.antitheftdemo.detection

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.antitheftdemo.detection.service.MonitoringService
import com.example.antitheftdemo.home.HomeItemType
import com.example.antitheftdemo.security.PinStore

interface MonitorBackend {
    fun start(type: HomeItemType, onAlarm: () -> Unit, onFailure: (String) -> Unit)
    fun stop()
}

/** Main-thread application state; the service alone owns sensor, callback and audio resources. */
object DetectionRepository {
    val controller = DetectionController()
    val state = controller.state
    private val handler = Handler(Looper.getMainLooper())
    private var stopRuntime: ((String?) -> Unit)? = null
    private var initialized = false
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val prefs = context.getSharedPreferences("monitor_runtime", Context.MODE_PRIVATE)
        val interrupted = prefs.getString("active_type", null)
        if (interrupted != null) {
            controller.message("@string/interrupted")
            prefs.edit().remove("active_type").apply()
        }
    }
    fun activate(context: Context, type: HomeItemType) {
        initialize(context)
        if (!PinStore(context).hasPin) { controller.message("@string/pin_required"); return }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            controller.message("@string/notification_required"); return
        }
        val session = controller.request(type) ?: return
        com.example.antitheftdemo.alarm.AlarmPreview.stop()
        try {
            ContextCompat.startForegroundService(context, Intent(context, MonitoringService::class.java)
                .putExtra("session", session).putExtra("type", type.name))
            handler.postDelayed({
                if (state.value.session == session && state.value.starting)
                    failed(session, "@string/service_start_error")
            }, 10_000)
        } catch (_: Exception) { failed(session, "@string/service_not_allowed") }
    }
    fun attach(session: String, stop: (String?) -> Unit): Boolean {
        if (state.value.session != session || !state.value.starting) return false
        stopRuntime = stop
        return true
    }
    fun started(context: Context, session: String) {
        if (state.value.session != session) return
        context.getSharedPreferences("monitor_runtime", Context.MODE_PRIVATE).edit()
            .putString("active_type", state.value.type?.name).apply()
        controller.started(session)
    }
    fun stopVerified(proof: String): Boolean {
        val session = controller.consumeProof(proof) ?: return false
        val stop = stopRuntime ?: return false
        stop(null)
        return state.value.session != session
    }
    fun failed(session: String, message: String) {
        if (state.value.session != session) return
        val stop = stopRuntime
        if (stop != null) stop(message) else controller.ended(session, message)
    }
    fun ended(context: Context, session: String, message: String?) {
        if (state.value.session != session) return
        stopRuntime = null
        context.getSharedPreferences("monitor_runtime", Context.MODE_PRIVATE).edit().remove("active_type").apply()
        controller.ended(session, message)
    }
}

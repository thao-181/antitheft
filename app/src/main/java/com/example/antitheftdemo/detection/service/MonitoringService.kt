package com.example.antitheftdemo.detection.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.antitheftdemo.MainActivity
import com.example.antitheftdemo.R
import com.example.antitheftdemo.alarm.AlarmEffectsController
import com.example.antitheftdemo.alarm.AndroidAlarmEffects
import com.example.antitheftdemo.alarm.AlarmPreview
import com.example.antitheftdemo.settings.AlarmSettings
import com.example.antitheftdemo.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.antitheftdemo.alarm.AlarmStatus
import com.example.antitheftdemo.alarm.AlarmController
import com.example.antitheftdemo.alarm.AndroidAlarmPlayback
import com.example.antitheftdemo.detection.DetectionRepository
import com.example.antitheftdemo.detection.monitor.AndroidMonitor
import com.example.antitheftdemo.home.HomeItemType

class MonitoringService : Service() {
    private var session: String? = null
    private var mode: HomeItemType? = null
    private var monitor: AndroidMonitor? = null
    private var alarm: AlarmController? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var finishing = false
    private var effects: AlarmEffectsController? = null
    private var settings = AlarmSettings()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("session")
        val type = intent?.getStringExtra("type")?.let { runCatching { HomeItemType.valueOf(it) }.getOrNull() }
        if (session != null) return START_NOT_STICKY
        if (id == null || type == null || !DetectionRepository.attach(id, ::finishMonitoring)) {
            stopSelf(); return START_NOT_STICKY
        }
        session = id
        mode = type
        try {
            createChannel()
            if (Build.VERSION.SDK_INT >= 26) {
                check(getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE) {
                    "Monitoring notification channel is blocked. Enable it in App Settings."
                }
            }
            ServiceCompat.startForeground(this, NOTIFICATION, notification(false),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
            val repository = SettingsRepository(this)
            settings = repository.current
            alarm = AlarmController(AndroidAlarmPlayback(this, settings))
            effects = AlarmEffectsController(AndroidAlarmEffects(this) { DetectionRepository.controller.message(it) })
            scope.launch {
                repository.settings.collect { value ->
                    settings = value
                    alarm?.applySettings(value)
                    if (DetectionRepository.state.value.alarming) {
                        effects?.update(value)
                    }
                }
            }
            monitor = AndroidMonitor(this)
            acquireSensorWakeLock(type)
            monitor!!.start(type, { trigger(id) }, { finishMonitoring(it) })
            DetectionRepository.started(this, id)
        } catch (error: Exception) {
            finishMonitoring(error.message ?: "Cannot start monitoring service.")
        }
        return START_NOT_STICKY
    }
    @SuppressLint("WakelockTimeout")
    private fun acquireSensorWakeLock(type: HomeItemType) {
        if (type != HomeItemType.MOTION_DETECTION && type != HomeItemType.PROXIMITY_DETECTION) return
        // Non-wakeup sensors need the CPU awake with the screen off. Release on every service exit.
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AntiTheftDemo:monitor").apply { acquire() }
    }
    @SuppressLint("WakelockTimeout") // User-armed alarm; released by finishMonitoring/onDestroy.
    private fun trigger(id: String) {
        val snapshot = DetectionRepository.state.value
        if (finishing || snapshot.session != id || snapshot.starting || snapshot.alarming) return
        AlarmPreview.stop()
        if (wakeLock?.isHeld != true) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AntiTheftDemo:alarm").apply { acquire() }
        }
        DetectionRepository.controller.alarm(id)
        effects?.start(settings)
        alarm?.trigger(id, DetectionRepository.state.value.alarming,
            { status ->
                DetectionRepository.controller.playback(id, status)
                if (!finishing) getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(true))
            }, ::alarmUnavailable)
        if (!finishing) getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(true))
    }
    private fun alarmUnavailable(reason: String) {
        if (finishing) return
        alarm?.stop()
        session?.let { DetectionRepository.controller.playback(it, AlarmStatus.FAILED) }
        DetectionRepository.controller.message("Alarm sound unavailable. Monitoring remains armed; enter PIN to deactivate.")
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(true))
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Anti-theft monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Visible while a user-activated protection mode is monitoring"
                    setSound(null, null)
                })
        }
    }
    private fun notification(alarming: Boolean): Notification {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("monitor_session", session)
        }
        val pending = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val name = mode?.name?.lowercase()?.replace('_', ' ') ?: "Anti-theft"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(when {
                DetectionRepository.state.value.alarmStatus == AlarmStatus.FAILED -> "Alarm sound unavailable"
                DetectionRepository.state.value.alarmStatus == AlarmStatus.SUSPENDED -> "Alarm audio paused or muted"
                alarming -> "Alarm triggered"
                else -> "Protection active"
            })
            .setContentText("$name - tap to enter PIN and deactivate")
            .setContentIntent(pending)
            .addAction(0, "Open protection", pending)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).build()
    }
    private fun finishMonitoring(message: String?) {
        if (finishing) return
        finishing = true
        scope.cancel()
        monitor?.stop()
        monitor = null
        alarm?.stop()
        alarm = null
        effects?.stop()
        effects = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        session?.let { DetectionRepository.ended(this, it, message) }
    }
    override fun onDestroy() {
        finishMonitoring("Monitoring service was stopped by Android. Activate protection again.")
        super.onDestroy()
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        finishMonitoring("Android ended the monitoring service. Activate protection again.")
    }
    companion object {
        const val CHANNEL = "antitheft_monitoring"
        private const val NOTIFICATION = 42
    }
}

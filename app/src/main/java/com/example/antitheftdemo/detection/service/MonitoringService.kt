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
import android.util.Log
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
import com.example.antitheftdemo.util.releaseResources

class MonitoringService : Service() {
    private val localePreferences by lazy { getSharedPreferences("setup", MODE_PRIVATE) }
    private val localeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "language") scope.launch { refreshNotificationLocale() }
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshNotificationLocale()
    }
    private fun refreshNotificationLocale() {
        if (session != null && !finishing) {
            createChannel()
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION,
                notification(DetectionRepository.state.value.alarming))
        }
    }
    private var session: String? = null
    private var mode: HomeItemType? = null
    private var monitor: AndroidMonitor? = null
    private var alarm: AlarmController? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var finishing = false
    private var effects: AlarmEffectsController? = null
    private var settings = AlarmSettings()
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("session")
        val type = intent?.getStringExtra("type")?.let { runCatching { HomeItemType.valueOf(it) }.getOrNull() }
        // A new start may reach this same instance before Android delivers onDestroy after stopSelf.
        if (finishing) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            finishing = false
            session = null
            mode = null
        }
        if (session != null) return START_NOT_STICKY
        if (id == null || type == null || !DetectionRepository.attach(id, type, ::finishMonitoring)) {
            stopSelf(); return START_NOT_STICKY
        }
        session = id
        mode = type
        try {
            localePreferences.registerOnSharedPreferenceChangeListener(localeListener)
            createChannel()
            if (Build.VERSION.SDK_INT >= 26) {
                check(getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE) {
                    "@string/channel_blocked"
                }
            }
            ServiceCompat.startForeground(this, NOTIFICATION, notification(false),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
            val repository = SettingsRepository(this)
            settings = repository.current
            alarm = AlarmController(AndroidAlarmPlayback(this, settings))
            effects = AlarmEffectsController(AndroidAlarmEffects(this) {
                if (!finishing && session == id) DetectionRepository.controller.message(it)
            })
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
            monitor!!.start(type, { trigger(id) }, { if (session == id) finishMonitoring(it) })
            if (!finishing) DetectionRepository.started(this, id)
        } catch (error: Exception) {
            finishMonitoring(error.message?.takeIf { it.startsWith("@string/") } ?: "@string/service_start_error")
        }
        return START_NOT_STICKY
    }
    @SuppressLint("WakelockTimeout")
    private fun acquireSensorWakeLock(type: HomeItemType) {
        if (type != HomeItemType.MOTION_DETECTION && type != HomeItemType.PROXIMITY_DETECTION) return
        // Non-wakeup sensors need the CPU awake with the screen off. Release on every service exit.
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AntiTheftDemo:monitor")
        wakeLock!!.acquire()
    }
    @SuppressLint("WakelockTimeout") // User-armed alarm; released by finishMonitoring/onDestroy.
    private fun trigger(id: String) {
        val snapshot = DetectionRepository.state.value
        if (finishing || snapshot.session != id || snapshot.starting || snapshot.alarming) return
        AlarmPreview.stop()
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock = getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AntiTheftDemo:alarm")
                wakeLock!!.acquire()
            }
        } catch (_: Exception) {
            finishMonitoring("@string/service_stopped")
            return
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
        DetectionRepository.controller.message("@string/audio_unavailable")
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(true))
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, com.example.antitheftdemo.language.AppLocale.text(this, "@string/monitor_channel"), NotificationManager.IMPORTANCE_LOW).apply {
                    description = com.example.antitheftdemo.language.AppLocale.text(this@MonitoringService, "@string/monitor_channel_description")
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
        val name = mode?.titleReference ?: "@string/app_title"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(com.example.antitheftdemo.language.AppLocale.text(this, when {
                DetectionRepository.state.value.alarmStatus == AlarmStatus.FAILED -> "@string/audio_unavailable_title"
                DetectionRepository.state.value.alarmStatus == AlarmStatus.SUSPENDED -> "@string/audio_suspended_title"
                alarming -> "@string/alarm_triggered"
                else -> "@string/protection_active"
            }))
            .setContentText(com.example.antitheftdemo.language.AppLocale.text(this, name) + " - " + com.example.antitheftdemo.language.AppLocale.text(this, "@string/notification_open"))
            .setContentIntent(pending)
            .addAction(0, com.example.antitheftdemo.language.AppLocale.text(this, "@string/open_protection"), pending)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).build()
    }
    private fun finishMonitoring(message: String?) {
        if (finishing) return
        finishing = true
        // Invalidate ownership before releasing: callbacks arriving during cleanup cannot re-arm.
        val oldMonitor = monitor
        val oldAlarm = alarm
        val oldEffects = effects
        val oldWakeLock = wakeLock
        monitor = null
        alarm = null
        effects = null
        wakeLock = null
        releaseResources({ resource, error -> Log.w("AntiTheftService", "Could not release $resource", error) },
            "locale listener" to { localePreferences.unregisterOnSharedPreferenceChangeListener(localeListener) },
            "settings collector" to { scope.cancel() },
            "monitor" to { oldMonitor?.stop(); Unit },
            "alarm" to { oldAlarm?.stop(); Unit },
            "effects" to { oldEffects?.stop(); Unit },
            "wake lock" to { if (oldWakeLock?.isHeld == true) oldWakeLock.release() },
            "foreground notification" to { stopForeground(STOP_FOREGROUND_REMOVE) },
            "service" to { stopSelf() },
            "session" to { session?.let { DetectionRepository.ended(this, it, message) }; Unit })
    }
    override fun onDestroy() {
        finishMonitoring("@string/service_stopped")
        super.onDestroy()
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        finishMonitoring("@string/service_stopped")
    }
    companion object {
        const val CHANNEL = "antitheft_monitoring"
        private const val NOTIFICATION = 42
    }
}

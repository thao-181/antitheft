package com.example.antitheftdemo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.antitheftdemo.detection.DetectionRepository
import com.example.antitheftdemo.navigation.AppNavigation
import com.example.antitheftdemo.ui.theme.AntiTheftDemoTheme

class MainActivity : ComponentActivity() {
    private var notificationSession by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DetectionRepository.initialize(applicationContext)
        notificationSession = if (savedInstanceState?.getBoolean("notification_handled") == true) null
            else intent?.getStringExtra("monitor_session")
        setContent {
            AntiTheftDemoTheme {
                AppNavigation(notificationSession) {
                    notificationSession = null
                    intent?.removeExtra("monitor_session")
                }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("notification_handled", notificationSession == null)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationSession = intent.getStringExtra("monitor_session")
    }
}

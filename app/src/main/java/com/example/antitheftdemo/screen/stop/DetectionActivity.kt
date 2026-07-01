package com.example.antitheftdemo.screen.stop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.antitheftdemo.ui.theme.AntiTheftDemoTheme

class DetectionActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AntiTheftDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    StopScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
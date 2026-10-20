package com.example.antitheftdemo.detection

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antitheftdemo.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.antitheftdemo.home.HomeItemType

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun DetectionScreen(
    title: String,             // Tiêu đề (VD: "Wi-Fi Detection")
    itemType: HomeItemType,    // Phân loại chức năng để Receiver biết đường đăng ký
    viewModel: DetectionViewModel,
    onBackClick: () -> Unit,
    onNavigateToPin: () -> Unit // Chuyển sang màn PIN khi bấm Deactivate
) {
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val currentState = snapshot.states.getValue(itemType)
    val imageRes = when (currentState) {
        DetectionState.ACTIVE -> R.drawable.active
        DetectionState.SECURED -> R.drawable.secured
        DetectionState.STOP -> R.drawable.stop
    }

    val statusText = when (currentState) {
        DetectionState.ACTIVE -> "Tap to active"
        DetectionState.SECURED -> "Tap to deactivate"
        DetectionState.STOP -> "Tap to stop"
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF5F7FB)
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = when (currentState) {
                        DetectionState.ACTIVE -> "Activate protection"
                        DetectionState.SECURED -> "Protection secured"
                        DetectionState.STOP -> "Reset protection"
                    },
                    modifier = Modifier.fillMaxSize().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { if (!snapshot.starting) viewModel.onCircleClick(itemType) }
                )


            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(text = statusText, color = Color(0xFF9CA3AF), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            snapshot.message?.let { Text(it, color = Color(0xFFEF4444), modifier = Modifier.padding(horizontal = 24.dp)) }
            if (snapshot.starting) Text("Starting monitoring...", color = Color.Gray)
            if (snapshot.alarming && snapshot.type == itemType) Text(
                when (snapshot.alarmStatus) {
                    com.example.antitheftdemo.alarm.AlarmStatus.PLAYING -> "Alarm playing - enter PIN to stop"
                    com.example.antitheftdemo.alarm.AlarmStatus.FAILED -> "Alarm sound unavailable - enter PIN to stop"
                    com.example.antitheftdemo.alarm.AlarmStatus.SUSPENDED -> "Alarm audio paused by Android - enter PIN to stop"
                    else -> "Starting alarm - enter PIN to stop"
                }, color = Color(0xFFEF4444), modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(modifier = Modifier.height(24.dp))

            if (currentState == DetectionState.SECURED) {
                Button(
                    // CHÍNH Ở ĐÂY: Thay vì tắt ngay, ta gọi callback mở màn hình PIN
                    onClick = onNavigateToPin,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.width(130.dp).height(38.dp)
                ) {
                    Text("Deactivate", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.height(38.dp))
            }
        }
    }
}

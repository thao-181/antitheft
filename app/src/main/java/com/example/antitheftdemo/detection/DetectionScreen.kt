package com.example.antitheftdemo.detection

import com.example.antitheftdemo.language.userText
import androidx.compose.ui.res.stringResource
import com.example.antitheftdemo.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.antitheftdemo.home.HomeItemType

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun DetectionScreen(
    title: String,             // Tiêu đề (VD: "@string/wifi")
    itemType: HomeItemType,    // Phân loại chức năng để Receiver biết đường đăng ký
    viewModel: DetectionViewModel,
    onBackClick: () -> Unit,
    authorizedSession: String? = null,
    stopCompleted: Boolean = false,
    onDeactivate: () -> Unit = {},
    onConfirmStop: () -> Unit // Chuyển sang màn PIN khi bấm Deactivate
) {
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val currentState = snapshot.states.getValue(itemType)
    val presentation = detectionPresentation(snapshot, itemType, authorizedSession, stopCompleted)
    val confirming = presentation == DetectionPresentation.AUTHORIZED_STOP
    val imageRes = when (presentation) {
        DetectionPresentation.ACTIVE -> R.drawable.active
        DetectionPresentation.SECURED -> R.drawable.secured
        DetectionPresentation.AUTHORIZED_STOP -> R.drawable.stop
    }
    val statusText = when (presentation) {
        DetectionPresentation.ACTIVE -> "@string/tap_active"
        DetectionPresentation.SECURED -> "@string/tap_deactivate"
        DetectionPresentation.AUTHORIZED_STOP -> "@string/tap_stop"
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(userText(title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.Black) }
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
                    contentDescription = userText(when (presentation) {
                        DetectionPresentation.ACTIVE -> "@string/activate_protection"
                        DetectionPresentation.SECURED -> "@string/secured"
                        DetectionPresentation.AUTHORIZED_STOP -> "@string/stop_protection"
                    }),
                    modifier = Modifier.fillMaxSize().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (stopCompleted) return@clickable
                        if (confirming) onConfirmStop()
                        else if (!snapshot.starting) viewModel.onCircleClick(itemType)
                    }
                )
                // Source PNGs include English lettering. Keep their rings; replace only the
                // inner label surface when a translated locale is selected.
                if (LocalConfiguration.current.locales[0].language in listOf("hi", "es", "fr", "de", "id", "in", "pt")) {
                    val colors = when (presentation) {
                        DetectionPresentation.ACTIVE -> listOf(Color(0xFF78BCF7), Color(0xFF87C9EE))
                        DetectionPresentation.SECURED -> listOf(Color(0xFF65CEB4), Color(0xFF91E0CB))
                        else -> listOf(Color(0xFFECA19F), Color(0xFFECA8A5))
                    }
                    Box(Modifier.size(148.dp).background(Brush.radialGradient(colors), CircleShape), contentAlignment = Alignment.Center) {
                        Text(stringResource(when (presentation) {
                            DetectionPresentation.ACTIVE -> R.string.active_label
                            DetectionPresentation.SECURED -> R.string.secured_label
                            else -> R.string.stop_label
                        }), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(text = userText(statusText), color = Color(0xFF9CA3AF), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            snapshot.message?.let { Text(userText(it), color = Color(0xFFEF4444), modifier = Modifier.padding(horizontal = 24.dp)) }
            if (snapshot.starting) Text(stringResource(R.string.starting_monitor), color = Color.Gray)
            if (snapshot.alarming && snapshot.type == itemType) Text(
                userText(when (snapshot.alarmStatus) {
                    com.example.antitheftdemo.alarm.AlarmStatus.PLAYING -> "@string/alarm_playing"
                    com.example.antitheftdemo.alarm.AlarmStatus.FAILED -> "@string/alarm_failed"
                    com.example.antitheftdemo.alarm.AlarmStatus.SUSPENDED -> "@string/alarm_suspended"
                    else -> "@string/alarm_starting"
                }), color = Color(0xFFEF4444), modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(modifier = Modifier.height(24.dp))

            if (currentState == DetectionState.SECURED && !confirming) {
                Button(
                    // CHÍNH Ở ĐÂY: Thay vì tắt ngay, ta gọi callback mở màn hình PIN
                    onClick = onDeactivate,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.width(130.dp).height(38.dp)
                ) {
                    Text(stringResource(R.string.deactivate), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.height(38.dp))
            }
        }
    }
}

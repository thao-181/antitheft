package com.example.antitheftdemo.settings

import com.example.antitheftdemo.language.userText
import androidx.compose.ui.res.stringResource
import com.example.antitheftdemo.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, flashSupported: Boolean, previewAllowed: Boolean,
    onBack: () -> Unit, onPin: () -> Unit, onLanguage: () -> Unit,
    onPreview: (AlarmTone) -> Unit = {}, onStopPreview: () -> Unit = {}) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val flashLabel = stringResource(R.string.flash_light)
    val vibrateLabel = stringResource(R.string.vibrate)
    var toneDialog by remember { mutableStateOf(false) }
    Scaffold(containerColor = Color.White, topBar = {
        Column {
            CenterAlignedTopAppBar(title = { Text(stringResource(R.string.settings), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.Black) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White))
            HorizontalDivider(color = Color(0xFFEEEEEE))
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            SettingsRow(Icons.Outlined.Lock, Color(0xFF1686F5), "@string/pin_code", "@string/pin_subtitle", onPin)
            SettingsRow(Icons.Outlined.Alarm, Color(0xFFFF9A24), "@string/alarm_tone", "@string/tone_subtitle", { toneDialog = true })
            SettingsRow(Icons.Outlined.Language, Color(0xFF9367E8), "@string/language", "@string/language_subtitle", onLanguage)
            SettingsRow(Icons.Outlined.FlashOn, Color(0xFFEF5360), "@string/flash_light", "@string/flash_subtitle") {
                Switch(checked = settings.flash, enabled = flashSupported && !viewModel.saving,
                    colors = alarmSwitchColors(), onCheckedChange = viewModel::flash, modifier = Modifier.semantics { contentDescription = flashLabel })
            }
            if (!flashSupported) Text(stringResource(R.string.no_flash), color = Color.Gray, fontSize = 12.sp)
            SettingsRow(Icons.Outlined.Vibration, Color(0xFF2AB979), "@string/vibrate", "@string/vibrate_subtitle") {
                Switch(checked = settings.vibrate, enabled = !viewModel.saving,
                    colors = alarmSwitchColors(), onCheckedChange = viewModel::vibrate, modifier = Modifier.semantics { contentDescription = vibrateLabel })
            }
            viewModel.error?.let { Text(userText(it), color = Color(0xFFEF4444)) }
        }
    }
    if (toneDialog) AlarmToneDialog(settings.tone, viewModel.saving, previewAllowed,
        onPreview, onStopPreview, { toneDialog = false }, { value -> viewModel.tone(value) { toneDialog = false } })
}

@Composable
private fun SettingsRow(icon: ImageVector, color: Color, title: String, subtitle: String,
    onClick: (() -> Unit)? = null, control: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(color.copy(alpha = .12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(userText(title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2937))
            Spacer(Modifier.height(5.dp))
            Text(userText(subtitle), fontSize = 13.sp, color = Color(0xFF9CA3AF))
        }
        if (control != null) control()
        else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFF9CA3AF))
    }
}

@Composable
private fun alarmSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = Color(0xFF0075FF),
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = Color(0xFFD9D9D9),
    uncheckedBorderColor = Color.Transparent,
    disabledCheckedThumbColor = Color.White,
    disabledCheckedTrackColor = Color(0xFF0075FF).copy(alpha = .4f),
    disabledUncheckedThumbColor = Color.White,
    disabledUncheckedTrackColor = Color(0xFFD9D9D9)
)

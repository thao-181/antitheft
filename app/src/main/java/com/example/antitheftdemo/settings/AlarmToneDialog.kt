package com.example.antitheftdemo.settings

import com.example.antitheftdemo.language.userText
import androidx.compose.ui.res.stringResource
import com.example.antitheftdemo.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun AlarmToneDialog(selected: AlarmTone, saving: Boolean, previewAllowed: Boolean,
    onPreview: (AlarmTone) -> Unit, onStopPreview: () -> Unit,
    onDismiss: () -> Unit, onSave: (AlarmTone) -> Unit) {
    var draft by remember { mutableStateOf(selected) }
    DisposableEffect(Unit) { onDispose { onStopPreview() } }
    Dialog(onDismissRequest = { if (!saving) { onStopPreview(); onDismiss() } }) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(stringResource(R.string.alarm_tone), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                Spacer(Modifier.height(16.dp))
                AlarmTone.entries.forEach { tone ->
                    Row(Modifier.fillMaxWidth().selectable(selected = draft == tone, enabled = !saving,
                        role = Role.RadioButton, onClick = { draft = tone; if (previewAllowed) onPreview(tone) })
                        .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(userText(tone.label), Modifier.weight(1f), color = Color(0xFF374151), fontSize = 16.sp)
                        RadioButton(selected = draft == tone, onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF0075FF)))
                    }
                }
                if (!previewAllowed) Text(stringResource(R.string.preview_armed), color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onStopPreview(); onDismiss() }, enabled = !saving,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0F1F4), contentColor = Color(0xFF6B7280))) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = { onStopPreview(); onSave(draft) }, enabled = !saving,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0075FF))) { Text(stringResource(R.string.ok)) }
                }
            }
        }
    }
}

package com.example.antitheftdemo.permission

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.TextButton
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antitheftdemo.R
@Preview
@Composable
fun PermissionScreen(
    modifier: Modifier = Modifier,
    onContinueClick: () -> Unit = {}
) {
    val context = LocalContext.current
    fun allowed() = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    val activity = remember(context) { context.activity() }
    val preferences = remember { context.getSharedPreferences("permission_ui", Context.MODE_PRIVATE) }
    var requested by remember { mutableStateOf(preferences.getBoolean("notification_requested", false)) }
    var isAllowed by remember { mutableStateOf(allowed()) }
    var denied by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isAllowed = allowed()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        isAllowed = allowed()
        denied = !isAllowed
    }
    fun openSettings() {
        val intent = if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        context.startActivity(intent)
    }
    fun requestAccess() {
        val permanentlyDenied = requested && activity != null && Build.VERSION.SDK_INT >= 33 &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        if (isAllowed || Build.VERSION.SDK_INT < 33 || permanentlyDenied) openSettings()
        else {
            requested = true
            preferences.edit().putBoolean("notification_requested", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        Text(
            text = "Permission",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        )

        HorizontalDivider(Modifier, thickness = 1.dp, color = Color.LightGray.copy(alpha = 0.3f))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painter = painterResource(id = R.drawable.layer1),
                contentDescription = "Permission",
                modifier = Modifier
                    .height(300.dp)
                    .fillMaxWidth()
            )

            Text(
                text = "This app needs permissions below to work properly",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF7F8C8D),
                textAlign = TextAlign.Center,

            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFF4F5F9))
                    .size(50.dp)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Allow access",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1D20)
                )

                Switch(
                    checked = isAllowed,
                    onCheckedChange = { requestAccess() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF0075FF),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFD9D9D9)
                    )
                )
            }
            Text(
                text = if (isAllowed) "Notifications allowed. Monitoring can show its status."
                    else "Allow notifications to activate background monitoring. You can continue setup without granting it.",
                color = Color(0xFF7F8C8D), fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
            if (denied) Text("Permission denied. Retry or enable notifications in App Settings.",
                color = Color(0xFFEF4444), fontSize = 14.sp)
            TextButton(onClick = { openSettings() }) { Text("App Settings") }

        }

        // --- 3. NÚT CONTINUE DƯỚI ĐÁY ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding() // Tránh đè lên thanh điều hướng hệ thống
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Continue",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0075FF), // Chữ màu xanh dương
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        // Logic khi bấm Continue (ví dụ: yêu cầu hệ thống cấp quyền thật)
                        onContinueClick()
                    }
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

    }


}


private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

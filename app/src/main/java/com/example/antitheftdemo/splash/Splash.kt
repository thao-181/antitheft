package com.example.antitheftdemo.splash


import com.example.antitheftdemo.language.userText
import androidx.compose.ui.res.stringResource
import com.example.antitheftdemo.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay


@Preview(showBackground = true, showSystemUi = true)
@Composable
@Suppress("DEPRECATION") // Needed for opaque system bars before enforced edge-to-edge.
fun SplashContent(onFinished: () -> Unit = {}) {

    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.splashActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        val oldStatusColor = window?.statusBarColor
        val oldNavigationColor = window?.navigationBarColor
        val oldContrast = if (Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced else null
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        window?.statusBarColor = android.graphics.Color.WHITE
        window?.navigationBarColor = android.graphics.Color.WHITE
        if (Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced = false
        onDispose {
            oldStatus?.let { controller?.isAppearanceLightStatusBars = it }
            oldNavigation?.let { controller?.isAppearanceLightNavigationBars = it }
            oldStatusColor?.let { window.statusBarColor = it }
            oldNavigationColor?.let { window.navigationBarColor = it }
            if (Build.VERSION.SDK_INT >= 29 && oldContrast != null) window?.isNavigationBarContrastEnforced = oldContrast
        }
    }
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        repeat(100) { step ->
            delay(30)
            progress = (step + 1) / 100f
        }

        onFinished()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally

    ) {
        Spacer(modifier = Modifier.weight(1f))

        Image(
            painterResource(id = R.drawable.logo),
            contentDescription = ""
        )


        Text(
            text = stringResource(R.string.app_title),

            color = Color.Black,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.weight(1f))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        )

        Text(
            text = stringResource(R.string.splash_ad),
            color = Color.Black,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

    }

}

private tailrec fun Context.splashActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.splashActivity()
    else -> null
}

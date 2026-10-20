package com.example.antitheftdemo.pincode

import com.example.antitheftdemo.language.userText
import androidx.compose.ui.res.stringResource
import com.example.antitheftdemo.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun PinCodeScreen(
    viewModel: PinCodeViewModel,
    onPinCreated: () -> Unit = {}
) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.pinActivity()?.window
        val hadSecureFlag = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!hadSecureFlag) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF2196F3), Color(0xFF1976D2))
                )
            )
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Bỏ Arrangement.Center để tự điều chỉnh bằng Spacer Weight cho linh hoạt
        ) {
            // 1. Khoảng trống trên cùng (giữ cho ảnh không đụng trần)
            Spacer(modifier = Modifier.weight(1f))

            Image(
                painter = painterResource(id = R.drawable.pincode),
                contentDescription = stringResource(R.string.pin_illustration),
                modifier = Modifier
                    .height(200.dp)
                    .fillMaxWidth(0.6f),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = userText(viewModel.title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(32.dp))

            // --- KHU VỰC ĐIỀU KHIỂN BÀN PHÍM ---
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = viewModel.currentPin,
                    onValueChange = { viewModel.onPinChange(it) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .size(1.dp)
                )

                PinInputDisplay(viewModel.currentPin)
            }

            Spacer(modifier = Modifier.height(40.dp))

            viewModel.error?.let { Text(userText(it), color = Color.White) }

            Button(
                enabled = !viewModel.busy,
                onClick = {
                    viewModel.handleAction(onPinCreated)
                    keyboardController?.hide()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .width(160.dp)
                    .height(48.dp)
            ) {
                Text(
                    text = userText(viewModel.buttonText),
                    color = Color(0xFF1976D2),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            // 2. CHÍNH TẠI ĐÂY: Khoảng trống phía dưới được nâng lực nặng lên 1.8f
            // Lực đẩy từ dưới lên lớn hơn từ trên xuống (1.8f > 1f) sẽ ép toàn bộ cụm UI nhích lên trên rất đẹp
            Spacer(modifier = Modifier.weight(1.8f))
        }
    }
}

@Composable
fun PinInputDisplay(pin: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            val digit = if (index < pin.length) pin[index].toString() else ""
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(40.dp)
            ) {
                Text(
                    text = digit,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.height(40.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.White)
                )
            }
        }
    }
}

private tailrec fun Context.pinActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.pinActivity()
    else -> null
}

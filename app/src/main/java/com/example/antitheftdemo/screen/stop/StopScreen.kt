package com.example.antitheftdemo.screen.stop

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antitheftdemo.R
@Preview()
@Composable
fun StopScreen(
    modifier: Modifier = Modifier
) {
    StopContent(modifier = modifier)
}

@Composable
fun StopContent(
    modifier: Modifier = Modifier,
    onStopClick: () -> Unit = {} // Thêm hàm callback để truyền sự kiện click ra ngoài
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hình ảnh bây giờ đóng vai trò là một Button
        Image(
            painter = painterResource(R.drawable.stop),
            contentDescription = "Stop Button",
            modifier = Modifier
                .clip(CircleShape) // Tạo giới hạn hình tròn cho hiệu ứng chạm
                .clickable { onStopClick() } // Lắng nghe sự kiện click
                .padding(8.dp) // Thêm chút khoảng trống xung quanh viền
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Tap to stop",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}


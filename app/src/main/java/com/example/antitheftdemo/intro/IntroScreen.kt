package com.example.antitheftdemo.intro

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

@Preview
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IntroScreen(
    modifier: Modifier = Modifier,
    onIntroFinished: () -> Unit = {} // Gọi callback này khi nhấn "Next" ở trang cuối để chuyển sang HomeScreen
) {
    // Khởi tạo trạng thái Pager dựa vào kích thước danh sách listIntro của bạn
    val pagerState = rememberPagerState(pageCount = { listIntro.size })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding() // Tránh đè lên thanh trạng thái hệ thống
    ) {

        // --- 1. PHẦN TRƯỢT NỘI DUNG (SLIDE PAGER) ---
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f) // Chiếm toàn bộ không gian phía trên
        ) { page ->
            val item = listIntro[page]
            IntroPageContent(item = item)
        }

        // --- 2. THANH ĐIỀU HƯỚNG DƯỚI ĐÁY (INDICATOR & BUTTON NEXT) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Chỉ báo dấu chấm (Page Indicator) giống y hệt trong ảnh thiết kế
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(listIntro.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            // Nếu đang chọn thì kéo dài ra thành hình chữ nhật bo góc (16.dp), ngược lại là hình tròn (8.dp)
                            .size(width = if (isSelected) 16.dp else 8.dp, height = 8.dp)
                            .clip(CircleShape)
                            // Đổi màu xanh dương khi active, xám khi inactive
                            .background(if (isSelected) Color(0xFF0075FF) else Color(0xFFD9D9D9))
                    )
                }
            }

            // Nút bấm "Next"
            Text(
                text = "Next",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1D20),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        if (pagerState.currentPage < listIntro.size - 1) {
                            // Nếu chưa tới trang cuối, cuộn mượt sang trang tiếp theo
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            // Nếu đang ở trang cuối cùng, thực hiện chuyển màn hình chính
                            onIntroFinished()
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

// --- 3. COMPOSABLE HIỂN THỊ CHI TIẾT TỪNG TRANG ---
@Composable
fun IntroPageContent(item: IntroItem) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(20.dp))


        Image(painter = painterResource(item.icon),
            contentDescription = null,
            modifier = Modifier.size(400.dp)
        )


        Spacer(modifier = Modifier.height(30.dp))


        Text(text = item.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
            )


        Spacer(modifier = Modifier.height(16.dp))


        Text(text = item.description,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
            )
    }
}
package com.example.antitheftdemo.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.antitheftdemo.detection.DetectionRepository
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- 1. MÀN HÌNH CHÍNH (Chứa Tiêu đề + Nội dung lưới) ---
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {},
    onNotificationPermissionClick: () -> Unit = {},
    onNavigation: (HomeItem) -> Unit = {}
) {
    val snapshot by DetectionRepository.state.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFFF4F5F9)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- THANH TIÊU ĐỀ ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Anti Theft Alarm",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Color.Black,
                        modifier = Modifier.size(28.dp))
                }
            }

            // --- ĐƯỜNG KẺ MÀU TÍM ---
            HorizontalDivider(
                color = Color(0xFF9D65FF),
                thickness = 2.dp,
                modifier = Modifier.fillMaxWidth()
            )

            // --- NỘI DUNG LƯỚI ---
            snapshot.type?.let { type ->
                TextButton(onClick = { listItem.firstOrNull { it.type == type }?.let(onNavigation) }) {
                    Text(if (snapshot.alarming) "Alarm triggered - open protection to enter PIN"
                        else if (snapshot.starting) "Starting protection..."
                        else "Protection active: ${listItem.first { it.type == type }.title}")
                }
            }
            snapshot.message?.let { Text(it, color = Color(0xFFEF4444), modifier = Modifier.padding(horizontal = 20.dp)) }
            if (snapshot.message?.startsWith("Allow notifications") == true) {
                TextButton(onClick = onNotificationPermissionClick) { Text("Review notification permission") }
            }
            HomeContent(
                modifier = Modifier.weight(1f)
            ) { selectedItem ->
                onNavigation(selectedItem)
            }
        }
    }
}

// --- 2. LƯỚI DANH SÁCH (Xử lý logic chọn Item) ---
@Composable
fun HomeContent(modifier: Modifier = Modifier, onClick: (HomeItem) -> Unit = {}) {
    // FIX LỖI: Đổi tên biến state thành 'itemsState' để không bị trùng với 'listItem' toàn cục
    var itemsState by remember {
        mutableStateOf(listItem) // 'listItem' là biến toàn cục chứa danh sách của bạn
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp) // Để 16.dp cho 2 cột cân đối
    ) {
        items(itemsState) { item ->
            ItemContent(
                homeItem = item,
                onClick = { itemSelected ->
                    // Logic update list khi click
                    itemsState = itemsState.map { currentItem ->
                        if (currentItem == itemSelected) {
                            onClick(currentItem) // Trả sự kiện ra HomeScreen
                            currentItem.copyWithSelection(true)
                        } else {
                            currentItem.copyWithSelection(false)
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun ItemContent(homeItem: HomeItem, onClick: (HomeItem) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp) // Chỉnh lại chiều cao cho cân đối với ảnh
            .shadow(
                elevation = if (homeItem.isSelected) 8.dp else 2.dp, // Đổ bóng nhẹ
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                color = if (homeItem.isSelected) Color(0xFF1E88E5) else Color.White
            )
            .clickable { onClick(homeItem) }
            .padding(12.dp) // Padding bên trong thẻ
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hình ảnh (Icon)
            Image(
                modifier = Modifier
                    .size(90.dp)
                    .padding(bottom = 12.dp),
                painter = painterResource(homeItem.icon),
                contentDescription = homeItem.title
            )

            // Tiêu đề chức năng
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = homeItem.title,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    color = if (homeItem.isSelected) Color.White else Color(0xFF2C3E50),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            // Mô tả chức năng
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                text = homeItem.description,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    color = if (homeItem.isSelected) Color(0xFFE3F2FD) else Color(0xFF7F8C8D),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 16.sp
                )
            )
        }
    }
}


@Preview
@Composable
fun HomeScreenPreview() {
    HomeScreen() {}
}



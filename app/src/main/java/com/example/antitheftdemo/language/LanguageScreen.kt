package com.example.antitheftdemo.language

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LanguageScreen(
    modifier: Modifier = Modifier,
    initialLanguage: String? = null,
    onSaveClick: (LanguageItem) -> Unit = {}
) {
    var selectedLanguage by remember { mutableStateOf(listItem.find { it.title == initialLanguage }) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- 1. THANH TIÊU ĐỀ ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Language",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                // Nút dấu tick lưu (Chỉ cho bấm nếu đã chọn ít nhất 1 ngôn ngữ)
                IconButton(
                    onClick = { selectedLanguage?.let { onSaveClick(it) } },
                    enabled = selectedLanguage != null, // Vô hiệu hóa nếu chưa chọn gì
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save Language",
                        // Nếu chưa chọn thì mờ đi, chọn rồi thì hiện màu xanh dương
                        tint = if (selectedLanguage != null) Color(0xFF0075FF) else Color.Gray.copy(
                            alpha = 0.5f
                        )
                    )
                }
            }

            HorizontalDivider(
                Modifier,
                thickness = 1.dp,
                color = Color.LightGray.copy(alpha = 0.3f)
            )

            // --- 2. DANH SÁCH NGÔN NGỮ ---
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(listItem) { item ->
                    // Kiểm tra xem item hiện tại có trùng với item được click không
                    val isSelected = (item == selectedLanguage)


                    LanguageItemView(
                        item = item,
                        isSelected = isSelected,
                        onClick = {
                            selectedLanguage = item // Khi user ấn vào thì nó mới hiện màu xanh
                        }
                    )
                }
            }
        }
    }
}

// --- 3. GIAO DIỆN TỪNG Ô NGÔN NGỮ (Giữ nguyên logic màu sắc) ---
@Composable
fun LanguageItemView(
    item: LanguageItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color(0xFFE3F2FD) else Color(0xFFF4F5F9)
    val borderColor = if (isSelected) Color(0xFF0075FF) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = item.icon),
            contentDescription = item.title,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = item.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )
    }
}

@Preview
@Composable
fun LanguagePreview() {
    LanguageScreen { }
}
package com.example.antitheftdemo.screen.home

import com.example.antitheftdemo.R

data class HomeItem(
    val type: HomeItemType? = null,
    val title: String,
    val description: String,
    val icon: Int,
    val isSelected: Boolean = false
) {
    fun copyWithSelection(isSelected: Boolean): HomeItem {
        return this.copy(isSelected = isSelected)
    }

}

var listItem = listOf(
    HomeItem(
        type = HomeItemType.PROXIMITY_DETECTION,
        title = "Proximily Detection",
        description = "Detected when taken out of the bag",
        icon = R.drawable.proximily
    ),
    HomeItem(
        type = HomeItemType.MOTION_DETECTION,
        title = "Motion detection",
        description = "Detect when the device is moved",
        icon = R.drawable.motion
    ),
    HomeItem(
        type = HomeItemType.CHARGER_DETECTION,
        title = "Changer detection",
        description = "Intruder alert detected when taken out of the bag",
        icon = R.drawable.changer
    ),
    HomeItem(
        type = HomeItemType.HANDSFREE_DETECTION,
        title = "Handsfree detection",
        description = "Detect when phone is plugged in handsfree",
        icon = R.drawable.handsfree
    ),
    HomeItem(
        type = HomeItemType.WIFI_DETECTION,
        title = "wi-fi detection",
        description = "Detect when wifi status is connected",
        icon = R.drawable.wifi
    ),
    HomeItem(
        type = HomeItemType.BATTERY_FULL_DETECTION,
        title = "Battery full detection",
        description = "Detect when the battery is fully charged",
        icon = R.drawable.battery
    )
)

enum class HomeItemType {
    PROXIMITY_DETECTION,
    MOTION_DETECTION,
    CHARGER_DETECTION,
    HANDSFREE_DETECTION,
    WIFI_DETECTION,
    BATTERY_FULL_DETECTION
}
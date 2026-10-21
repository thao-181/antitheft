package com.example.antitheftdemo.home

import com.example.antitheftdemo.R

data class HomeItem(
    val type: HomeItemType? = null,
    val title: String,
    val description: String,
    val icon: Int,
    val isSelected: Boolean = false
)

val listItem = listOf(
    HomeItem(
        type = HomeItemType.PROXIMITY_DETECTION,
        title = "@string/proximity",
        description = "@string/proximity_description",
        icon = R.drawable.proximily
    ),
    HomeItem(
        type = HomeItemType.MOTION_DETECTION,
        title = "@string/motion",
        description = "@string/motion_description",
        icon = R.drawable.motion
    ),
    HomeItem(
        type = HomeItemType.CHARGER_DETECTION,
        title = "@string/charger",
        description = "@string/charger_description",
        icon = R.drawable.changer
    ),
    HomeItem(
        type = HomeItemType.HANDSFREE_DETECTION,
        title = "@string/handsfree",
        description = "@string/handsfree_description",
        icon = R.drawable.handsfree
    ),
    HomeItem(
        type = HomeItemType.WIFI_DETECTION,
        title = "@string/wifi",
        description = "@string/wifi_description",
        icon = R.drawable.wifi
    ),
    HomeItem(
        type = HomeItemType.BATTERY_FULL_DETECTION,
        title = "@string/battery_full",
        description = "@string/battery_description",
        icon = R.drawable.battery
    )
)

enum class HomeItemType(val titleReference: String) {
    PROXIMITY_DETECTION("@string/proximity"),
    MOTION_DETECTION("@string/motion"),
    CHARGER_DETECTION("@string/charger"),
    HANDSFREE_DETECTION("@string/handsfree"),
    WIFI_DETECTION("@string/wifi"),
    BATTERY_FULL_DETECTION("@string/battery_full")
}

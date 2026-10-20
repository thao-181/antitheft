package com.example.antitheftdemo.intro

import com.example.antitheftdemo.R

data class IntroItem(
    val title: String,
    val description: String,
    val icon: Int
)

val listIntro = listOf(
    IntroItem(
        title = "@string/intro_theft",
        description = "@string/intro_theft_description",
        icon = R.drawable.hand_holding
    ),
    IntroItem(
        title = "@string/intro_battery",
        description = "@string/intro_battery_description",
        icon = R.drawable.ecoo
    ),
    IntroItem(
        title = "@string/wifi",
        description = "@string/intro_wifi_description",
        icon = R.drawable.wifi
    )
)


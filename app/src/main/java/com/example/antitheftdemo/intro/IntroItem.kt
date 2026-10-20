package com.example.antitheftdemo.intro

import com.example.antitheftdemo.R

data class IntroItem(
    val title: String,
    val description: String,
    val icon: Int
)

val listIntro = listOf(
    IntroItem(
        title = "Anti-pickpocket detection",
        description = "Theft detection is optimized to protect your phone",
        icon = R.drawable.hand_holding
    ),
    IntroItem(
        title = "Full battery detection",
        description = "When the battery is fully charged, it will make a sound to let you know the battery is fully charged",
        icon = R.drawable.ecoo
    ),
    IntroItem(
        title = "Wi-Fi Detection",
        description = "When the Wi-Fi connection is changed, it will alert you with a wonderful alarm sound",
        icon = R.drawable.wifi
    )
)


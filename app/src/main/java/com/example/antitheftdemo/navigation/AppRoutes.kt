package com.example.antitheftdemo.navigation

import com.example.antitheftdemo.home.HomeItemType

internal object AppRoutes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val LANGUAGE = "language"
    const val INTRO = "intro"
    const val PERMISSION = "permission"
    const val PIN_SETUP = "pin_setup"
    const val FINISH_SETUP = "finish_setup"
    const val SETTINGS = "settings"
    const val SETTINGS_PIN = "settings_pin"
    const val SETTINGS_LANGUAGE = "settings_language"
    const val SETTINGS_PERMISSION = "settings_permission"
    const val DETECTION = "detection/{type}"
    const val PIN_VERIFICATION = "pin_verification/{session}"

    fun detection(type: HomeItemType) = "detection/${type.name}"
    // Session IDs identify the running service; authorization proofs never enter a route.
    fun verification(session: String) = "pin_verification/$session"
    val notificationDestinations = setOf(HOME, DETECTION, PIN_VERIFICATION, SETTINGS,
        SETTINGS_PIN, SETTINGS_LANGUAGE, SETTINGS_PERMISSION)
}

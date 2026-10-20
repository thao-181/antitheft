package com.example.antitheftdemo.settings

import com.example.antitheftdemo.R

enum class AlarmTone(val id: Int, val label: String, val resource: Int) {
    TONE_1(1, "Alarm tone 1", R.raw.alarm_tone_1),
    TONE_2(2, "Alarm tone 2", R.raw.alarm_tone_2),
    TONE_3(3, "Alarm tone 3", R.raw.alarm_tone_3),
    TONE_4(4, "Alarm tone 4", R.raw.alarm_tone_4);
    companion object { fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: TONE_1 }
}
data class AlarmSettings(val tone: AlarmTone = AlarmTone.TONE_1, val flash: Boolean = false, val vibrate: Boolean = true)

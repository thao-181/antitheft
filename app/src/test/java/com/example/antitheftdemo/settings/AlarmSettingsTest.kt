package com.example.antitheftdemo.settings

import org.junit.Assert.*
import org.junit.Test

class AlarmSettingsTest {
    @Test fun defaultsPreserveVibrationAndDoNotEnableFlash() {
        assertEquals(AlarmTone.TONE_1, AlarmSettings().tone)
        assertTrue(AlarmSettings().vibrate)
        assertFalse(AlarmSettings().flash)
    }
    @Test fun everyToneHasItsOwnResourceAndInvalidValueFallsBack() {
        assertEquals(4, AlarmTone.entries.map { it.resource }.toSet().size)
        assertEquals(AlarmTone.TONE_2, AlarmTone.fromId(2))
        assertEquals(AlarmTone.TONE_1, AlarmTone.fromId(-1))
    }
    @Test fun dialogDraftCanBeDiscardedWithoutChangingSavedSettings() {
        val saved = AlarmSettings(tone = AlarmTone.TONE_2, flash = true, vibrate = false)
        val draft = saved.copy(tone = AlarmTone.TONE_4)
        assertEquals(AlarmTone.TONE_2, saved.tone)
        assertEquals(AlarmTone.TONE_4, draft.tone)
        assertEquals(saved.copy(tone = AlarmTone.TONE_4), draft)
    }
}

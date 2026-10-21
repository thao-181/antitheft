package com.example.antitheftdemo.alarm

import com.example.antitheftdemo.settings.*
import org.junit.Assert.*
import org.junit.Test

class AlarmEffectsTest {
    @Test fun unavailableVibratorDoesNotBlockFlashOrItsCleanup() {
        var flashing = false
        val effects = AlarmEffectsController(object : AlarmEffectOutput {
            override fun vibration(enabled: Boolean) { error("Vibrator unavailable") }
            override fun flash(enabled: Boolean) { flashing = enabled }
        })
        effects.start(AlarmSettings(flash = true, vibrate = true))
        assertTrue(flashing)
        effects.stop()
        effects.stop()
        assertFalse(flashing)
    }
    private class Output : AlarmEffectOutput {
        var vibrating = false
        var flashing = false
        var starts = 0
        override fun vibration(enabled: Boolean) { if (enabled) starts++; vibrating = enabled }
        override fun flash(enabled: Boolean) { flashing = enabled }
    }
    @Test fun switchesDoNotActivateEffectsBeforeAnAlarmAndStopReleasesBoth() {
        val output = Output()
        val effects = AlarmEffectsController(output)
        val configured = AlarmSettings(AlarmTone.TONE_2, flash = true, vibrate = false)
        effects.update(configured)
        assertFalse(output.flashing)
        assertFalse(output.vibrating)
        effects.start(configured)
        assertTrue(output.flashing)
        assertFalse(output.vibrating)
        effects.update(configured.copy(vibrate = true, flash = false))
        assertFalse(output.flashing)
        assertTrue(output.vibrating)
        effects.update(configured.copy(vibrate = true, flash = false))
        assertEquals(1, output.starts)
        effects.stop()
        assertFalse(output.vibrating)
        assertFalse(output.flashing)
        effects.update(configured)
        assertFalse(output.flashing)
    }
    @Test fun defaultVibrationStartsOnlyOnAlarmAndDoesNotOverlap() {
        val output = Output()
        val effects = AlarmEffectsController(output)
        effects.update(AlarmSettings(vibrate = true, flash = true))
        assertEquals(0, output.starts)
        effects.start(AlarmSettings(vibrate = true, flash = true))
        assertTrue(output.vibrating)
        assertTrue(output.flashing)
        effects.update(AlarmSettings(vibrate = true, flash = true))
        assertEquals(1, output.starts)
        effects.stop()
        assertFalse(output.vibrating)
        assertFalse(output.flashing)
    }
    @Test fun volumeAndFocusRestrictionsAreReportedWithoutPretendingToPlay() {
        assertEquals(AlarmStatus.SUSPENDED, AlarmAudibility.status(true, true, 0, false))
        assertEquals(AlarmStatus.SUSPENDED, AlarmAudibility.status(true, false, 5, false))
        assertEquals(AlarmStatus.SUSPENDED, AlarmAudibility.status(true, true, 5, true))
        assertEquals(AlarmStatus.PREPARING, AlarmAudibility.status(false, true, 5, false))
        assertEquals(AlarmStatus.PLAYING, AlarmAudibility.status(true, true, 5, false))
    }
}

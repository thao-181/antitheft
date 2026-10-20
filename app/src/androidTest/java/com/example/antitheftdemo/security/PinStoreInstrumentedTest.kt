package com.example.antitheftdemo.security

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.antitheftdemo.data.AppPreferences
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated preference namespaces: never clear or replace the user's application PIN. */
@RunWith(AndroidJUnit4::class)
class PinStoreInstrumentedTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "instrumentation_${UUID.randomUUID()}"
    private val files = mutableSetOf<String>()
    private val context = object : ContextWrapper(target) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolated = "${prefix}_$name"
            files += isolated
            return target.getSharedPreferences(isolated, mode)
        }
    }
    @After fun cleanup() { files.forEach { target.deleteSharedPreferences(it) } }
    @Test fun encryptedPinSurvivesRepositoryRecreationAndCannotBeOverwritten() = runBlocking {
        val store = PinStore(context)
        assertFalse(store.hasPin)
        store.create("2468")
        val recreated = PinStore(context)
        assertTrue(recreated.hasPin)
        assertEquals(PinResult.Verified, recreated.verify("2468"))
        assertEquals(PinResult.Incorrect, recreated.verify("2469"))
        val prefs = context.getSharedPreferences("pin_security", Context.MODE_PRIVATE)
        assertFalse(prefs.all.values.any { it == "2468" })
        assertNotNull(prefs.getString("iv", null))
        assertTrue(android.util.Base64.decode(prefs.getString("verifier", ""), android.util.Base64.NO_WRAP).size > 32)
        try { recreated.create("1357"); fail("Cannot overwrite an existing PIN") }
        catch (_: IllegalStateException) { }
        assertEquals(PinResult.Verified, recreated.verify("2468"))
    }
    @Test fun lockoutPersistsAndRejectsEvenCorrectPinUntilDelayExpires() = runBlocking {
        val store = PinStore(context)
        store.create("2468")
        repeat(4) { assertEquals(PinResult.Incorrect, store.verify("0000")) }
        assertTrue(store.verify("0000") is PinResult.Locked)
        assertTrue(PinStore(context).verify("2468") is PinResult.Locked)
    }
    @Test fun incompleteOrNonNumericPinCannotBeStored() = runBlocking {
        val store = PinStore(context)
        for (invalid in listOf("", "123", "12345", "12x4")) {
            try { store.create(invalid); fail("Invalid PIN accepted") }
            catch (_: IllegalArgumentException) { }
        }
        assertFalse(store.hasPin)
    }
    @Test fun onboardingProgressPersistsAcrossNewInstances() = runBlocking {
        val preferences = AppPreferences(context)
        assertNull(preferences.language)
        assertFalse(preferences.onboardingComplete)
        preferences.saveLanguage("English")
        preferences.finishIntro()
        assertEquals("en", AppPreferences(context).language)
        assertTrue(AppPreferences(context).introComplete)
        assertFalse(AppPreferences(context).permissionsReviewed)
        preferences.finishPermissions()
        preferences.finishOnboarding()
        assertTrue(AppPreferences(context).onboardingComplete)
    }
    @Test fun pinChangeRequiresOldPinAuthorizationAndDoesNotResetOnboarding() = runBlocking {
        val store = PinStore(context)
        store.create("2468")
        AppPreferences(context).finishOnboarding()
        assertNull(store.authorizeChange("0000").proof)
        try { store.replace("1357", "not-authorized"); fail("Unauthenticated change accepted") }
        catch (_: IllegalStateException) { }
        assertEquals(PinResult.Verified, store.verify("2468"))
        val check = store.authorizeChange("2468")
        assertEquals(PinResult.Verified, check.result)
        store.replace("1357", checkNotNull(check.proof))
        assertEquals(PinResult.Incorrect, store.verify("2468"))
        assertEquals(PinResult.Verified, PinStore(context).verify("1357"))
        try { store.replace("9999", check.proof!!); fail("Reused change proof accepted") }
        catch (_: IllegalStateException) { }
        assertTrue(AppPreferences(context).onboardingComplete)
    }
    @Test fun alarmSettingsPersistWithoutChangingPinOrSetup() = runBlocking {
        val repository = com.example.antitheftdemo.settings.SettingsRepository(context)
        val store = PinStore(context)
        store.create("2468")
        AppPreferences(context).saveLanguage("English")
        repository.setTone(com.example.antitheftdemo.settings.AlarmTone.TONE_2)
        repository.setFlash(true)
        repository.setVibrate(false)
        val saved = com.example.antitheftdemo.settings.SettingsRepository(context).current
        assertEquals(com.example.antitheftdemo.settings.AlarmTone.TONE_2, saved.tone)
        assertTrue(saved.flash)
        assertFalse(saved.vibrate)
        assertEquals(PinResult.Verified, store.verify("2468"))
        assertEquals("en", AppPreferences(context).language)
    }
    @Test fun languageCodesPersistWithoutResettingPinOrOnboarding() = runBlocking {
        val pin = PinStore(context)
        pin.create("2468")
        AppPreferences(context).finishOnboarding()
        for (code in listOf("en", "es", "fr", "hi", "de", "id", "pt")) {
            AppPreferences(context).saveLanguage(code)
            assertEquals(code, AppPreferences(context).language)
            assertTrue(AppPreferences(context).onboardingComplete)
        }
        assertEquals(PinResult.Verified, pin.verify("2468"))
    }
    @Test fun translatedResourcesCoverHomeSettingsPinAndDetectionInAllLocales() {
        val ids = listOf(com.example.antitheftdemo.R.string.settings, com.example.antitheftdemo.R.string.pin_verify,
            com.example.antitheftdemo.R.string.app_title, com.example.antitheftdemo.R.string.proximity,
            com.example.antitheftdemo.R.string.notifications_needed, com.example.antitheftdemo.R.string.audio_unavailable)
        for (code in listOf("hi", "es", "fr", "de", "id", "pt")) {
            val configuration = android.content.res.Configuration(target.resources.configuration).apply {
                setLocale(java.util.Locale.forLanguageTag(code))
            }
            val localized = target.createConfigurationContext(configuration)
            for (id in ids) {
                assertTrue(localized.getString(id).isNotBlank())
                assertNotEquals(target.createConfigurationContext(android.content.res.Configuration(configuration).apply {
                    setLocale(java.util.Locale.ENGLISH)
                }).getString(id), localized.getString(id))
            }
        }
    }
    @Test fun failedSettingsCommitRollsBackObservedValue() = runBlocking {
        var rejectNext = true
        val failingContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val real = context.getSharedPreferences(name, mode)
                return object : SharedPreferences by real {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = real.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun commit(): Boolean {
                                val committed = editor.commit()
                                if (rejectNext) { rejectNext = false; return false }
                                return committed
                            }
                        }
                    }
                }
            }
        }
        val repository = com.example.antitheftdemo.settings.SettingsRepository(failingContext)
        try { repository.setFlash(true); fail("Failed persistence must be reported") }
        catch (_: IllegalStateException) { }
        assertFalse(repository.current.flash)
        assertFalse(com.example.antitheftdemo.settings.SettingsRepository(context).current.flash)
    }
    @Test fun allFourAlarmResourcesAreDistinctAndDecodeAsAudio() {
        val hashes = mutableSetOf<String>()
        com.example.antitheftdemo.settings.AlarmTone.entries.forEach { tone ->
            val bytes = target.resources.openRawResource(tone.resource).use { it.readBytes() }
            hashes += java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val player = android.media.MediaPlayer()
            try {
                target.resources.openRawResourceFd(tone.resource).use { resource ->
                    player.setDataSource(resource.fileDescriptor, resource.startOffset, resource.length)
                }
                player.prepare()
                assertTrue(player.duration >= 2000)
            } finally { player.release() }
        }
        assertEquals(4, hashes.size)
    }
}

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
        assertEquals("English", AppPreferences(context).language)
        assertTrue(AppPreferences(context).introComplete)
        assertFalse(AppPreferences(context).permissionsReviewed)
        preferences.finishPermissions()
        preferences.finishOnboarding()
        assertTrue(AppPreferences(context).onboardingComplete)
    }
}

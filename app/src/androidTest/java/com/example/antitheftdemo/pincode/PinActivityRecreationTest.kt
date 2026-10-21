package com.example.antitheftdemo.pincode

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.antitheftdemo.R
import com.example.antitheftdemo.security.PinResult
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PinActivityRecreationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun actualActivityRecreationInvalidatesVerificationInRetainedViewModel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val access = DeferredPinAccess()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PinCodeViewModel(target.applicationContext as Application, access) as T
        }
        lateinit var model: PinCodeViewModel
        var successes = 0
        compose.runOnUiThread {
            model = ViewModelProvider(compose.activity, factory)[PinCodeViewModel::class.java]
            model.setVerifyMode()
        }
        compose.setContent { PinCodeScreen(model) { successes++ } }
        compose.onNodeWithTag("pin_input").performTextReplacement("2468")
        compose.onNodeWithText(target.getString(R.string.check)).assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { action -> assertTrue(action()) }
        compose.onNodeWithTag("pin_verifying_progress").assertExists()
        compose.activityRule.scenario.recreate()
        instrumentation.runOnMainSync {
            assertSame(model, ViewModelProvider(compose.activity, factory)[PinCodeViewModel::class.java])
            access.result.complete(PinResult.Verified)
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertFalse(model.busy)
            assertFalse(model.consumeCompletion())
            assertEquals(0, successes)
        }
    }
}

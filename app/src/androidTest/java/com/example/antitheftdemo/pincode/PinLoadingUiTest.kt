package com.example.antitheftdemo.pincode

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.antitheftdemo.R
import com.example.antitheftdemo.security.PinResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PinLoadingUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun checkShowsProgressImmediatelyAndRecreatedCompositionRejectsLateResult() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val access = DeferredPinAccess()
        lateinit var model: PinCodeViewModel
        var successes = 0
        compose.runOnUiThread { model = PinCodeViewModel(target.applicationContext as Application, access).apply { setVerifyMode() } }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { PinCodeScreen(model) { successes++ } }
        compose.onNodeWithTag("pin_input").performTextReplacement("2468")
        compose.onNodeWithText(target.getString(R.string.check)).performClick().assertIsNotEnabled()
        compose.onNodeWithTag("pin_verifying_progress").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { access.result.complete(PinResult.Verified) }
        compose.waitUntil { !model.busy }
        compose.onNodeWithTag("pin_verifying_progress").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, successes); assertEquals(1, access.verifications) }
    }
}

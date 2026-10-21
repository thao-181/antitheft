package com.example.antitheftdemo.language

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.antitheftdemo.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LanguageSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun uncommittedLanguageSelectionSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        var selected: String? = null
        restoration.setContent { LanguageScreen(initialLanguage = "en") { selected = it.code } }
        compose.onNodeWithText("Español").performClick()
        restoration.emulateSavedInstanceStateRestore()
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithContentDescription(target.getString(R.string.save_language)).performClick()
        compose.runOnIdle { assertEquals("es", selected) }
    }
}

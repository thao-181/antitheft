package com.example.antitheftdemo.navigation

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.antitheftdemo.R
import com.example.antitheftdemo.detection.DetectionRepository
import com.example.antitheftdemo.home.HomeItemType.PROXIMITY_DETECTION
import com.example.antitheftdemo.home.HomeScreen
import com.example.antitheftdemo.pincode.PinCodeViewModel
import com.example.antitheftdemo.security.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Real NavHost and route ViewModels; fake PIN verifier/runtime isolate all user credentials/resources. */
class ProtectionNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = "navigation_test_${UUID.randomUUID()}"
    private val isolated = object : ContextWrapper(target) {
        override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences(file, mode)
    }
    private var session: String? = null
    private var stops = 0
    private lateinit var nav: NavHostController
    private val pin = object : PinAccess {
        override val hasPin = true
        override suspend fun verify(pin: String) = if (pin == "2468") PinResult.Verified else PinResult.Incorrect
        override suspend fun create(pin: String) { error("Cannot create user PIN from navigation test") }
        override suspend fun replace(pin: String, proof: String) { error("Cannot change user PIN from navigation test") }
        override suspend fun authorizeChange(pin: String): PinChangeCheck = error("Not used")
        override fun revokeChange(proof: String) {}
    }
    private fun launch() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertNull("Do not replace an existing monitoring session", DetectionRepository.state.value.session)
            val id = DetectionRepository.controller.request(PROXIMITY_DETECTION)!!
            session = id
            assertTrue(DetectionRepository.attach(id, PROXIMITY_DETECTION) { message ->
                stops++
                DetectionRepository.ended(isolated, id, message)
            })
            DetectionRepository.controller.started(id)
            DetectionRepository.controller.alarm(id)
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PinCodeViewModel(target.applicationContext as Application, pin) as T
        }
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, AppRoutes.HOME) {
                composable(AppRoutes.HOME) {
                    HomeScreen(onNavigation = { nav.navigate(AppRoutes.detection(requireNotNull(it.type))) })
                }
                protectionRoutes(nav, factory)
            }
        }
        compose.onNodeWithTag("home_PROXIMITY_DETECTION").performClick()
    }
    private fun submit(value: String) {
        compose.onNodeWithTag("pin_input").performTextReplacement(value)
        compose.onNodeWithText(target.getString(R.string.check)).performClick()
        // Verification completion is delivered at RESUMED, after the Navigation transition.
        // Compose idleness alone does not mean that this Android lifecycle event was delivered.
        val expected = target.getString(if (value == "2468") R.string.stop_protection else R.string.pin_incorrect)
        try {
            compose.waitUntil(5_000) {
                if (value == "2468") compose.onAllNodesWithContentDescription(expected).fetchSemanticsNodes().isNotEmpty()
                else compose.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: AssertionError) {
            throw AssertionError("PIN result not presented; route=${nav.currentDestination?.route}, " +
                "sessionRunning=${DetectionRepository.state.value.session == session}", error)
        }
    }
    @After fun cleanup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            session?.let { DetectionRepository.ended(isolated, it, null) }
        }
        target.deleteSharedPreferences(file)
    }

    @Test fun wrongPinKeepsMonitoringCorrectPinShowsStopThenHomeAndReopenIsActive() {
        launch()
        compose.onNodeWithTag("detection_circle").performClick()
        submit("0000")
        compose.onNodeWithText(target.getString(R.string.pin_incorrect)).assertExists()
        compose.runOnIdle {
            assertEquals(session, DetectionRepository.state.value.session)
            assertTrue(DetectionRepository.state.value.alarming)
        }
        submit("2468")
        compose.onNodeWithContentDescription(target.getString(R.string.stop_protection)).assertExists()
        compose.runOnIdle {
            assertEquals(0, stops)
            assertTrue(DetectionRepository.state.value.alarming)
            assertFalse(nav.currentBackStackEntry!!.savedStateHandle.contains("pin_verified"))
        }
        compose.onNodeWithTag("detection_circle").performClick()
        compose.onNodeWithTag("home_PROXIMITY_DETECTION").assertIsNotSelected().performClick()
        compose.onNodeWithContentDescription(target.getString(R.string.activate_protection)).assertExists()
        compose.runOnIdle { assertEquals(1, stops); assertNull(DetectionRepository.state.value.session) }
    }

    @Test fun backFromPinAndStopRevokesAuthorizationWithoutStoppingMonitoring() {
        launch()
        compose.onNodeWithText(target.getString(R.string.deactivate)).performClick()
        Espresso.pressBack()
        compose.onNodeWithContentDescription(target.getString(R.string.secured)).assertExists()
        compose.onNodeWithText(target.getString(R.string.deactivate)).performClick()
        submit("2468")
        compose.onNodeWithContentDescription(target.getString(R.string.stop_protection)).assertExists()
        Espresso.pressBack()
        compose.onNodeWithContentDescription(target.getString(R.string.secured)).assertExists()
        compose.runOnIdle {
            assertEquals(0, stops)
            assertTrue(DetectionRepository.state.value.alarming)
        }
    }

    @Test fun repeatedNotificationDoesNotStackPinScreensAndOldSessionDoesNothing() {
        launch()
        compose.runOnIdle {
            nav.openNotificationProtection("stale-session")
            assertEquals(AppRoutes.DETECTION, nav.currentDestination?.route)
            nav.openNotificationProtection(session!!)
            nav.openNotificationProtection(session!!)
            assertEquals(AppRoutes.PIN_VERIFICATION, nav.currentDestination?.route)
        }
        compose.onNodeWithTag("pin_input").assertExists()
        Espresso.pressBack()
        compose.onNodeWithContentDescription(target.getString(R.string.secured)).assertExists()
        compose.runOnIdle { assertEquals(AppRoutes.DETECTION, nav.currentDestination?.route) }
        Espresso.pressBack()
        compose.onNodeWithTag("home_PROXIMITY_DETECTION").assertIsSelected()
        compose.runOnIdle { assertEquals(AppRoutes.HOME, nav.currentDestination?.route); assertEquals(0, stops) }
    }

    @Test fun interruptedSessionWhilePinIsVisibleReturnsHomeWithoutAuthorizingAnything() {
        launch()
        compose.onNodeWithTag("detection_circle").performClick()
        compose.onNodeWithTag("pin_input").assertExists()
        compose.runOnIdle { DetectionRepository.ended(isolated, session!!, "@string/service_stopped") }
        compose.onNodeWithTag("home_PROXIMITY_DETECTION").assertIsNotSelected()
        compose.onNodeWithTag("pin_input").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, stops); assertEquals(AppRoutes.HOME, nav.currentDestination?.route) }
    }

    @Test fun rapidRepeatedPinStopFlowsNeverLoseAuthorizationOrReuseIt() {
        launch()
        repeat(25) { index ->
            if (index > 0) {
                compose.runOnIdle {
                    val id = DetectionRepository.controller.request(PROXIMITY_DETECTION)!!
                    session = id
                    assertTrue(DetectionRepository.attach(id, PROXIMITY_DETECTION) { message ->
                        stops++
                        DetectionRepository.ended(isolated, id, message)
                    })
                    DetectionRepository.controller.started(id)
                }
                compose.onNodeWithTag("home_PROXIMITY_DETECTION").performClick()
            }
            compose.onNodeWithTag("detection_circle").performClick()
            submit("2468")
            compose.onNodeWithTag("detection_circle").performClick()
            compose.onNodeWithTag("home_PROXIMITY_DETECTION").assertIsNotSelected()
            compose.runOnIdle { assertEquals(index + 1, stops); assertNull(DetectionRepository.state.value.session) }
        }
    }
}

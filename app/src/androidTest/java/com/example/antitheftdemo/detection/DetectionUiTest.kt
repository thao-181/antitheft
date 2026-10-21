package com.example.antitheftdemo.detection

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.antitheftdemo.R
import com.example.antitheftdemo.home.HomeContent
import com.example.antitheftdemo.home.HomeItemType.PROXIMITY_DETECTION
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Uses a local controller, never arms the user's service or accesses their PIN. */
class DetectionUiTest {
    @get:Rule val compose = createComposeRule()
    private val controller = DetectionController()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun securedCircleAndButtonRequestPinWithoutActivatingAgain() {
        val session = controller.request(PROXIMITY_DETECTION)!!
        controller.started(session)
        var activations = 0
        var verifications = 0
        compose.setContent {
            val snapshot by controller.state.collectAsState()
            DetectionContent("@string/proximity", PROXIMITY_DETECTION, snapshot, {},
                onDeactivate = { verifications++ }, onConfirmStop = {}, onActivate = { activations++ })
        }
        compose.onNodeWithTag("detection_circle").performClick()
        compose.onNodeWithText(context.getString(R.string.deactivate)).performClick()
        compose.runOnIdle {
            assertEquals(2, verifications)
            assertEquals(0, activations)
            assertEquals(session, controller.state.value.session)
        }
    }

    @Test fun pinAuthorizationKeepsAlarmUntilStopAndReopenedModeIsActive() {
        val session = controller.request(PROXIMITY_DETECTION)!!
        controller.started(session)
        controller.alarm(session)
        val proof = controller.issueProof(session)!! // Simulates successful PIN verification only.
        val authorized = mutableStateOf<String?>(session)
        val home = mutableStateOf(false)
        compose.setContent {
            val snapshot by controller.state.collectAsState()
            if (home.value) HomeContent(securedType = snapshot.type) { home.value = false }
            else DetectionContent("@string/proximity", PROXIMITY_DETECTION, snapshot, {}, authorized.value,
                onDeactivate = {}, onConfirmStop = {
                    assertEquals(session, controller.consumeProof(proof))
                    controller.ended(session)
                    authorized.value = null
                    home.value = true
                }, onActivate = {})
        }
        compose.runOnIdle { assertTrue(controller.state.value.alarming) }
        compose.onNodeWithContentDescription(context.getString(R.string.stop_protection)).assertExists()
        compose.onNodeWithTag("detection_circle").performClick()
        compose.onNodeWithTag("home_PROXIMITY_DETECTION").assertIsNotSelected().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.activate_protection)).assertExists()
        compose.runOnIdle { assertFalse(controller.state.value.alarming) }
    }

    @Test fun homeSelectionOnlyFollowsSecuredStateAndClearsAfterStop() {
        val session = controller.request(PROXIMITY_DETECTION)!!
        compose.setContent {
            val snapshot by controller.state.collectAsState()
            HomeContent(securedType = snapshot.type?.takeIf { snapshot.states[it] == DetectionState.SECURED })
        }
        compose.onNodeWithTag("home_PROXIMITY_DETECTION").assertIsNotSelected().performClick()
        compose.onNodeWithTag("home_PROXIMITY_DETECTION").assertIsNotSelected()
        compose.runOnIdle { controller.started(session) }
        compose.onNodeWithTag("home_PROXIMITY_DETECTION").assertIsSelected()
        compose.onNodeWithTag("home_MOTION_DETECTION").assertIsNotSelected()
        compose.runOnIdle { controller.ended(session) }
        compose.onNodeWithTag("home_PROXIMITY_DETECTION").assertIsNotSelected()
    }

    @Test fun activeCircleWaitsForBackendAcknowledgement() {
        var requests = 0
        compose.setContent {
            val snapshot by controller.state.collectAsState()
            DetectionContent("@string/proximity", PROXIMITY_DETECTION, snapshot, {},
                onDeactivate = {}, onConfirmStop = {}, onActivate = {
                    requests++
                    controller.request(PROXIMITY_DETECTION)
                })
        }
        compose.onNodeWithTag("detection_circle").performClick().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.activate_protection)).assertExists()
        compose.runOnIdle {
            assertEquals(1, requests)
            controller.started(controller.state.value.session!!)
        }
        compose.onNodeWithContentDescription(context.getString(R.string.secured)).assertExists()
    }
}

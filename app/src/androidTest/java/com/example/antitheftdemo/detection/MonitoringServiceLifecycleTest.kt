package com.example.antitheftdemo.detection

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.antitheftdemo.detection.service.MonitoringService
import com.example.antitheftdemo.home.HomeItemType.BATTERY_FULL_DETECTION
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Exercises real battery receiver + FGS lifecycle; never reads/writes PIN, setup or alarm settings. */
class MonitoringServiceLifecycleTest {
    @get:Rule val activityRule = ActivityScenarioRule(ComponentActivity::class.java)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync { action() }
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Service lifecycle transition timed out", condition())
    }
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val runtime = target.getSharedPreferences("monitor_runtime", Context.MODE_PRIVATE)
    private var previousEvidence: String? = null
    private var session: String? = null

    @Before fun preserveEvidence() {
        onMain { assertNull("Do not replace an existing session", DetectionRepository.state.value.session) }
        previousEvidence = runtime.getString("active_type", null)
    }
    private fun start(): String {
        lateinit var id: String
        onMain {
            id = DetectionRepository.controller.request(BATTERY_FULL_DETECTION)!!
            session = id
            startIntent(id)
        }
        waitUntil { DetectionRepository.state.value.session == id && !DetectionRepository.state.value.starting }
        onMain { assertEquals(DetectionState.SECURED, DetectionRepository.state.value.states[BATTERY_FULL_DETECTION]) }
        return id
    }
    private fun startIntent(id: String) {
        ContextCompat.startForegroundService(target, Intent(target, MonitoringService::class.java)
            .putExtra("session", id).putExtra("type", BATTERY_FULL_DETECTION.name))
    }
    @After fun stopOnlyOwnedSessionAndRestoreEvidence() {
        onMain {
            session?.takeIf { DetectionRepository.state.value.session == it }?.let { id ->
                val proof = DetectionRepository.controller.issueProof(id)
                if (proof != null) DetectionRepository.stopVerified(proof)
                else DetectionRepository.failed(id, "@string/service_start_error")
            }
        }
        waitUntil { DetectionRepository.state.value.session != session || session == null }
        runtime.edit().apply {
            previousEvidence?.let { putString("active_type", it) } ?: remove("active_type")
        }.commit()
    }

    @Test fun repeatedStartsRecreationAndBackgroundKeepSessionUntilAuthorizedStop() {
        val id = start()
        onMain { repeat(3) { startIntent(id) } }
        activityRule.scenario.recreate()
        activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        onMain { assertEquals(id, DetectionRepository.state.value.session) }
        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onMain {
            assertEquals(DetectionState.SECURED, DetectionRepository.state.value.states[BATTERY_FULL_DETECTION])
            assertFalse(DetectionRepository.stopVerified("invalid-proof"))
            val proof = DetectionRepository.controller.issueProof(id)!!
            assertTrue(DetectionRepository.stopVerified(proof))
            assertFalse(DetectionRepository.stopVerified(proof))
            assertNull(DetectionRepository.state.value.session)
            assertEquals(DetectionState.ACTIVE, DetectionRepository.state.value.states[BATTERY_FULL_DETECTION])
        }
        waitUntil { target.getSystemService(NotificationManager::class.java).activeNotifications.none { it.id == 42 } }
    }

    @Test fun unexpectedServiceDestructionClearsSecuredAndImmediateRearmCanStartAgain() {
        start()
        onMain { target.stopService(Intent(target, MonitoringService::class.java)) }
        waitUntil { DetectionRepository.state.value.session == null }
        onMain {
            assertEquals(DetectionState.ACTIVE, DetectionRepository.state.value.states[BATTERY_FULL_DETECTION])
            assertEquals("@string/service_stopped", DetectionRepository.state.value.message)
        }
        val next = start()
        onMain {
            val proof = DetectionRepository.controller.issueProof(next)!!
            assertTrue(DetectionRepository.stopVerified(proof))
        }
        start()
    }
}

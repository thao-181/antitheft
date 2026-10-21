package com.example.antitheftdemo.security

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.platform.app.InstrumentationRegistry
import com.example.antitheftdemo.R
import com.example.antitheftdemo.alarm.AndroidAlarmPlayback
import com.example.antitheftdemo.detection.DetectionRepository
import com.example.antitheftdemo.detection.DetectionViewModel
import com.example.antitheftdemo.home.HomeItemType.PROXIMITY_DETECTION
import com.example.antitheftdemo.home.HomeScreen
import com.example.antitheftdemo.navigation.AppRoutes
import com.example.antitheftdemo.navigation.protectionRoutes
import com.example.antitheftdemo.pincode.PinCodeViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Real PIN/Keystore + NavHost + first STOP draw, with real looping audio and isolated test storage. */
class PinEndToEndBenchmark {
    @get:Rule val compose = createComposeRule()
    @Test fun checkToFirstStopDrawWhileAlarmIsPlaying() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "pin_ui_benchmark_${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(unused: String, mode: Int): SharedPreferences = target.getSharedPreferences(name, mode)
        }
        val store = PinStore(context)
        runBlocking { store.create("2468") }
        val samples = mutableMapOf<PinVerificationTimings.Step, MutableList<Long>>()
        PinVerificationTimings.observer = { step, nanos -> synchronized(samples) { samples.getOrPut(step) { mutableListOf() }.add(nanos) } }
        lateinit var nav: NavHostController
        var playback: AndroidAlarmPlayback? = null
        var ownedSession: String? = null
        var pinModel: PinCodeViewModel? = null
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PinCodeViewModel(target.applicationContext as Application, store).also { pinModel = it } as T
        }
        try {
            compose.setContent {
                nav = rememberNavController()
                NavHost(nav, AppRoutes.HOME) {
                    composable(AppRoutes.HOME) { HomeScreen(onNavigation = { nav.navigate(AppRoutes.detection(it.type!!)) }) }
                    protectionRoutes(nav, factory)
                }
            }
            repeat(21) { sampleIndex ->
                compose.runOnIdle {
                    assertNull(DetectionRepository.state.value.session)
                    val id = DetectionRepository.controller.request(PROXIMITY_DETECTION)!!
                    ownedSession = id
                    val output = AndroidAlarmPlayback(target)
                    playback = output
                    assertTrue(DetectionRepository.attach(id, PROXIMITY_DETECTION) { message ->
                        output.stop()
                        DetectionRepository.ended(context, id, message)
                    })
                    DetectionRepository.controller.started(id)
                    DetectionRepository.controller.alarm(id)
                    output.start({ DetectionRepository.controller.playback(id, it) },
                        { DetectionRepository.controller.playback(id, com.example.antitheftdemo.alarm.AlarmStatus.FAILED) })
                }
                compose.waitUntil(10_000) { DetectionRepository.state.value.alarmStatus == com.example.antitheftdemo.alarm.AlarmStatus.PLAYING }
                compose.onNodeWithTag("home_PROXIMITY_DETECTION").performClick()
                compose.onNodeWithTag("detection_circle").performClick()
                compose.waitUntil(5_000) { nav.currentDestination?.route == AppRoutes.PIN_VERIFICATION &&
                    nav.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED && pinModel?.isVerifyMode == true }
                compose.onNodeWithTag("pin_input").performTextReplacement("2468")
                // Benchmark the Check handler, not a pointer coordinate moving during Android IME animation.
                // Touch interaction itself is exercised by the Navigation/loading regression tests.
                compose.onNodeWithText(target.getString(R.string.check)).assertIsEnabled()
                    .performSemanticsAction(SemanticsActions.OnClick) { action -> assertTrue(action()) }
                try {
                    compose.waitUntil(20_000) { synchronized(samples) { samples[PinVerificationTimings.Step.CHECK_TO_STOP]?.size == sampleIndex + 1 } }
                } catch (error: Throwable) {
                    var diagnostic = ""
                    InstrumentationRegistry.getInstrumentation().runOnMainSync {
                        val authorized = runCatching {
                            ViewModelProvider(nav.getBackStackEntry(AppRoutes.detection(PROXIMITY_DETECTION)))[DetectionViewModel::class.java].authorizedSession != null
                        }.getOrDefault(false)
                        diagnostic = "sample=$sampleIndex route=${nav.currentDestination?.route} authorized=$authorized " +
                            "sessionUnchanged=${ownedSession == DetectionRepository.state.value.session} " +
                            "verifications=${samples[PinVerificationTimings.Step.VERIFY_TOTAL]?.size} " +
                            "launches=${samples[PinVerificationTimings.Step.CLICK_TO_LAUNCH]?.size} " +
                            "pinReady=${pinModel?.currentPin?.length == 4} busy=${pinModel?.busy} " +
                            "error=${pinModel?.error?.substringBefore('|')} " +
                            "deliveries=${samples[PinVerificationTimings.Step.SUCCESS_DELIVERY]?.size} " +
                            "draws=${samples[PinVerificationTimings.Step.CHECK_TO_STOP]?.size}"
                    }
                    throw AssertionError(diagnostic, error)
                }
                compose.runOnIdle { assertTrue(DetectionRepository.state.value.alarming) }
                compose.onNodeWithTag("detection_circle").performClick()
                compose.onNodeWithTag("home_PROXIMITY_DETECTION").assertIsNotSelected()
            }
            samples.forEach { (step, values) ->
                Log.i("AntiTheftPinBenchmark", "ui_cold_${step.name} duration_ms=${values.first() / 1_000_000.0}")
                val warm = values.drop(1).sorted()
                Log.i("AntiTheftPinBenchmark", "ui_warm_median_${step.name} duration_ms=${warm[(warm.size - 1) / 2] / 1_000_000.0}")
                Log.i("AntiTheftPinBenchmark", "ui_warm_p95_${step.name} duration_ms=${warm[kotlin.math.ceil(warm.size * .95).toInt() - 1] / 1_000_000.0}")
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ownedSession?.let { DetectionRepository.ended(context, it, null) }
                playback?.stop()
            }
            PinVerificationTimings.observer = null
            target.deleteSharedPreferences(name)
        }
    }
}

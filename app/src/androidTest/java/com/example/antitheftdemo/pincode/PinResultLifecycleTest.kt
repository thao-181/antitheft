package com.example.antitheftdemo.pincode

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import com.example.antitheftdemo.security.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test

internal class DeferredPinAccess : PinAccess {
    var result = CompletableDeferred<PinResult>()
    var revoked: String? = null
    var creations = 0
    override val hasPin = true
    override suspend fun verify(pin: String) = result.await()
    override suspend fun authorizeChange(pin: String) = PinChangeCheck(result.await(), "change-proof")
    override fun revokeChange(proof: String) { revoked = proof }
    override suspend fun create(pin: String) { result.await(); creations++ }
    override suspend fun replace(pin: String, proof: String) { error("Not a replacement test") }
}

class PinResultLifecycleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application get() = instrumentation.targetContext.applicationContext as Application

    @Test fun completedVerificationOnlyDeliversSuccessOnce() {
        val access = DeferredPinAccess()
        lateinit var model: PinCodeViewModel
        var successes = 0
        instrumentation.runOnMainSync {
            model = PinCodeViewModel(application, access)
            model.setVerifyMode()
            model.onPinChange("2468")
            model.handleAction()
            access.result.complete(PinResult.Verified)
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            if (model.consumeCompletion()) successes++
            model.handleAction()
            if (model.consumeCompletion()) successes++
            assertEquals(1, successes)
        }
    }

    @Test fun leavingPinBeforeVerificationCompletesDiscardsLateResult() {
        val access = DeferredPinAccess()
        lateinit var model: PinCodeViewModel
        var successes = 0
        instrumentation.runOnMainSync {
            model = PinCodeViewModel(application, access)
            model.setVerifyMode()
            model.onPinChange("2468")
            model.handleAction()
            model.invalidateVerification()
            access.result.complete(PinResult.Verified)
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            if (model.consumeCompletion()) successes++
            assertEquals(0, successes)
            assertFalse(model.busy)
            assertEquals("", model.currentPin)
            access.result = CompletableDeferred(PinResult.Incorrect)
            model.onPinChange("0000")
            model.handleAction()
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            if (model.consumeCompletion()) successes++
            assertEquals(0, successes)
            assertEquals("@string/pin_incorrect", model.error)
        }
    }

    @Test fun leavingSettingsRevokesLatePinChangeAuthorization() {
        val access = DeferredPinAccess()
        lateinit var model: PinCodeViewModel
        instrumentation.runOnMainSync {
            model = PinCodeViewModel(application, access)
            model.configureSettings()
            model.onPinChange("2468")
            model.handleAction()
            model.invalidateVerification()
            access.result.complete(PinResult.Verified)
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertEquals("change-proof", access.revoked)
            assertTrue(model.isVerifyMode)
            assertFalse(model.consumeCompletion())
        }
    }

    @Test fun committedCreationRetainsOneCompletionAcrossRecreationWithoutWritingAgain() {
        val access = DeferredPinAccess()
        lateinit var model: PinCodeViewModel
        instrumentation.runOnMainSync {
            model = PinCodeViewModel(application, access)
            model.onPinChange("2468")
            model.handleAction()
            model.onPinChange("2468")
            model.handleAction()
            // UI stops while the storage operation is in progress.
            model.invalidateVerification()
            access.result.complete(PinResult.Verified)
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertTrue(model.consumeCompletion())
            model.handleAction()
            assertFalse(model.consumeCompletion())
            assertEquals(1, access.creations)
        }
    }
}

package com.example.antitheftdemo.security

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/** Same Keystore, algorithm and iteration count as user storage; isolated preference file only. */
class PinVerificationBenchmark {
    @Test fun profileColdAndWarmExistingPinVerification() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "pin_benchmark_${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(unused: String, mode: Int): SharedPreferences = target.getSharedPreferences(name, mode)
        }
        try {
            PinStore(context).create("2468")
            // Create is excluded. First verify is cold verification after provisioning, not a cold boot.
            val samples = mutableMapOf<PinVerificationTimings.Step, MutableList<Long>>()
            PinVerificationTimings.observer = { step, nanos -> samples.getOrPut(step) { mutableListOf() }.add(nanos) }
            repeat(21) { assertEquals(PinResult.Verified, PinStore(context).verify("2468")) }
            samples.forEach { (step, values) ->
                Log.i("AntiTheftPinBenchmark", "cold_${step.name} duration_ms=${values.first() / 1_000_000.0}")
                val warm = values.drop(1).sorted()
                Log.i("AntiTheftPinBenchmark", "warm_median_${step.name} duration_ms=${warm[(warm.size - 1) / 2] / 1_000_000.0}")
                Log.i("AntiTheftPinBenchmark", "warm_p95_${step.name} duration_ms=${warm[kotlin.math.ceil(warm.size * .95).toInt() - 1] / 1_000_000.0}")
            }
        } finally {
            PinVerificationTimings.observer = null
            target.deleteSharedPreferences(name)
        }
    }
}

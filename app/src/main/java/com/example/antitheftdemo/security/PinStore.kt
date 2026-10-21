package com.example.antitheftdemo.security

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.example.antitheftdemo.security.PinVerificationTimings.Step

internal interface PinAccess {
    val hasPin: Boolean
    suspend fun create(pin: String)
    suspend fun verify(pin: String): PinResult
    suspend fun authorizeChange(pin: String): PinChangeCheck
    suspend fun replace(pin: String, proof: String)
    fun revokeChange(proof: String)
}

/** The salted verifier is encrypted by a non-exportable Android Keystore key. */
class PinStore(context: Context) : PinAccess {
    private val prefs = context.applicationContext.getSharedPreferences("pin_security", Context.MODE_PRIVATE)
    override val hasPin: Boolean get() = prefs.contains("verifier")
    override suspend fun create(pin: String) = withContext(Dispatchers.IO) { mutex.withLock {
        require(PinPolicy.valid(pin))
        check(!hasPin) { "PIN already exists" }
        savePin(pin, createKey = true)
    } }
    override suspend fun verify(pin: String): PinResult {
        val started = PinVerificationTimings.now()
        var finished = started
        val result = withContext(Dispatchers.IO) {
            PinVerificationTimings.record(Step.IO_DISPATCH, PinVerificationTimings.now() - started)
            val waiting = PinVerificationTimings.now()
            mutex.withLock {
                PinVerificationTimings.record(Step.MUTEX_WAIT, PinVerificationTimings.now() - waiting)
                verifyLocked(pin).also { finished = PinVerificationTimings.now() }
            }
        }
        PinVerificationTimings.record(Step.IO_RESUME, PinVerificationTimings.now() - finished)
        PinVerificationTimings.record(Step.VERIFY_TOTAL, PinVerificationTimings.now() - started)
        return result
    }
    override suspend fun authorizeChange(pin: String): PinChangeCheck = withContext(Dispatchers.IO) { mutex.withLock {
        val result = verifyLocked(pin)
        val proof = if (result == PinResult.Verified)
            changes.issue(checkNotNull(prefs.getString("verifier", null)), SystemClock.elapsedRealtime()) else null
        PinChangeCheck(result, proof)
    } }
    override suspend fun replace(pin: String, proof: String) = withContext(Dispatchers.IO) { mutex.withLock {
        require(PinPolicy.valid(pin))
        val verifier = prefs.getString("verifier", null) ?: error("PIN is missing")
        check(changes.consume(proof, verifier, SystemClock.elapsedRealtime())) { "Verify current PIN again" }
        savePin(pin, createKey = false)
    } }
    override fun revokeChange(proof: String) = changes.revoke(proof)
    private fun savePin(pin: String, createKey: Boolean) {
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(create = createKey))
        val verifier = PinPolicy.derive(pin, salt)
        try {
            check(prefs.edit().putString("salt", encode(salt)).putString("iv", encode(cipher.iv))
                .putString("verifier", encode(cipher.doFinal(verifier))).putInt("failures", 0)
                .putLong("blocked_until", 0).putLong("blocked_elapsed", 0).commit()) { "Cannot save PIN" }
        } finally { verifier.fill(0) }
    }
    private fun verifyLocked(pin: String): PinResult {
        val now = System.currentTimeMillis()
        val (salt, stored, iv) = PinVerificationTimings.measure(Step.PREFERENCES_READ) {
            val remaining = remainingDelay(now)
            if (remaining > 0) return PinResult.Locked((remaining + 999) / 1000)
            Triple(prefs.getString("salt", null) ?: error("PIN is not configured"),
                prefs.getString("verifier", null) ?: error("Missing verifier"),
                prefs.getString("iv", null) ?: error("Missing verifier IV"))
        }
        val secretKey = PinVerificationTimings.measure(Step.KEYSTORE_LOOKUP) { key(create = false) }
        val expected = PinVerificationTimings.measure(Step.AES_DECRYPT) {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, decode(iv)))
                doFinal(decode(stored))
            }
        }
        val success = try {
            PinVerificationTimings.measure(Step.PBKDF2_MATCH) { PinPolicy.matches(pin, decode(salt), expected) }
        } finally { expected.fill(0) }
        return if (success) {
            PinVerificationTimings.measure(Step.PREFERENCES_WRITE) {
                // A clean successful attempt has nothing to reset. Every attempt still derives the PIN.
                if (prefs.getInt("failures", 0) != 0 || prefs.getLong("blocked_until", 0) != 0L ||
                    prefs.getLong("blocked_elapsed", 0) != 0L) {
                    check(prefs.edit().putInt("failures", 0).putLong("blocked_until", 0)
                        .putLong("blocked_elapsed", 0).commit())
                }
            }
            PinResult.Verified
        } else {
            val failures = (prefs.getInt("failures", 0) + 1).coerceAtMost(30)
            val delay = PinPolicy.delayAfterFailures(failures)
            PinVerificationTimings.measure(Step.PREFERENCES_WRITE) {
                check(prefs.edit().putInt("failures", failures)
                .putLong("blocked_until", now + delay)
                .putLong("blocked_elapsed", SystemClock.elapsedRealtime() + delay)
                    .putLong("last_elapsed", SystemClock.elapsedRealtime()).commit())
            }
            if (delay > 0) PinResult.Locked(delay / 1000) else PinResult.Incorrect
        }
    }
    private fun remainingDelay(now: Long): Long {
        return PinPolicy.remainingDelay(prefs.getLong("blocked_until", 0),
            prefs.getLong("blocked_elapsed", 0), prefs.getLong("last_elapsed", 0),
            now, SystemClock.elapsedRealtime())
    }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "PIN key unavailable; cannot verify" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)
    companion object {
        private const val ALIAS = "antitheft_pin_v1"
        private val mutex = Mutex()
        private val changes = PinChangeAuthorizations()
    }
}

package com.example.antitheftdemo.security

import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinPolicy {
    fun valid(pin: String) = pin.length == 4 && pin.all { it in '0'..'9' }
    fun confirmed(first: String, second: String) = valid(first) && first == second
    fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 210_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
    }
    fun matches(pin: String, salt: ByteArray, expected: ByteArray) =
        valid(pin) && MessageDigest.isEqual(expected, derive(pin, salt))
    fun remainingDelay(blockedWall: Long, blockedElapsed: Long, lastElapsed: Long,
        nowWall: Long, nowElapsed: Long): Long {
        val wall = blockedWall - nowWall
        val monotonic = if (nowElapsed >= lastElapsed) blockedElapsed - nowElapsed else 0L
        return maxOf(wall, monotonic, 0L).coerceAtMost(900_000L)
    }
    fun delayAfterFailures(failures: Int): Long =
        if (failures < 5) 0L else (30_000L * (1L shl ((failures - 5).coerceAtMost(5)))).coerceAtMost(900_000L)
}

sealed interface PinResult {
    data object Verified : PinResult
    data object Incorrect : PinResult
    data class Locked(val seconds: Long) : PinResult
}

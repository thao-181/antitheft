package com.example.antitheftdemo.security

import java.util.UUID

/** In-memory, single-use, expiring permission bound to the verifier that was authenticated. */
class PinChangeAuthorizations {
    private data class Grant(val verifier: String, val expires: Long)
    private val grants = mutableMapOf<String, Grant>()
    @Synchronized fun issue(verifier: String, now: Long): String {
        grants.entries.removeAll { it.value.expires <= now }
        if (grants.size >= 32) grants.clear()
        return UUID.randomUUID().toString().also { grants[it] = Grant(verifier, now + 300_000) }
    }
    @Synchronized fun consume(proof: String, verifier: String, now: Long): Boolean {
        val grant = grants.remove(proof) ?: return false
        return grant.verifier == verifier && now < grant.expires
    }
    @Synchronized fun revoke(proof: String) { grants.remove(proof) }
}
data class PinChangeCheck(val result: PinResult, val proof: String? = null)

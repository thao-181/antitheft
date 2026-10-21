package com.example.antitheftdemo.util

/** Each owner gets a release attempt even when an earlier Android API throws. */
internal fun releaseResources(
    onFailure: (String, Exception) -> Unit,
    vararg resources: Pair<String, () -> Unit>
) {
    resources.forEach { (name, release) ->
        try { release() } catch (error: Exception) { onFailure(name, error) }
    }
}

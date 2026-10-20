package com.example.antitheftdemo.language

/** Includes names written by older versions, preserving existing onboarding data. */
object LanguageCodes {
    private val codes = mapOf("English" to "en", "Hindi" to "hi", "Spanish" to "es",
        "French" to "fr", "German" to "de", "Indonesian" to "id", "Portuguese" to "pt")
    fun code(value: String?): String? = when {
        value == "in" -> "id"
        value in codes.values -> value
        else -> codes[value]
    }
}

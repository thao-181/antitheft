package com.example.antitheftdemo.language

import org.junit.Assert.*
import org.junit.Test

class LanguageCodesTest {
    @Test fun legacyNamesAndCanonicalCodesAreCompatible() {
        listOf("English", "Hindi", "Spanish", "French", "German", "Indonesian", "Portuguese")
            .zip(listOf("en", "hi", "es", "fr", "de", "id", "pt")).forEach { (name, code) ->
                assertEquals(code, LanguageCodes.code(name))
                assertEquals(code, LanguageCodes.code(code))
            }
        assertEquals("id", LanguageCodes.code("in"))
        assertNull(LanguageCodes.code("unsupported"))
        assertNull(LanguageCodes.code(null))
    }
}

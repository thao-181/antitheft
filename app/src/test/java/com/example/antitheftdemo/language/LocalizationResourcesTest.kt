package com.example.antitheftdemo.language

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class LocalizationResourcesTest {
    private val root = File("src/main")
    private fun strings(folder: String): Map<String, Element> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val nodes = factory.newDocumentBuilder().parse(File(root, "res/$folder/strings.xml")).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associateBy { it.getAttribute("name") }
    }
    private fun placeholders(text: String) = Regex("%[0-9$]*[a-zA-Z]").findAll(text).map { it.value }.toList().sorted()

    @Test fun everyTranslatedLocaleHasAllKeysAndCompatiblePlaceholders() {
        val base = strings("values")
        val translated = base.filterValues { it.getAttribute("translatable") != "false" }
        for (folder in listOf("values-hi", "values-es", "values-fr", "values-de", "values-in", "values-pt")) {
            val actual = strings(folder)
            assertTrue("Missing keys in $folder", actual.keys.containsAll(translated.keys))
            assertTrue("Unknown keys in $folder", base.keys.containsAll(actual.keys))
            translated.forEach { (key, english) ->
                assertEquals("Placeholder mismatch $folder/$key", placeholders(english.textContent), placeholders(actual.getValue(key).textContent))
                assertTrue("Empty translation $folder/$key", actual.getValue(key).textContent.isNotBlank())
            }
        }
    }

    @Test fun allKotlinMessageReferencesAreMappedToExistingResources() {
        val sources = File(root, "java").walkTopDown().filter { it.extension == "kt" }.toList()
        val refs = Regex("\"(@string/[a-z_0-9]+)").findAll(sources.joinToString("\n") { it.readText() })
            .map { it.groupValues[1] }.toSet()
        val mapping = sources.single { it.name == "MessageResources.kt" }.readText()
        val entries = Regex("\"(@string/[a-z_0-9]+)\" to R.string.([a-z_0-9]+)").findAll(mapping).toList()
        assertTrue(entries.map { it.groupValues[1] }.toSet().containsAll(refs))
        val resources = strings("values")
        entries.forEach { assertTrue("Missing resource ${it.value}", resources.containsKey(it.groupValues[2])) }
    }
}

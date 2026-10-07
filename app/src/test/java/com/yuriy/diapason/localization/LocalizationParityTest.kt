package com.yuriy.diapason.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every shipped locale's strings.xml must have exactly the default locale's keys, and each
 * string the same format placeholders. Eight hand-maintained locales drift easily: before
 * this test, Persian and Chinese silently missed a Guide sentence and kept a label wording
 * that the other locales had already fixed.
 *
 * Reads the XML sources directly (the test JVM runs in the module directory).
 */
class LocalizationParityTest {

    private val resDir = File("src/main/res")
    private val locales = listOf("fr", "it", "es", "pt", "zh", "fa", "ar")

    private val placeholder = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfx%]""")

    private fun parse(dir: String): Map<String, List<String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resDir, "$dir/strings.xml"))
        val entries = mutableMapOf<String, List<String>>()
        val children = doc.documentElement.childNodes
        for (i in 0 until children.length) {
            val el = children.item(i) as? Element ?: continue
            if (el.getAttribute("translatable") == "false") continue
            when (el.tagName) {
                "string" -> entries[el.getAttribute("name")] = listOf(el.textContent)
                "plurals" -> {
                    val items = el.getElementsByTagName("item")
                    entries[el.getAttribute("name")] = (0 until items.length).map { items.item(it).textContent }
                }
            }
        }
        return entries
    }

    /** Placeholders of a string, ignoring "%%". For plurals, the union over all quantities. */
    private fun placeholders(texts: List<String>): Set<String> =
        texts.flatMap { t -> placeholder.findAll(t).map { it.value }.filter { it != "%%" } }.toSet()

    @Test
    fun `every locale has exactly the default locale's keys`() {
        val default = parse("values").keys
        locales.forEach { locale ->
            val keys = parse("values-$locale").keys
            assertEquals("missing in $locale", emptySet<String>(), default - keys)
            assertEquals("extra in $locale", emptySet<String>(), keys - default)
        }
    }

    @Test
    fun `every string keeps the default locale's format placeholders`() {
        val default = parse("values")
        locales.forEach { locale ->
            parse("values-$locale").forEach { (key, texts) ->
                val expected = placeholders(default.getValue(key))
                val actual = placeholders(texts)
                // A plural quantity may spell its number out ("one second"), so plurals
                // only need to stay within the default's placeholders.
                if (texts.size > 1) {
                    assertTrue("$locale/$key uses $actual, default allows $expected", expected.containsAll(actual))
                } else {
                    assertEquals("$locale/$key", expected, actual)
                }
            }
        }
    }

    @Test
    fun `every locale folder in build_gradle localeFilters is covered here`() {
        val gradle = File("build.gradle.kts").readText()
        val filters = Regex("""localeFilters \+= listOf\(([^)]*)\)""").find(gradle)!!.groupValues[1]
            .split(",").map { it.trim().trim('"') }.toSet()
        assertEquals(setOf("en") + locales, filters)
    }
}

package com.pineandpackets.pocketlab.engine.ioc

import com.pineandpackets.pocketlab.core.model.IndicatorType
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Property-based tests over [IocExtractor]. Extracted indicators are hostile
 * sample-derived values, so these tests assert invariants over arbitrary text
 * that mixes genuine indicator shapes with junk that must never be reported.
 */
class IocExtractorPropertyTest {

    private val config = PropTestConfig(seed = 20260807L, iterations = 300)

    private val tld = Arb.element(listOf("com", "org", "net", "io", "xyz", "info", "co", "uk", "dev"))
    private val label = Arb.string(1..12, Codepoint.az())

    private val domainArb: Arb<String> = arbitrary { rs ->
        val labelCount = (1..3).random(rs.random)
        val labels = List(labelCount) { label.bind() }
        labels.joinToString(".") + "." + tld.bind()
    }

    private val ipArb: Arb<String> = arbitrary { rs ->
        List(4) { (0..255).random(rs.random) }.joinToString(".")
    }

    private val urlArb: Arb<String> = arbitrary { rs ->
        val scheme = if ((0..1).random(rs.random) == 0) "http://" else "https://"
        val domain = domainArb.bind()
        val path = if ((0..1).random(rs.random) == 0) "" else "/" + label.bind()
        scheme + domain + path
    }

    private val emailArb: Arb<String> = arbitrary { rs ->
        label.bind() + "@" + domainArb.bind()
    }

    private val junkToken: Arb<String> = Arb.choice(
        Arb.string(0..8, Codepoint.az()),
        Arb.element(listOf("!", "%", "$", "#", "&", "*", "+", "=", "?", ",", ";", "~", "|", "(", ")", "_"))
    )

    private val separator = Arb.element(listOf(" ", ", ", "; ", "\n", "\t", " | "))

    private val mixedText: Arb<String> = arbitrary { rs ->
        val token = Arb.choice(listOf(urlArb, domainArb, ipArb, emailArb, junkToken))
        val count = (0..8).random(rs.random)
        List(count) { token.bind() }.joinToString(separator.bind())
    }

    private val junkOnlyText: Arb<String> = arbitrary { rs ->
        val count = (0..10).random(rs.random)
        List(count) { junkToken.bind() }.joinToString(separator.bind())
    }

    @Test
    fun `extraction is deterministic for the same input`() {
        runBlocking {
            forAll(config, mixedText) { text ->
                IocExtractor().extractIndicators(text) == IocExtractor().extractIndicators(text)
            }
        }
    }

    @Test
    fun `returned indicators are deduplicated by canonical value`() {
        runBlocking {
            forAll(config, mixedText) { text ->
                val indicators = IocExtractor().extractIndicators(text)
                indicators.map { it.canonicalValue }.distinct().size == indicators.size
            }
        }
    }

    @Test
    fun `every display value is a substring of the source text`() {
        runBlocking {
            forAll(config, mixedText) { text ->
                IocExtractor().extractIndicators(text).all { text.contains(it.displayValue) }
            }
        }
    }

    @Test
    fun `canonical value equals lowercased display value`() {
        runBlocking {
            forAll(config, mixedText) { text ->
                IocExtractor().extractIndicators(text).all {
                    it.canonicalValue == it.displayValue.lowercase()
                }
            }
        }
    }

    @Test
    fun `defanged values round-trip back to the original display value`() {
        runBlocking {
            forAll(config, mixedText) { text ->
                IocExtractor().extractIndicators(text).all {
                    refang(it.defangedValue) == it.displayValue
                }
            }
        }
    }

    @Test
    fun `defanged url values carry no raw http scheme`() {
        runBlocking {
            forAll(config, mixedText) { text ->
                IocExtractor().extractIndicators(text)
                    .filter { it.type == IndicatorType.URL }
                    .all { !it.defangedValue.contains("http://") && !it.defangedValue.contains("https://") }
            }
        }
    }

    @Test
    fun `ipv4 indicators are valid octets`() {
        runBlocking {
            forAll(config, mixedText) { text ->
                IocExtractor().extractIndicators(text)
                    .filter { it.type == IndicatorType.IPV4 }
                    .all { indicator ->
                        val octets = indicator.canonicalValue.split(".")
                        octets.size == 4 && octets.all { (it.toIntOrNull() ?: -1) in 0..255 }
                    }
            }
        }
    }

    @Test
    fun `junk-only text yields no indicators`() {
        runBlocking {
            forAll(config, junkOnlyText) { text ->
                assertTrue(
                    "Junk text reported indicators: $text -> ${IocExtractor().extractIndicators(text)}",
                    IocExtractor().extractIndicators(text).isEmpty()
                )
                true
            }
        }
    }

    @Test
    fun `empty and whitespace text yields no indicators`() {
        assertEquals(emptyList<Any>(), IocExtractor().extractIndicators(""))
        assertTrue(IocExtractor().extractIndicators("   \n\t  ").isEmpty())
    }

    private fun refang(value: String): String {
        return value
            .replace("[.]", ".")
            .replace("[@]", "@")
            .replace("hxxps://", "https://")
            .replace("hxxp://", "http://")
    }
}

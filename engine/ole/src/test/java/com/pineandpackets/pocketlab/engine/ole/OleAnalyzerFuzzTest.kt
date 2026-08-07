package com.pineandpackets.pocketlab.engine.ole

import com.pineandpackets.pocketlab.core.testing.FuzzHarness
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fuzz tests over the OLE/CFB analyzer boundary. The scanner parses hostile
 * binary CFB structures (header, FAT, directory, chains), so it must never
 * crash, hang, or produce non-deterministic results for any byte sequence,
 * including truncated headers, malformed geometry, and cyclic chains.
 */
class OleAnalyzerFuzzTest {

    private val prefixes = listOf(
        ByteArray(0),
        byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte(),
        ),
        byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 0x03.toByte(), 0x04.toByte()),
        byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte()),
    )

    private val sizes = intArrayOf(0, 1, 2, 3, 4, 7, 8, 15, 16, 31, 32, 64, 128, 256, 512, 1024, 2048, 4096)

    @Test
    fun `ole analyzer never crashes hangs or is nondeterministic on random input`() {
        val corpus = FuzzHarness.corpus(
            prefixes = prefixes,
            sizes = sizes,
            perSize = 40,
            seed = 0x0111L,
        )

        val failures = FuzzHarness.fuzz(corpus) { bytes ->
            val ref = OleArtifactRef(bytes, "fuzz.doc")
            val result = OleAnalyzer().analyze(ref.context(), ref)
            val digest = result.findings.sortedBy { it.ruleId }.joinToString("|") { it.ruleId } +
                "|" + result.parserErrors.map { it.code }.sorted().joinToString("|")
            "${result.incomplete}|$digest"
        }

        assertTrue("OLE analyzer fuzz failures: $failures", failures.isEmpty())
    }

    @Test
    fun `ole scanner handles hostile cfb structures deterministically`() {
        val corpus = FuzzHarness.corpus(
            prefixes = listOf(
                OleFixture.macroDoc().copyOf(512),
                OleFixture.embeddedDoc().copyOf(768),
                ByteArray(0),
            ),
            sizes = intArrayOf(0, 16, 128, 1024),
            perSize = 30,
            seed = 0x0CE7L,
        )

        val failures = FuzzHarness.fuzz(corpus) { bytes ->
            val ref = OleArtifactRef(bytes, "fuzz.doc")
            val result = OleAnalyzer().analyze(ref.context(), ref)
            result.abnormalities().sorted().joinToString("|")
        }

        assertTrue("OLE scanner fuzz failures: $failures", failures.isEmpty())
    }
}

private fun com.pineandpackets.pocketlab.engine.api.AnalyzerResult.abnormalities(): List<String> =
    facts.filter { it.type == "OLE_ABNORMALITY" }.map { it.value }
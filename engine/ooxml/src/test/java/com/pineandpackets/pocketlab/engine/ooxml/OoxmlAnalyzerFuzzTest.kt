package com.pineandpackets.pocketlab.engine.ooxml

import com.pineandpackets.pocketlab.core.testing.FuzzHarness
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fuzz tests over the OOXML analyzer boundary. The scanner opens hostile
 * bytes as a ZIP through a bounded channel, so it must never crash, hang, or
 * produce non-deterministic results for any byte sequence, including valid ZIP
 * containers with hostile part names and truncated/malformed packages.
 */
class OoxmlAnalyzerFuzzTest {

    private val prefixes = listOf(
        ByteArray(0),
        byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 0x03.toByte(), 0x04.toByte()),
        byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte()),
        byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte()),
        "<Relationship Target=\"https://evil.example.net/x\" TargetMode=\"External\"/>".toByteArray(),
        "[Content_Types].xml vbaProject.bin activeX embeddings word/document.xml".toByteArray(),
    )

    private val sizes = intArrayOf(0, 1, 2, 3, 4, 7, 8, 15, 16, 31, 32, 64, 128, 256, 512, 1024, 2048, 4096)

    @Test
    fun `ooxml analyzer never crashes hangs or is nondeterministic on random input`() {
        val corpus = FuzzHarness.corpus(
            prefixes = prefixes,
            sizes = sizes,
            perSize = 40,
            seed = 0x0C1L,
        )

        val failures = FuzzHarness.fuzz(corpus) { bytes ->
            val ref = ByteArtifactRef(bytes, "fuzz.docx")
            val result = OoxmlAnalyzer().analyze(ref.context(), ref)
            val digest = result.findings.sortedBy { it.ruleId }.joinToString("|") { it.ruleId } +
                "|" + result.parserErrors.map { it.code }.sorted().joinToString("|")
            "${result.incomplete}|$digest"
        }

        assertTrue("OOXML analyzer fuzz failures: $failures", failures.isEmpty())
    }

    @Test
    fun `ooxml handles corrupted zip strings deterministically`() {
        val corpus = FuzzHarness.corpus(
            prefixes = prefixes,
            sizes = intArrayOf(0, 16, 128, 1024),
            perSize = 30,
            seed = 0xCA2FL,
        )

        val failures = FuzzHarness.fuzz(corpus) { bytes ->
            val ref = ByteArtifactRef(bytes, "fuzz.xlsx")
            val result = OoxmlAnalyzer().analyze(ref.context(), ref)
            result.abnormalities().sorted().joinToString("|")
        }

        assertTrue("OOXML scanner fuzz failures: $failures", failures.isEmpty())
    }
}

private fun com.pineandpackets.pocketlab.engine.api.AnalyzerResult.abnormalities(): List<String> =
    facts.filter { it.type == "OOXML_ABNORMALITY" }.map { it.value }
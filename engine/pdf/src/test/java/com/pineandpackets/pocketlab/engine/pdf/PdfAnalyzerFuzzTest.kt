package com.pineandpackets.pocketlab.engine.pdf

import com.pineandpackets.pocketlab.core.testing.FuzzHarness
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fuzz tests over the PDF analyzer boundary. The scanner treats imported bytes
 * as hostile Latin-1 text, so it must never crash, hang, or produce
 * non-deterministic results for any byte sequence, including PDF magic
 * prefixes and truncated/malformed object dictionaries.
 */
class PdfAnalyzerFuzzTest {

    private val prefixes = listOf(
        ByteArray(0),
        byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte()),
        "%PDF-1.7\n".toByteArray(Charsets.ISO_8859_1),
        "/JavaScript /JS /OpenAction /Launch /XFA /EmbeddedFiles /URI\n".toByteArray(Charsets.ISO_8859_1),
        "%%EOF startxref trailer".toByteArray(Charsets.ISO_8859_1),
    )

    private val sizes = intArrayOf(0, 1, 2, 3, 4, 7, 8, 15, 16, 31, 32, 64, 128, 256, 512, 1024, 2048, 4096)

    @Test
    fun `pdf analyzer never crashes hangs or is nondeterministic on random input`() {
        val corpus = FuzzHarness.corpus(
            prefixes = prefixes,
            sizes = sizes,
            perSize = 40,
            seed = 0xAB1F0DL,
        )

        val failures = FuzzHarness.fuzz(corpus) { bytes ->
            val ref = ByteArtifactRef(bytes, "fuzz.pdf")
            val result = PdfAnalyzer().analyze(ref.context(), ref)
            val digest = result.findings.sortedBy { it.ruleId }
                .joinToString("|") { it.ruleId }
            "${result.incomplete}|$digest"
        }

        assertTrue("PDF analyzer fuzz failures: $failures", failures.isEmpty())
    }

    @Test
    fun `pdf scanner handles truncated and oversized inputs deterministically`() {
        val corpus = FuzzHarness.corpus(
            prefixes = prefixes,
            sizes = intArrayOf(0, 3, 16, 1024),
            perSize = 20,
            seed = 0xAB2E5FL,
        )

        val failures = FuzzHarness.fuzz(corpus) { bytes ->
            val ref = ByteArtifactRef(bytes, "fuzz.pdf")
            PdfScanner(ref, ref.context()).scan().abnormalities.sorted().joinToString("|")
        }

        assertTrue("PDF scanner fuzz failures: $failures", failures.isEmpty())
    }
}

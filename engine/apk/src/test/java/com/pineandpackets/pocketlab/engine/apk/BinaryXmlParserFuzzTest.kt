package com.pineandpackets.pocketlab.engine.apk

import com.pineandpackets.pocketlab.core.testing.FuzzHarness
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fuzz tests for the binary XML (AXML) parser and the resources.arsc parser.
 * Both recorders consume hostile bytes directly, so they must never crash,
 * hang, allocate without bound, or behave non-deterministically.
 */
class BinaryXmlParserFuzzTest {

    private val parser = BinaryXmlParser()
    private val resourceParser = ResourceTableParser()

    private fun axmlPrefix(): ByteArray {
        val prefix = ByteArray(8)
        // CHUNK_AXML_FILE little-endian 32-bit
        prefix[0] = 0x03; prefix[1] = 0x00; prefix[2] = 0x08; prefix[3] = 0x00
        // fileSize
        prefix[4] = 0x00; prefix[5] = 0x00; prefix[6] = 0x00; prefix[7] = 0x00
        return prefix
    }

    private val axmlPrefixes = listOf(
        axmlPrefix(),
        byteArrayOf(),
        // String pool chunk with a hostile count field is unlikely from pure random,
        // so also generate a known-good-ish header and let the body fuzz.
        byteArrayOf(0x03, 0x00, 0x08, 0x00)
    )

    private val sizes = intArrayOf(0, 1, 4, 7, 8, 15, 16, 31, 32, 64, 128, 256, 512, 1024, 2048, 4096)

    @Test
    fun `binary XML parser never crashes hangs or allocates out of bounds`() {
        val corpus = FuzzHarness.corpus(
            prefixes = axmlPrefixes,
            sizes = sizes,
            perSize = 60,
            seed = 0x0B1F_7EEDL
        )

        val failures = FuzzHarness.fuzz(corpus) { bytes ->
            val r = parser.parse(bytes)
            r.isSuccess to (r.exceptionOrNull()?.message ?: "")
        }

        assertTrue("BinaryXmlParser fuzz failures: $failures", failures.isEmpty())
    }

    @Test
    fun `resource table parser never crashes or allocates on random input`() {
        val tablePrefixes = listOf(
            byteArrayOf(
                0x02, 0x00, // RES_TABLE_TYPE little-endian
                0x0C, 0x00, // header size
                0x00, 0x00, 0x00, 0x00, // size
                0x00, 0x00, 0x00, 0x00 // package count
            ),
            byteArrayOf(),
            byteArrayOf(0x02, 0x00)
        )

        val corpus = FuzzHarness.corpus(
            prefixes = tablePrefixes,
            sizes = sizes,
            perSize = 60,
            seed = 0x0A5C_0DE1L
        )

        val failures = FuzzHarness.fuzz(corpus) { bytes ->
            val r = resourceParser.parse(bytes)
            r.isSuccess to (r.exceptionOrNull()?.message ?: "")
        }

        assertTrue("ResourceTableParser fuzz failures: $failures", failures.isEmpty())
    }
}
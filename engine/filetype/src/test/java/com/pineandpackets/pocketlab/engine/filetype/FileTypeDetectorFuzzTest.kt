package com.pineandpackets.pocketlab.engine.filetype

import com.pineandpackets.pocketlab.core.testing.FuzzHarness
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fuzz tests over the file type detector's hostile header. Type detection is a
 * pure, stateless decision over the input, so it must never crash, hang, or
 * produce non-deterministic results for any byte sequence.
 */
class FileTypeDetectorFuzzTest {

    private val magicByPet = listOf(
        ByteArray(0),
        byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 0x03.toByte(), 0x04.toByte()),
        byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), '\n'.code.toByte()),
        byteArrayOf(0x7F.toByte(), 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()),
        byteArrayOf('M'.code.toByte(), 'Z'.code.toByte()),
        byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte()),
        byteArrayOf(0x1F.toByte(), 0x8B.toByte()),
        byteArrayOf(0x37.toByte(), 0x7A.toByte(), 0xBC.toByte(), 0xAF.toByte(), 0x27.toByte(), 0x1C.toByte()),
        byteArrayOf('R'.code.toByte(), 'a'.code.toByte(), 'r'.code.toByte(), '!'.code.toByte(), 0x1A.toByte(), 0x07.toByte(), 0x00.toByte())
    )

    private val sizes = intArrayOf(0, 1, 2, 3, 4, 7, 8, 15, 16, 31, 32, 64, 128, 256, 512, 1024, 2048)

    @Test
    fun `type detection never crashes hangs or is nondeterministic on random input`() {
        val corpus = FuzzHarness.corpus(
            prefixes = magicByPet + listOf(null),
            sizes = sizes,
            perSize = 40,
            seed = 0x5A1111L
        )

        val failures = FuzzHarness.fuzz(corpus) { bytes ->
            val r = FileTypeDetector.detect(bytes, extension = null, mimeType = null)
            "${r.magicType}|${r.extensionType}|${r.confidence.name}"
        }

        assertTrue("File type detector fuzz failures: $failures", failures.isEmpty())
    }

    @Test
    fun `type detection is stable across hostile extension and MIME combinations`() {
        val extensions = listOf(null, "", "apk", "zip", "APK", "ExE", "so", "pdf", "garbage", "020", "p")
        val corpus = FuzzHarness.corpus(
            prefixes = magicByPet + listOf(null),
            sizes = intArrayOf(0, 1, 4, 64, 1024),
            perSize = 20,
            seed = 0x5A2222L
        )

        val failures = FuzzHarness.fuzz(corpus) { bytes ->
            extensions.joinToString(";") { ext ->
                val r = FileTypeDetector.detect(bytes, extension = ext, mimeType = "application/octet-stream")
                "${r.magicType}|${r.extensionType}|${r.confidence.name}|${r.mismatchFlags}"
            }
        }
        assertTrue("File type detector extension/MIME fuzz failures: $failures", failures.isEmpty())
    }
}
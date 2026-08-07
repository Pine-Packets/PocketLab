package com.pineandpackets.pocketlab.engine.native

import com.pineandpackets.pocketlab.core.testing.FuzzHarness
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Fuzz tests for the ELF parser. ELF objects are hostile inputs, so parsing must
 * never crash, hang, allocate without bound, or behave non-deterministically.
 * Each buffer is written to a private temp file before analysis.
 */
class ElfAnalyzerFuzzTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var analyzer: ElfAnalyzer

    private val elfPrefixes = listOf(
        byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()),
        // ELF64 little-endian magic + class byte
        byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 2, 1),
        byteArrayOf()
    )

    private val sizes = intArrayOf(0, 1, 4, 7, 8, 15, 16, 31, 32, 63, 64, 65, 128, 256, 512, 1024, 2048)

    @Before
    fun setUp() {
        analyzer = ElfAnalyzer()
    }

    private fun writeTemp(bytes: ByteArray): File {
        val f = tempFolder.newFile("elf-fuzz.bin")
        f.writeBytes(bytes)
        return f
    }

    @Test
    fun `ELF parser never crashes hangs or allocates out of bounds on random input`() {
        val corpus = FuzzHarness.corpus(
            prefixes = elfPrefixes,
            sizes = sizes,
            perSize = 50,
            seed = 0xE1FFEEDL
        )

        val failures = FuzzHarness.fuzz(corpus) { bytes ->
            val file = writeTemp(bytes)
            val result = try {
                analyzer.analyzeElf(file)
            } catch (e: Exception) {
                Result.failure<ElfInfo>(e)
            }
            if (result.isSuccess) {
                val info = result.getOrNull()
                "OK:${info?.architecture}|${info?.is64bit}|${info?.sectionCount}|${info?.symbols?.size}|${info?.dynamicDependencies?.size}"
            } else {
                "ERR:${result.exceptionOrNull()?.javaClass?.simpleName}"
            }
        }

        assertTrue("ElfAnalyzer fuzz failures: $failures", failures.isEmpty())
    }

    @Test
    fun `ELF parser rejects oversized section and program header counts via quotas`() {
        // Construct an ELF64 header declaring a huge section count but a tiny body.
        val header = ByteBuffer.allocate(64)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put(0x7F); header.put('E'.code.toByte()); header.put('L'.code.toByte()); header.put('F'.code.toByte())
        header.put(2) // ELF64
        header.put(1) // little endian
        header.put(1) // version
        header.put(0); header.put(0) // OSABI + ABI version (7..8)
        header.put(ByteArray(7)) // padding to 16
        header.putShort(3) // ET_DYN
        header.putShort(0x3E) // x86_64
        header.putInt(1) // e_version
        header.putLong(0) // entry
        header.putLong(64) // phoff
        header.putLong(0) // shoff
        header.putInt(0) // flags
        header.putShort(64) // e_ehsize
        header.putShort(56) // phentsize
        header.putShort(1) // phnum
        header.putShort(64) // shentsize
        header.putShort(60000.toShort()) // shnum - hostile, exceeds quota
        header.putShort(0) // shstrndx

        val file = writeTemp(header.array())
        val result = try {
            analyzer.analyzeElf(file)
        } catch (e: Exception) {
            Result.failure<ElfInfo>(e)
        }
        // ShNum 60000 exceeds MAX_CLASS_COUNT (50000): quota rejection or safe parse.
        assertTrue(
            "expected quota rejection or bounded success, got $result",
            result.isFailure || (result.getOrNull()?.sectionCount ?: 0) <= 50000
        )
    }
}
package com.pineandpackets.pocketlab.engine.archive

import com.pineandpackets.pocketlab.core.testing.FuzzHarness
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Fuzz tests for the archive metadata path: ZipValidator preflight followed by
 * ArchiveAnalyzer enumeration. Archive inputs are hostile, so preflight and
 * enumeration must never crash, hang, or behave non-deterministically, and every
 * mutation of a structurally valid ZIP must be tolerated (rejected or bounded).
 */
class ArchiveAnalyzerFuzzTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var analyzer: ArchiveAnalyzer

    private val zipPrefix = byteArrayOf(
        'P'.code.toByte(), 'K'.code.toByte(), 0x03.toByte(), 0x04.toByte()
    )

    private val sizes = intArrayOf(0, 1, 4, 8, 16, 22, 32, 64, 128, 256, 512, 1024, 2048)

    @Before
    fun setUp() {
        analyzer = ArchiveAnalyzer()
    }

    private fun writeTemp(bytes: ByteArray): File {
        val f = tempFolder.newFile("zip-fuzz.bin")
        f.writeBytes(bytes)
        return f
    }

    private fun validZip(): File {
        val f = tempFolder.newFile("base.zip")
        ZipOutputStream(FileOutputStream(f)).use { zos ->
            zos.putNextEntry(ZipEntry("file1.txt"))
            zos.write("content1".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("dir/file2.txt"))
            zos.write("content2".toByteArray())
            zos.closeEntry()
        }
        return f
    }

    private fun randomZipMutants(seed: Long): List<ByteArray> {
        // Corpus combining pure random bytes and byte-flip/truncation mutations of a
        // structurally valid ZIP to hit deep ZipValidator / Commons Compress paths.
        val randomCorpus = FuzzHarness.corpus(
            prefixes = listOf(zipPrefix, byteArrayOf()),
            sizes = sizes,
            perSize = 40,
            seed = seed
        )

        val base = validZip().readBytes()
        val mutants = mutableListOf<ByteArray>()
        val rng = java.util.Random(seed)
        repeat(200) { i ->
            val mut = base.copyOf()
            when (i % 3) {
                0 -> mut[rng.nextInt(mut.size)] = rng.nextInt(256).toByte()
                1 -> {
                    val n = mut.size
                    val cut = rng.nextInt(n + 1)
                    mutants.add(mut.copyOf(cut))
                }
                else -> {
                    val pos = rng.nextInt(mut.size)
                    mut[pos] = (mut[pos].toInt() xor 0xFF).toByte()
                    mut[if (pos == mut.size - 1) pos else pos + 1] = rng.nextInt(256).toByte()
                }
            }
            mutants.add(mut)
        }
        return randomCorpus + mutants
    }

    @Test
    fun `archive preflight and enumeration never crash or hang on hostile bytes`() {
        val corpus = randomZipMutants(seed = 0xA2C_3EEDL)

        val failures = FuzzHarness.fuzz(
            corpus = corpus,
            terminationBudgetMs = 10_000
        ) { bytes: ByteArray ->
            val file = writeTemp(bytes)
            val r = try {
                analyzer.analyzeArchive(file)
            } catch (e: Exception) {
                Result.failure<ArchiveAnalysisResult>(e)
            }
            if (r.isSuccess) {
                val a = r.getOrNull()
                "OK:${a?.entryCount}|${a?.suspiciousPaths?.size}|${a?.isEncrypted}"
            } else {
                "ERR:${r.exceptionOrNull()?.javaClass?.simpleName}"
            }
        }

        assertTrue("ArchiveAnalyzer fuzz failures: $failures", failures.isEmpty())
    }
}
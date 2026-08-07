package com.pineandpackets.pocketlab.engine.archive

import com.pineandpackets.pocketlab.core.model.IndicatorSource
import com.pineandpackets.pocketlab.core.model.IndicatorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CaseZipTextScannerTest {

    private val scanner = CaseZipTextScanner()

    private fun createZip(entries: Map<String, ByteArray>): File {
        val zipFile = createTempFile("case-zip", ".zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content)
                zos.closeEntry()
            }
        }
        return zipFile
    }

    @Test
    fun `extracts indicators from text entries with container provenance`() {
        val zipFile = createZip(
            mapOf(
                "notes.txt" to "Check this url https://malware.example.net/steal and email bad@evil.test".toByteArray(),
                "urls.log" to "http://10.0.0.5/c2".toByteArray()
            )
        )

        val result = scanner.scan(zipFile, "sample_case.zip")

        assertTrue(result.isSuccess)
        val entries = result.getOrThrow()
        assertEquals(2, entries.size)

        val notes = entries.first { it.path == "notes.txt" }
        assertTrue(notes.indicators.any { it.type == IndicatorType.URL && it.canonicalValue.contains("malware.example.net") })
        assertTrue(notes.indicators.any { it.type == IndicatorType.EMAIL })
        notes.indicators.forEach { indicator ->
            assertEquals("sample_case.zip", (indicator.source as? IndicatorSource)?.container)
            assertEquals("notes.txt", (indicator.source as? IndicatorSource)?.entry)
        }

        val urlsLog = entries.first { it.path == "urls.log" }
        assertTrue(urlsLog.indicators.any { it.type == IndicatorType.IPV4 })
        assertTrue(urlsLog.indicators.any { it.type == IndicatorType.URL })
    }

    @Test
    fun `skips binary entries that look like text`() {
        val binaryContent = byteArrayOf(0x00, 0x01, 0x02) + "https://fake.example.com".toByteArray()
        val zipFile = createZip(mapOf("blob.bin" to binaryContent))

        val result = scanner.scan(zipFile, "case.zip")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `skips non text extensions`() {
        val zipFile = createZip(
            mapOf(
                "photo.png" to "https://fake.example.com".toByteArray(),
                "base.apk" to "https://fake.example.com".toByteArray()
            )
        )

        val result = scanner.scan(zipFile, "case.zip")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `skips entries larger than per entry scan cap`() {
        val big = ByteArray(1024 * 1024 + 100) { 'a'.code.toByte() }
        val zipFile = createZip(mapOf("big.txt" to big))

        val result = scanner.scan(zipFile, "case.zip")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `returns empty list for archive with no text entries`() {
        val zipFile = createZip(mapOf("binary.dat" to ByteArray(10) { it.toByte() }))

        val result = scanner.scan(zipFile, "case.zip")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }
}

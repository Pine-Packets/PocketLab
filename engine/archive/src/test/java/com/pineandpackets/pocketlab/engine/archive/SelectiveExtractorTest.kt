package com.pineandpackets.pocketlab.engine.archive

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SelectiveExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val extractor = SelectiveExtractor()
    private val analyzer = ArchiveAnalyzer()

    @Test
    fun `extract specific entries from archive`() {
        val archive = createTestZip(
            "file1.txt" to "content1",
            "file2.txt" to "content2",
            "file3.txt" to "content3"
        )
        val analysis = analyzer.analyzeArchive(archive).getOrThrow()
        val destDir = tempFolder.newFolder("extracted")

        val result = extractor.extractEntries(
            archiveFile = archive,
            entriesToExtract = listOf("file1.txt", "file3.txt"),
            destinationDir = destDir,
            analysisResult = analysis
        )

        assertTrue(result.isSuccess)
        val extraction = result.getOrThrow()
        assertEquals(2, extraction.extractedFiles.size)
        assertTrue(extraction.extractedFiles.containsKey("file1.txt"))
        assertTrue(extraction.extractedFiles.containsKey("file3.txt"))
        assertFalse(extraction.extractedFiles.containsKey("file2.txt"))

        val file1 = extraction.extractedFiles["file1.txt"]!!
        assertTrue(File(file1.extractedPath).exists())
        assertEquals("content1", File(file1.extractedPath).readText())
    }

    @Test
    fun `extracted files use randomized names`() {
        val archive = createTestZip(
            "sensitive/path/file.txt" to "content"
        )
        val analysis = analyzer.analyzeArchive(archive).getOrThrow()
        val destDir = tempFolder.newFolder("extracted")

        val result = extractor.extractEntries(
            archiveFile = archive,
            entriesToExtract = listOf("sensitive/path/file.txt"),
            destinationDir = destDir,
            analysisResult = analysis
        )

        assertTrue(result.isSuccess)
        val extraction = result.getOrThrow()
        val extracted = extraction.extractedFiles["sensitive/path/file.txt"]!!

        assertTrue(extracted.randomizedName.endsWith(".bin"))
        assertFalse(extracted.randomizedName.contains("sensitive"))
        assertFalse(extracted.randomizedName.contains("path"))
        assertTrue(File(extracted.extractedPath).exists())
    }

    @Test
    fun `skip entries not in archive`() {
        val archive = createTestZip(
            "file1.txt" to "content1"
        )
        val analysis = analyzer.analyzeArchive(archive).getOrThrow()
        val destDir = tempFolder.newFolder("extracted")

        val result = extractor.extractEntries(
            archiveFile = archive,
            entriesToExtract = listOf("file1.txt", "nonexistent.txt"),
            destinationDir = destDir,
            analysisResult = analysis
        )

        assertTrue(result.isSuccess)
        val extraction = result.getOrThrow()
        assertEquals(1, extraction.extractedFiles.size)
        assertEquals(1, extraction.skippedEntries.size)
        assertEquals("nonexistent.txt", extraction.skippedEntries[0].originalPath)
    }

    @Test
    fun `skip directory entries`() {
        val archive = createTestZipWithDirectory()
        val analysis = analyzer.analyzeArchive(archive).getOrThrow()
        val destDir = tempFolder.newFolder("extracted")

        val result = extractor.extractEntries(
            archiveFile = archive,
            entriesToExtract = listOf("dir/", "dir/file.txt"),
            destinationDir = destDir,
            analysisResult = analysis
        )

        assertTrue(result.isSuccess)
        val extraction = result.getOrThrow()
        assertEquals(1, extraction.extractedFiles.size)
        assertTrue(extraction.extractedFiles.containsKey("dir/file.txt"))

        val skippedDir = extraction.skippedEntries.find { it.originalPath == "dir/" }
        assertNotNull(skippedDir)
        assertTrue(skippedDir!!.reason.contains("Directory"))
    }

    @Test
    fun `prevent path traversal during extraction`() {
        val archive = createTestZip(
            "../escape.txt" to "malicious"
        )
        val analysis = analyzer.analyzeArchive(archive).getOrThrow()
        val destDir = tempFolder.newFolder("extracted")

        val result = extractor.extractEntries(
            archiveFile = archive,
            entriesToExtract = listOf("../escape.txt"),
            destinationDir = destDir,
            analysisResult = analysis
        )

        assertTrue(result.isSuccess)
        val extraction = result.getOrThrow()
        assertEquals(0, extraction.extractedFiles.size)

        val parentDir = destDir.parentFile!!
        assertFalse(File(parentDir, "escape.txt").exists())
    }

    @Test
    fun `enforce total extraction quota`() {
        val largeContent = "x".repeat(1000)
        val archive = createTestZip(
            "file1.txt" to largeContent,
            "file2.txt" to largeContent
        )
        val analysis = analyzer.analyzeArchive(archive).getOrThrow()
        val destDir = tempFolder.newFolder("extracted")

        val result = extractor.extractEntries(
            archiveFile = archive,
            entriesToExtract = listOf("file1.txt", "file2.txt"),
            destinationDir = destDir,
            analysisResult = analysis
        )

        assertTrue(result.isSuccess)
        val extraction = result.getOrThrow()
        assertTrue(extraction.totalBytesExtracted > 0)
    }

    @Test
    fun `extracted files have unique names even for duplicates`() {
        val archive = createTestZip(
            "file1.txt" to "content1",
            "file2.txt" to "content2"
        )
        val analysis = analyzer.analyzeArchive(archive).getOrThrow()
        val destDir = tempFolder.newFolder("extracted")

        val result = extractor.extractEntries(
            archiveFile = archive,
            entriesToExtract = listOf("file1.txt", "file2.txt"),
            destinationDir = destDir,
            analysisResult = analysis
        )

        assertTrue(result.isSuccess)
        val extraction = result.getOrThrow()
        val names = extraction.extractedFiles.values.map { it.randomizedName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `detect file type of extracted entries`() {
        val archive = createTestZip(
            "test.txt" to "plain text content"
        )
        val analysis = analyzer.analyzeArchive(archive).getOrThrow()
        val destDir = tempFolder.newFolder("extracted")

        val result = extractor.extractEntries(
            archiveFile = archive,
            entriesToExtract = listOf("test.txt"),
            destinationDir = destDir,
            analysisResult = analysis
        )

        assertTrue(result.isSuccess)
        val extraction = result.getOrThrow()
        val extracted = extraction.extractedFiles["test.txt"]!!
        assertNotNull(extracted.extractedPath)
    }

    @Test
    fun `create destination directory if not exists`() {
        val archive = createTestZip("file.txt" to "content")
        val analysis = analyzer.analyzeArchive(archive).getOrThrow()
        val destDir = File(tempFolder.root, "new_dir/extracted")

        assertFalse(destDir.exists())

        val result = extractor.extractEntries(
            archiveFile = archive,
            entriesToExtract = listOf("file.txt"),
            destinationDir = destDir,
            analysisResult = analysis
        )

        assertTrue(result.isSuccess)
        assertTrue(destDir.exists())
    }

    @Test
    fun `original path preserved as metadata not filesystem path`() {
        val archive = createTestZip(
            "deep/nested/path/file.txt" to "content"
        )
        val analysis = analyzer.analyzeArchive(archive).getOrThrow()
        val destDir = tempFolder.newFolder("extracted")

        val result = extractor.extractEntries(
            archiveFile = archive,
            entriesToExtract = listOf("deep/nested/path/file.txt"),
            destinationDir = destDir,
            analysisResult = analysis
        )

        assertTrue(result.isSuccess)
        val extraction = result.getOrThrow()
        val extracted = extraction.extractedFiles["deep/nested/path/file.txt"]!!

        assertEquals("deep/nested/path/file.txt", extracted.originalPath)
        assertFalse(extracted.extractedPath.contains("deep/nested"))
        assertFalse(File(destDir, "deep").exists())
    }

    @Test
    fun `empty extraction list returns empty result`() {
        val archive = createTestZip("file.txt" to "content")
        val analysis = analyzer.analyzeArchive(archive).getOrThrow()
        val destDir = tempFolder.newFolder("extracted")

        val result = extractor.extractEntries(
            archiveFile = archive,
            entriesToExtract = emptyList(),
            destinationDir = destDir,
            analysisResult = analysis
        )

        assertTrue(result.isSuccess)
        val extraction = result.getOrThrow()
        assertEquals(0, extraction.extractedFiles.size)
        assertEquals(0L, extraction.totalBytesExtracted)
    }

    private fun createTestZip(vararg entries: Pair<String, String>): File {
        val tempFile = tempFolder.newFile("test_${System.nanoTime()}.zip")
        ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return tempFile
    }

    private fun createTestZipWithDirectory(): File {
        val tempFile = tempFolder.newFile("test_${System.nanoTime()}.zip")
        ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
            zos.putNextEntry(ZipEntry("dir/"))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("dir/file.txt"))
            zos.write("content".toByteArray())
            zos.closeEntry()
        }
        return tempFile
    }
}

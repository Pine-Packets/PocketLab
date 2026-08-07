package com.pineandpackets.pocketlab.engine.archive

import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NestedArchiveTest {
    
    private val analyzer = ArchiveAnalyzer()
    
    @Test
    fun `analyze archive with nested archive`() {
        // Create a nested archive (zip inside zip)
        val outerArchive = createNestedArchive()
        
        val result = analyzer.analyzeArchive(outerArchive)
        
        assertTrue(result.isSuccess)
        val analysis = result.getOrThrow()
        
        // Should have 2 entries: nested.zip and file.txt
        assertEquals(2, analysis.entryCount)
        
        // Find the nested archive entry
        val nestedEntry = analysis.entries.find { it.originalPath == "nested.zip" }
        assertNotNull(nestedEntry)
        assertTrue(nestedEntry!!.isNestedArchive)
        assertFalse(nestedEntry.isDirectory)
        assertFalse(nestedEntry.isEncrypted)
        
        // The nested archive should have been analyzed
        assertNotNull(nestedEntry.nestedArchiveResult)
        val nestedResult = nestedEntry.nestedArchiveResult!!
        assertEquals(2, nestedResult.entryCount) // nested.zip contains file1.txt and file2.txt
        
        outerArchive.delete()
    }
    
    @Test
    fun `nested archive respects depth limit`() {
        // Create a deeply nested archive (3 levels deep, but limit is 2)
        val deepArchive = createDeeplyNestedArchive(depth = 3)
        
        val result = analyzer.analyzeArchive(deepArchive)
        
        // Should succeed but not analyze beyond depth 2
        assertTrue(result.isSuccess)
        val analysis = result.getOrThrow()
        
        // First level should be analyzed (level3.zip)
        val level3Entry = analysis.entries.find { it.originalPath == "level3.zip" }
        assertNotNull(level3Entry)
        assertTrue(level3Entry!!.isNestedArchive)
        assertNotNull(level3Entry.nestedArchiveResult)
        
        // Second level should be analyzed (level2.zip inside level3.zip)
        val level3Result = level3Entry.nestedArchiveResult!!
        val level2Entry = level3Result.entries.find { it.originalPath == "level2.zip" }
        assertNotNull(level2Entry)
        assertTrue(level2Entry!!.isNestedArchive)
        assertNotNull(level2Entry.nestedArchiveResult)
        
        // Third level should NOT be analyzed (depth limit exceeded)
        val level2Result = level2Entry.nestedArchiveResult!!
        val level1Entry = level2Result.entries.find { it.originalPath == "level1.zip" }
        assertNotNull(level1Entry)
        assertTrue(level1Entry!!.isNestedArchive)
        assertNull(level1Entry.nestedArchiveResult) // Should not be analyzed
        
        deepArchive.delete()
    }
    
    @Test
    fun `global quota tracker limits total entries across nested archives`() {
        // Create archives that would exceed the global entry limit
        val quota = ArchiveQuotaTracker()
        
        // Simulate processing many entries
        for (i in 0 until AnalysisLimits.MAX_ARCHIVE_ENTRIES) {
            quota.addEntry()
        }
        
        // Should not be able to process more
        assertFalse(quota.canProcessMoreEntries())
        
        // Create an archive and try to analyze with exhausted quota
        val archive = createTestZip("file.txt" to "content")
        val result = analyzer.analyzeArchive(archive, globalQuota = quota)
        
        // Should fail due to quota
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("quota") == true)
        
        archive.delete()
    }
    
    @Test
    fun `global quota tracker limits total bytes across nested archives`() {
        val quota = ArchiveQuotaTracker()
        
        // Simulate processing many bytes
        quota.addExpandedBytes(AnalysisLimits.MAX_ARCHIVE_EXPANDED_BYTES + 1)
        
        // Should not be able to process more
        assertFalse(quota.canProcessMoreBytes())
        
        // Create an archive and try to analyze with exhausted quota
        val archive = createTestZip("file.txt" to "content")
        val result = analyzer.analyzeArchive(archive, globalQuota = quota)
        
        // Should fail due to quota
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("quota") == true)
        
        archive.delete()
    }
    
    @Test
    fun `depth limit is enforced`() {
        val quota = ArchiveQuotaTracker()
        
        // Simulate reaching max depth
        quota.updateDepth(AnalysisLimits.MAX_NESTING_DEPTH + 1)
        
        // Should not be able to process at this depth
        assertFalse(quota.canProcessDepth(AnalysisLimits.MAX_NESTING_DEPTH + 1))
        
        // Create an archive and try to analyze at exceeded depth
        val archive = createTestZip("file.txt" to "content")
        val result = analyzer.analyzeArchive(
            archive, 
            currentDepth = AnalysisLimits.MAX_NESTING_DEPTH + 1,
            globalQuota = quota
        )
        
        // Should fail due to depth limit
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("depth") == true)
        
        archive.delete()
    }
    
    @Test
    fun `directory entries are not recursively analyzed`() {
        // Create an archive with a directory entry that looks like an archive
        val archive = createArchiveWithDirectoryNamedAsArchive()
        
        val result = analyzer.analyzeArchive(archive)
        
        assertTrue(result.isSuccess)
        val analysis = result.getOrThrow()
        
        // Find the directory entry
        val dirEntry = analysis.entries.find { it.originalPath == "fake_archive.zip/" }
        assertNotNull(dirEntry)
        assertTrue(dirEntry!!.isDirectory)
        
        // Should NOT have nested analysis result (directories are not recursively analyzed)
        assertNull(dirEntry.nestedArchiveResult)
        
        archive.delete()
    }
    
    @Test
    fun `quota tracker tracks max depth correctly`() {
        val quota = ArchiveQuotaTracker()
        
        assertEquals(0, quota.maxDepthReached)
        
        quota.updateDepth(1)
        assertEquals(1, quota.maxDepthReached)
        
        quota.updateDepth(3)
        assertEquals(3, quota.maxDepthReached)
        
        quota.updateDepth(2)
        assertEquals(3, quota.maxDepthReached) // Should still be 3
    }
    
    @Test
    fun `nested archive result reports nested depth`() {
        val outerArchive = createNestedArchive()
        
        val result = analyzer.analyzeArchive(outerArchive)
        
        assertTrue(result.isSuccess)
        val analysis = result.getOrThrow()
        
        // Nested depth should be 1 (one level of nesting)
        assertEquals(1, analysis.nestedDepth)
        
        outerArchive.delete()
    }
    
    @Test
    fun `deeply nested archive reports depth up to limit`() {
        val deepArchive = createDeeplyNestedArchive(depth = 3)
        
        val result = analyzer.analyzeArchive(deepArchive)
        
        assertTrue(result.isSuccess)
        val analysis = result.getOrThrow()
        
        // Depth should be capped at the nesting limit (2)
        assertEquals(AnalysisLimits.MAX_NESTING_DEPTH, analysis.nestedDepth)
        
        deepArchive.delete()
    }
    
    @Test
    fun `archive result reports max observed compression ratio`() {
        // Create an archive with a highly compressible file
        val archive = createTestZip("big.txt" to "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        
        val result = analyzer.analyzeArchive(archive)
        
        assertTrue(result.isSuccess)
        val analysis = result.getOrThrow()
        
        assertTrue("Expected positive ratio, got ${analysis.maxObservedRatio}", analysis.maxObservedRatio > 1.0)
        
        archive.delete()
    }
    
    @Test
    fun `unsupported compression method entries are reported`() {
        val archive = createZipWithUnsupportedMethod()
        
        val result = analyzer.analyzeArchive(archive)
        
        assertTrue(result.isSuccess)
        val analysis = result.getOrThrow()
        
        assertTrue(analysis.unsupportedEntries.any { it.contains("unsupported") })
        
        archive.delete()
    }
    
    private fun createNestedArchive(): File {
        val tempFile = File.createTempFile("nested_test_", ".zip")
        
        // First, create the inner archive
        val innerArchive = createTestZip(
            "file1.txt" to "content1",
            "file2.txt" to "content2"
        )
        
        // Now create the outer archive containing the inner one
        ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
            // Add the inner archive
            zos.putNextEntry(ZipEntry("nested.zip"))
            innerArchive.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
            
            // Add a regular file
            zos.putNextEntry(ZipEntry("file.txt"))
            zos.write("outer content".toByteArray())
            zos.closeEntry()
        }
        
        innerArchive.delete()
        return tempFile
    }
    
    private fun createDeeplyNestedArchive(depth: Int): File {
        if (depth == 0) {
            return createTestZip("file.txt" to "content")
        }
        
        val innerArchive = createDeeplyNestedArchive(depth - 1)
        val outerArchive = File.createTempFile("level${depth}_", ".zip")
        
        ZipOutputStream(FileOutputStream(outerArchive)).use { zos ->
            zos.putNextEntry(ZipEntry("level$depth.zip"))
            innerArchive.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
        
        innerArchive.delete()
        return outerArchive
    }
    
    private fun createArchiveWithDirectoryNamedAsArchive(): File {
        val tempFile = File.createTempFile("dir_test_", ".zip")
        
        ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
            // Add a directory entry that looks like an archive
            val entry = ZipEntry("fake_archive.zip/")
            zos.putNextEntry(entry)
            zos.closeEntry()
            
            // Add a regular file
            zos.putNextEntry(ZipEntry("file.txt"))
            zos.write("content".toByteArray())
            zos.closeEntry()
        }
        
        return tempFile
    }
    
    private fun createTestZip(vararg entries: Pair<String, String>): File {
        val tempFile = File.createTempFile("test_", ".zip")
        ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
            entries.forEach { (name, content) ->
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return tempFile
    }
    
    private fun createZipWithUnsupportedMethod(): File {
        val tempFile = File.createTempFile("unsupported_", ".zip")
        // Write a minimal valid ZIP where the single entry uses compression method 99.
        // Both the local file header and central directory must agree.
        val name = "unsupported.dat".toByteArray()
        val data = ByteArray(0)
        val localHeaderOffset = 0
        
        val bytes = ByteArrayOutputStream().apply {
            // --- Local file header ---
            write(0x50); write(0x4B); write(0x03); write(0x04) // signature
            write(0x14); write(0x00) // version needed (2.0)
            write(0x00); write(0x00) // general purpose flags
            write(0x63); write(0x00) // method 99
            write(0x00); write(0x00) // mod time
            write(0x00); write(0x00) // mod date
            write(0x00); write(0x00); write(0x00); write(0x00) // crc32
            writeIntLE(data.size); writeIntLE(data.size) // compressed and uncompressed size
            writeShortLE(name.size); writeShortLE(0) // filename length, extra length
            write(name)
            write(data)
            
            // --- Central directory header ---
            val cdStart = size()
            write(0x50); write(0x4B); write(0x01); write(0x02) // signature
            write(0x14); write(0x00) // version made by
            write(0x14); write(0x00) // version needed
            write(0x00); write(0x00) // flags
            write(0x63); write(0x00) // method 99
            write(0x00); write(0x00) // mod time
            write(0x00); write(0x00) // mod date
            write(0x00); write(0x00); write(0x00); write(0x00) // crc32
            writeIntLE(data.size); writeIntLE(data.size) // sizes
            writeShortLE(name.size); writeShortLE(0); writeShortLE(0) // name, extra, comment lengths
            writeShortLE(0); writeShortLE(0) // disk number, internal attrs
            write(0x00); write(0x00); write(0x00); write(0x00) // external attrs
            writeIntLE(localHeaderOffset) // local header offset
            write(name)
            
            // --- End of central directory ---
            val eocdStart = size()
            write(0x50); write(0x4B); write(0x05); write(0x06) // signature
            writeShortLE(0); writeShortLE(0) // disk number, cd disk
            writeShortLE(1); writeShortLE(1) // entries this disk, total entries
            writeIntLE(eocdStart - cdStart) // cd size
            writeIntLE(cdStart) // cd offset
            writeShortLE(0) // comment length
        }
        
        tempFile.writeBytes(bytes.toByteArray())
        return tempFile
    }
    
    private fun ByteArrayOutputStream.writeIntLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }
    
    private fun ByteArrayOutputStream.writeShortLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }
}

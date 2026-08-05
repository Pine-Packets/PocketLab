package com.pineandpackets.pocketlab.engine.archive

import com.pineandpackets.pocketlab.core.common.AnalysisError
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveAnalyzerTest {
    
    private val analyzer = ArchiveAnalyzer()
    
    @Test
    fun `analyze valid ZIP archive`() {
        val tempFile = createTestZip(
            "file1.txt" to "content1",
            "file2.txt" to "content2"
        )
        
        val result = analyzer.analyzeArchive(tempFile)
        
        assertTrue(result.isSuccess)
        val analysis = result.getOrThrow()
        assertEquals(2, analysis.entryCount)
        assertEquals(2, analysis.entries.size)
        assertFalse(analysis.isEncrypted)
        
        tempFile.delete()
    }
    
    @Test
    fun `detect path traversal attempts`() {
        val tempFile = createTestZipWithSuspiciousPaths(
            "../etc/passwd",
            "normal/file.txt",
            "..\\windows\\system32"
        )
        
        val result = analyzer.analyzeArchive(tempFile)
        
        assertTrue(result.isSuccess)
        val analysis = result.getOrThrow()
        assertTrue(analysis.suspiciousPaths.isNotEmpty())
        
        tempFile.delete()
    }
    
    @Test
    fun `reject archive with too many entries`() {
        val entries = (1..6000).map { "file$it.txt" to "content" }.toMap()
        val tempFile = createTestZip(entries)
        
        val result = analyzer.analyzeArchive(tempFile)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AnalysisError.QuotaExceededError)
        
        tempFile.delete()
    }
    
    @Test
    fun `normalize paths correctly`() {
        val tempFile = createTestZip(
            "./file.txt" to "content",
            "dir/./file.txt" to "content",
            "dir/subdir/../file.txt" to "content"
        )
        
        val result = analyzer.analyzeArchive(tempFile)
        
        assertTrue(result.isSuccess)
        val analysis = result.getOrThrow()
        
        val normalizedPaths = analysis.entries.map { it.normalizedPath }
        assertTrue(normalizedPaths.all { !it.contains("./") })
        assertTrue(normalizedPaths.all { !it.contains("..") })
        
        tempFile.delete()
    }
    
    @Test
    fun `calculate total sizes correctly`() {
        val tempFile = createTestZip(
            "file1.txt" to "a".repeat(1000),
            "file2.txt" to "b".repeat(2000)
        )
        
        val result = analyzer.analyzeArchive(tempFile)
        
        assertTrue(result.isSuccess)
        val analysis = result.getOrThrow()
        assertTrue(analysis.totalExpandedSize >= 3000)
        
        tempFile.delete()
    }
    
    private fun createTestZip(vararg entries: Pair<String, String>): File {
        return createTestZip(entries.toMap())
    }
    
    private fun createTestZip(entries: Map<String, String>): File {
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
    
    private fun createTestZipWithSuspiciousPaths(vararg paths: String): File {
        val tempFile = File.createTempFile("test_", ".zip")
        ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
            paths.forEach { path ->
                val entry = ZipEntry(path)
                zos.putNextEntry(entry)
                zos.write("content".toByteArray())
                zos.closeEntry()
            }
        }
        return tempFile
    }
}

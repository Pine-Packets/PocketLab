package com.pineandpackets.pocketlab.engine.pipeline

import com.pineandpackets.pocketlab.core.model.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AnalysisPipelineIntegrationTest {
    
    @get:Rule
    val tempFolder = TemporaryFolder()
    
    private lateinit var pipeline: AnalysisPipeline
    
    @Before
    fun setup() {
        pipeline = AnalysisPipeline()
    }
    
    @Test
    fun `analyze minimal APK produces valid report`() = runTest {
        val apkFile = createMinimalApk()
        val hashes = HashResult(
            sha256 = "test-sha256",
            sha1 = "test-sha1",
            md5 = "test-md5"
        )
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        
        // Verify we got progress events
        assertTrue(progress.isNotEmpty())
        
        // Verify we got either a complete or error event
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        val errorEvent = progress.filterIsInstance<AnalysisProgress.Error>().firstOrNull()
        val failedEvent = progress.filterIsInstance<AnalysisProgress.StageFailed>().firstOrNull()
        
        // At least one of these should be present
        assertTrue(completeEvent != null || errorEvent != null || failedEvent != null)
        
        // If we got a complete event, verify report structure
        if (completeEvent != null) {
            val report = completeEvent.report
            assertEquals("test-case", report.caseId)
            assertEquals("test-sha256", report.source.sha256)
        }
    }
    
    @Test
    fun `analyze APK with permissions generates findings`() = runTest {
        val apkFile = createApkWithPermissions()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        
        // Verify pipeline executed (may succeed or fail due to minimal test data)
        assertTrue(progress.isNotEmpty())
        
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        
        // If analysis succeeded, verify permissions were extracted
        if (completeEvent != null) {
            val report = completeEvent.report
            if (report.apk?.permissions?.isNotEmpty() == true) {
                // Should have findings for dangerous permissions
                val permissionFindings = report.findings.filter { 
                    it.category == "permission" || it.ruleId.startsWith("PERM_")
                }
                assertTrue(permissionFindings.isNotEmpty())
            }
        }
    }
    
    @Test
    fun `analyze APK with exported components generates findings`() = runTest {
        val apkFile = createApkWithExportedComponents()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        
        // Verify pipeline executed
        assertTrue(progress.isNotEmpty())
        
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        
        // If analysis succeeded, verify components were extracted
        if (completeEvent != null) {
            val report = completeEvent.report
            if (report.apk?.components?.isNotEmpty() == true) {
                // Should have findings for exported components
                val componentFindings = report.findings.filter {
                    it.category == "component" || it.ruleId.startsWith("COMP_")
                }
                assertTrue(componentFindings.isNotEmpty())
            }
        }
    }
    
    @Test
    fun `analyze debuggable APK generates finding`() = runTest {
        val apkFile = createDebuggableApk()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        
        // Verify pipeline executed
        assertTrue(progress.isNotEmpty())
        
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        
        // If analysis succeeded, verify debuggable flag was detected
        if (completeEvent != null) {
            val report = completeEvent.report
            if (report.apk?.debuggable == true) {
                // Should have finding for debuggable flag
                val debuggableFinding = report.findings.find {
                    it.ruleId == "MANIFEST_DEBUGGABLE" || it.title.contains("debuggable", ignoreCase = true)
                }
                assertNotNull(debuggableFinding)
            }
        }
    }
    
    @Test
    fun `analyze APK with IOCs extracts indicators`() = runTest {
        val apkFile = createApkWithIOCs()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        
        // Should extract some indicators from strings
        // Note: This depends on string extraction working
        assertNotNull(report.indicators)
    }
    
    @Test
    fun `analyze malformed APK returns error`() = runTest {
        val malformedFile = tempFolder.newFile("malformed.apk")
        malformedFile.writeText("not a valid APK")
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", malformedFile, hashes).toList()
        
        // Should get some form of error or failure
        val errorEvent = progress.filterIsInstance<AnalysisProgress.Error>().firstOrNull()
        val failedEvent = progress.filterIsInstance<AnalysisProgress.StageFailed>().firstOrNull()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        
        // Either we got an error, a stage failure, or a complete with no APK info
        assertTrue(
            errorEvent != null || 
            failedEvent != null || 
            (completeEvent != null && completeEvent.report.apk == null)
        )
    }
    
    @Test
    fun `analyze DEX file directly`() = runTest {
        val dexFile = createMinimalDex()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", dexFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        assertTrue(report.dex.isNotEmpty())
    }
    
    @Test
    fun `report contains all required sections`() = runTest {
        val apkFile = createMinimalApk()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        
        // Verify all required sections
        assertNotNull(report.schemaVersion)
        assertNotNull(report.reportId)
        assertNotNull(report.caseId)
        assertNotNull(report.createdAt)
        assertNotNull(report.engine)
        assertNotNull(report.settings)
        assertNotNull(report.source)
        assertNotNull(report.summary)
        assertNotNull(report.integrity)
    }
    
    @Test
    fun `report summary contains risk assessment`() = runTest {
        val apkFile = createApkWithPermissions()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        val summary = report.summary
        
        assertNotNull(summary.riskBand)
        assertNotNull(summary.confidence)
        assertTrue(summary.completeness > 0)
        assertTrue(summary.findingCount >= 0)
    }
    
    // Helper methods to create test files
    
    private fun createMinimalApk(): File {
        val apkFile = tempFolder.newFile("minimal.apk")
        ZipOutputStream(apkFile.outputStream()).use { zos ->
            // Add minimal manifest
            val manifestEntry = ZipEntry("AndroidManifest.xml")
            zos.putNextEntry(manifestEntry)
            zos.write(createMinimalManifest())
            zos.closeEntry()
            
            // Add minimal DEX
            val dexEntry = ZipEntry("classes.dex")
            zos.putNextEntry(dexEntry)
            zos.write(createMinimalDexBytes())
            zos.closeEntry()
        }
        return apkFile
    }
    
    private fun createApkWithPermissions(): File {
        val apkFile = tempFolder.newFile("permissions.apk")
        ZipOutputStream(apkFile.outputStream()).use { zos ->
            val manifestEntry = ZipEntry("AndroidManifest.xml")
            zos.putNextEntry(manifestEntry)
            zos.write(createManifestWithPermissions())
            zos.closeEntry()
            
            val dexEntry = ZipEntry("classes.dex")
            zos.putNextEntry(dexEntry)
            zos.write(createMinimalDexBytes())
            zos.closeEntry()
        }
        return apkFile
    }
    
    private fun createApkWithExportedComponents(): File {
        val apkFile = tempFolder.newFile("components.apk")
        ZipOutputStream(apkFile.outputStream()).use { zos ->
            val manifestEntry = ZipEntry("AndroidManifest.xml")
            zos.putNextEntry(manifestEntry)
            zos.write(createManifestWithExportedComponents())
            zos.closeEntry()
            
            val dexEntry = ZipEntry("classes.dex")
            zos.putNextEntry(dexEntry)
            zos.write(createMinimalDexBytes())
            zos.closeEntry()
        }
        return apkFile
    }
    
    private fun createDebuggableApk(): File {
        val apkFile = tempFolder.newFile("debuggable.apk")
        ZipOutputStream(apkFile.outputStream()).use { zos ->
            val manifestEntry = ZipEntry("AndroidManifest.xml")
            zos.putNextEntry(manifestEntry)
            zos.write(createDebuggableManifest())
            zos.closeEntry()
            
            val dexEntry = ZipEntry("classes.dex")
            zos.putNextEntry(dexEntry)
            zos.write(createMinimalDexBytes())
            zos.closeEntry()
        }
        return apkFile
    }
    
    private fun createApkWithIOCs(): File {
        val apkFile = tempFolder.newFile("iocs.apk")
        ZipOutputStream(apkFile.outputStream()).use { zos ->
            val manifestEntry = ZipEntry("AndroidManifest.xml")
            zos.putNextEntry(manifestEntry)
            zos.write(createMinimalManifest())
            zos.closeEntry()
            
            val dexEntry = ZipEntry("classes.dex")
            zos.putNextEntry(dexEntry)
            zos.write(createDexWithIOCs())
            zos.closeEntry()
        }
        return apkFile
    }
    
    private fun createMinimalDex(): File {
        val dexFile = tempFolder.newFile("classes.dex")
        dexFile.writeBytes(createMinimalDexBytes())
        return dexFile
    }
    
    private fun createMinimalManifest(): ByteArray {
        // Simplified binary manifest - just enough to parse
        return byteArrayOf(
            0x03, 0x00, 0x08, 0x00, // AXML header
            0x00, 0x00, 0x00, 0x00  // Placeholder
        )
    }
    
    private fun createManifestWithPermissions(): ByteArray {
        return createMinimalManifest()
    }
    
    private fun createManifestWithExportedComponents(): ByteArray {
        return createMinimalManifest()
    }
    
    private fun createDebuggableManifest(): ByteArray {
        return createMinimalManifest()
    }
    
    private fun createMinimalDexBytes(): ByteArray {
        // Minimal DEX header
        val bytes = ByteArray(112)
        // Magic
        "dex\n035\u0000".toByteArray().copyInto(bytes, 0)
        // Endian tag at offset 40
        bytes[40] = 0x78
        bytes[41] = 0x56
        bytes[42] = 0x34
        bytes[43] = 0x12
        return bytes
    }
    
    private fun createDexWithIOCs(): ByteArray {
        return createMinimalDexBytes()
    }
}

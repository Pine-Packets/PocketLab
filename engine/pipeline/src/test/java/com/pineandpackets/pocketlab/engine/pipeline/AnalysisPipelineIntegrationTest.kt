package com.pineandpackets.pocketlab.engine.pipeline

import com.pineandpackets.pocketlab.core.model.*
import com.pineandpackets.pocketlab.engine.api.AnalysisProfile
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
    
    @Test
    fun `analyze minimal APK produces valid report`() = runTest {
        val pipeline = AnalysisPipeline()
        val apkFile = createMinimalApk()
        val hashes = HashResult(
            sha256 = "test-sha256",
            sha1 = "test-sha1",
            md5 = "test-md5"
        )
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        
        assertTrue(progress.isNotEmpty())
        
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        assertEquals("test-case", report.caseId)
        assertEquals("test-sha256", report.source.sha256)
        assertNotNull(report.stageResults)
        assertTrue(report.stageResults.isNotEmpty())
    }
    
    @Test
    fun `analyze with custom config uses config settings`() = runTest {
        val config = AnalysisConfig(
            analysisProfile = AnalysisProfile.ADVANCED,
            iocExtractionEnabled = false,
            deepDexAnalysisEnabled = false,
            sourceDisplayName = "custom.apk",
            sourceMimeType = "application/vnd.android.package-archive"
        )
        val pipeline = AnalysisPipeline(config)
        val apkFile = createMinimalApk()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        assertEquals("advanced", report.settings.analysisProfile)
        assertFalse(report.settings.iocExtractionEnabled)
        assertFalse(report.settings.deepDexAnalysisEnabled)
        assertEquals("custom.apk", report.source.displayName)
        assertEquals("application/vnd.android.package-archive", report.source.mimeType)
    }
    
    @Test
    fun `ioc extraction disabled produces skipped stage`() = runTest {
        val config = AnalysisConfig(iocExtractionEnabled = false)
        val pipeline = AnalysisPipeline(config)
        val apkFile = createMinimalApk()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        val iocStage = report.stageResults.find { it.stageId == "ioc" }
        assertNotNull(iocStage)
        assertEquals(StageState.SKIPPED, iocStage!!.state)
        assertTrue(report.limitations.any { it.contains("IOC extraction disabled") })
    }
    
    @Test
    fun `deep dex analysis disabled produces skipped stage when APK analysis succeeds`() = runTest {
        val config = AnalysisConfig(deepDexAnalysisEnabled = false)
        val pipeline = AnalysisPipeline(config)
        val apkFile = createMinimalApk()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        
        val apkStage = report.stageResults.find { it.stageId == "apk" }
        if (apkStage?.state == StageState.COMPLETE) {
            val codeStage = report.stageResults.find { it.stageId == "code_analysis" }
            assertNotNull(codeStage)
            assertEquals(StageState.SKIPPED, codeStage!!.state)
            assertTrue(report.limitations.any { it.contains("Deep DEX analysis disabled") })
        }
    }
    
    @Test
    fun `report contains all required sections`() = runTest {
        val pipeline = AnalysisPipeline()
        val apkFile = createMinimalApk()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        
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
        val pipeline = AnalysisPipeline()
        val apkFile = createMinimalApk()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        val summary = report.summary
        
        assertNotNull(summary.riskBand)
        assertNotNull(summary.confidence)
        assertTrue(summary.completeness >= 0.0)
        assertTrue(summary.completeness <= 1.0)
        assertTrue(summary.findingCount >= 0)
    }
    
    @Test
    fun `report completeness reflects stage results`() = runTest {
        val pipeline = AnalysisPipeline()
        val apkFile = createMinimalApk()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        val completedStages = report.stageResults.count { it.state == StageState.COMPLETE }
        val totalStages = report.stageResults.size
        
        if (totalStages > 0) {
            val expectedCompleteness = completedStages.toDouble() / totalStages.toDouble()
            assertEquals(expectedCompleteness, report.summary.completeness, 0.01)
        }
    }
    
    @Test
    fun `analyze malformed APK returns error`() = runTest {
        val pipeline = AnalysisPipeline()
        val malformedFile = tempFolder.newFile("malformed.apk")
        malformedFile.writeText("not a valid APK")
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", malformedFile, hashes).toList()
        
        val errorEvent = progress.filterIsInstance<AnalysisProgress.Error>().firstOrNull()
        val failedEvent = progress.filterIsInstance<AnalysisProgress.StageFailed>().firstOrNull()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        
        assertTrue(
            errorEvent != null || 
            failedEvent != null || 
            (completeEvent != null && completeEvent.report.apk == null)
        )
    }
    
    @Test
    fun `analyze DEX file directly`() = runTest {
        val pipeline = AnalysisPipeline()
        val dexFile = createMinimalDex()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", dexFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        assertTrue(report.dex.isNotEmpty())
    }
    
    @Test
    fun `archive section populated for APK`() = runTest {
        val pipeline = AnalysisPipeline()
        val apkFile = createMinimalApk()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        assertNotNull(report.archive)
        
        val archive = report.archive!!
        assertEquals("ZIP", archive.archiveType)
        assertEquals(2, archive.entryCount)
        assertFalse(archive.encrypted)
        assertTrue(archive.analyzedChildren.isNotEmpty())
    }
    
    @Test
    fun `archive section null for non-archive file`() = runTest {
        val pipeline = AnalysisPipeline()
        val dexFile = createMinimalDex()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", dexFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        assertNull(report.archive)
    }
    
    @Test
    fun `stage results track timing`() = runTest {
        val pipeline = AnalysisPipeline()
        val apkFile = createMinimalApk()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        val completedStages = report.stageResults.filter { it.state == StageState.COMPLETE }
        
        for (stage in completedStages) {
            assertNotNull("Stage ${stage.stageId} should have startedAt", stage.startedAt)
            assertNotNull("Stage ${stage.stageId} should have completedAt", stage.completedAt)
        }
    }
    
    @Test
    fun `report limitations include signature warning`() = runTest {
        val pipeline = AnalysisPipeline()
        val apkFile = createMinimalApk()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        assertTrue(report.limitations.any { it.contains("Signature verification") })
        assertTrue(report.limitations.any { it.contains("runtime behavior") })
    }
    
    @Test
    fun `report errors populated on stage failure`() = runTest {
        val pipeline = AnalysisPipeline()
        val malformedFile = tempFolder.newFile("malformed.apk")
        malformedFile.writeText("not a valid APK")
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", malformedFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        
        if (completeEvent != null) {
            val report = completeEvent.report
            val failedStages = report.stageResults.filter { it.state == StageState.FAILED }
            if (failedStages.isNotEmpty()) {
                assertTrue(report.errors.isNotEmpty())
            }
        }
    }
    
    @Test
    fun `confidence adjusts based on stage failures`() = runTest {
        val pipeline = AnalysisPipeline()
        val malformedFile = tempFolder.newFile("malformed.apk")
        malformedFile.writeText("not a valid APK")
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", malformedFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        
        if (completeEvent != null) {
            val report = completeEvent.report
            val failedStages = report.stageResults.count { it.state == StageState.FAILED }
            if (failedStages > 0) {
                assertNotEquals(Confidence.HIGH, report.summary.confidence)
            }
        }
    }
    
    @Test
    fun `analyze APKS package set produces merged report`() = runTest {
        val pipeline = AnalysisPipeline()
        val apksFile = createMinimalApks()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", apksFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        val packagesetStage = report.stageResults.find { it.stageId == "packageset" }
        assertNotNull(packagesetStage)
        
        apksFile.delete()
    }
    
    @Test
    fun `analyze XAPK package set produces merged report`() = runTest {
        val pipeline = AnalysisPipeline()
        val xapkFile = createMinimalXapk()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", xapkFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        val packagesetStage = report.stageResults.find { it.stageId == "packageset" }
        assertNotNull(packagesetStage)
        
        xapkFile.delete()
    }
    
    @Test
    fun `analyze regular APK does not trigger packageset stage`() = runTest {
        val pipeline = AnalysisPipeline()
        val apkFile = createMinimalApk()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        val packagesetStage = report.stageResults.find { it.stageId == "packageset" }
        assertNull(packagesetStage)
    }

    @Test
    fun `analyze raw APK runs APK analysis stages`() = runTest {
        val pipeline = AnalysisPipeline()
        val apkFile = createMinimalApk()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", apkFile, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        val apkStage = report.stageResults.find { it.stageId == "apk" }
        assertNotNull("Raw APK analysis should run an 'apk' stage", apkStage)
        val structureStage = report.stageResults.find { it.stageId == "apk_structure" }
        assertNotNull("Raw APK analysis should run an 'apk_structure' stage", structureStage)
    }
    
    @Test
    fun `analyze case archive with contained APK extracts and analyzes APK`() = runTest {
        val pipeline = AnalysisPipeline()
        val caseZip = createCaseArchiveWithApk()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", caseZip, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        val apkStage = report.stageResults.find { it.stageId == "apk" }
        assertNotNull("Case archive containing an APK should run an 'apk' stage", apkStage)
        
        // Archive section should inventory both the APK entry and the non-APK note entry
        val archive = report.archive
        assertNotNull(archive)
        assertTrue(
            "Archive section should inventory the contained APK entry",
            archive!!.analyzedChildren.any { it.path.endsWith(".apk") }
        )
        assertTrue(
            "Archive section should inventory non-APK case notes",
            archive.analyzedChildren.any { it.path.endsWith(".txt") }
        )
        
        assertTrue(
            "Report should record container provenance for the analyzed APK",
            report.limitations.any { it.contains("archive container") || it.contains("inside an archive") }
        )
    }

    @Test
    fun `case archive notes inventory extracts indicators with provenance`() = runTest {
        val pipeline = AnalysisPipeline()
        val caseZip = createCaseArchiveWithApk()
        val hashes = HashResult("sha256", "sha1", "md5")

        val progress = pipeline.analyze("test-case", caseZip, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)

        val report = completeEvent!!.report
        val archive = report.archive
        assertNotNull(archive)

        val textEntries = archive!!.textEntryInventory
        assertTrue(
            "Archive notes inventory should include the analyst_notes.txt entry",
            textEntries.any { it.path == "analyst_notes.txt" }
        )

        val notesEntry = textEntries.first { it.path == "analyst_notes.txt" }
        assertTrue(
            "Notes inventory should extract the URL from the notes text",
            notesEntry.indicators.any { it.type == IndicatorType.URL && it.canonicalValue.contains("evil.example.com") }
        )
        assertTrue(
            "Notes indicators should carry the archive entry provenance",
            notesEntry.indicators.all {
                (it.source as? IndicatorSource)?.entry == "analyst_notes.txt" &&
                    (it.source as? IndicatorSource)?.container != null
            }
        )
    }
    
    @Test
    fun `analyze split APK set built from multiple files produces package set report`() = runTest {
        val pipeline = AnalysisPipeline()
        val apk1 = createMinimalApk()
        val apk2 = createMinimalApk()
        val outputDir = tempFolder.newFolder("split_out")
        val builder = com.pineandpackets.pocketlab.engine.archive.SplitApkSetBuilder()
        val buildResult = builder.build(listOf(apk1, apk2), outputDir)
        assertTrue(buildResult.isSuccess)
        
        val splitSet = buildResult.getOrNull()!!.containerFile
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", splitSet, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        val packagesetStage = report.stageResults.find { it.stageId == "packageset" }
        assertNotNull(
            "Split APK set built from multiple user files should run a 'packageset' stage",
            packagesetStage
        )
    }
    
    @Test
    fun `analyze plain archive without APK does not run APK stages`() = runTest {
        val pipeline = AnalysisPipeline()
        val plainZip = createPlainArchive()
        val hashes = HashResult("sha256", "sha1", "md5")
        
        val progress = pipeline.analyze("test-case", plainZip, hashes).toList()
        val completeEvent = progress.filterIsInstance<AnalysisProgress.Complete>().firstOrNull()
        assertNotNull(completeEvent)
        
        val report = completeEvent!!.report
        val apkStage = report.stageResults.find { it.stageId == "apk" }
        assertNull("Plain archive without an APK should not run an 'apk' stage", apkStage)
    }
    
    private fun createMinimalApk(): File {
        val apkFile = tempFolder.newFile("minimal_${System.nanoTime()}.apk")
        ZipOutputStream(apkFile.outputStream()).use { zos ->
            val manifestEntry = ZipEntry("AndroidManifest.xml")
            zos.putNextEntry(manifestEntry)
            zos.write(createMinimalManifest())
            zos.closeEntry()
            
            val dexEntry = ZipEntry("classes.dex")
            zos.putNextEntry(dexEntry)
            zos.write(createMinimalDexBytes())
            zos.closeEntry()
        }
        return apkFile
    }
    
    private fun createMinimalDex(): File {
        val dexFile = tempFolder.newFile("classes_${System.nanoTime()}.dex")
        dexFile.writeBytes(createMinimalDexBytes())
        return dexFile
    }
    
    private fun createCaseArchiveWithApk(): File {
        val caseFile = tempFolder.newFile("case_${System.nanoTime()}.zip")
        ZipOutputStream(caseFile.outputStream()).use { zos ->
            val apkEntry = ZipEntry("evidence/sample.apk")
            zos.putNextEntry(apkEntry)
            zos.write(createMinimalApkBytes())
            zos.closeEntry()
            
            val noteEntry = ZipEntry("analyst_notes.txt")
            zos.putNextEntry(noteEntry)
            zos.write(
                "Suspicious APK received via email on 2026-08-06\nhttps://evil.example.com/payload.apk".toByteArray()
            )
            zos.closeEntry()
        }
        return caseFile
    }
    
    private fun createPlainArchive(): File {
        val plainFile = tempFolder.newFile("plain_${System.nanoTime()}.zip")
        ZipOutputStream(plainFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("readme.txt"))
            zos.write("hello world".toByteArray())
            zos.closeEntry()
        }
        return plainFile
    }
    
    private fun createMinimalApks(): File {
        val apksFile = tempFolder.newFile("test_${System.nanoTime()}.apks")
        ZipOutputStream(apksFile.outputStream()).use { zos ->
            val baseApk = ZipEntry("base.apk")
            zos.putNextEntry(baseApk)
            zos.write(createMinimalApkBytes())
            zos.closeEntry()
            
            val splitApk = ZipEntry("split_config.arm64_v8a.apk")
            zos.putNextEntry(splitApk)
            zos.write(createMinimalApkBytes())
            zos.closeEntry()
            
            val bundleConfig = ZipEntry("BundleConfig.pb")
            zos.putNextEntry(bundleConfig)
            zos.write(byteArrayOf(0x00, 0x01, 0x02))
            zos.closeEntry()
        }
        return apksFile
    }
    
    private fun createMinimalXapk(): File {
        val xapkFile = tempFolder.newFile("test_${System.nanoTime()}.xapk")
        ZipOutputStream(xapkFile.outputStream()).use { zos ->
            val manifest = ZipEntry("manifest.json")
            zos.putNextEntry(manifest)
            val manifestJson = """
                {
                    "xapk_version": 2,
                    "package_name": "com.example.test",
                    "version_name": "1.0.0",
                    "version_code": 1
                }
            """.trimIndent()
            zos.write(manifestJson.toByteArray())
            zos.closeEntry()
            
            val apkEntry = ZipEntry("com.example.test.apk")
            zos.putNextEntry(apkEntry)
            zos.write(createMinimalApkBytes())
            zos.closeEntry()
            
            val splitApk = ZipEntry("com.example.test.split.apk")
            zos.putNextEntry(splitApk)
            zos.write(createMinimalApkBytes())
            zos.closeEntry()
        }
        return xapkFile
    }
    
    private fun createMinimalApkBytes(): ByteArray {
        val tempFile = File.createTempFile("temp_apk", ".apk")
        ZipOutputStream(tempFile.outputStream()).use { zos ->
            val manifestEntry = ZipEntry("AndroidManifest.xml")
            zos.putNextEntry(manifestEntry)
            zos.write(createMinimalManifest())
            zos.closeEntry()
            
            val dexEntry = ZipEntry("classes.dex")
            zos.putNextEntry(dexEntry)
            zos.write(createMinimalDexBytes())
            zos.closeEntry()
        }
        val bytes = tempFile.readBytes()
        tempFile.delete()
        return bytes
    }
    
    private fun createMinimalManifest(): ByteArray {
        return byteArrayOf(
            0x03, 0x00, 0x08, 0x00,
            0x00, 0x00, 0x00, 0x00
        )
    }
    
    private fun createMinimalDexBytes(): ByteArray {
        val bytes = ByteArray(112)
        "dex\n035\u0000".toByteArray().copyInto(bytes, 0)
        bytes[40] = 0x78
        bytes[41] = 0x56
        bytes[42] = 0x34
        bytes[43] = 0x12
        return bytes
    }
}

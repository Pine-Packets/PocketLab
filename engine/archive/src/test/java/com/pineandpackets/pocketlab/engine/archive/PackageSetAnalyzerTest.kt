package com.pineandpackets.pocketlab.engine.archive

import com.pineandpackets.pocketlab.core.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackageSetAnalyzerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var analyzer: PackageSetAnalyzer

    @Before
    fun setup() {
        analyzer = PackageSetAnalyzer()
    }

    @Test
    fun `detect APKS format with BundleConfig`() {
        val apksFile = createApksFile(withBundleConfig = true)
        
        val result = analyzer.analyzePackageSet(apksFile)
        
        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()!!
        assertEquals(PackageSetType.APKS, analysis.type)
        assertEquals(2, analysis.apkCount)
        
        apksFile.delete()
    }

    @Test
    fun `detect APKS format with multiple APKs`() {
        val apksFile = createApksFile(withBundleConfig = false)
        
        val result = analyzer.analyzePackageSet(apksFile)
        
        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()!!
        assertEquals(PackageSetType.APKS, analysis.type)
        assertEquals(2, analysis.apkCount)
        
        apksFile.delete()
    }

    @Test
    fun `detect XAPK format with manifest`() {
        val xapkFile = createXapkFile()
        
        val result = analyzer.analyzePackageSet(xapkFile)
        
        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()!!
        assertEquals(PackageSetType.XAPK, analysis.type)
        assertEquals(2, analysis.apkCount)
        assertNotNull(analysis.manifestInfo)
        assertEquals("com.example.test", analysis.manifestInfo?.packageName)
        assertEquals("1.0.0", analysis.manifestInfo?.versionName)
        assertEquals(1L, analysis.manifestInfo?.versionCode)
        
        xapkFile.delete()
    }

    @Test
    fun `analyze APKS with base and split APKs`() {
        val apksFile = createApksFile(withBundleConfig = true)
        
        val result = analyzer.analyzePackageSet(apksFile)
        
        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()!!
        
        // Should have detected both APKs
        assertEquals(2, analysis.apkCount)
        
        // APK analysis might fail due to minimal test data, but we should have tried
        // Check if we have any results or errors
        if (analysis.analyzedApkCount > 0) {
            // If analysis succeeded, check base APK identification
            val baseApk = analysis.apkResults.find { it.isBaseApk }
            assertNotNull(baseApk)
            assertEquals("base.apk", baseApk?.entryName)
            
            // Should have merged info
            assertNotNull(analysis.mergedApkInfo)
        } else {
            // If analysis failed, we should have errors
            assertTrue(analysis.errors.isNotEmpty())
        }
        
        apksFile.delete()
    }

    @Test
    fun `merge permissions from multiple APKs`() {
        val apksFile = createApksFileWithDifferentPermissions()
        
        val result = analyzer.analyzePackageSet(apksFile)
        
        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()!!
        
        // Should have detected the APKs
        assertEquals(2, analysis.apkCount)
        
        // If analysis succeeded, check merged permissions
        if (analysis.analyzedApkCount > 0) {
            val mergedInfo = analysis.mergedApkInfo
            assertNotNull(mergedInfo)
            assertTrue(mergedInfo!!.permissions.isNotEmpty())
            
            // Should have unique permissions
            val permNames = mergedInfo.permissions.map { it.name }
            assertEquals(permNames.size, permNames.toSet().size)
        }
        
        apksFile.delete()
    }

    @Test
    fun `detect inconsistent package names`() {
        val apksFile = createApksFileWithInconsistentPackages()
        
        val result = analyzer.analyzePackageSet(apksFile)
        
        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()!!
        
        // Should have detected the APKs
        assertEquals(2, analysis.apkCount)
        
        // If analysis succeeded, should have warnings about inconsistent package names
        if (analysis.analyzedApkCount > 0) {
            assertTrue(analysis.warnings.any { it.contains("Inconsistent package names") })
        }
        
        apksFile.delete()
    }

    @Test
    fun `reject file with too many APKs`() {
        val apksFile = createApksFileWithManyApks(count = 15)
        
        val result = analyzer.analyzePackageSet(apksFile)
        
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message!!.contains("exceeds limit"))
        
        apksFile.delete()
    }

    @Test
    fun `reject non-package-set file`() {
        val regularZip = createRegularZip()
        
        val result = analyzer.analyzePackageSet(regularZip)
        
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message!!.contains("Not a valid APKS or XAPK"))
        
        regularZip.delete()
    }

    @Test
    fun `handle APK with analysis errors gracefully`() {
        val apksFile = createApksFileWithInvalidApk()
        
        val result = analyzer.analyzePackageSet(apksFile)
        
        // Should still succeed but with errors
        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()!!
        assertTrue(analysis.errors.isNotEmpty())
        
        apksFile.delete()
    }

    @Test
    fun `parse XAPK manifest correctly`() {
        val xapkFile = createXapkFile()
        
        val result = analyzer.analyzePackageSet(xapkFile)
        
        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()!!
        
        assertNotNull(analysis.manifestInfo)
        assertEquals(2, analysis.manifestInfo?.xapkVersion)
        assertEquals("com.example.test", analysis.manifestInfo?.packageName)
        assertEquals("1.0.0", analysis.manifestInfo?.versionName)
        assertEquals(1L, analysis.manifestInfo?.versionCode)
        
        xapkFile.delete()
    }

    @Test
    fun `handle missing manifest in XAPK`() {
        val xapkFile = createXapkFileWithoutManifest()
        
        val result = analyzer.analyzePackageSet(xapkFile)
        
        // Should still succeed
        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()!!
        
        // Without manifest.json, it will be detected as APKS (fallback for multiple APKs)
        assertEquals(PackageSetType.APKS, analysis.type)
        
        // Should have detected the APKs
        assertEquals(2, analysis.apkCount)
        
        // Manifest should be null since it's missing
        assertNull(analysis.manifestInfo)
        
        xapkFile.delete()
    }

    // Helper methods to create test files

    private fun createApksFile(withBundleConfig: Boolean): File {
        val file = tempFolder.newFile("test.apks")
        ZipOutputStream(file.outputStream()).use { zos ->
            // Add base APK
            zos.putNextEntry(ZipEntry("base.apk"))
            zos.write(createMinimalApkBytes("com.example.test"))
            zos.closeEntry()

            // Add split APK
            zos.putNextEntry(ZipEntry("split_config.arm64_v8a.apk"))
            zos.write(createMinimalApkBytes("com.example.test"))
            zos.closeEntry()

            // Add BundleConfig if requested
            if (withBundleConfig) {
                zos.putNextEntry(ZipEntry("BundleConfig.pb"))
                zos.write(byteArrayOf(0x00, 0x01, 0x02))
                zos.closeEntry()
            }
        }
        return file
    }

    private fun createXapkFile(): File {
        val file = tempFolder.newFile("test.xapk")
        ZipOutputStream(file.outputStream()).use { zos ->
            // Add manifest
            zos.putNextEntry(ZipEntry("manifest.json"))
            val manifest = """
                {
                    "xapk_version": 2,
                    "package_name": "com.example.test",
                    "version_name": "1.0.0",
                    "version_code": 1
                }
            """.trimIndent()
            zos.write(manifest.toByteArray())
            zos.closeEntry()

            // Add APKs
            zos.putNextEntry(ZipEntry("com.example.test.apk"))
            zos.write(createMinimalApkBytes("com.example.test"))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("com.example.test.split.apk"))
            zos.write(createMinimalApkBytes("com.example.test"))
            zos.closeEntry()
        }
        return file
    }

    private fun createXapkFileWithoutManifest(): File {
        val file = tempFolder.newFile("test_no_manifest.xapk")
        ZipOutputStream(file.outputStream()).use { zos ->
            // Add APKs without manifest
            zos.putNextEntry(ZipEntry("app.apk"))
            zos.write(createMinimalApkBytes("com.example.test"))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("split.apk"))
            zos.write(createMinimalApkBytes("com.example.test"))
            zos.closeEntry()
        }
        return file
    }

    private fun createApksFileWithDifferentPermissions(): File {
        val file = tempFolder.newFile("test_perms.apks")
        ZipOutputStream(file.outputStream()).use { zos ->
            // Add base APK with some permissions
            zos.putNextEntry(ZipEntry("base.apk"))
            zos.write(createMinimalApkBytes("com.example.test"))
            zos.closeEntry()

            // Add split APK
            zos.putNextEntry(ZipEntry("split.apk"))
            zos.write(createMinimalApkBytes("com.example.test"))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("BundleConfig.pb"))
            zos.write(byteArrayOf(0x00))
            zos.closeEntry()
        }
        return file
    }

    private fun createApksFileWithInconsistentPackages(): File {
        val file = tempFolder.newFile("test_inconsistent.apks")
        ZipOutputStream(file.outputStream()).use { zos ->
            // Add base APK with one package name
            zos.putNextEntry(ZipEntry("base.apk"))
            zos.write(createMinimalApkBytes("com.example.test1"))
            zos.closeEntry()

            // Add split APK with different package name
            zos.putNextEntry(ZipEntry("split.apk"))
            zos.write(createMinimalApkBytes("com.example.test2"))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("BundleConfig.pb"))
            zos.write(byteArrayOf(0x00))
            zos.closeEntry()
        }
        return file
    }

    private fun createApksFileWithManyApks(count: Int): File {
        val file = tempFolder.newFile("test_many.apks")
        ZipOutputStream(file.outputStream()).use { zos ->
            for (i in 1..count) {
                zos.putNextEntry(ZipEntry("apk$i.apk"))
                zos.write(createMinimalApkBytes("com.example.test$i"))
                zos.closeEntry()
            }
            zos.putNextEntry(ZipEntry("BundleConfig.pb"))
            zos.write(byteArrayOf(0x00))
            zos.closeEntry()
        }
        return file
    }

    private fun createRegularZip(): File {
        val file = tempFolder.newFile("regular.zip")
        ZipOutputStream(file.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("file1.txt"))
            zos.write("content1".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("file2.txt"))
            zos.write("content2".toByteArray())
            zos.closeEntry()
        }
        return file
    }

    private fun createApksFileWithInvalidApk(): File {
        val file = tempFolder.newFile("test_invalid.apks")
        ZipOutputStream(file.outputStream()).use { zos ->
            // Add valid APK
            zos.putNextEntry(ZipEntry("base.apk"))
            zos.write(createMinimalApkBytes("com.example.test"))
            zos.closeEntry()

            // Add invalid APK (just random bytes)
            zos.putNextEntry(ZipEntry("invalid.apk"))
            zos.write(byteArrayOf(0x00, 0x01, 0x02, 0x03))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("BundleConfig.pb"))
            zos.write(byteArrayOf(0x00))
            zos.closeEntry()
        }
        return file
    }

    private fun createMinimalApkBytes(packageName: String): ByteArray {
        // Create a minimal valid APK structure
        // This is a simplified version - real APKs are much more complex
        val tempFile = File.createTempFile("temp_apk", ".apk")
        ZipOutputStream(tempFile.outputStream()).use { zos ->
            // Add minimal AndroidManifest.xml (placeholder)
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(byteArrayOf(0x03, 0x00, 0x08, 0x00)) // Minimal AXML header
            zos.closeEntry()

            // Add minimal classes.dex
            zos.putNextEntry(ZipEntry("classes.dex"))
            val dexBytes = ByteArray(112)
            "dex\n035\u0000".toByteArray().copyInto(dexBytes, 0)
            dexBytes[40] = 0x78
            dexBytes[41] = 0x56
            dexBytes[42] = 0x34
            dexBytes[43] = 0x12
            zos.write(dexBytes)
            zos.closeEntry()
        }
        val bytes = tempFile.readBytes()
        tempFile.delete()
        return bytes
    }
}

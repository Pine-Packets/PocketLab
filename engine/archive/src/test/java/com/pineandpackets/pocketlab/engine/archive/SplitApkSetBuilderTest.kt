package com.pineandpackets.pocketlab.engine.archive

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class SplitApkSetBuilderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val builder = SplitApkSetBuilder()

    @Test
    fun `build container from two APKs is detected as APKS`() {
        val apk1 = createMinimalApk("com.example.one")
        val apk2 = createMinimalApk("com.example.two")
        val outputDir = tempFolder.newFolder("out")

        val result = builder.build(listOf(apk1, apk2), outputDir)

        assertTrue(result.isSuccess)
        val build = result.getOrNull()!!
        assertEquals(2, build.apkCount)
        assertTrue(build.containerFile.exists())

        // The container must be recognized as an APKS package set
        val packageSetAnalyzer = PackageSetAnalyzer()
        assertEquals(PackageSetType.APKS, packageSetAnalyzer.detectPackageSetType(build.containerFile))
    }

    @Test
    fun `first APK becomes base entry`() {
        val apk1 = createMinimalApk("com.example.base")
        val apk2 = createMinimalApk("com.example.split")

        val result = builder.build(listOf(apk1, apk2), tempFolder.newFolder("out"))
        assertTrue(result.isSuccess)

        val container = result.getOrNull()!!.containerFile
        ZipFile(container).use { zip ->
            assertNotNull(zip.getEntry("base.apk"))
            assertNotNull(zip.getEntry("split_1.apk"))
            assertNotNull(zip.getEntry("BundleConfig.pb"))
        }
    }

    @Test
    fun `stored entries preserve exact APK bytes`() {
        val apk1 = createMinimalApk("com.example.one")
        val apk2 = createMinimalApk("com.example.two")

        val result = builder.build(listOf(apk1, apk2), tempFolder.newFolder("out"))
        assertTrue(result.isSuccess)

        val container = result.getOrNull()!!.containerFile
        ZipFile(container).use { zip ->
            val baseEntry = zip.getEntry("base.apk")!!
            assertEquals(ZipEntry.STORED, baseEntry.method)
            assertEquals(apk1.length(), baseEntry.size)
            assertEquals(apk1.length(), baseEntry.compressedSize)
            assertEquals(
                apk1.readBytes().contentHashCode(),
                zip.getInputStream(baseEntry).readBytes().contentHashCode()
            )
        }
    }

    @Test
    fun `reject empty list`() {
        val result = builder.build(emptyList(), tempFolder.newFolder("out"))
        assertTrue(result.isFailure)
    }

    @Test
    fun `reject list exceeding max APK count`() {
        val files = (1..15).map { createMinimalApk("com.example.$it") }
        val result = builder.build(files, tempFolder.newFolder("out"))
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error!!.message!!.contains("exceeds limit"))
    }

    @Test
    fun `reject missing file`() {
        val missing = tempFolder.newFile("missing.apk")
        missing.delete()
        val result = builder.build(listOf(missing), tempFolder.newFolder("out"))
        assertTrue(result.isFailure)
    }

    @Test
    fun `cleanup container on failure`() {
        val outputDir = tempFolder.newFolder("out")
        val result = builder.build(emptyList(), outputDir)
        assertTrue(result.isFailure)
        // Nothing to clean up, but the call must not create files
        assertEquals(0, outputDir.listFiles()?.size ?: 0)
    }

    private fun createMinimalApk(packageName: String): File {
        val file = tempFolder.newFile("apk_${System.nanoTime()}.apk")
        ZipOutputStream(file.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(byteArrayOf(0x03, 0x00, 0x08, 0x00))
            zos.closeEntry()

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
        return file
    }
}

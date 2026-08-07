package com.pineandpackets.pocketlab.engine.apk

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApksigVerifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val verifier = ApksigVerifier()

    @Test
    fun `verify unsigned APK returns not verified`() {
        val apkFile = createUnsignedApk()

        val result = verifier.verify(apkFile)

        assertFalse(result.verified)
        assertNull(result.signingInfo)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `verify non-existent file returns error`() {
        val fakeFile = File(tempFolder.root, "nonexistent.apk")

        val result = verifier.verify(fakeFile)

        assertFalse(result.verified)
        assertNull(result.signingInfo)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `verify empty file returns error`() {
        val emptyFile = tempFolder.newFile("empty.apk")

        val result = verifier.verify(emptyFile)

        assertFalse(result.verified)
        assertNull(result.signingInfo)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `verify invalid ZIP returns error`() {
        val invalidFile = tempFolder.newFile("invalid.apk")
        invalidFile.writeText("not a zip file")

        val result = verifier.verify(invalidFile)

        assertFalse(result.verified)
        assertNull(result.signingInfo)
        assertTrue(result.errors.isNotEmpty())
    }

    private fun createUnsignedApk(): File {
        val apkFile = tempFolder.newFile("unsigned_${System.nanoTime()}.apk")
        ZipOutputStream(apkFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(byteArrayOf(0x03, 0x00, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00))
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
        return apkFile
    }
}

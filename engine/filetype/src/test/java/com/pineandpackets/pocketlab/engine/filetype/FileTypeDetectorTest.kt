package com.pineandpackets.pocketlab.engine.filetype

import com.pineandpackets.pocketlab.core.model.DetectedType
import com.pineandpackets.pocketlab.core.model.FileTypeConfidence
import org.junit.Assert.*
import org.junit.Test

class FileTypeDetectorTest {
    
    @Test
    fun `detect ZIP by magic bytes`() {
        val zipMagic = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        val result = FileTypeDetector.detect(zipMagic, "zip", null)
        assertEquals(DetectedType.ZIP.name, result.magicType)
        assertEquals(FileTypeConfidence.DEFINITIVE, result.confidence)
    }
    
    @Test
    fun `detect DEX by magic bytes`() {
        val dexMagic = "dex\n035\u0000".toByteArray()
        val result = FileTypeDetector.detect(dexMagic, "dex", null)
        assertEquals(DetectedType.DEX.name, result.magicType)
    }
    
    @Test
    fun `detect ELF by magic bytes`() {
        val elfMagic = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())
        val result = FileTypeDetector.detect(elfMagic, "so", null)
        assertEquals(DetectedType.ELF.name, result.magicType)
    }
    
    @Test
    fun `detect PE by magic bytes`() {
        val peMagic = "MZ".toByteArray()
        val result = FileTypeDetector.detect(peMagic, "exe", null)
        assertEquals(DetectedType.PE.name, result.magicType)
    }
    
    @Test
    fun `detect PDF by magic bytes`() {
        val pdfMagic = "%PDF".toByteArray()
        val result = FileTypeDetector.detect(pdfMagic, "pdf", null)
        assertEquals(DetectedType.PDF.name, result.magicType)
    }
    
    @Test
    fun `detect APK by extension`() {
        val result = FileTypeDetector.detect(ByteArray(4), "apk", null)
        assertEquals(DetectedType.APK.name, result.extensionType)
    }
    
    @Test
    fun `detect mismatch between magic and extension`() {
        val zipMagic = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        val result = FileTypeDetector.detect(zipMagic, "pdf", null)
        assertTrue(result.mismatchFlags.contains("MAGIC_EXTENSION_MISMATCH"))
    }
    
    @Test
    fun `unknown file type returns UNKNOWN confidence`() {
        val unknownBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        val result = FileTypeDetector.detect(unknownBytes, null, null)
        assertEquals(FileTypeConfidence.UNKNOWN, result.confidence)
    }
}

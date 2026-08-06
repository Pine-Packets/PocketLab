package com.pineandpackets.pocketlab.engine.native

import com.pineandpackets.pocketlab.core.common.AnalysisError
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfAnalyzerTest {
    
    private val analyzer = ElfAnalyzer()
    
    @Test
    fun `reject file too small for ELF header`() {
        val tempFile = File.createTempFile("test_", ".so")
        tempFile.writeBytes(ByteArray(32))
        
        val result = analyzer.analyzeElf(tempFile)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AnalysisError.ParserError)
        
        tempFile.delete()
    }
    
    @Test
    fun `reject file with invalid magic`() {
        val tempFile = File.createTempFile("test_", ".so")
        val bytes = ByteArray(64)
        bytes[0] = 0x00
        bytes[1] = 0x00
        bytes[2] = 0x00
        bytes[3] = 0x00
        tempFile.writeBytes(bytes)
        
        val result = analyzer.analyzeElf(tempFile)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AnalysisError.ParserError)
        
        tempFile.delete()
    }
    
    @Test
    fun `parse valid ELF header`() {
        val tempFile = createMinimalElf(is64bit = true, machine = 0xB7)
        
        val result = analyzer.analyzeElf(tempFile)
        
        assertTrue(result.isSuccess)
        val elfInfo = result.getOrThrow()
        assertTrue(elfInfo.is64bit)
        assertEquals("AArch64", elfInfo.architecture)
        assertEquals("arm64-v8a", elfInfo.abi)
        
        tempFile.delete()
    }
    
    @Test
    fun `detect 32-bit ARM architecture`() {
        val tempFile = createMinimalElf(is64bit = false, machine = 0x28)
        
        val result = analyzer.analyzeElf(tempFile)
        
        assertTrue(result.isSuccess)
        val elfInfo = result.getOrThrow()
        assertFalse(elfInfo.is64bit)
        assertEquals("ARM", elfInfo.architecture)
        assertEquals("armeabi-v7a", elfInfo.abi)
        
        tempFile.delete()
    }
    
    @Test
    fun `detect x86_64 architecture`() {
        val tempFile = createMinimalElf(is64bit = true, machine = 0x3E)
        
        val result = analyzer.analyzeElf(tempFile)
        
        assertTrue(result.isSuccess)
        val elfInfo = result.getOrThrow()
        assertTrue(elfInfo.is64bit)
        assertEquals("x86_64", elfInfo.architecture)
        
        tempFile.delete()
    }
    
    @Test
    fun `detect little endian encoding`() {
        val tempFile = createMinimalElf(is64bit = true, machine = 0xB7, littleEndian = true)
        
        val result = analyzer.analyzeElf(tempFile)
        
        assertTrue(result.isSuccess)
        val elfInfo = result.getOrThrow()
        assertTrue(elfInfo.isLittleEndian)
        
        tempFile.delete()
    }
    
    @Test
    fun `extract section count`() {
        val tempFile = createMinimalElf(is64bit = true, machine = 0xB7, sectionCount = 5)
        
        val result = analyzer.analyzeElf(tempFile)
        
        assertTrue(result.isSuccess)
        val elfInfo = result.getOrThrow()
        assertEquals(5, elfInfo.sectionCount)
        
        tempFile.delete()
    }
    
    @Test
    fun `report stripped status when no symbols`() {
        val tempFile = createMinimalElf(is64bit = true, machine = 0xB7)
        
        val result = analyzer.analyzeElf(tempFile)
        
        assertTrue(result.isSuccess)
        val elfInfo = result.getOrThrow()
        assertTrue(elfInfo.isStripped)
        
        tempFile.delete()
    }
    
    private fun createMinimalElf(
        is64bit: Boolean,
        machine: Int,
        littleEndian: Boolean = true,
        sectionCount: Int = 0
    ): File {
        val tempFile = File.createTempFile("test_", ".so")
        val buffer = ByteBuffer.allocate(256)
        buffer.order(if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        
        buffer.put(0x7F.toByte())
        buffer.put('E'.code.toByte())
        buffer.put('L'.code.toByte())
        buffer.put('F'.code.toByte())
        
        buffer.put(if (is64bit) 2.toByte() else 1.toByte())
        buffer.put(if (littleEndian) 1.toByte() else 2.toByte())
        buffer.put(1.toByte())
        buffer.put(0.toByte())
        buffer.put(0.toByte())
        
        buffer.put(ByteArray(7))
        
        buffer.position(16)
        buffer.putShort(2.toShort())
        buffer.putShort(machine.toShort())
        buffer.putInt(1)
        
        if (is64bit) {
            buffer.putLong(0L)
            buffer.putLong(0L)
            buffer.putLong(0L)
        } else {
            buffer.putInt(0)
            buffer.putInt(0)
            buffer.putInt(0)
        }
        
        buffer.putInt(0)
        buffer.putShort(64.toShort())
        buffer.putShort(0.toShort())
        buffer.putShort(0.toShort())
        buffer.putShort(if (is64bit) 64 else 40)
        buffer.putShort(sectionCount.toShort())
        buffer.putShort(0.toShort())
        
        FileOutputStream(tempFile).use { it.write(buffer.array()) }
        
        return tempFile
    }
}

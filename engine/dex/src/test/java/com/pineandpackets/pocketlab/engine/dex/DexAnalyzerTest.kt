package com.pineandpackets.pocketlab.engine.dex

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DexAnalyzerTest {
    
    private lateinit var analyzer: DexAnalyzer
    private lateinit var tempDir: File
    
    @Before
    fun setup() {
        analyzer = DexAnalyzer()
        tempDir = File(System.getProperty("java.io.tmpdir"), "dex-test-${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }
    
    @Test
    fun `analyze minimal valid DEX file`() {
        val dexFile = createMinimalDex()
        val result = analyzer.analyzeDex(dexFile, extractCode = false)
        
        if (result.isFailure) {
            println("DEX analysis failed: ${result.exceptionOrNull()?.message}")
            result.exceptionOrNull()?.printStackTrace()
        }
        
        assertTrue(result.isSuccess)
        val dexInfo = result.getOrNull()
        assertNotNull(dexInfo)
        assertEquals("035", dexInfo!!.version)
        assertEquals(0, dexInfo.stringCount)
        assertEquals(0, dexInfo.methodCount)
        assertEquals(0, dexInfo.classCount)
    }
    
    @Test
    fun `extract strings from DEX`() {
        val dexFile = createDexWithStrings(listOf("Hello", "World", "Test"))
        val result = analyzer.analyzeDex(dexFile, extractCode = true)
        
        assertTrue(result.isSuccess)
        val dexInfo = result.getOrNull()
        assertNotNull(dexInfo)
        assertEquals(3, dexInfo!!.strings.size)
        assertEquals("Hello", dexInfo.strings[0].value)
        assertEquals("World", dexInfo.strings[1].value)
        assertEquals("Test", dexInfo.strings[2].value)
    }
    
    @Test
    fun `handle DEX with too many strings`() {
        val dexFile = createDexWithExcessiveStringCount()
        val result = analyzer.analyzeDex(dexFile, extractCode = true)
        
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception!!.message?.contains("String count exceeds limit") == true)
    }
    
    @Test
    fun `handle DEX with too many methods`() {
        val dexFile = createDexWithExcessiveMethodCount()
        val result = analyzer.analyzeDex(dexFile, extractCode = true)
        
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception!!.message?.contains("Method count exceeds limit") == true)
    }
    
    @Test
    fun `handle DEX with too many classes`() {
        val dexFile = createDexWithExcessiveClassCount()
        val result = analyzer.analyzeDex(dexFile, extractCode = true)
        
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception!!.message?.contains("Class count exceeds limit") == true)
    }
    
    @Test
    fun `handle truncated DEX file`() {
        val dexFile = createTruncatedDex()
        val result = analyzer.analyzeDex(dexFile, extractCode = true)
        
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception!!.message?.contains("too small") == true)
    }
    
    @Test
    fun `handle invalid DEX magic`() {
        val dexFile = createDexWithInvalidMagic()
        val result = analyzer.analyzeDex(dexFile, extractCode = true)
        
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception!!.message?.contains("Invalid DEX magic") == true)
    }
    
    @Test
    fun `handle invalid endian tag`() {
        val dexFile = createDexWithInvalidEndianTag()
        val result = analyzer.analyzeDex(dexFile, extractCode = true)
        
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception!!.message?.contains("Invalid endian tag") == true)
    }
    
    @Test
    fun `skip code extraction when disabled`() {
        val dexFile = createDexWithStrings(listOf("Test"))
        val result = analyzer.analyzeDex(dexFile, extractCode = false)
        
        assertTrue(result.isSuccess)
        val dexInfo = result.getOrNull()
        assertNotNull(dexInfo)
        assertEquals(0, dexInfo!!.strings.size)
        assertEquals(0, dexInfo.methodIds.size)
        assertEquals(0, dexInfo.classDefs.size)
    }
    
    @Test
    fun `parse DEX version correctly`() {
        val dexFile = createDexWithVersion("037")
        val result = analyzer.analyzeDex(dexFile, extractCode = false)
        
        assertTrue(result.isSuccess)
        val dexInfo = result.getOrNull()
        assertNotNull(dexInfo)
        assertEquals("037", dexInfo!!.version)
    }
    
    @Test
    fun `handle empty string pool`() {
        val dexFile = createDexWithEmptyStringPool()
        val result = analyzer.analyzeDex(dexFile, extractCode = true)
        
        assertTrue(result.isSuccess)
        val dexInfo = result.getOrNull()
        assertNotNull(dexInfo)
        assertEquals(0, dexInfo!!.strings.size)
    }
    
    @Test
    fun `handle malformed string data`() {
        val dexFile = createDexWithMalformedStrings()
        val result = analyzer.analyzeDex(dexFile, extractCode = true)
        
        // Should succeed but with error strings
        assertTrue(result.isSuccess)
        val dexInfo = result.getOrNull()
        assertNotNull(dexInfo)
    }
    
    @Test
    fun `calculate correct file size`() {
        val dexFile = createMinimalDex()
        val result = analyzer.analyzeDex(dexFile, extractCode = false)
        
        assertTrue(result.isSuccess)
        val dexInfo = result.getOrNull()
        assertNotNull(dexInfo)
        assertEquals(dexFile.length(), dexInfo!!.size)
    }
    
    // Helper methods to create test DEX files
    
    private fun createMinimalDex(): File {
        val file = File(tempDir, "minimal.dex")
        val buffer = ByteBuffer.allocate(112).order(ByteOrder.LITTLE_ENDIAN)
        
        // Magic
        buffer.put("dex\n035\u0000".toByteArray())
        
        // Checksum (placeholder)
        buffer.putInt(0)
        
        // Signature (placeholder)
        buffer.put(ByteArray(20))
        
        // File size
        buffer.putInt(112)
        
        // Header size
        buffer.putInt(112)
        
        // Endian tag
        buffer.putInt(0x12345678)
        
        // Link size and offset
        buffer.putInt(0)
        buffer.putInt(0)
        
        // Map offset
        buffer.putInt(0)
        
        // String IDs
        buffer.putInt(0) // size
        buffer.putInt(0) // offset
        
        // Type IDs
        buffer.putInt(0)
        buffer.putInt(0)
        
        // Proto IDs
        buffer.putInt(0)
        buffer.putInt(0)
        
        // Field IDs
        buffer.putInt(0)
        buffer.putInt(0)
        
        // Method IDs
        buffer.putInt(0)
        buffer.putInt(0)
        
        // Class defs
        buffer.putInt(0)
        buffer.putInt(0)
        
        // Data size and offset
        buffer.putInt(0)
        buffer.putInt(0)
        
        file.writeBytes(buffer.array())
        return file
    }
    
    private fun createDexWithStrings(strings: List<String>): File {
        val file = File(tempDir, "strings.dex")
        
        // Calculate sizes
        val stringDataSize = strings.sumOf { it.toByteArray(Charsets.UTF_8).size + 2 }
        val totalSize = 112 + strings.size * 4 + stringDataSize
        
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        
        // Magic
        buffer.put("dex\n035\u0000".toByteArray())
        
        // Checksum
        buffer.putInt(0)
        
        // Signature
        buffer.put(ByteArray(20))
        
        // File size
        buffer.putInt(totalSize)
        
        // Header size
        buffer.putInt(112)
        
        // Endian tag
        buffer.putInt(0x12345678)
        
        // Link
        buffer.putInt(0)
        buffer.putInt(0)
        
        // Map
        buffer.putInt(0)
        
        // String IDs
        buffer.putInt(strings.size)
        buffer.putInt(112) // offset right after header
        
        // Type IDs
        buffer.putInt(0)
        buffer.putInt(0)
        
        // Proto IDs
        buffer.putInt(0)
        buffer.putInt(0)
        
        // Field IDs
        buffer.putInt(0)
        buffer.putInt(0)
        
        // Method IDs
        buffer.putInt(0)
        buffer.putInt(0)
        
        // Class defs
        buffer.putInt(0)
        buffer.putInt(0)
        
        // Data
        buffer.putInt(0)
        buffer.putInt(0)
        
        // String offsets
        var offset = 0
        strings.forEach { _ ->
            buffer.putInt(112 + strings.size * 4 + offset)
            offset += strings[strings.indexOfFirst { true }].toByteArray(Charsets.UTF_8).size + 2
        }
        
        // String data (simplified)
        strings.forEach { str ->
            val bytes = str.toByteArray(Charsets.UTF_8)
            buffer.put(bytes.size.toByte()) // length
            buffer.put(bytes)
            buffer.put(0) // null terminator
        }
        
        file.writeBytes(buffer.array())
        return file
    }
    
    private fun createDexWithExcessiveStringCount(): File {
        val file = File(tempDir, "excessive_strings.dex")
        val buffer = ByteBuffer.allocate(112).order(ByteOrder.LITTLE_ENDIAN)
        
        // Magic
        buffer.put("dex\n035\u0000".toByteArray())
        buffer.putInt(0)
        buffer.put(ByteArray(20))
        buffer.putInt(112)
        buffer.putInt(112)
        buffer.putInt(0x12345678)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        
        // Excessive string count (over limit)
        buffer.putInt(200_000)
        buffer.putInt(0)
        
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        
        file.writeBytes(buffer.array())
        return file
    }
    
    private fun createDexWithExcessiveMethodCount(): File {
        val file = File(tempDir, "excessive_methods.dex")
        val buffer = ByteBuffer.allocate(112).order(ByteOrder.LITTLE_ENDIAN)
        
        buffer.put("dex\n035\u0000".toByteArray())
        buffer.putInt(0)
        buffer.put(ByteArray(20))
        buffer.putInt(112)
        buffer.putInt(112)
        buffer.putInt(0x12345678)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        
        // Now at position 56, write all the ID table sizes and offsets
        buffer.putInt(0) // string_ids_size (56)
        buffer.putInt(0) // string_ids_off (60)
        buffer.putInt(0) // type_ids_size (64)
        buffer.putInt(0) // type_ids_off (68)
        buffer.putInt(0) // proto_ids_size (72)
        buffer.putInt(0) // proto_ids_off (76)
        buffer.putInt(0) // field_ids_size (80)
        buffer.putInt(0) // field_ids_off (84)
        
        // Excessive method count at position 88
        buffer.putInt(200_000) // method_ids_size (88)
        buffer.putInt(0)       // method_ids_off (92)
        
        buffer.putInt(0) // class_defs_size (96)
        buffer.putInt(0) // class_defs_off (100)
        buffer.putInt(0) // data_size (104)
        buffer.putInt(0) // data_off (108)
        
        file.writeBytes(buffer.array())
        return file
    }
    
    private fun createDexWithExcessiveClassCount(): File {
        val file = File(tempDir, "excessive_classes.dex")
        val buffer = ByteBuffer.allocate(112).order(ByteOrder.LITTLE_ENDIAN)
        
        buffer.put("dex\n035\u0000".toByteArray())
        buffer.putInt(0)
        buffer.put(ByteArray(20))
        buffer.putInt(112)
        buffer.putInt(112)
        buffer.putInt(0x12345678)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        
        // Now at position 56, write all the ID table sizes and offsets
        buffer.putInt(0) // string_ids_size (56)
        buffer.putInt(0) // string_ids_off (60)
        buffer.putInt(0) // type_ids_size (64)
        buffer.putInt(0) // type_ids_off (68)
        buffer.putInt(0) // proto_ids_size (72)
        buffer.putInt(0) // proto_ids_off (76)
        buffer.putInt(0) // field_ids_size (80)
        buffer.putInt(0) // field_ids_off (84)
        buffer.putInt(0) // method_ids_size (88)
        buffer.putInt(0) // method_ids_off (92)
        
        // Excessive class count at position 96
        buffer.putInt(100_000) // class_defs_size (96)
        buffer.putInt(0)       // class_defs_off (100)
        buffer.putInt(0) // data_size (104)
        buffer.putInt(0) // data_off (108)
        
        file.writeBytes(buffer.array())
        return file
    }
    
    private fun createTruncatedDex(): File {
        val file = File(tempDir, "truncated.dex")
        val buffer = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN)
        
        buffer.put("dex\n035\u0000".toByteArray())
        buffer.putInt(0)
        buffer.put(ByteArray(20))
        
        file.writeBytes(buffer.array())
        return file
    }
    
    private fun createDexWithInvalidMagic(): File {
        val file = File(tempDir, "invalid_magic.dex")
        val buffer = ByteBuffer.allocate(112).order(ByteOrder.LITTLE_ENDIAN)
        
        buffer.put("bad\n035\u0000".toByteArray())
        buffer.putInt(0)
        buffer.put(ByteArray(20))
        buffer.putInt(112)
        buffer.putInt(112)
        buffer.putInt(0x12345678)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        
        file.writeBytes(buffer.array())
        return file
    }
    
    private fun createDexWithInvalidEndianTag(): File {
        val file = File(tempDir, "invalid_endian.dex")
        val buffer = ByteBuffer.allocate(112).order(ByteOrder.LITTLE_ENDIAN)
        
        buffer.put("dex\n035\u0000".toByteArray())
        buffer.putInt(0)
        buffer.put(ByteArray(20))
        buffer.putInt(112)
        buffer.putInt(112)
        buffer.putInt(0x87654321.toInt()) // Wrong endian
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        
        file.writeBytes(buffer.array())
        return file
    }
    
    private fun createDexWithVersion(version: String): File {
        val file = File(tempDir, "version.dex")
        val buffer = ByteBuffer.allocate(112).order(ByteOrder.LITTLE_ENDIAN)
        
        buffer.put("dex\n${version}\u0000".toByteArray())
        buffer.putInt(0)
        buffer.put(ByteArray(20))
        buffer.putInt(112)
        buffer.putInt(112)
        buffer.putInt(0x12345678)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        
        file.writeBytes(buffer.array())
        return file
    }
    
    private fun createDexWithEmptyStringPool(): File {
        return createMinimalDex()
    }
    
    private fun createDexWithMalformedStrings(): File {
        val file = File(tempDir, "malformed_strings.dex")
        val buffer = ByteBuffer.allocate(120).order(ByteOrder.LITTLE_ENDIAN)
        
        buffer.put("dex\n035\u0000".toByteArray())
        buffer.putInt(0)
        buffer.put(ByteArray(20))
        buffer.putInt(120)
        buffer.putInt(112)
        buffer.putInt(0x12345678)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(1) // 1 string
        buffer.putInt(112) // offset
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        
        // Malformed string data
        buffer.put(0xFF.toByte()) // Invalid length
        buffer.put(0xFF.toByte())
        
        file.writeBytes(buffer.array())
        return file
    }
}

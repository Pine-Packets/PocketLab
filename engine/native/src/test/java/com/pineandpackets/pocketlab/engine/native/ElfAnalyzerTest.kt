package com.pineandpackets.pocketlab.engine.native

import com.pineandpackets.pocketlab.core.common.AnalysisError
import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun `report stripped status when no symtab symbols`() {
        val tempFile = createMinimalElf(is64bit = true, machine = 0xB7)

        val result = analyzer.analyzeElf(tempFile)

        assertTrue(result.isSuccess)
        val elfInfo = result.getOrThrow()
        assertTrue(elfInfo.isStripped)

        tempFile.delete()
    }

    @Test
    fun `reject oversized ELF file`() {
        val tempFile = File.createTempFile("test_", ".so")
        RandomAccessFile(tempFile, "rw").use {
            it.setLength(AnalysisLimits.MAX_SINGLE_ENTRY_BYTES + 1)
        }

        val result = analyzer.analyzeElf(tempFile)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AnalysisError.QuotaExceededError)

        tempFile.delete()
    }

    @Test
    fun `parse dynamic dependencies from dynamic section`() {
        val builder = Elf64Builder()
            .withProgramHeaders(listOf(PhdrSpec(PT_LOAD, PF_R or PF_X)))
            .withDynamicDependencies("libc.so", "liblog.so")
            .withDynamicSymbols(
                listOf("Java_com_example_Lib_init" to 0x12, "JNI_OnLoad" to 0x12)
            )
            .withSymtabSymbols("my_secret_function")
            .withSections(".text")
        val tempFile = writeTemp(builder.build())

        val result = analyzer.analyzeElf(tempFile)

        assertTrue(result.isSuccess)
        val elfInfo = result.getOrThrow()
        assertEquals(listOf("libc.so", "liblog.so"), elfInfo.dynamicDependencies)
        assertEquals(
            listOf("Java_com_example_Lib_init", "JNI_OnLoad"),
            elfInfo.dynamicSymbols.mapNotNull { it.name.takeIf(String::isNotEmpty) }
        )
        assertTrue(elfInfo.jniExports.contains("Java_com_example_Lib_init"))
        assertEquals("my_secret_function", elfInfo.symbols.first().name)
        assertFalse(elfInfo.isStripped)
        assertEquals(".text", elfInfo.sections.firstOrNull { it.name == ".text" }?.name)

        tempFile.delete()
    }

    @Test
    fun `executable writable segment detected`() {
        val builder = Elf64Builder()
            .withProgramHeaders(listOf(PhdrSpec(PT_LOAD, PF_R or PF_W or PF_X)))
        val tempFile = writeTemp(builder.build())

        val result = analyzer.analyzeElf(tempFile)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().hasExecutableWritableSegment)

        tempFile.delete()
    }

    @Test
    fun `executable read segment not flagged as writable executable`() {
        val builder = Elf64Builder()
            .withProgramHeaders(listOf(PhdrSpec(PT_LOAD, PF_R or PF_X)))
        val tempFile = writeTemp(builder.build())

        val result = analyzer.analyzeElf(tempFile)

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().hasExecutableWritableSegment)

        tempFile.delete()
    }

    @Test
    fun `truncated section header table is tolerated`() {
        val bytes = Elf64Builder()
            .withSections(".text")
            .build()
        val truncated = bytes.copyOf(bytes.size - 40)
        val tempFile = writeTemp(truncated)

        val result = analyzer.analyzeElf(tempFile)

        assertTrue(result.isSuccess)

        tempFile.delete()
    }

    private fun writeTemp(bytes: ByteArray): File {
        val tempFile = File.createTempFile("test_", ".so")
        tempFile.writeBytes(bytes)
        return tempFile
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

        tempFile.outputStream().use { it.write(buffer.array()) }

        return tempFile
    }

    private companion object {
        const val PT_LOAD = 1
        const val PF_X = 1L
        const val PF_W = 2L
        const val PF_R = 4L

        const val SHT_PROGBITS = 1
        const val SHT_SYMTAB = 2
        const val SHT_STRTAB = 3
        const val SHT_DYNAMIC = 6
        const val SHT_DYNSYM = 11

        const val DT_NEEDED = 1L
        const val DT_NULL = 0L
    }
}

data class PhdrSpec(val type: Int, val flags: Long)

/**
 * Builds a minimal synthetic 64-bit little-endian ELF shared object with a
 * valid section header table, program headers, symbol tables, string tables,
 * and a dynamic section. Used only for parser fixtures.
 */
private class Elf64Builder {

    private class Section(
        val name: String,
        val type: Int,
        val data: ByteArray,
        val link: Int = 0
    )

    private val sections = mutableListOf<Section>()
    private val programHeaders = mutableListOf<PhdrSpec>()
    private var symtabSymbols = emptyList<String>()
    private var dynamicSymbols = emptyList<Pair<String, Int>>()
    private var dynamicDependencies = emptyList<String>()

    fun withSections(vararg names: String): Elf64Builder {
        names.forEach { sections.add(Section(it, SHT_PROGBITS, ByteArray(0))) }
        return this
    }

    fun withProgramHeaders(specs: List<PhdrSpec>): Elf64Builder {
        programHeaders.addAll(specs)
        return this
    }

    fun withSymtabSymbols(vararg names: String): Elf64Builder {
        symtabSymbols = names.toList()
        return this
    }

    fun withDynamicSymbols(symbols: List<Pair<String, Int>>): Elf64Builder {
        dynamicSymbols = symbols
        return this
    }

    fun withDynamicDependencies(vararg deps: String): Elf64Builder {
        dynamicDependencies = deps.toList()
        return this
    }

    fun build(): ByteArray {
        val order = ByteOrder.LITTLE_ENDIAN

        // Build the section name string table.
        val shstrNames = listOf("", ".shstrtab", ".text", ".dynsym", ".dynstr", ".dynamic", ".symtab", ".strtab")
        val shstrTab = buildStringTable(shstrNames)
        val shstrNameIndex = shstrTab.second

        // Build string tables for symbol/dependency names.
        val strNames = listOf("") + symtabSymbols
        val strTab = buildStringTable(strNames)
        val strtabIndex = strTab.second

        val dynstrNames = listOf("") + dynamicSymbols.map { it.first } + dynamicDependencies
        val dynStr = buildStringTable(dynstrNames)
        val dynstrIndex = dynStr.second

        // Build section data blobs.
        val sectionData = mutableListOf<Section>()
        sectionData.add(Section(".shstrtab", SHT_STRTAB, shstrTab.first))
        sectionData.add(Section(".text", SHT_PROGBITS, ByteArray(8)))
        sectionData.add(Section(".dynsym", SHT_DYNSYM, buildDynSym(dynamicSymbols, dynstrIndex, order), link = 3))
        sectionData.add(Section(".dynstr", SHT_STRTAB, dynStr.first))
        sectionData.add(Section(".dynamic", SHT_DYNAMIC, buildDynamic(dynamicDependencies, dynstrIndex, order), link = 3))
        sectionData.add(Section(".symtab", SHT_SYMTAB, buildSymtab(symtabSymbols, strtabIndex, order), link = 6))
        sectionData.add(Section(".strtab", SHT_STRTAB, strTab.first))

        val headerSize = 64
        val phdrSize = 56 * programHeaders.size
        var cursor = headerSize + phdrSize
        val sectionOffsets = mutableListOf<Long>()
        for (section in sectionData) {
            sectionOffsets.add(cursor.toLong())
            cursor += section.data.size
        }
        val shOff = cursor
        val shNum = sectionData.size
        val shStrNdx = 0

        val total = shOff.toInt() + shNum * 64
        val buffer = ByteBuffer.allocate(total).order(order)

        // ELF header
        buffer.put(0x7F.toByte())
        buffer.put('E'.code.toByte())
        buffer.put('L'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put(2.toByte()) // ELFCLASS64
        buffer.put(1.toByte()) // little endian
        buffer.put(1.toByte()) // version
        buffer.put(0.toByte()) // osabi
        buffer.put(0.toByte()) // abiversion
        buffer.put(ByteArray(7))
        buffer.putShort(3.toShort()) // ET_DYN
        buffer.putShort(0xB7.toShort()) // AArch64
        buffer.putInt(1)
        buffer.putLong(0L) // entry
        buffer.putLong(headerSize.toLong()) // e_phoff
        buffer.putLong(shOff.toLong()) // e_shoff
        buffer.putInt(0) // flags
        buffer.putShort(64.toShort()) // e_ehsize
        buffer.putShort(56.toShort()) // e_phentsize
        buffer.putShort(programHeaders.size.toShort()) // e_phnum
        buffer.putShort(64.toShort()) // e_shentsize
        buffer.putShort(shNum.toShort()) // e_shnum
        buffer.putShort(shStrNdx.toShort()) // e_shstrndx

        // Program headers
        for (spec in programHeaders) {
            buffer.putInt(spec.type)
            buffer.putInt(spec.flags.toInt())
            buffer.putLong(0L) // p_offset
            buffer.putLong(0L) // p_vaddr
            buffer.putLong(0L) // p_paddr
            buffer.putLong(0L) // p_filesz
            buffer.putLong(0L) // p_memsz
            buffer.putLong(0L) // p_align
        }

        // Section data
        for ((index, section) in sectionData.withIndex()) {
            val offset = sectionOffsets[index]
            buffer.position(offset.toInt())
            buffer.put(section.data)
        }

        // Section header table
        for ((index, section) in sectionData.withIndex()) {
            buffer.position(shOff.toInt() + index * 64)
            buffer.putInt(shstrNameIndex[section.name] ?: 0)
            buffer.putInt(section.type)
            buffer.putLong(0L) // sh_flags
            buffer.putLong(0L) // sh_addr
            buffer.putLong(sectionOffsets[index]) // sh_offset
            buffer.putLong(section.data.size.toLong()) // sh_size
            buffer.putInt(section.link) // sh_link
            buffer.putInt(0) // sh_info
            buffer.putLong(0L) // sh_addralign
            buffer.putLong(0L) // sh_entsize
        }

        return buffer.array()
    }

    private fun buildStringTable(names: List<String>): Pair<ByteArray, Map<String, Int>> {
        val bytes = ByteArrayOutputStream()
        bytes.write(0)
        val indexMap = mutableMapOf<String, Int>()
        for (name in names) {
            indexMap[name] = bytes.size()
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            bytes.write(nameBytes)
            bytes.write(0)
        }
        return Pair(bytes.toByteArray(), indexMap)
    }

    private fun buildSymtab(names: List<String>, strtabIndex: Map<String, Int>, order: ByteOrder): ByteArray {
        val size = 24 * names.size
        val buffer = ByteBuffer.allocate(size).order(order)
        for (name in names) {
            val nameIdx = strtabIndex[name] ?: 0
            buffer.putInt(nameIdx)
            buffer.put(((1 shl 4) or 2).toByte()) // STB_GLOBAL, STT_FUNC
            buffer.put(0.toByte()) // st_other
            buffer.putShort(1.toShort()) // st_shndx
            buffer.putLong(0x1000L) // st_value
            buffer.putLong(16L) // st_size
        }
        return buffer.array()
    }

    private fun buildDynSym(symbols: List<Pair<String, Int>>, dynstrIndex: Map<String, Int>, order: ByteOrder): ByteArray {
        val size = 24 * symbols.size
        val buffer = ByteBuffer.allocate(size).order(order)
        for ((name, infoByte) in symbols) {
            buffer.putInt(dynstrIndex[name] ?: 0)
            buffer.put(infoByte.toByte())
            buffer.put(0.toByte()) // st_other
            buffer.putShort(1.toShort()) // st_shndx
            buffer.putLong(0x1000L) // st_value
            buffer.putLong(16L) // st_size
        }
        return buffer.array()
    }

    private fun buildDynamic(deps: List<String>, dynstrIndex: Map<String, Int>, order: ByteOrder): ByteArray {
        val entries = deps.map { DT_NEEDED to (dynstrIndex[it] ?: 0).toLong() } + listOf(DT_NULL to 0L)
        val buffer = ByteBuffer.allocate(16 * entries.size).order(order)
        for ((tag, value) in entries) {
            buffer.putLong(tag)
            buffer.putLong(value)
        }
        return buffer.array()
    }
}

private const val SHT_PROGBITS = 1
private const val SHT_SYMTAB = 2
private const val SHT_STRTAB = 3
private const val SHT_DYNAMIC = 6
private const val SHT_DYNSYM = 11

private const val DT_NEEDED = 1L
private const val DT_NULL = 0L

private class ByteArrayOutputStream {
    private val bytes = mutableListOf<Byte>()
    private var count = 0

    fun write(value: Int) {
        bytes.add(value.toByte())
        count++
    }

    fun write(data: ByteArray) {
        data.forEach { bytes.add(it) }
        count += data.size
    }

    fun size(): Int = count

    fun toByteArray(): ByteArray = bytes.toByteArray()
}

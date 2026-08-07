package com.pineandpackets.pocketlab.engine.native

import com.pineandpackets.pocketlab.core.common.AnalysisError
import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Read-only, bounded ELF parser for native library inventory.
 *
 * Treated as a hostile-input boundary: the file size is bounded before any
 * allocation, all section/symbol/program-header counts are capped, string
 * reads are length-limited, and every offset is validated against the buffer.
 */
class ElfAnalyzer {

    fun analyzeElf(elfFile: File): Result<ElfInfo> {
        return try {
            if (elfFile.length() > AnalysisLimits.MAX_SINGLE_ENTRY_BYTES) {
                return Result.failure(
                    AnalysisError.QuotaExceededError("ELF file exceeds size limit")
                )
            }

            val bytes = elfFile.readBytes()

            if (bytes.size < ELF_HEADER_SIZE) {
                return Result.failure(AnalysisError.ParserError("ELF file too small"))
            }

            val buffer = ByteBuffer.wrap(bytes)

            val magic = ByteArray(4)
            buffer.get(magic)

            if (magic[0] != 0x7F.toByte() ||
                magic[1] != 'E'.code.toByte() ||
                magic[2] != 'L'.code.toByte() ||
                magic[3] != 'F'.code.toByte()
            ) {
                return Result.failure(AnalysisError.ParserError("Invalid ELF magic"))
            }

            val elfClass = buffer.get()
            val is64bit = elfClass == 2.toByte()

            val dataEncoding = buffer.get()
            val isLittleEndian = dataEncoding == 1.toByte()
            buffer.order(if (isLittleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)

            val version = buffer.get()
            val osAbi = buffer.get()
            val abiVersion = buffer.get()

            buffer.position(16)
            val type = buffer.short.toInt()
            val machine = buffer.short.toInt()

            val elfVersion = buffer.int
            val entry = if (is64bit) buffer.long else buffer.int.toLong()
            val phOff = if (is64bit) buffer.long else buffer.int.toLong()
            val shOff = if (is64bit) buffer.long else buffer.int.toLong()

            val flags = buffer.int
            val ehSize = buffer.short.toInt()
            val phEntSize = buffer.short.toInt()
            val phNum = buffer.short.toInt()
            val shEntSize = buffer.short.toInt()
            val shNum = buffer.short.toInt()
            val shStrNdx = buffer.short.toInt()

            if (shNum > AnalysisLimits.MAX_CLASS_COUNT) {
                return Result.failure(
                    AnalysisError.QuotaExceededError("ELF section count exceeds limit")
                )
            }
            if (phNum > AnalysisLimits.MAX_CLASS_COUNT) {
                return Result.failure(
                    AnalysisError.QuotaExceededError("ELF program header count exceeds limit")
                )
            }

            val sections = parseSections(buffer, shOff, shNum, shEntSize, shStrNdx, is64bit)
            val programHeaders = parseProgramHeaders(buffer, phOff, phNum, phEntSize, is64bit)
            val hasExecutableWritableSegment = programHeaders.any {
                it.type == PT_LOAD && (it.flags and PF_X) != 0L && (it.flags and PF_W) != 0L
            }

            val symtabSymbols = extractSymbols(buffer, sections, type = SHT_SYMTAB, is64bit)
            val dynamicSymbols = extractSymbols(buffer, sections, type = SHT_DYNSYM, is64bit)
            val dynamicDependencies = extractDynamicDependencies(buffer, sections, is64bit)
            val jniExports = dynamicSymbols
                .mapNotNull { it.name.takeIf { name -> name.startsWith("Java_") } }
                .distinct()
                .take(MAX_JNI_EXPORTS)

            val architecture = getArchitectureName(machine)
            val abi = getAbiName(architecture, is64bit)

            Result.success(
                ElfInfo(
                    path = elfFile.name,
                    abi = abi,
                    size = elfFile.length(),
                    architecture = architecture,
                    is64bit = is64bit,
                    isLittleEndian = isLittleEndian,
                    entryPoint = entry,
                    sectionCount = shNum,
                    programHeaderCount = phNum,
                    sections = sections,
                    symbols = symtabSymbols,
                    dynamicSymbols = dynamicSymbols,
                    dynamicDependencies = dynamicDependencies,
                    jniExports = jniExports,
                    programHeaders = programHeaders,
                    hasExecutableWritableSegment = hasExecutableWritableSegment,
                    isStripped = symtabSymbols.isEmpty()
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to analyze ELF")
            Result.failure(AnalysisError.ParserError("Failed to analyze ELF", e))
        }
    }

    private fun parseSections(
        buffer: ByteBuffer,
        shOff: Long,
        shNum: Int,
        shEntSize: Int,
        shStrNdx: Int,
        is64bit: Boolean
    ): List<ElfSection> {
        if (shOff == 0L || shNum == 0) return emptyList()

        val expectedSize = if (is64bit) 64 else 40
        if (shEntSize < expectedSize) return emptyList()

        val rawSections = mutableListOf<ElfSection>()
        for (i in 0 until minOf(shNum, AnalysisLimits.MAX_CLASS_COUNT)) {
            val offset = shOff + i.toLong() * shEntSize
            if (offset + expectedSize > buffer.capacity()) break

            buffer.position(offset.toInt())
            val nameIdx = buffer.int
            val type = buffer.int
            val flags = if (is64bit) buffer.long else buffer.int.toLong()
            val addr = if (is64bit) buffer.long else buffer.int.toLong()
            val sectionOffset = if (is64bit) buffer.long else buffer.int.toLong()
            val size = if (is64bit) buffer.long else buffer.int.toLong()
            val link = buffer.int
            val info = buffer.int

            rawSections.add(
                ElfSection(
                    nameIndex = nameIdx,
                    type = type,
                    flags = flags,
                    address = addr,
                    offset = sectionOffset,
                    size = size,
                    link = link,
                    info = info,
                    name = ""
                )
            )
        }

        val shstrtab = rawSections.getOrNull(shStrNdx)
        return rawSections.map { section ->
            val name = if (shstrtab != null) {
                readStringTableString(buffer, shstrtab, section.nameIndex) ?: ""
            } else {
                ""
            }
            section.copy(name = name)
        }
    }

    private fun parseProgramHeaders(
        buffer: ByteBuffer,
        phOff: Long,
        phNum: Int,
        phEntSize: Int,
        is64bit: Boolean
    ): List<ElfProgramHeader> {
        if (phOff == 0L || phNum == 0) return emptyList()

        val expectedSize = if (is64bit) 56 else 32
        if (phEntSize < expectedSize) return emptyList()

        val headers = mutableListOf<ElfProgramHeader>()
        for (i in 0 until minOf(phNum, AnalysisLimits.MAX_CLASS_COUNT)) {
            val offset = phOff + i.toLong() * phEntSize
            if (offset + expectedSize > buffer.capacity()) break

            buffer.position(offset.toInt())
            val type = buffer.int
            val flags: Long
            val segmentOffset: Long
            val vaddr: Long
            val fileSize: Long
            val memSize: Long
            if (is64bit) {
                flags = buffer.int.toLong() and 0xFFFFFFFFL
                segmentOffset = buffer.long
                vaddr = buffer.long
                buffer.long // p_paddr
                fileSize = buffer.long
                memSize = buffer.long
            } else {
                segmentOffset = buffer.int.toLong()
                vaddr = buffer.int.toLong()
                buffer.int // p_paddr
                fileSize = buffer.int.toLong()
                memSize = buffer.int.toLong()
                flags = buffer.int.toLong() and 0xFFFFFFFFL
            }

            headers.add(
                ElfProgramHeader(
                    type = type,
                    flags = flags,
                    offset = segmentOffset,
                    vaddr = vaddr,
                    fileSize = fileSize,
                    memSize = memSize
                )
            )
        }
        return headers
    }

    private fun extractSymbols(
        buffer: ByteBuffer,
        sections: List<ElfSection>,
        type: Int,
        is64bit: Boolean
    ): List<ElfSymbol> {
        val section = sections.find { it.type == type } ?: return emptyList()
        if (section.offset == 0L || section.size == 0L) return emptyList()

        val symbolSize = if (is64bit) 24 else 16
        val symbolCount = (section.size / symbolSize).toInt()

        if (symbolCount > AnalysisLimits.MAX_METHOD_COUNT) {
            return emptyList()
        }

        val stringTable = sections.getOrNull(section.link)

        val symbols = mutableListOf<ElfSymbol>()
        for (i in 0 until minOf(symbolCount, MAX_SYMBOLS)) {
            val offset = section.offset + i.toLong() * symbolSize
            if (offset + symbolSize > buffer.capacity()) break

            buffer.position(offset.toInt())
            val nameIdx = buffer.int
            if (is64bit) {
                val info = buffer.get().toInt() and 0xFF
                buffer.get() // st_other
                buffer.short // st_shndx
                val value = buffer.long
                val size = buffer.long
                if (nameIdx != 0) {
                    symbols.add(
                        ElfSymbol(
                            nameIndex = nameIdx,
                            type = info and 0xF,
                            binding = (info shr 4) and 0xF,
                            value = value,
                            size = size,
                            name = readStringTableString(buffer, stringTable, nameIdx) ?: ""
                        )
                    )
                }
            } else {
                val value = buffer.int.toLong()
                val size = buffer.int.toLong()
                val info = buffer.get().toInt() and 0xFF
                if (nameIdx != 0) {
                    symbols.add(
                        ElfSymbol(
                            nameIndex = nameIdx,
                            type = info and 0xF,
                            binding = (info shr 4) and 0xF,
                            value = value,
                            size = size,
                            name = readStringTableString(buffer, stringTable, nameIdx) ?: ""
                        )
                    )
                }
            }
        }
        return symbols
    }

    private fun extractDynamicDependencies(
        buffer: ByteBuffer,
        sections: List<ElfSection>,
        is64bit: Boolean
    ): List<String> {
        val dynamicSection = sections.find { it.type == SHT_DYNAMIC } ?: return emptyList()
        if (dynamicSection.offset == 0L || dynamicSection.size == 0L) return emptyList()

        val entrySize = if (is64bit) 16 else 8
        val entryCount = (dynamicSection.size / entrySize).toInt()
        if (entryCount > AnalysisLimits.MAX_METHOD_COUNT) return emptyList()

        val stringTable = sections.getOrNull(dynamicSection.link)

        val dependencies = mutableListOf<String>()
        for (i in 0 until minOf(entryCount, MAX_DYNAMIC_ENTRIES)) {
            val offset = dynamicSection.offset + i.toLong() * entrySize
            if (offset + entrySize > buffer.capacity()) break

            buffer.position(offset.toInt())
            val tag = if (is64bit) buffer.long else buffer.int.toLong()
            val value = if (is64bit) buffer.long else buffer.int.toLong()

            if (tag == DT_NEEDED) {
                val name = readStringTableString(buffer, stringTable, value.toInt())
                if (name != null && name.isNotEmpty()) {
                    dependencies.add(name)
                }
            } else if (tag == DT_NULL) {
                break
            }
        }
        return dependencies.distinct()
    }

    /**
     * Reads a null-terminated string at [index] from a string-table section,
     * bounded to [AnalysisLimits.MAX_STRING_LENGTH].
     */
    private fun readStringTableString(
        buffer: ByteBuffer,
        section: ElfSection?,
        index: Int
    ): String? {
        if (section == null || index < 0) return null
        val start = section.offset + index.toLong()
        if (start >= buffer.capacity()) return null

        var end = start
        val maxEnd = minOf(start + AnalysisLimits.MAX_STRING_LENGTH, buffer.capacity().toLong())
        while (end < maxEnd && buffer.get(end.toInt()) != 0.toByte()) {
            end++
        }
        if (end >= maxEnd) return null

        val len = (end - start).toInt()
        if (len <= 0) return ""
        val out = ByteArray(len)
        buffer.position(start.toInt())
        buffer.get(out)
        return String(out, Charsets.UTF_8)
    }

    private fun getArchitectureName(machine: Int): String {
        return when (machine) {
            0x03 -> "x86"
            0x3E -> "x86_64"
            0x28 -> "ARM"
            0xB7 -> "AArch64"
            0x08 -> "MIPS"
            else -> "Unknown ($machine)"
        }
    }

    private fun getAbiName(architecture: String, is64bit: Boolean): String {
        return when {
            architecture == "ARM" && !is64bit -> "armeabi-v7a"
            architecture == "AArch64" -> "arm64-v8a"
            architecture == "x86" && !is64bit -> "x86"
            architecture == "x86_64" -> "x86_64"
            else -> "unknown"
        }
    }

    companion object {
        private const val ELF_HEADER_SIZE = 64
        private const val SHT_SYMTAB = 2
        private const val SHT_STRTAB = 3
        private const val SHT_DYNAMIC = 6
        private const val SHT_DYNSYM = 11
        private const val PT_LOAD = 1
        private const val PF_X = 1L
        private const val PF_W = 2L
        private const val DT_NULL = 0L
        private const val DT_NEEDED = 1L
        private const val MAX_SYMBOLS = 1000
        private const val MAX_DYNAMIC_ENTRIES = 500
        private const val MAX_JNI_EXPORTS = 100
    }
}

data class ElfInfo(
    val path: String,
    val abi: String,
    val size: Long,
    val architecture: String?,
    val is64bit: Boolean,
    val isLittleEndian: Boolean,
    val entryPoint: Long,
    val sectionCount: Int,
    val programHeaderCount: Int,
    val sections: List<ElfSection>,
    val symbols: List<ElfSymbol>,
    val dynamicSymbols: List<ElfSymbol> = emptyList(),
    val dynamicDependencies: List<String> = emptyList(),
    val jniExports: List<String> = emptyList(),
    val programHeaders: List<ElfProgramHeader> = emptyList(),
    val hasExecutableWritableSegment: Boolean = false,
    val isStripped: Boolean
)

data class ElfSection(
    val nameIndex: Int,
    val type: Int,
    val flags: Long,
    val address: Long,
    val offset: Long,
    val size: Long,
    val link: Int = 0,
    val info: Int = 0,
    val name: String = ""
)

data class ElfSymbol(
    val nameIndex: Int,
    val type: Int,
    val binding: Int,
    val value: Long,
    val size: Long,
    val name: String = ""
)

data class ElfProgramHeader(
    val type: Int,
    val flags: Long,
    val offset: Long,
    val vaddr: Long,
    val fileSize: Long,
    val memSize: Long
)

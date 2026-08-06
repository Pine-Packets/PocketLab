package com.pineandpackets.pocketlab.engine.native

import com.pineandpackets.pocketlab.core.common.AnalysisError
import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfAnalyzer {
    
    fun analyzeElf(elfFile: File): Result<ElfInfo> {
        return try {
            val bytes = elfFile.readBytes()
            
            if (bytes.size < 64) {
                return Result.failure(AnalysisError.ParserError("ELF file too small"))
            }
            
            val buffer = ByteBuffer.wrap(bytes)
            
            val magic = ByteArray(4)
            buffer.get(magic)
            
            if (magic[0] != 0x7F.toByte() || 
                magic[1] != 'E'.code.toByte() || 
                magic[2] != 'L'.code.toByte() || 
                magic[3] != 'F'.code.toByte()) {
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
            
            val sections = parseSections(buffer, shOff, shNum, shEntSize, is64bit)
            val symbols = extractSymbols(buffer, sections, is64bit)
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
                    symbols = symbols,
                    isStripped = symbols.isEmpty()
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
        is64bit: Boolean
    ): List<ElfSection> {
        if (shOff == 0L || shNum == 0) return emptyList()
        
        val sections = mutableListOf<ElfSection>()
        val sectionHeaderSize = if (is64bit) 64 else 40
        
        for (i in 0 until minOf(shNum, AnalysisLimits.MAX_CLASS_COUNT)) {
            val offset = (shOff + i * sectionHeaderSize).toInt()
            if (offset + sectionHeaderSize > buffer.capacity()) break
            
            buffer.position(offset)
            
            val nameIdx = buffer.int
            val type = buffer.int
            val flags = if (is64bit) buffer.long else buffer.int.toLong()
            val addr = if (is64bit) buffer.long else buffer.int.toLong()
            val sectionOffset = if (is64bit) buffer.long else buffer.int.toLong()
            val size = if (is64bit) buffer.long else buffer.int.toLong()
            
            sections.add(
                ElfSection(
                    nameIndex = nameIdx,
                    type = type,
                    flags = flags,
                    address = addr,
                    offset = sectionOffset,
                    size = size
                )
            )
        }
        
        return sections
    }
    
    private fun extractSymbols(
        buffer: ByteBuffer,
        sections: List<ElfSection>,
        is64bit: Boolean
    ): List<ElfSymbol> {
        val symbols = mutableListOf<ElfSymbol>()
        val symTabSection = sections.find { it.type == 2 } ?: return emptyList()
        
        if (symTabSection.offset == 0L || symTabSection.size == 0L) return emptyList()
        
        val symbolSize = if (is64bit) 24 else 16
        val symbolCount = (symTabSection.size / symbolSize).toInt()
        
        if (symbolCount > AnalysisLimits.MAX_METHOD_COUNT) {
            return emptyList()
        }
        
        for (i in 0 until minOf(symbolCount, 1000)) {
            val offset = (symTabSection.offset + i * symbolSize).toInt()
            if (offset + symbolSize > buffer.capacity()) break
            
            buffer.position(offset)
            
            val nameIdx = buffer.int
            val info = buffer.get().toInt() and 0xFF
            val value = if (is64bit) buffer.long else buffer.int.toLong()
            val size = if (is64bit) buffer.long else buffer.int.toLong()
            
            if (nameIdx != 0) {
                symbols.add(
                    ElfSymbol(
                        nameIndex = nameIdx,
                        type = info and 0xF,
                        binding = (info shr 4) and 0xF,
                        value = value,
                        size = size
                    )
                )
            }
        }
        
        return symbols
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
    val isStripped: Boolean
)

data class ElfSection(
    val nameIndex: Int,
    val type: Int,
    val flags: Long,
    val address: Long,
    val offset: Long,
    val size: Long
)

data class ElfSymbol(
    val nameIndex: Int,
    val type: Int,
    val binding: Int,
    val value: Long,
    val size: Long
)

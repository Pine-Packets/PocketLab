package com.pineandpackets.pocketlab.engine.dex

import com.pineandpackets.pocketlab.core.common.AnalysisError
import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import com.pineandpackets.pocketlab.core.model.DexInfo
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DexAnalyzer {
    
    fun analyzeDex(dexFile: File): Result<DexInfo> {
        return try {
            val bytes = dexFile.readBytes()
            
            if (bytes.size < 112) {
                return Result.failure(AnalysisError.ParserError("DEX file too small"))
            }
            
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            
            val magic = ByteArray(8)
            buffer.get(magic)
            
            if (!String(magic, 0, 3).startsWith("dex")) {
                return Result.failure(AnalysisError.ParserError("Invalid DEX magic"))
            }
            
            val version = String(magic, 3, 3).trimEnd('\u0000')
            
            buffer.position(56)
            val headerSize = buffer.int
            val endianTag = buffer.int
            
            if (endianTag != 0x12345678) {
                return Result.failure(AnalysisError.ParserError("Invalid endian tag"))
            }
            
            buffer.position(88)
            val stringIdsSize = buffer.int
            val stringIdsOff = buffer.int
            val typeIdsSize = buffer.int
            val typeIdsOff = buffer.int
            val protoIdsSize = buffer.int
            val protoIdsOff = buffer.int
            val fieldIdsSize = buffer.int
            val fieldIdsOff = buffer.int
            val methodIdsSize = buffer.int
            val methodIdsOff = buffer.int
            val classDefsSize = buffer.int
            val classDefsOff = buffer.int
            
            if (stringIdsSize > AnalysisLimits.MAX_STRING_COUNT) {
                return Result.failure(
                    AnalysisError.QuotaExceededError("String count exceeds limit")
                )
            }
            
            if (methodIdsSize > AnalysisLimits.MAX_METHOD_COUNT) {
                return Result.failure(
                    AnalysisError.QuotaExceededError("Method count exceeds limit")
                )
            }
            
            if (classDefsSize > AnalysisLimits.MAX_CLASS_COUNT) {
                return Result.failure(
                    AnalysisError.QuotaExceededError("Class count exceeds limit")
                )
            }
            
            Result.success(
                DexInfo(
                    name = dexFile.name,
                    version = version,
                    classCount = classDefsSize,
                    methodCount = methodIdsSize,
                    stringCount = stringIdsSize,
                    size = dexFile.length()
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to analyze DEX")
            Result.failure(AnalysisError.ParserError("Failed to analyze DEX", e))
        }
    }
}

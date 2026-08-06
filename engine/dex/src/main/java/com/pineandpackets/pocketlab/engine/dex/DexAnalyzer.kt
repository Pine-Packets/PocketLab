package com.pineandpackets.pocketlab.engine.dex

import com.pineandpackets.pocketlab.core.common.AnalysisError
import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import com.pineandpackets.pocketlab.core.model.*
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DexAnalyzer {
    
    fun analyzeDex(dexFile: File, extractCode: Boolean = true): Result<DexInfo> {
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
            
            // Version is at bytes 4-6 (after "dex\n")
            val version = String(magic, 4, 3).trimEnd('\u0000')
            
            buffer.position(36)
            val headerSize = buffer.int
            val endianTag = buffer.int
            
            if (endianTag != 0x12345678) {
                return Result.failure(AnalysisError.ParserError("Invalid endian tag"))
            }
            
            buffer.position(56)
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
            
            val strings = if (extractCode) parseStrings(buffer, stringIdsOff, stringIdsSize) else emptyList()
            val typeIds = if (extractCode) parseTypeIds(buffer, typeIdsOff, typeIdsSize, strings) else emptyList()
            val protoIds = if (extractCode) parseProtoIds(buffer, protoIdsOff, protoIdsSize, typeIds, strings) else emptyList()
            val fieldIds = if (extractCode) parseFieldIds(buffer, fieldIdsOff, fieldIdsSize, typeIds, strings) else emptyList()
            val methodIds = if (extractCode) parseMethodIds(buffer, methodIdsOff, methodIdsSize, typeIds, protoIds, strings) else emptyList()
            val classDefs = if (extractCode) parseClassDefs(buffer, classDefsOff, classDefsSize, typeIds, strings, methodIds, bytes) else emptyList()
            
            val apiReferences = if (extractCode) buildApiReferences(classDefs, methodIds) else emptyList()
            val reconstructedStrings = if (extractCode) {
                ConstantPropagator().reconstructStrings(classDefs, strings, methodIds)
            } else {
                emptyList()
            }
            
            Result.success(
                DexInfo(
                    name = dexFile.name,
                    version = version,
                    classCount = classDefsSize,
                    methodCount = methodIdsSize,
                    stringCount = stringIdsSize,
                    size = dexFile.length(),
                    strings = strings,
                    typeIds = typeIds,
                    methodIds = methodIds,
                    fieldIds = fieldIds,
                    classDefs = classDefs,
                    apiReferences = apiReferences,
                    reconstructedStrings = reconstructedStrings
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to analyze DEX")
            Result.failure(AnalysisError.ParserError("Failed to analyze DEX", e))
        }
    }
    
    private fun parseStrings(buffer: ByteBuffer, offset: Int, count: Int): List<DexString> {
        if (offset == 0 || count == 0) return emptyList()
        
        val strings = mutableListOf<DexString>()
        buffer.position(offset)
        
        val stringDataOffsets = IntArray(count)
        for (i in 0 until count) {
            stringDataOffsets[i] = buffer.int
        }
        
        for (i in 0 until count) {
            try {
                buffer.position(stringDataOffsets[i])
                val size = readUleb128(buffer)
                val value = readMutf8String(buffer, size)
                strings.add(DexString(i, value, value.length))
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse string at index $i")
                strings.add(DexString(i, "<error>", 0))
            }
        }
        
        return strings
    }
    
    private fun parseTypeIds(buffer: ByteBuffer, offset: Int, count: Int, strings: List<DexString>): List<DexTypeId> {
        if (offset == 0 || count == 0) return emptyList()
        
        val typeIds = mutableListOf<DexTypeId>()
        buffer.position(offset)
        
        for (i in 0 until count) {
            val descriptorIdx = buffer.int
            val descriptor = strings.getOrNull(descriptorIdx)?.value ?: "<unknown>"
            typeIds.add(DexTypeId(i, descriptorIdx, descriptor))
        }
        
        return typeIds
    }
    
    private fun parseProtoIds(
        buffer: ByteBuffer,
        offset: Int,
        count: Int,
        typeIds: List<DexTypeId>,
        strings: List<DexString>
    ): List<ProtoId> {
        if (offset == 0 || count == 0) return emptyList()
        
        val protoIds = mutableListOf<ProtoId>()
        buffer.position(offset)
        
        for (i in 0 until count) {
            val shortyIdx = buffer.int
            val returnTypeIdx = buffer.int
            val parametersOff = buffer.int
            
            val shorty = strings.getOrNull(shortyIdx)?.value ?: "<unknown>"
            val returnType = typeIds.getOrNull(returnTypeIdx)?.descriptor ?: "<unknown>"
            
            protoIds.add(ProtoId(i, shorty, returnType, parametersOff))
        }
        
        return protoIds
    }
    
    private fun parseFieldIds(
        buffer: ByteBuffer,
        offset: Int,
        count: Int,
        typeIds: List<DexTypeId>,
        strings: List<DexString>
    ): List<DexFieldId> {
        if (offset == 0 || count == 0) return emptyList()
        
        val fieldIds = mutableListOf<DexFieldId>()
        buffer.position(offset)
        
        for (i in 0 until count) {
            val classIdx = buffer.short.toInt() and 0xFFFF
            val typeIdx = buffer.short.toInt() and 0xFFFF
            val nameIdx = buffer.int
            
            val className = typeIds.getOrNull(classIdx)?.descriptor ?: "<unknown>"
            val fieldType = typeIds.getOrNull(typeIdx)?.descriptor ?: "<unknown>"
            val fieldName = strings.getOrNull(nameIdx)?.value ?: "<unknown>"
            
            fieldIds.add(DexFieldId(i, classIdx, typeIdx, nameIdx, className, fieldName, fieldType))
        }
        
        return fieldIds
    }
    
    private fun parseMethodIds(
        buffer: ByteBuffer,
        offset: Int,
        count: Int,
        typeIds: List<DexTypeId>,
        protoIds: List<ProtoId>,
        strings: List<DexString>
    ): List<DexMethodId> {
        if (offset == 0 || count == 0) return emptyList()
        
        val methodIds = mutableListOf<DexMethodId>()
        buffer.position(offset)
        
        for (i in 0 until count) {
            val classIdx = buffer.short.toInt() and 0xFFFF
            val protoIdx = buffer.short.toInt() and 0xFFFF
            val nameIdx = buffer.int
            
            val className = typeIds.getOrNull(classIdx)?.descriptor ?: "<unknown>"
            val proto = protoIds.getOrNull(protoIdx)
            val methodName = strings.getOrNull(nameIdx)?.value ?: "<unknown>"
            val prototype = proto?.let { "${it.returnType}(${it.shorty})" } ?: "<unknown>"
            
            methodIds.add(DexMethodId(i, classIdx, protoIdx, nameIdx, className, methodName, prototype))
        }
        
        return methodIds
    }
    
    private fun parseClassDefs(
        buffer: ByteBuffer,
        offset: Int,
        count: Int,
        typeIds: List<DexTypeId>,
        strings: List<DexString>,
        methodIds: List<DexMethodId>,
        dexBytes: ByteArray
    ): List<DexClassDef> {
        if (offset == 0 || count == 0) return emptyList()
        
        val classDefs = mutableListOf<DexClassDef>()
        buffer.position(offset)
        
        for (i in 0 until count) {
            val classIdx = buffer.int
            val accessFlags = buffer.int
            val superclassIdx = buffer.int
            val interfacesOff = buffer.int
            val sourceFileIdx = buffer.int
            val annotationsOff = buffer.int
            val classDataOff = buffer.int
            val staticValuesOff = buffer.int
            
            val className = typeIds.getOrNull(classIdx)?.descriptor ?: "<unknown>"
            val superclass = if (superclassIdx == -1) null else typeIds.getOrNull(superclassIdx)?.descriptor
            val sourceFile = if (sourceFileIdx == -1) null else strings.getOrNull(sourceFileIdx)?.value
            
            val methods = if (classDataOff != 0) {
                parseClassData(buffer, classDataOff, methodIds, strings, dexBytes)
            } else {
                emptyList()
            }
            
            classDefs.add(
                DexClassDef(
                    index = i,
                    classIdx = classIdx,
                    accessFlags = accessFlags,
                    superclassIdx = superclassIdx,
                    interfacesOff = interfacesOff,
                    sourceFileIdx = sourceFileIdx,
                    annotationsOff = annotationsOff,
                    classDataOff = classDataOff,
                    staticValuesOff = staticValuesOff,
                    className = className,
                    superclass = superclass,
                    sourceFile = sourceFile,
                    methods = methods
                )
            )
        }
        
        return classDefs
    }
    
    private fun parseClassData(
        buffer: ByteBuffer,
        offset: Int,
        methodIds: List<DexMethodId>,
        strings: List<DexString>,
        dexBytes: ByteArray
    ): List<DexMethod> {
        return try {
            buffer.position(offset)
            
            val staticFieldsSize = readUleb128(buffer)
            val instanceFieldsSize = readUleb128(buffer)
            val directMethodsSize = readUleb128(buffer)
            val virtualMethodsSize = readUleb128(buffer)
            
            val methods = mutableListOf<DexMethod>()
            
            var methodIndex = 0
            
            for (i in 0 until directMethodsSize) {
                val methodIdxDiff = readUleb128(buffer)
                val accessFlags = readUleb128(buffer)
                val codeOff = readUleb128(buffer)
                
                methodIndex += methodIdxDiff
                val methodId = methodIds.getOrNull(methodIndex) ?: continue
                
                val codeItem = if (codeOff != 0) {
                    parseCodeItem(buffer, codeOff, strings, methodIds, dexBytes)
                } else {
                    CodeItemParseResult(0, emptyList(), emptyList(), emptyList())
                }
                
                methods.add(
                    DexMethod(
                        methodIdx = methodIndex,
                        accessFlags = accessFlags,
                        codeOff = codeOff,
                        name = methodId.methodName,
                        prototype = methodId.prototype,
                        instructionCount = codeItem.instructionCount,
                        referencedStrings = codeItem.referencedStrings,
                        referencedMethods = codeItem.referencedMethods,
                        instructions = codeItem.instructions.take(500)
                    )
                )
            }
            
            methodIndex = 0
            for (i in 0 until virtualMethodsSize) {
                val methodIdxDiff = readUleb128(buffer)
                val accessFlags = readUleb128(buffer)
                val codeOff = readUleb128(buffer)
                
                methodIndex += methodIdxDiff
                val methodId = methodIds.getOrNull(methodIndex) ?: continue
                
                val codeItem = if (codeOff != 0) {
                    parseCodeItem(buffer, codeOff, strings, methodIds, dexBytes)
                } else {
                    CodeItemParseResult(0, emptyList(), emptyList(), emptyList())
                }
                
                methods.add(
                    DexMethod(
                        methodIdx = methodIndex,
                        accessFlags = accessFlags,
                        codeOff = codeOff,
                        name = methodId.methodName,
                        prototype = methodId.prototype,
                        instructionCount = codeItem.instructionCount,
                        referencedStrings = codeItem.referencedStrings,
                        referencedMethods = codeItem.referencedMethods,
                        instructions = codeItem.instructions.take(500)
                    )
                )
            }
            
            methods
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse class data at offset $offset")
            emptyList()
        }
    }
    
    private fun parseCodeItem(
        buffer: ByteBuffer,
        offset: Int,
        strings: List<DexString>,
        methodIds: List<DexMethodId>,
        dexBytes: ByteArray
    ): CodeItemParseResult {
        return try {
            buffer.position(offset)

            val registersSize = buffer.short.toInt() and 0xFFFF
            val insSize = buffer.short.toInt() and 0xFFFF
            val outsSize = buffer.short.toInt() and 0xFFFF
            val triesSize = buffer.short.toInt() and 0xFFFF
            val debugInfoOff = buffer.int
            val insnsSize = buffer.int

            val instructionCount = insnsSize
            val referencedStrings = mutableListOf<String>()
            val referencedMethods = mutableListOf<String>()
            val instructions = mutableListOf<com.pineandpackets.pocketlab.core.model.DexInstruction>()

            if (insnsSize > 0 && insnsSize < AnalysisLimits.MAX_INSTRUCTION_COUNT) {
                val insnsStart = buffer.position()
                val maxInstructions = minOf(insnsSize, 1000)

                // Build reference lists and instruction list in one pass
                var pos = 0
                while (pos < maxInstructions) {
                    if (insnsStart + pos * 2 + 2 > dexBytes.size) break

                    buffer.position(insnsStart + pos * 2)
                    val instructionUnit = buffer.short.toInt() and 0xFFFF
                    val opcode = instructionUnit and 0xFF

                    when (opcode) {
                        0x1A -> {
                            val stringIdx = buffer.short.toInt() and 0xFFFF
                            strings.getOrNull(stringIdx)?.value?.let {
                                if (it.length < 256) referencedStrings.add(it)
                            }
                            pos += 2
                        }
                        0x6E, 0x6F, 0x70, 0x71, 0x72 -> {
                            buffer.position(insnsStart + pos * 2 + 2)
                            val methodIdx = buffer.short.toInt() and 0xFFFF
                            methodIds.getOrNull(methodIdx)?.let {
                                referencedMethods.add("${it.className}->${it.methodName}")
                            }
                            pos += 3
                        }
                        else -> {
                            pos += getInstructionWidth(opcode)
                        }
                    }
                }

                // Also parse detailed instructions for constant propagation
                instructions.addAll(
                    ConstantPropagator.parseCodeItemInstructions(dexBytes, offset, maxInstructions)
                )
            }

            CodeItemParseResult(
                instructionCount = instructionCount,
                referencedStrings = referencedStrings.take(100),
                referencedMethods = referencedMethods.take(100),
                instructions = instructions
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse code item at offset $offset")
            CodeItemParseResult(0, emptyList(), emptyList(), emptyList())
        }
    }

    private data class CodeItemParseResult(
        val instructionCount: Int,
        val referencedStrings: List<String>,
        val referencedMethods: List<String>,
        val instructions: List<com.pineandpackets.pocketlab.core.model.DexInstruction>
    )
    
    private fun getInstructionWidth(opcode: Int): Int {
        return when (opcode) {
            0x00 -> 1
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07 -> 1
            0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D -> 1
            0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17 -> 1
            0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27 -> 2
            0x28, 0x29, 0x2A, 0x2B, 0x2C -> 2
            0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38 -> 2
            0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F -> 2
            0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46 -> 2
            0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F, 0x50, 0x51 -> 2
            0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5A, 0x5B, 0x5C, 0x5D, 0x5E, 0x5F -> 2
            0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6A, 0x6B, 0x6C, 0x6D -> 2
            0x6E, 0x6F, 0x70, 0x71, 0x72 -> 3
            0x73 -> 1
            0x74, 0x75, 0x76, 0x77, 0x78 -> 3
            0x79, 0x7A, 0x7B, 0x7C, 0x7D, 0x7E, 0x7F -> 2
            0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8A, 0x8B, 0x8C, 0x8D, 0x8E, 0x8F -> 2
            0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E, 0x9F -> 2
            0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6, 0xA7, 0xA8, 0xA9, 0xAA, 0xAB, 0xAC, 0xAD, 0xAE, 0xAF -> 2
            0xB0, 0xB1, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB7, 0xB8, 0xB9, 0xBA, 0xBB, 0xBC, 0xBD, 0xBE, 0xBF -> 1
            0xC0, 0xC1, 0xC2, 0xC3, 0xC4, 0xC5, 0xC6, 0xC7, 0xC8 -> 2
            else -> 1
        }
    }
    
    private fun buildApiReferences(classDefs: List<DexClassDef>, methodIds: List<DexMethodId>): List<ApiReference> {
        val apiMap = mutableMapOf<String, MutableList<CallSite>>()
        
        for (classDef in classDefs) {
            for (method in classDef.methods) {
                for (refMethod in method.referencedMethods) {
                    val parts = refMethod.split("->")
                    if (parts.size == 2) {
                        val className = parts[0]
                        val methodName = parts[1]
                        val key = "$className->$methodName"
                        
                        apiMap.getOrPut(key) { mutableListOf() }
                            .add(CallSite(classDef.className, method.name, 0, null))
                    }
                }
            }
        }
        
        return apiMap.map { (key, callSites) ->
            val parts = key.split("->")
            ApiReference(
                className = parts.getOrElse(0) { "" },
                methodName = parts.getOrElse(1) { "" },
                callSites = callSites.take(50)
            )
        }.take(1000)
    }
    
    private fun readUleb128(buffer: ByteBuffer): Int {
        var result = 0
        var shift = 0
        var byte: Int
        
        do {
            byte = buffer.get().toInt() and 0xFF
            result = result or ((byte and 0x7F) shl shift)
            shift += 7
        } while (byte and 0x80 != 0 && shift < 32)
        
        return result
    }
    
    private fun readMutf8String(buffer: ByteBuffer, expectedLength: Int): String {
        val bytes = mutableListOf<Byte>()
        var count = 0
        
        while (count < expectedLength * 2) {
            val b = buffer.get()
            if (b.toInt() == 0) break
            bytes.add(b)
            count++
        }
        
        return try {
            decodeMutf8(bytes.toByteArray())
        } catch (e: Exception) {
            String(bytes.toByteArray(), Charsets.UTF_8)
        }
    }
    
    private fun decodeMutf8(bytes: ByteArray): String {
        val result = StringBuilder()
        var i = 0
        
        while (i < bytes.size) {
            val b1 = bytes[i].toInt() and 0xFF
            
            when {
                b1 == 0 -> break
                b1 < 0x80 -> {
                    result.append(b1.toChar())
                    i++
                }
                b1 and 0xE0 == 0xC0 -> {
                    if (i + 1 >= bytes.size) break
                    val b2 = bytes[i + 1].toInt() and 0xFF
                    result.append(((b1 and 0x1F shl 6) or (b2 and 0x3F)).toChar())
                    i += 2
                }
                b1 and 0xF0 == 0xE0 -> {
                    if (i + 2 >= bytes.size) break
                    val b2 = bytes[i + 1].toInt() and 0xFF
                    val b3 = bytes[i + 2].toInt() and 0xFF
                    result.append(((b1 and 0x0F shl 12) or (b2 and 0x3F shl 6) or (b3 and 0x3F)).toChar())
                    i += 3
                }
                else -> {
                    result.append('?')
                    i++
                }
            }
        }
        
        return result.toString()
    }
    
    private data class ProtoId(
        val index: Int,
        val shorty: String,
        val returnType: String,
        val parametersOff: Int
    )
}

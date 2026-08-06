package com.pineandpackets.pocketlab.engine.dex

import com.pineandpackets.pocketlab.core.model.DexClassDef
import com.pineandpackets.pocketlab.core.model.DexInstruction
import com.pineandpackets.pocketlab.core.model.DexMethod
import com.pineandpackets.pocketlab.core.model.DexMethodId
import com.pineandpackets.pocketlab.core.model.DexString
import com.pineandpackets.pocketlab.core.model.ReconstructedString
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Performs bounded intraprocedural constant propagation for DEX bytecode.
 *
 * Reconstructs strings that are:
 * - loaded directly with const-string / const-string/jumbo
 * - built through StringBuilder append chains
 * - passed as arguments to sensitive APIs (Class.forName, DexClassLoader, etc.)
 *
 * This is intentionally limited: no method execution, no interprocedural analysis,
 * no unbounded branch exploration, and strict instruction/register limits.
 */
class ConstantPropagator {

    data class PropagationConfig(
        val maxInstructionsPerMethod: Int = 1000,
        val maxTrackedRegisters: Int = 64,
        val maxStringLength: Int = 512,
        val maxResultsPerMethod: Int = 32,
        val maxTotalResults: Int = 5000
    )

    private val config = PropagationConfig()

    /**
     * Reconstruct strings from all methods in the class definitions.
     */
    fun reconstructStrings(
        classDefs: List<DexClassDef>,
        strings: List<DexString>,
        methodIds: List<DexMethodId>
    ): List<ReconstructedString> {
        val results = mutableListOf<ReconstructedString>()

        for (classDef in classDefs) {
            for (method in classDef.methods) {
                if (method.instructions.isEmpty()) continue

                val methodResults = reconstructMethodStrings(
                    className = classDef.className,
                    method = method,
                    strings = strings,
                    methodIds = methodIds
                )

                results.addAll(methodResults)
                if (results.size >= config.maxTotalResults) {
                    return results.take(config.maxTotalResults)
                }
            }
        }

        return results
    }

    private fun reconstructMethodStrings(
        className: String,
        method: DexMethod,
        strings: List<DexString>,
        methodIds: List<DexMethodId>
    ): List<ReconstructedString> {
        val results = mutableListOf<ReconstructedString>()
        val registerValues = mutableMapOf<Int, RegisterValue>()

        val instructions = method.instructions.take(config.maxInstructionsPerMethod)

        for (instruction in instructions) {
            try {
                when (instruction.opcode) {
                    // const-string vAA, string@BBBB
                    0x1A -> {
                        val register = instruction.operands.getOrNull(0) ?: continue
                        val stringIdx = instruction.stringIndex ?: continue
                        val value = strings.getOrNull(stringIdx)?.value ?: continue

                        if (value.length <= config.maxStringLength) {
                            registerValues[register] = RegisterValue.StringLiteral(
                                value = value,
                                sourceStrings = listOf(value),
                                offset = instruction.offset
                            )
                        }
                    }

                    // const-string/jumbo vAA, string@BBBBBBBB
                    0x1B -> {
                        val register = instruction.operands.getOrNull(0) ?: continue
                        val stringIdx = instruction.stringIndex ?: continue
                        val value = strings.getOrNull(stringIdx)?.value ?: continue

                        if (value.length <= config.maxStringLength) {
                            registerValues[register] = RegisterValue.StringLiteral(
                                value = value,
                                sourceStrings = listOf(value),
                                offset = instruction.offset
                            )
                        }
                    }

                    // move-result-object vAA
                    0x0C -> {
                        val destReg = instruction.operands.getOrNull(0) ?: continue
                        // StringBuilder.toString results are tracked by leaving the
                        // reconstructed value in the result register when toString is invoked.
                        registerValues.remove(destReg)
                    }

                    // invoke-virtual, invoke-direct, invoke-static, invoke-interface
                    0x6E, 0x6F, 0x70, 0x71, 0x72 -> {
                        handleInvocation(
                            instruction = instruction,
                            registerValues = registerValues,
                            className = className,
                            methodName = method.name,
                            results = results,
                            strings = strings,
                            methodIds = methodIds
                        )
                    }
                }

                if (results.size >= config.maxResultsPerMethod) break

                // Limit tracked registers to prevent unbounded growth
                if (registerValues.size > config.maxTrackedRegisters) {
                    Timber.w("Register tracking limit exceeded in $className.${method.name}")
                    break
                }
            } catch (e: Exception) {
                Timber.w(e, "Constant propagation failed at offset ${instruction.offset}")
            }
        }

        return results
    }

    private fun handleInvocation(
        instruction: DexInstruction,
        registerValues: MutableMap<Int, RegisterValue>,
        className: String,
        methodName: String,
        results: MutableList<ReconstructedString>,
        strings: List<DexString>,
        methodIds: List<DexMethodId>
    ) {
        if (instruction.operands.size < 2) return

        val argCount = instruction.operands[0]
        val args = instruction.operands.drop(1).take(argCount)
        val methodIdx = instruction.methodIndex ?: return
        val targetMethod = methodIds.getOrNull(methodIdx)
        val targetDescriptor = targetMethod?.let { "${it.className}->${it.methodName}${it.prototype}" }

        if (targetDescriptor == null) return

        // StringBuilder.append(String) -> receiver becomes concatenation
        if (isStringBuilderAppend(targetDescriptor)) {
            val receiverReg = args.getOrNull(0) ?: return
            val argReg = args.getOrNull(1) ?: return

            val receiver = registerValues[receiverReg]
            val argument = registerValues[argReg]

            if (receiver is RegisterValue.StringLiteral && argument is RegisterValue.StringLiteral) {
                val combined = receiver.value + argument.value
                if (combined.length <= config.maxStringLength) {
                    registerValues[receiverReg] = RegisterValue.StringLiteral(
                        value = combined,
                        sourceStrings = receiver.sourceStrings + argument.sourceStrings,
                        offset = instruction.offset
                    )
                }
            }
            return
        }

        // StringBuilder.<init>(String)
        if (isStringBuilderInit(targetDescriptor)) {
            val receiverReg = args.getOrNull(0) ?: return
            val argReg = args.getOrNull(1)

            val initial = argReg?.let { registerValues[it] }
            if (initial is RegisterValue.StringLiteral) {
                registerValues[receiverReg] = initial
            }
            return
        }

        // StringBuilder.toString() -> result register populated when move-result-object follows
        if (isStringBuilderToString(targetDescriptor)) {
            val receiverReg = args.getOrNull(0) ?: return
            val receiver = registerValues[receiverReg]
            if (receiver is RegisterValue.StringLiteral) {
                results.add(
                    ReconstructedString(
                        value = receiver.value,
                        constructionType = "STRING_BUILDER",
                        className = className,
                        methodName = methodName,
                        instructionOffset = instruction.offset,
                        confidence = "HIGH",
                        sourceStrings = receiver.sourceStrings.take(20),
                        targetApi = targetDescriptor
                    )
                )
            }
            return
        }

        // Sensitive APIs where argument strings are high-value evidence
        val sensitiveApi = classifySensitiveApi(targetDescriptor)
        if (sensitiveApi != null) {
            for (argReg in args) {
                val value = registerValues[argReg]
                if (value is RegisterValue.StringLiteral) {
                    results.add(
                        ReconstructedString(
                            value = value.value,
                            constructionType = if (value.sourceStrings.size > 1) "PARTIALLY_RECONSTRUCTED" else "LITERAL",
                            className = className,
                            methodName = methodName,
                            instructionOffset = instruction.offset,
                            confidence = if (value.sourceStrings.size > 1) "MEDIUM" else "HIGH",
                            sourceStrings = value.sourceStrings.take(20),
                            targetApi = sensitiveApi
                        )
                    )
                }
            }
        }
    }

    private fun isStringBuilderAppend(descriptor: String): Boolean {
        return (descriptor.startsWith("Ljava/lang/StringBuilder;->append(Ljava/lang/String;") ||
            descriptor.startsWith("Ljava/lang/StringBuffer;->append(Ljava/lang/String;")) &&
            descriptor.endsWith(")Ljava/lang/String;")
    }

    private fun isStringBuilderInit(descriptor: String): Boolean {
        return (descriptor.startsWith("Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;") ||
            descriptor.startsWith("Ljava/lang/StringBuffer;-><init>(Ljava/lang/String;")) &&
            descriptor.endsWith(")V")
    }

    private fun isStringBuilderToString(descriptor: String): Boolean {
        return descriptor == "Ljava/lang/StringBuilder;->toString()Ljava/lang/String;" ||
            descriptor == "Ljava/lang/StringBuffer;->toString()Ljava/lang/String;"
    }

    private fun classifySensitiveApi(descriptor: String): String? {
        return when {
            descriptor.startsWith("Ljava/lang/Class;->forName(Ljava/lang/String;") ->
                "Ljava/lang/Class;->forName(Ljava/lang/String;)"
            descriptor.startsWith("Ldalvik/system/DexClassLoader;-><init>(Ljava/lang/String;") ->
                "Ldalvik/system/DexClassLoader;-><init>(Ljava/lang/String;)"
            descriptor.startsWith("Ldalvik/system/PathClassLoader;-><init>(Ljava/lang/String;") ->
                "Ldalvik/system/PathClassLoader;-><init>(Ljava/lang/String;)"
            descriptor.startsWith("Ljava/lang/Runtime;->exec(Ljava/lang/String;") ->
                "Ljava/lang/Runtime;->exec(Ljava/lang/String;)"
            descriptor.startsWith("Ljava/lang/ProcessBuilder;-><init>(Ljava/lang/String;") ->
                "Ljava/lang/ProcessBuilder;-><init>(Ljava/lang/String;)"
            descriptor.startsWith("Ljava/lang/System;->loadLibrary(Ljava/lang/String;") ->
                "Ljava/lang/System;->loadLibrary(Ljava/lang/String;)"
            descriptor.startsWith("Ljava/lang/System;->load(Ljava/lang/String;") ->
                "Ljava/lang/System;->load(Ljava/lang/String;)"
            descriptor.startsWith("Ljava/lang/reflect/Method;->invoke(Ljava/lang/Object;") ->
                "Ljava/lang/reflect/Method;->invoke(Ljava/lang/Object;[Ljava/lang/Object;)"
            else -> null
        }
    }

    private sealed class RegisterValue {
        abstract val value: String
        abstract val sourceStrings: List<String>
        abstract val offset: Int

        data class StringLiteral(
            override val value: String,
            override val sourceStrings: List<String>,
            override val offset: Int
        ) : RegisterValue()
    }

    companion object {
        /**
         * Parse a raw DEX code item into a list of DexInstruction objects suitable for
         * constant propagation. This is a focused decoder that extracts opcodes, operands,
         * and indexes for string/method/field/type references.
         */
        fun parseCodeItemInstructions(
            dexBytes: ByteArray,
            codeOffset: Int,
            maxInstructions: Int = 1000
        ): List<DexInstruction> {
            return try {
                val buffer = ByteBuffer.wrap(dexBytes).order(ByteOrder.LITTLE_ENDIAN)
                buffer.position(codeOffset)

                val registersSize = buffer.short.toInt() and 0xFFFF
                val insSize = buffer.short.toInt() and 0xFFFF
                val outsSize = buffer.short.toInt() and 0xFFFF
                val triesSize = buffer.short.toInt() and 0xFFFF
                val debugInfoOff = buffer.int
                val insnsSize = buffer.int

                val instructions = mutableListOf<DexInstruction>()
                val insnsStart = buffer.position()
                var pos = 0
                val limit = minOf(insnsSize, maxInstructions)

                while (pos < limit) {
                    val instructionOffset = insnsStart + pos * 2
                    if (instructionOffset + 2 > dexBytes.size) break

                    buffer.position(instructionOffset)
                    val instructionUnit = buffer.short.toInt() and 0xFFFF
                    val opcode = instructionUnit and 0xFF
                    val width = getInstructionWidth(opcode)

                    if (instructionOffset + width * 2 > dexBytes.size) break

                    val instruction = decodeInstruction(
                        buffer = buffer,
                        opcode = opcode,
                        width = width,
                        offset = instructionOffset - insnsStart,
                        instructionUnit = instructionUnit
                    )

                    instructions.add(instruction)
                    pos += width
                }

                instructions
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse code item instructions at offset $codeOffset")
                emptyList()
            }
        }

        private fun decodeInstruction(
            buffer: ByteBuffer,
            opcode: Int,
            width: Int,
            offset: Int,
            instructionUnit: Int
        ): DexInstruction {
            val operands = mutableListOf<Int>()
            var stringIndex: Int? = null
            var methodIndex: Int? = null

            when (opcode) {
                // const/4 vA, #+B
                0x12 -> {
                    operands.add((instructionUnit shr 8) and 0xF)
                    operands.add((instructionUnit shr 12) and 0xF)
                }

                // const-string vAA, string@BBBB
                0x1A -> {
                    val reg = (instructionUnit shr 8) and 0xFF
                    val idx = buffer.short.toInt() and 0xFFFF
                    operands.add(reg)
                    stringIndex = idx
                }

                // const-string/jumbo vAA, string@BBBBBBBB
                0x1B -> {
                    val reg = (instructionUnit shr 8) and 0xFF
                    val idx = buffer.int
                    operands.add(reg)
                    stringIndex = idx
                }

                // move-result-object vAA
                0x0C -> {
                    operands.add((instructionUnit shr 8) and 0xFF)
                }

                // invoke-virtual, invoke-super, invoke-direct, invoke-static, invoke-interface
                0x6E, 0x6F, 0x70, 0x71, 0x72 -> {
                    val argCount = (instructionUnit shr 12) and 0xF
                    val argBits = (instructionUnit shr 8) and 0xF
                    operands.add(argCount)

                    val nextShort = buffer.short.toInt() and 0xFFFF
                    val methodIdx = buffer.short.toInt() and 0xFFFF

                    val regs = mutableListOf<Int>()
                    if (argCount > 0) regs.add(argBits)
                    if (argCount > 1) regs.add(nextShort and 0xF)
                    if (argCount > 2) regs.add((nextShort shr 4) and 0xF)
                    if (argCount > 3) regs.add((nextShort shr 8) and 0xF)
                    if (argCount > 4) regs.add((nextShort shr 12) and 0xF)
                    operands.addAll(regs.take(argCount))
                    methodIndex = methodIdx
                }

                else -> {
                    operands.add(instructionUnit)
                    for (i in 1 until width) {
                        if (buffer.position() + 2 <= buffer.capacity()) {
                            operands.add(buffer.short.toInt() and 0xFFFF)
                        }
                    }
                }
            }

            return DexInstruction(
                offset = offset,
                opcode = opcode,
                width = width,
                operands = operands,
                stringIndex = stringIndex,
                methodIndex = methodIndex
            )
        }

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
    }
}

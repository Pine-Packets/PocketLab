package com.pineandpackets.pocketlab.engine.dex

import com.pineandpackets.pocketlab.core.model.DexClassDef
import com.pineandpackets.pocketlab.core.model.DexInstruction
import com.pineandpackets.pocketlab.core.model.DexMethod
import com.pineandpackets.pocketlab.core.model.DexMethodId
import com.pineandpackets.pocketlab.core.model.DexString
import org.junit.Assert.*
import org.junit.Test

class ConstantPropagatorTest {

    @Test
    fun `reconstruct literal const-string`() {
        val instructions = listOf(
            DexInstruction(offset = 0, opcode = 0x1A, width = 2, operands = listOf(0), stringIndex = 0),
            DexInstruction(
                offset = 4,
                opcode = 0x71,
                width = 3,
                operands = listOf(1, 0),
                methodIndex = 0
            )
        )

        val method = DexMethod(
            methodIdx = 0,
            accessFlags = 0,
            codeOff = 100,
            name = "loadClass",
            prototype = "()V",
            instructionCount = 2,
            instructions = instructions
        )

        val classDef = DexClassDef(
            index = 0,
            classIdx = 0,
            accessFlags = 0,
            superclassIdx = -1,
            interfacesOff = 0,
            sourceFileIdx = -1,
            annotationsOff = 0,
            classDataOff = 200,
            staticValuesOff = 0,
            className = "Lcom/example/Test;",
            superclass = null,
            sourceFile = null,
            methods = listOf(method)
        )

        val strings = listOf(DexString(0, "com.example.MaliciousClass", 26))
        val methodIds = listOf(
            DexMethodId(0, 0, 0, 0, "Ljava/lang/Class;", "forName", "(Ljava/lang/String;)")
        )

        val results = ConstantPropagator().reconstructStrings(
            classDefs = listOf(classDef),
            strings = strings,
            methodIds = methodIds
        )

        assertEquals(1, results.size)
        assertEquals("com.example.MaliciousClass", results[0].value)
        assertEquals("LITERAL", results[0].constructionType)
        assertEquals("Ljava/lang/Class;->forName(Ljava/lang/String;)", results[0].targetApi)
        assertEquals("HIGH", results[0].confidence)
    }

    @Test
    fun `reconstruct StringBuilder concatenation`() {
        val instructions = listOf(
            // new StringBuilder("http://")
            DexInstruction(offset = 0, opcode = 0x1A, width = 2, operands = listOf(0), stringIndex = 0),
            DexInstruction(
                offset = 4,
                opcode = 0x70,
                width = 3,
                operands = listOf(2, 1, 0),
                methodIndex = 0
            ),
            // append("example.com")
            DexInstruction(offset = 10, opcode = 0x1A, width = 2, operands = listOf(2), stringIndex = 1),
            DexInstruction(
                offset = 14,
                opcode = 0x6E,
                width = 3,
                operands = listOf(2, 1, 2),
                methodIndex = 1
            ),
            // toString()
            DexInstruction(
                offset = 20,
                opcode = 0x6E,
                width = 3,
                operands = listOf(1, 1),
                methodIndex = 2
            )
        )

        val method = DexMethod(
            methodIdx = 0,
            accessFlags = 0,
            codeOff = 100,
            name = "buildUrl",
            prototype = "()V",
            instructionCount = 5,
            instructions = instructions
        )

        val classDef = DexClassDef(
            index = 0,
            classIdx = 0,
            accessFlags = 0,
            superclassIdx = -1,
            interfacesOff = 0,
            sourceFileIdx = -1,
            annotationsOff = 0,
            classDataOff = 200,
            staticValuesOff = 0,
            className = "Lcom/example/Test;",
            superclass = null,
            sourceFile = null,
            methods = listOf(method)
        )

        val strings = listOf(
            DexString(0, "http://", 7),
            DexString(1, "example.com", 11)
        )

        val methodIds = listOf(
            DexMethodId(0, 0, 0, 0, "Ljava/lang/StringBuilder;", "<init>", "(Ljava/lang/String;)V"),
            DexMethodId(1, 0, 0, 0, "Ljava/lang/StringBuilder;", "append", "(Ljava/lang/String;)Ljava/lang/String;"),
            DexMethodId(2, 0, 0, 0, "Ljava/lang/StringBuilder;", "toString", "()Ljava/lang/String;")
        )

        val results = ConstantPropagator().reconstructStrings(
            classDefs = listOf(classDef),
            strings = strings,
            methodIds = methodIds
        )

        assertEquals(1, results.size)
        assertEquals("http://example.com", results[0].value)
        assertEquals("STRING_BUILDER", results[0].constructionType)
        assertEquals("HIGH", results[0].confidence)
    }

    @Test
    fun `ignore non-sensitive APIs`() {
        val instructions = listOf(
            DexInstruction(offset = 0, opcode = 0x1A, width = 2, operands = listOf(0), stringIndex = 0),
            DexInstruction(
                offset = 4,
                opcode = 0x6E,
                width = 3,
                operands = listOf(1, 0),
                methodIndex = 0
            )
        )

        val method = DexMethod(
            methodIdx = 0,
            accessFlags = 0,
            codeOff = 100,
            name = "safeCall",
            prototype = "()V",
            instructionCount = 2,
            instructions = instructions
        )

        val classDef = DexClassDef(
            index = 0,
            classIdx = 0,
            accessFlags = 0,
            superclassIdx = -1,
            interfacesOff = 0,
            sourceFileIdx = -1,
            annotationsOff = 0,
            classDataOff = 200,
            staticValuesOff = 0,
            className = "Lcom/example/Test;",
            superclass = null,
            sourceFile = null,
            methods = listOf(method)
        )

        val strings = listOf(DexString(0, "some value", 10))
        val methodIds = listOf(
            DexMethodId(0, 0, 0, 0, "Ljava/lang/String;", "length", "()")
        )

        val results = ConstantPropagator().reconstructStrings(
            classDefs = listOf(classDef),
            strings = strings,
            methodIds = methodIds
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `reconstruct Runtime exec command`() {
        val instructions = listOf(
            DexInstruction(offset = 0, opcode = 0x1A, width = 2, operands = listOf(0), stringIndex = 0),
            DexInstruction(
                offset = 4,
                opcode = 0x71,
                width = 3,
                operands = listOf(1, 0),
                methodIndex = 0
            )
        )

        val method = DexMethod(
            methodIdx = 0,
            accessFlags = 0,
            codeOff = 100,
            name = "execCommand",
            prototype = "()V",
            instructionCount = 2,
            instructions = instructions
        )

        val classDef = DexClassDef(
            index = 0,
            classIdx = 0,
            accessFlags = 0,
            superclassIdx = -1,
            interfacesOff = 0,
            sourceFileIdx = -1,
            annotationsOff = 0,
            classDataOff = 200,
            staticValuesOff = 0,
            className = "Lcom/example/Test;",
            superclass = null,
            sourceFile = null,
            methods = listOf(method)
        )

        val strings = listOf(DexString(0, "su -c reboot", 12))
        val methodIds = listOf(
            DexMethodId(0, 0, 0, 0, "Ljava/lang/Runtime;", "exec", "(Ljava/lang/String;)")
        )

        val results = ConstantPropagator().reconstructStrings(
            classDefs = listOf(classDef),
            strings = strings,
            methodIds = methodIds
        )

        assertEquals(1, results.size)
        assertEquals("su -c reboot", results[0].value)
        assertEquals("Ljava/lang/Runtime;->exec(Ljava/lang/String;)", results[0].targetApi)
    }

    @Test
    fun `respect maximum results limit`() {
        val methods = (0 until 200).map { index ->
            DexMethod(
                methodIdx = index,
                accessFlags = 0,
                codeOff = 100,
                name = "method$index",
                prototype = "()V",
                instructionCount = 2,
                instructions = listOf(
                    DexInstruction(offset = 0, opcode = 0x1A, width = 2, operands = listOf(0), stringIndex = 0),
                    DexInstruction(
                        offset = 4,
                        opcode = 0x71,
                        width = 3,
                        operands = listOf(1, 0),
                        methodIndex = 0
                    )
                )
            )
        }

        val classDef = DexClassDef(
            index = 0,
            classIdx = 0,
            accessFlags = 0,
            superclassIdx = -1,
            interfacesOff = 0,
            sourceFileIdx = -1,
            annotationsOff = 0,
            classDataOff = 200,
            staticValuesOff = 0,
            className = "Lcom/example/Test;",
            superclass = null,
            sourceFile = null,
            methods = methods
        )

        val strings = listOf(DexString(0, "payload", 7))
        val methodIds = listOf(
            DexMethodId(0, 0, 0, 0, "Ljava/lang/Class;", "forName", "(Ljava/lang/String;)")
        )

        val results = ConstantPropagator().reconstructStrings(
            classDefs = listOf(classDef),
            strings = strings,
            methodIds = methodIds
        )

        assertTrue("Should be capped", results.size <= 5000)
    }
}

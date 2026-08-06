package com.pineandpackets.pocketlab.engine.apk

import com.pineandpackets.pocketlab.core.common.AnalysisError
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser for Android binary XML format (AXML).
 * This is the format used for AndroidManifest.xml inside APK files.
 * 
 * Binary XML chunks:
 * - Chunk type (2 bytes)
 * - Header size (2 bytes)
 * - Chunk size (4 bytes)
 * - Chunk-specific data
 */
class BinaryXmlParser {
    
    companion object {
        // Chunk types
        const val CHUNK_AXML_FILE = 0x00080003
        const val CHUNK_STRING_POOL = 0x001C0001
        const val CHUNK_RESOURCE_IDS = 0x00080180
        const val CHUNK_XML_FIRST = 0x00100100
        const val CHUNK_XML_START_NAMESPACE = 0x00100100
        const val CHUNK_XML_END_NAMESPACE = 0x00100101
        const val CHUNK_XML_START_ELEMENT = 0x00100102
        const val CHUNK_XML_END_ELEMENT = 0x00100103
        const val CHUNK_XML_CDATA = 0x00100104
        const val CHUNK_XML_LAST = 0x00100104
        
        // Attribute types
        const val TYPE_NULL = 0x00
        const val TYPE_REFERENCE = 0x01
        const val TYPE_ATTRIBUTE = 0x02
        const val TYPE_STRING = 0x03
        const val TYPE_FLOAT = 0x04
        const val TYPE_DIMENSION = 0x05
        const val TYPE_FRACTION = 0x06
        const val TYPE_INT_DEC = 0x10
        const val TYPE_INT_HEX = 0x11
        const val TYPE_INT_BOOLEAN = 0x12
        const val TYPE_INT_COLOR_ARGB8 = 0x1C
        const val TYPE_INT_COLOR_RGB8 = 0x1D
        const val TYPE_INT_COLOR_ARGB4 = 0x1E
        const val TYPE_INT_COLOR_RGB4 = 0x1F
    }
    
    data class XmlElement(
        val namespace: String?,
        val name: String,
        val attributes: Map<String, AttributeValue>,
        val lineNumber: Int
    )
    
    data class AttributeValue(
        val namespace: String?,
        val name: String,
        val rawValue: String?,
        val typedValue: TypedValue
    )
    
    data class TypedValue(
        val type: Int,
        val data: Int
    )
    
    private var strings: Array<String> = emptyArray()
    private var resourceIds: IntArray = intArrayOf()
    
    fun parse(data: ByteArray): Result<List<XmlElement>> {
        return try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            
            // Read file header
            val fileType = buffer.int
            if (fileType != CHUNK_AXML_FILE) {
                return Result.failure(AnalysisError.ParserError("Not a binary XML file"))
            }
            val fileSize = buffer.int
            
            val elements = mutableListOf<XmlElement>()
            val namespaceStack = mutableListOf<Pair<String?, String>>()
            
            while (buffer.hasRemaining()) {
                val chunkType = buffer.int
                val headerSize = buffer.short.toInt()
                val chunkSize = buffer.int
                
                val chunkStart = buffer.position() - 8
                val dataStart = buffer.position()
                
                when (chunkType) {
                    CHUNK_STRING_POOL -> parseStringPool(buffer, headerSize, chunkSize)
                    CHUNK_RESOURCE_IDS -> parseResourceIds(buffer, headerSize, chunkSize)
                    CHUNK_XML_START_NAMESPACE -> {
                        val lineNumber = buffer.int
                        val comment = buffer.int
                        val prefix = buffer.int
                        val uri = buffer.int
                        val prefixStr = if (prefix >= 0 && prefix < strings.size) strings[prefix] else null
                        val uriStr = if (uri >= 0 && uri < strings.size) strings[uri] else null
                        if (uriStr != null) {
                            namespaceStack.add(prefixStr to uriStr)
                        }
                    }
                    CHUNK_XML_END_NAMESPACE -> {
                        if (namespaceStack.isNotEmpty()) {
                            namespaceStack.removeAt(namespaceStack.lastIndex)
                        }
                    }
                    CHUNK_XML_START_ELEMENT -> {
                        val element = parseStartElement(buffer, namespaceStack)
                        if (element != null) {
                            elements.add(element)
                        }
                    }
                    CHUNK_XML_END_ELEMENT -> {
                        // Skip end element
                    }
                    CHUNK_XML_CDATA -> {
                        // Skip CDATA
                    }
                }
                
                // Move to next chunk
                buffer.position(chunkStart + chunkSize)
            }
            
            Result.success(elements)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse binary XML")
            Result.failure(AnalysisError.ParserError("Failed to parse binary XML", e))
        }
    }
    
    private fun parseStringPool(buffer: ByteBuffer, headerSize: Int, chunkSize: Int) {
        val stringCount = buffer.int
        val styleCount = buffer.int
        val flags = buffer.int
        val stringsStart = buffer.int
        val stylesStart = buffer.int
        
        val isUtf8 = (flags and (1 shl 8)) != 0
        
        val stringOffsets = IntArray(stringCount) { buffer.int }
        val styleOffsets = IntArray(styleCount) { buffer.int }
        
        val absoluteStringsStart = buffer.position() - 8 + stringsStart
        
        strings = Array(stringCount) { i ->
            buffer.position(absoluteStringsStart + stringOffsets[i])
            if (isUtf8) {
                readUtf8String(buffer)
            } else {
                readUtf16String(buffer)
            }
        }
    }
    
    private fun readUtf8String(buffer: ByteBuffer): String {
        // UTF-8 strings have a 1 or 2 byte length prefix
        val len1 = buffer.get().toInt() and 0xFF
        val strLen = if (len1 and 0x80 != 0) {
            val len2 = buffer.get().toInt() and 0xFF
            ((len1 and 0x7F) shl 8) or len2
        } else {
            len1
        }
        
        val bytes = ByteArray(strLen)
        buffer.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }
    
    private fun readUtf16String(buffer: ByteBuffer): String {
        // UTF-16 strings have a 2 or 4 byte length prefix
        val len1 = buffer.short.toInt() and 0xFFFF
        val strLen = if (len1 and 0x8000 != 0) {
            val len2 = buffer.short.toInt() and 0xFFFF
            ((len1 and 0x7FFF) shl 16) or len2
        } else {
            len1
        }
        
        val chars = CharArray(strLen)
        for (i in 0 until strLen) {
            chars[i] = buffer.short.toInt().toChar()
        }
        return String(chars)
    }
    
    private fun parseResourceIds(buffer: ByteBuffer, headerSize: Int, chunkSize: Int) {
        val count = (chunkSize - 8) / 4
        resourceIds = IntArray(count) { buffer.int }
    }
    
    private fun parseStartElement(
        buffer: ByteBuffer,
        namespaceStack: List<Pair<String?, String>>
    ): XmlElement? {
        val lineNumber = buffer.int
        val comment = buffer.int
        val namespaceIdx = buffer.int
        val nameIdx = buffer.int
        val attributeStart = buffer.short.toInt()
        val attributeSize = buffer.short.toInt()
        val attributeCount = buffer.short.toInt()
        val idIndex = buffer.short.toInt()
        val classIndex = buffer.short.toInt()
        val styleIndex = buffer.short.toInt()
        
        val namespace = if (namespaceIdx >= 0 && namespaceIdx < strings.size) {
            strings[namespaceIdx]
        } else null
        
        val name = if (nameIdx >= 0 && nameIdx < strings.size) {
            strings[nameIdx]
        } else return null
        
        val attributes = mutableMapOf<String, AttributeValue>()
        
        for (i in 0 until attributeCount) {
            val attrNamespaceIdx = buffer.int
            val attrNameIdx = buffer.int
            val attrRawValueIdx = buffer.int
            val attrTypedSize = buffer.short.toInt()
            val attrTypedRes0 = buffer.get().toInt()
            val attrTypedType = buffer.get().toInt()
            val attrTypedData = buffer.int
            
            val attrNamespace = if (attrNamespaceIdx >= 0 && attrNamespaceIdx < strings.size) {
                strings[attrNamespaceIdx]
            } else null
            
            val attrName = if (attrNameIdx >= 0 && attrNameIdx < strings.size) {
                strings[attrNameIdx]
            } else continue
            
            val rawValue = if (attrRawValueIdx >= 0 && attrRawValueIdx < strings.size) {
                strings[attrRawValueIdx]
            } else null
            
            val key = if (attrNamespace != null) "$attrNamespace:$attrName" else attrName
            
            attributes[key] = AttributeValue(
                namespace = attrNamespace,
                name = attrName,
                rawValue = rawValue,
                typedValue = TypedValue(attrTypedType, attrTypedData)
            )
        }
        
        return XmlElement(
            namespace = namespace,
            name = name,
            attributes = attributes,
            lineNumber = lineNumber
        )
    }
}

package com.pineandpackets.pocketlab.engine.apk

import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser for Android resources.arsc (resource table) format.
 * This is a simplified implementation focused on resolving string resources.
 */
class ResourceTableParser {
    
    data class ResourceTable(
        val globalStrings: List<String>,
        val packages: List<Package>
    )
    
    data class Package(
        val id: Int,
        val name: String,
        val typeStrings: List<String>,
        val keyStrings: List<String>,
        val types: List<Type>
    )
    
    data class Type(
        val id: Int,
        val name: String,
        val entries: List<Entry?>
    )
    
    data class Entry(
        val keyIndex: Int,
        val value: Value?
    )
    
    data class Value(
        val type: Int,
        val data: Int,
        val stringValue: String?
    )
    
    companion object {
        // Chunk types
        const val RES_NULL_TYPE = 0x0000
        const val RES_STRING_POOL_TYPE = 0x0001
        const val RES_TABLE_TYPE = 0x0002
        const val RES_TABLE_PACKAGE_TYPE = 0x0200
        const val RES_TABLE_TYPE_TYPE = 0x0201
        const val RES_TABLE_TYPE_SPEC_TYPE = 0x0202
        
        // Value types
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
        const val TYPE_INT_COLOR_ARGB8 = 0x1c
        const val TYPE_INT_COLOR_RGB8 = 0x1d
        const val TYPE_INT_COLOR_ARGB4 = 0x1e
        const val TYPE_INT_COLOR_RGB4 = 0x1f
    }
    
    fun parse(data: ByteArray): Result<ResourceTable> {
        return try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            
            // Read table header
            val type = buffer.short.toInt() and 0xFFFF
            if (type != RES_TABLE_TYPE) {
                return Result.failure(IllegalArgumentException("Not a resource table"))
            }
            
            val headerSize = buffer.short.toInt() and 0xFFFF
            val size = buffer.int
            val packageCount = buffer.int
            
            // Parse global string pool
            val globalStrings = parseStringPool(buffer)
            
            // Parse packages
            val packages = mutableListOf<Package>()
            for (i in 0 until packageCount) {
                val pkg = parsePackage(buffer)
                if (pkg != null) {
                    packages.add(pkg)
                }
            }
            
            Result.success(ResourceTable(globalStrings, packages))
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse resource table")
            Result.failure(e)
        }
    }
    
    private fun parseStringPool(buffer: ByteBuffer): List<String> {
        val startPos = buffer.position()
        
        val type = buffer.short.toInt() and 0xFFFF
        if (type != RES_STRING_POOL_TYPE) {
            buffer.position(startPos)
            return emptyList()
        }
        
        val headerSize = buffer.short.toInt() and 0xFFFF
        val chunkSize = buffer.int
        val stringCount = buffer.int
        val styleCount = buffer.int
        val flags = buffer.int
        val stringsStart = buffer.int
        val stylesStart = buffer.int
        
        val isUtf8 = (flags and (1 shl 8)) != 0
        
        // Read string offsets
        val offsets = IntArray(stringCount) { buffer.int }
        
        // Skip style offsets
        buffer.position(startPos + headerSize + stringCount * 4 + styleCount * 4)
        
        // Read strings
        val strings = mutableListOf<String>()
        val absoluteStringsStart = startPos + stringsStart
        
        for (i in 0 until stringCount) {
            buffer.position(absoluteStringsStart + offsets[i])
            val str = if (isUtf8) {
                readUtf8String(buffer)
            } else {
                readUtf16String(buffer)
            }
            strings.add(str)
        }
        
        // Move to end of chunk
        buffer.position(startPos + chunkSize)
        
        return strings
    }
    
    private fun readUtf8String(buffer: ByteBuffer): String {
        // UTF-8 strings have character count and byte count
        val charCount = readUtf8Length(buffer)
        val byteCount = readUtf8Length(buffer)
        
        val bytes = ByteArray(byteCount)
        buffer.get(bytes)
        
        // Skip null terminator
        if (buffer.hasRemaining()) {
            buffer.get()
        }
        
        return String(bytes, Charsets.UTF_8)
    }
    
    private fun readUtf8Length(buffer: ByteBuffer): Int {
        val b1 = buffer.get().toInt() and 0xFF
        return if (b1 and 0x80 != 0) {
            val b2 = buffer.get().toInt() and 0xFF
            ((b1 and 0x7F) shl 8) or b2
        } else {
            b1
        }
    }
    
    private fun readUtf16String(buffer: ByteBuffer): String {
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
        
        // Skip null terminator
        if (buffer.hasRemaining()) {
            buffer.short
        }
        
        return String(chars)
    }
    
    private fun parsePackage(buffer: ByteBuffer): Package? {
        val startPos = buffer.position()
        
        val type = buffer.short.toInt() and 0xFFFF
        if (type != RES_TABLE_PACKAGE_TYPE) {
            return null
        }
        
        val headerSize = buffer.short.toInt() and 0xFFFF
        val chunkSize = buffer.int
        val id = buffer.int
        
        // Package name (128 uint16 chars)
        val nameChars = CharArray(128)
        for (i in 0 until 128) {
            nameChars[i] = buffer.short.toInt().toChar()
        }
        val name = String(nameChars).trimEnd('\u0000')
        
        val typeStrings = buffer.int
        val lastPublicType = buffer.int
        val keyStrings = buffer.int
        val lastPublicKey = buffer.int
        
        // Type ID offset (optional, added in later versions)
        val typeIdOffset = if (headerSize >= 288) buffer.int else 0
        
        // Parse type strings
        buffer.position(startPos + typeStrings)
        val typeStringList = parseStringPool(buffer)
        
        // Parse key strings
        buffer.position(startPos + keyStrings)
        val keyStringList = parseStringPool(buffer)
        
        // Parse types
        val types = mutableListOf<Type>()
        var currentPos = startPos + headerSize
        
        while (currentPos < startPos + chunkSize && buffer.hasRemaining()) {
            buffer.position(currentPos)
            
            val chunkType = buffer.short.toInt() and 0xFFFF
            val chunkHeaderSize = buffer.short.toInt() and 0xFFFF
            val chunkTotalSize = buffer.int
            
            if (chunkType == RES_TABLE_TYPE_TYPE) {
                val type = parseType(buffer, chunkHeaderSize, chunkTotalSize, typeStringList, keyStringList)
                if (type != null) {
                    types.add(type)
                }
            }
            
            currentPos += chunkTotalSize
        }
        
        return Package(id, name, typeStringList, keyStringList, types)
    }
    
    private fun parseType(
        buffer: ByteBuffer,
        headerSize: Int,
        totalSize: Int,
        typeStrings: List<String>,
        keyStrings: List<String>
    ): Type? {
        val startPos = buffer.position() - 8 // Back to chunk start
        
        val id = buffer.get().toInt() and 0xFF
        val res0 = buffer.get().toInt() and 0xFF
        val res1 = buffer.short.toInt() and 0xFFFF
        val entryCount = buffer.int
        val entriesStart = buffer.int
        
        // Config (variable size, skip for now)
        val configSize = buffer.int
        buffer.position(startPos + headerSize)
        
        // Read entry offsets
        val offsets = IntArray(entryCount) { buffer.int }
        
        // Parse entries
        val entries = mutableListOf<Entry?>()
        val absoluteEntriesStart = startPos + entriesStart
        
        for (i in 0 until entryCount) {
            if (offsets[i] == -1) {
                entries.add(null)
                continue
            }
            
            buffer.position(absoluteEntriesStart + offsets[i])
            
            val entrySize = buffer.short.toInt() and 0xFFFF
            val flags = buffer.short.toInt() and 0xFFFF
            val keyIndex = buffer.int
            
            val isComplex = (flags and 0x0001) != 0
            
            if (isComplex) {
                // Complex entry (map/array), skip for now
                entries.add(null)
            } else {
                // Simple entry
                val value = parseValue(buffer)
                entries.add(Entry(keyIndex, value))
            }
        }
        
        val typeName = if (id > 0 && id <= typeStrings.size) typeStrings[id - 1] else "unknown"
        
        return Type(id, typeName, entries)
    }
    
    private fun parseValue(buffer: ByteBuffer): Value {
        val size = buffer.short.toInt() and 0xFFFF
        val res0 = buffer.get().toInt() and 0xFF
        val type = buffer.get().toInt() and 0xFF
        val data = buffer.int
        
        val stringValue = if (type == TYPE_STRING) {
            // String values reference the global string pool
            // We'll resolve this later
            null
        } else {
            null
        }
        
        return Value(type, data, stringValue)
    }
    
    /**
     * Resolve a resource ID to its string value.
     * Resource ID format: 0xPPTTEEEE
     * PP = package ID
     * TT = type ID
     * EEEE = entry index
     */
    fun resolveStringResource(table: ResourceTable, resourceId: Int): String? {
        val packageId = (resourceId ushr 24) and 0xFF
        val typeId = (resourceId ushr 16) and 0xFF
        val entryIndex = resourceId and 0xFFFF
        
        val pkg = table.packages.find { it.id == packageId } ?: return null
        
        if (typeId <= 0 || typeId > pkg.types.size) return null
        val type = pkg.types[typeId - 1]
        
        if (entryIndex >= type.entries.size) return null
        val entry = type.entries[entryIndex] ?: return null
        
        val value = entry.value ?: return null
        
        return when (value.type) {
            TYPE_STRING -> {
                // String value references global string pool
                if (value.data >= 0 && value.data < table.globalStrings.size) {
                    table.globalStrings[value.data]
                } else {
                    null
                }
            }
            TYPE_INT_DEC -> value.data.toString()
            TYPE_INT_HEX -> "0x${value.data.toString(16)}"
            TYPE_INT_BOOLEAN -> if (value.data != 0) "true" else "false"
            else -> null
        }
    }
}

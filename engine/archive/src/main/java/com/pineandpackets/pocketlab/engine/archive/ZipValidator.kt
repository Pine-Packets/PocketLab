package com.pineandpackets.pocketlab.engine.archive

import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Validates ZIP archive structure including central directory, EOCD, and Zip64 records.
 * This helps detect malformed or malicious archives before processing.
 */
class ZipValidator {
    
    companion object {
        // ZIP signatures
        const val LOCAL_FILE_HEADER_SIG = 0x04034b50
        const val CENTRAL_DIR_SIG = 0x02014b50
        const val EOCD_SIG = 0x06054b50
        const val ZIP64_EOCD_SIG = 0x06064b50
        const val ZIP64_EOCD_LOCATOR_SIG = 0x07064b50
        const val DATA_DESCRIPTOR_SIG = 0x08074b50
        
        // Limits
        const val MAX_COMMENT_LENGTH = 65536
        const val MIN_EOCD_SIZE = 22
    }
    
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val entryCount: Int = 0,
        val centralDirectoryOffset: Long = 0,
        val centralDirectorySize: Long = 0,
        val isZip64: Boolean = false
    )
    
    /**
     * Validate a ZIP file's structure.
     */
    fun validate(file: File): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        if (!file.exists()) {
            return ValidationResult(false, listOf("File does not exist"))
        }
        
        if (file.length() < MIN_EOCD_SIZE) {
            return ValidationResult(false, listOf("File too small to be a valid ZIP"))
        }
        
        return try {
            RandomAccessFile(file, "r").use { raf ->
                // Find EOCD record
                val eocdOffset = findEocd(raf)
                if (eocdOffset == null) {
                    return ValidationResult(false, listOf("End of Central Directory record not found"))
                }
                
                // Parse EOCD
                raf.seek(eocdOffset)
                val eocdBuffer = ByteArray(22)
                raf.readFully(eocdBuffer)
                val eocd = ByteBuffer.wrap(eocdBuffer).order(ByteOrder.LITTLE_ENDIAN)
                
                val signature = eocd.int
                if (signature != EOCD_SIG) {
                    return ValidationResult(false, listOf("Invalid EOCD signature"))
                }
                
                eocd.short // disk number
                eocd.short // disk with central dir
                val entriesOnDisk = eocd.short.toInt() and 0xFFFF
                val totalEntries = eocd.short.toInt() and 0xFFFF
                val centralDirSize = eocd.int.toLong() and 0xFFFFFFFFL
                val centralDirOffset = eocd.int.toLong() and 0xFFFFFFFFL
                val commentLength = eocd.short.toInt() and 0xFFFF
                
                // Validate entry counts match
                if (entriesOnDisk != totalEntries) {
                    warnings.add("Entry count mismatch: $entriesOnDisk on disk, $totalEntries total")
                }
                
                // Check for Zip64
                var isZip64 = false
                if (entriesOnDisk == 0xFFFF || centralDirOffset == 0xFFFFFFFFL || centralDirSize == 0xFFFFFFFFL) {
                    isZip64 = true
                    // Find and validate Zip64 EOCD
                    val zip64Result = validateZip64(raf, eocdOffset)
                    if (!zip64Result.isValid) {
                        errors.addAll(zip64Result.errors)
                    }
                    warnings.addAll(zip64Result.warnings)
                }
                
                // Validate central directory offset
                if (centralDirOffset >= file.length()) {
                    errors.add("Central directory offset ($centralDirOffset) beyond file size (${file.length()})")
                }
                
                // Validate central directory size
                if (centralDirOffset + centralDirSize > file.length()) {
                    errors.add("Central directory extends beyond file size")
                }
                
                // Validate comment length
                if (commentLength > MAX_COMMENT_LENGTH) {
                    warnings.add("Unusually large comment: $commentLength bytes")
                }
                
                // Validate central directory entries
                val cdValidation = validateCentralDirectory(raf, centralDirOffset, totalEntries)
                errors.addAll(cdValidation.errors)
                warnings.addAll(cdValidation.warnings)
                
                ValidationResult(
                    isValid = errors.isEmpty(),
                    errors = errors,
                    warnings = warnings,
                    entryCount = totalEntries,
                    centralDirectoryOffset = centralDirOffset,
                    centralDirectorySize = centralDirSize,
                    isZip64 = isZip64
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to validate ZIP file")
            ValidationResult(false, listOf("Validation failed: ${e.message}"))
        }
    }
    
    /**
     * Find the End of Central Directory record.
     */
    private fun findEocd(raf: RandomAccessFile): Long? {
        val fileSize = raf.length()
        val maxCommentSearch = MIN_EOCD_SIZE + MAX_COMMENT_LENGTH
        val searchStart = maxOf(0, fileSize - maxCommentSearch)
        
        raf.seek(searchStart)
        val buffer = ByteArray((fileSize - searchStart).toInt())
        raf.readFully(buffer)
        
        // Search backwards for EOCD signature
        for (i in buffer.size - MIN_EOCD_SIZE downTo 0) {
            val sig = ByteBuffer.wrap(buffer, i, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (sig == EOCD_SIG) {
                return searchStart + i
            }
        }
        
        return null
    }
    
    /**
     * Validate Zip64 End of Central Directory record.
     */
    private fun validateZip64(raf: RandomAccessFile, eocdOffset: Long): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Look for Zip64 EOCD Locator (20 bytes before EOCD)
        val locatorOffset = eocdOffset - 20
        if (locatorOffset < 0) {
            warnings.add("Zip64 EOCD Locator not found")
            return ValidationResult(true, warnings = warnings)
        }
        
        raf.seek(locatorOffset)
        val locatorBuffer = ByteArray(20)
        raf.readFully(locatorBuffer)
        val locator = ByteBuffer.wrap(locatorBuffer).order(ByteOrder.LITTLE_ENDIAN)
        
        val locatorSig = locator.int
        if (locatorSig != ZIP64_EOCD_LOCATOR_SIG) {
            warnings.add("Invalid Zip64 EOCD Locator signature")
            return ValidationResult(true, warnings = warnings)
        }
        
        locator.int // disk with Zip64 EOCD
        val zip64EocdOffset = locator.long
        
        // Validate Zip64 EOCD
        if (zip64EocdOffset >= raf.length()) {
            errors.add("Zip64 EOCD offset beyond file size")
            return ValidationResult(false, errors = errors)
        }
        
        raf.seek(zip64EocdOffset)
        val zip64Buffer = ByteArray(56)
        raf.readFully(zip64Buffer)
        val zip64 = ByteBuffer.wrap(zip64Buffer).order(ByteOrder.LITTLE_ENDIAN)
        
        val zip64Sig = zip64.int
        if (zip64Sig != ZIP64_EOCD_SIG) {
            errors.add("Invalid Zip64 EOCD signature")
            return ValidationResult(false, errors = errors)
        }
        
        return ValidationResult(true, warnings = warnings)
    }
    
    /**
     * Validate central directory entries.
     */
    private fun validateCentralDirectory(
        raf: RandomAccessFile,
        offset: Long,
        expectedEntries: Int
    ): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        raf.seek(offset)
        var entriesFound = 0
        val fileSize = raf.length()
        
        while (entriesFound < expectedEntries) {
            val pos = raf.filePointer
            if (pos >= fileSize) {
                errors.add("Central directory truncated at entry $entriesFound")
                break
            }
            
            val sigBuffer = ByteArray(4)
            raf.readFully(sigBuffer)
            val signature = ByteBuffer.wrap(sigBuffer).order(ByteOrder.LITTLE_ENDIAN).int
            
            if (signature != CENTRAL_DIR_SIG) {
                errors.add("Invalid central directory entry signature at offset $pos")
                break
            }
            
            // Read central directory entry header
            val headerBuffer = ByteArray(42)
            raf.readFully(headerBuffer)
            val header = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN)
            
            header.short // version made by
            header.short // version needed
            header.short // flags
            header.short // compression
            header.short // mod time
            header.short // mod date
            header.int // crc32
            val compressedSize = header.int.toLong() and 0xFFFFFFFFL
            val uncompressedSize = header.int.toLong() and 0xFFFFFFFFL
            val fileNameLength = header.short.toInt() and 0xFFFF
            val extraFieldLength = header.short.toInt() and 0xFFFF
            val commentLength = header.short.toInt() and 0xFFFF
            header.short // disk number start
            header.short // internal attributes
            header.int // external attributes
            val localHeaderOffset = header.int.toLong() and 0xFFFFFFFFL
            
            // Validate local header offset
            if (localHeaderOffset >= fileSize) {
                errors.add("Local header offset ($localHeaderOffset) beyond file size at entry $entriesFound")
            }
            
            // Validate sizes
            if (compressedSize == 0xFFFFFFFFL || uncompressedSize == 0xFFFFFFFFL) {
                // Zip64 sizes, would need to parse extra field
                warnings.add("Zip64 sizes at entry $entriesFound")
            }
            
            // Skip filename, extra field, and comment
            val skipBytes = fileNameLength + extraFieldLength + commentLength
            if (pos + 46 + skipBytes > fileSize) {
                errors.add("Central directory entry $entriesFound extends beyond file")
                break
            }
            
            raf.seek(pos + 46 + skipBytes)
            entriesFound++
        }
        
        if (entriesFound != expectedEntries) {
            warnings.add("Expected $expectedEntries entries, found $entriesFound")
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            entryCount = entriesFound
        )
    }
}

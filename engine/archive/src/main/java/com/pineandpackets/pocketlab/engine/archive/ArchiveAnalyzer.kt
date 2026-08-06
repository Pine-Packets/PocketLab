package com.pineandpackets.pocketlab.engine.archive

import com.pineandpackets.pocketlab.core.common.AnalysisError
import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import com.pineandpackets.pocketlab.core.common.addChecked
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import timber.log.Timber
import java.io.File

class ArchiveAnalyzer {
    
    private val zipValidator = ZipValidator()
    
    fun analyzeArchive(archiveFile: File, password: String? = null): Result<ArchiveAnalysisResult> {
        return try {
            // Validate ZIP structure first
            val validationResult = zipValidator.validate(archiveFile)
            if (!validationResult.isValid) {
                return Result.failure(
                    AnalysisError.ArchiveError("Invalid ZIP structure: ${validationResult.errors.joinToString(", ")}")
                )
            }
            
            if (validationResult.warnings.isNotEmpty()) {
                Timber.w("ZIP validation warnings: ${validationResult.warnings.joinToString(", ")}")
            }
            
            val zipFile = ZipFile.builder().setFile(archiveFile).get()
            val decompressionCounter = DecompressionCounter()
            
            zipFile.use { zip ->
                val entries = zip.entries.toList()
                
                if (entries.size > AnalysisLimits.MAX_ARCHIVE_ENTRIES) {
                    return Result.failure(
                        AnalysisError.QuotaExceededError("Archive contains ${entries.size} entries, exceeds limit of ${AnalysisLimits.MAX_ARCHIVE_ENTRIES}")
                    )
                }
                
                val analyzedEntries = mutableListOf<AnalyzedArchiveEntry>()
                var totalExpandedSize = 0L
                var suspiciousPaths = mutableListOf<String>()
                
                for (entry in entries) {
                    // Track entry count
                    val entryResult = decompressionCounter.addEntry()
                    if (entryResult.isFailure) {
                        return Result.failure(
                            AnalysisError.QuotaExceededError(entryResult.exceptionOrNull()?.message ?: "Decompression limit exceeded")
                        )
                    }
                    
                    val entryName = entry.name
                    
                    if (isPathSuspicious(entryName)) {
                        suspiciousPaths.add(entryName)
                    }
                    
                    val normalizedPath = normalizePath(entryName)
                    if (normalizedPath == null) {
                        suspiciousPaths.add(entryName)
                        continue
                    }
                    
                    val expandedSize = entry.size
                    val compressedSize = entry.compressedSize
                    
                    // Track compressed bytes
                    decompressionCounter.addCompressedBytes(compressedSize)
                    
                    // Track uncompressed bytes
                    val uncompressedResult = decompressionCounter.addUncompressedBytes(expandedSize)
                    if (uncompressedResult.isFailure) {
                        return Result.failure(
                            AnalysisError.QuotaExceededError(uncompressedResult.exceptionOrNull()?.message ?: "Decompression limit exceeded")
                        )
                    }
                    
                    // Check compression ratio
                    val ratioResult = decompressionCounter.checkCompressionRatio(compressedSize, expandedSize)
                    if (ratioResult.isFailure) {
                        return Result.failure(
                            AnalysisError.QuotaExceededError(ratioResult.exceptionOrNull()?.message ?: "Compression ratio too high")
                        )
                    }
                    
                    if (expandedSize > AnalysisLimits.MAX_SINGLE_ENTRY_BYTES) {
                        return Result.failure(
                            AnalysisError.QuotaExceededError("Entry $entryName exceeds size limit")
                        )
                    }
                    
                    totalExpandedSize = addChecked(
                        totalExpandedSize,
                        expandedSize,
                        "Total expanded size overflow"
                    )
                    if (totalExpandedSize > AnalysisLimits.MAX_ARCHIVE_EXPANDED_BYTES) {
                        return Result.failure(
                            AnalysisError.QuotaExceededError("Total expanded size exceeds limit")
                        )
                    }
                    
                    analyzedEntries.add(
                        AnalyzedArchiveEntry(
                            originalPath = entryName,
                            normalizedPath = normalizedPath,
                            compressedSize = compressedSize,
                            expandedSize = expandedSize,
                            isDirectory = entry.isDirectory(),
                            isEncrypted = false, // TODO: Detect encryption from entry flags
                            isNestedArchive = isArchiveFile(entryName, entry)
                        )
                    )
                }
                
                val hasEncryptedEntries = analyzedEntries.any { it.isEncrypted }
                val decompressionStats = decompressionCounter.getStats()
                
                Timber.i("Decompression stats: ${decompressionStats.entryCount} entries, " +
                    "ratio: ${"%.2f".format(decompressionStats.getOverallRatio())}, " +
                    "max ratio: ${"%.2f".format(decompressionStats.maxCompressionRatio)}")
                
                Result.success(
                    ArchiveAnalysisResult(
                        entryCount = entries.size,
                        totalCompressedSize = archiveFile.length(),
                        totalExpandedSize = totalExpandedSize,
                        entries = analyzedEntries,
                        suspiciousPaths = suspiciousPaths,
                        isEncrypted = hasEncryptedEntries,
                        passwordRequired = hasEncryptedEntries && password == null,
                        passwordAccepted = password != null && hasEncryptedEntries
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to analyze archive")
            Result.failure(AnalysisError.ArchiveError("Failed to analyze archive", e))
        }
    }
    
    fun analyzeArchiveWithPasswordAttempts(
        archiveFile: File,
        passwords: List<String> = listOf("infected")
    ): Result<ArchiveAnalysisResult> {
        val noPasswordResult = analyzeArchive(archiveFile, null)
        
        if (noPasswordResult.isSuccess && !noPasswordResult.getOrNull()!!.passwordRequired) {
            return noPasswordResult
        }
        
        for (password in passwords) {
            val result = analyzeArchive(archiveFile, password)
            if (result.isSuccess && !result.getOrNull()!!.passwordRequired) {
                return result
            }
        }
        
        return Result.failure(
            AnalysisError.ArchiveError("Archive is encrypted and requires password. Password-protected content cannot be analyzed without decryption support.")
        )
    }
    
    private fun isPathSuspicious(path: String): Boolean {
        return path.contains("..") ||
               path.startsWith("/") ||
               path.startsWith("\\") ||
               path.matches(Regex("^[A-Za-z]:.*")) ||
               path.contains("\u0000")
    }
    
    private fun normalizePath(path: String): String? {
        if (path.contains("\u0000")) return null
        
        val normalized = path
            .replace("\\", "/")
            .split("/")
            .filter { it != "." && it != ".." && it.isNotEmpty() }
            .joinToString("/")
        
        if (normalized.startsWith("/") || normalized.contains("..")) {
            return null
        }
        
        return normalized
    }
    
    private fun isArchiveFile(entryName: String, entry: ZipArchiveEntry): Boolean {
        // Check by extension
        val lowerName = entryName.lowercase()
        val archiveExtensions = listOf(".zip", ".jar", ".apk", ".aar", ".war", ".ear")
        if (archiveExtensions.any { lowerName.endsWith(it) }) {
            return true
        }
        
        // Could also check magic bytes by reading the entry content, but that requires extraction
        // For now, we rely on extension-based detection
        
        return false
    }
}

data class ArchiveAnalysisResult(
    val entryCount: Int,
    val totalCompressedSize: Long,
    val totalExpandedSize: Long,
    val entries: List<AnalyzedArchiveEntry>,
    val suspiciousPaths: List<String>,
    val isEncrypted: Boolean,
    val passwordRequired: Boolean = false,
    val passwordAccepted: Boolean = false
)

data class AnalyzedArchiveEntry(
    val originalPath: String,
    val normalizedPath: String,
    val compressedSize: Long,
    val expandedSize: Long,
    val isDirectory: Boolean,
    val isEncrypted: Boolean,
    val isNestedArchive: Boolean = false
)

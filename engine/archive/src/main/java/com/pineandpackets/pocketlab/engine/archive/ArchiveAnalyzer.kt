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
    
    /**
     * Analyzes an archive with support for nested archives.
     * 
     * @param archiveFile The archive file to analyze
     * @param password Optional password for encrypted archives
     * @param currentDepth Current nesting depth (used for recursion tracking)
     * @param globalQuota Shared quota tracker across all nested archives
     * @return Analysis result with nested archive information
     */
    fun analyzeArchive(
        archiveFile: File, 
        password: String? = null,
        currentDepth: Int = 0,
        globalQuota: ArchiveQuotaTracker = ArchiveQuotaTracker()
    ): Result<ArchiveAnalysisResult> {
        // Check depth limit
        if (!globalQuota.canProcessDepth(currentDepth)) {
            return Result.failure(
                AnalysisError.QuotaExceededError("Maximum nesting depth of ${AnalysisLimits.MAX_NESTING_DEPTH} exceeded")
            )
        }
        globalQuota.updateDepth(currentDepth)
        
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
            
            // Build ZIP file with password if provided
            // First, try to open without password to check if it's actually encrypted
            val zipFile = try {
                ZipFile.builder().setFile(archiveFile).get()
            } catch (e: Exception) {
                // If opening without password fails and we have a password, try with password
                if (password != null) {
                    try {
                        @Suppress("DEPRECATION")
                        ZipFile(archiveFile, password)
                    } catch (e2: Exception) {
                        throw AnalysisError.ArchiveError("Failed to open archive with password: ${e2.message}", e2)
                    }
                } else {
                    throw AnalysisError.ArchiveError("Failed to open archive: ${e.message}", e)
                }
            }
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
                    // Check global quota before processing each entry
                    if (!globalQuota.canProcessMoreEntries()) {
                        return Result.failure(
                            AnalysisError.QuotaExceededError("Global entry quota exceeded across nested archives")
                        )
                    }
                    
                    // Track entry count
                    val entryResult = decompressionCounter.addEntry()
                    if (entryResult.isFailure) {
                        return Result.failure(
                            AnalysisError.QuotaExceededError(entryResult.exceptionOrNull()?.message ?: "Decompression limit exceeded")
                        )
                    }
                    
                    // Update global quota
                    globalQuota.addEntry()
                    
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
                    
                    // Check global byte quota
                    if (!globalQuota.canProcessMoreBytes()) {
                        return Result.failure(
                            AnalysisError.QuotaExceededError("Global byte quota exceeded across nested archives")
                        )
                    }
                    
                    // Track compressed bytes
                    decompressionCounter.addCompressedBytes(compressedSize)
                    
                    // Track uncompressed bytes
                    val uncompressedResult = decompressionCounter.addUncompressedBytes(expandedSize)
                    if (uncompressedResult.isFailure) {
                        return Result.failure(
                            AnalysisError.QuotaExceededError(uncompressedResult.exceptionOrNull()?.message ?: "Decompression limit exceeded")
                        )
                    }
                    
                    // Update global quota
                    globalQuota.addExpandedBytes(expandedSize)
                    
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
                    
                    // Detect encryption from entry flags
                    val isEncrypted = isEntryEncrypted(entry)
                    val isNested = isArchiveFile(entryName, entry)
                    
                    // Handle nested archive
                    var nestedResult: ArchiveAnalysisResult? = null
                    if (isNested && !entry.isDirectory && !isEncrypted) {
                        // Extract nested archive to temp file for analysis
                        val tempFile = extractEntryToTempFile(zip, entry)
                        if (tempFile != null) {
                            try {
                                val nestedAnalysis = analyzeArchive(
                                    tempFile,
                                    password = null,
                                    currentDepth = currentDepth + 1,
                                    globalQuota = globalQuota
                                )
                                if (nestedAnalysis.isSuccess) {
                                    nestedResult = nestedAnalysis.getOrNull()
                                } else {
                                    Timber.w("Failed to analyze nested archive $entryName: ${nestedAnalysis.exceptionOrNull()?.message}")
                                }
                            } finally {
                                tempFile.delete()
                            }
                        }
                    }
                    
                    analyzedEntries.add(
                        AnalyzedArchiveEntry(
                            originalPath = entryName,
                            normalizedPath = normalizedPath,
                            compressedSize = compressedSize,
                            expandedSize = expandedSize,
                            isDirectory = entry.isDirectory(),
                            isEncrypted = isEncrypted,
                            isNestedArchive = isNested,
                            nestedArchiveResult = nestedResult
                        )
                    )
                }
                
                val hasEncryptedEntries = analyzedEntries.any { it.isEncrypted }
                val decompressionStats = decompressionCounter.getStats()
                val unsupportedEntries = analyzedEntries
                    .filter { !it.isEncrypted && isUnsupportedMethod(entries.firstOrNull { e -> e.name == it.originalPath }) }
                    .map { it.normalizedPath }
                
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
                        passwordAccepted = password != null && hasEncryptedEntries,
                        maxObservedRatio = decompressionStats.maxCompressionRatio,
                        nestedDepth = globalQuota.maxDepthReached,
                        unsupportedEntries = unsupportedEntries
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to analyze archive")
            Result.failure(AnalysisError.ArchiveError("Failed to analyze archive", e))
        }
    }
    
    private fun extractEntryToTempFile(zip: ZipFile, entry: ZipArchiveEntry): File? {
        return try {
            val tempFile = File.createTempFile("nested_archive_", ".tmp")
            zip.getInputStream(entry).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract nested archive entry ${entry.name}")
            null
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
    
    private fun isEntryEncrypted(entry: ZipArchiveEntry): Boolean {
        // Check general purpose bit flag for encryption
        // Apache Commons Compress provides a method to check this directly
        return try {
            entry.generalPurposeBit?.usesEncryption() ?: false
        } catch (e: Exception) {
            // Fallback: assume not encrypted if we can't determine
            false
        }
    }
    
    private fun isUnsupportedMethod(entry: ZipArchiveEntry?): Boolean {
        if (entry == null) return false
        return try {
            val method = entry.method
            method != ZipArchiveEntry.STORED && method != ZipArchiveEntry.DEFLATED
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Tracks global quotas across nested archive processing to prevent resource exhaustion.
 */
class ArchiveQuotaTracker {
    var totalEntriesProcessed = 0
        private set
    var totalExpandedBytes = 0L
        private set
    var maxDepthReached = 0
        private set
    
    fun addEntry() {
        totalEntriesProcessed++
    }
    
    fun addExpandedBytes(bytes: Long) {
        totalExpandedBytes += bytes
    }
    
    fun updateDepth(depth: Int) {
        if (depth > maxDepthReached) {
            maxDepthReached = depth
        }
    }
    
    fun canProcessMoreEntries(): Boolean {
        return totalEntriesProcessed < AnalysisLimits.MAX_ARCHIVE_ENTRIES
    }
    
    fun canProcessMoreBytes(): Boolean {
        return totalExpandedBytes < AnalysisLimits.MAX_ARCHIVE_EXPANDED_BYTES
    }
    
    fun canProcessDepth(depth: Int): Boolean {
        return depth <= AnalysisLimits.MAX_NESTING_DEPTH
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
    val passwordAccepted: Boolean = false,
    val maxObservedRatio: Double = 0.0,
    val nestedDepth: Int = 0,
    val quotaEvents: List<String> = emptyList(),
    val unsupportedEntries: List<String> = emptyList()
)

data class AnalyzedArchiveEntry(
    val originalPath: String,
    val normalizedPath: String,
    val compressedSize: Long,
    val expandedSize: Long,
    val isDirectory: Boolean,
    val isEncrypted: Boolean,
    val isNestedArchive: Boolean = false,
    val nestedArchiveResult: ArchiveAnalysisResult? = null
)

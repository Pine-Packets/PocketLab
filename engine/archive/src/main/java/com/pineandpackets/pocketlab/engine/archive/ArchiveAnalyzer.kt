package com.pineandpackets.pocketlab.engine.archive

import com.pineandpackets.pocketlab.core.common.AnalysisError
import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import timber.log.Timber
import java.io.File

class ArchiveAnalyzer {
    
    fun analyzeArchive(archiveFile: File): Result<ArchiveAnalysisResult> {
        return try {
            val zipFile = ZipFile.builder().setFile(archiveFile).get()
            
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
                    if (expandedSize > AnalysisLimits.MAX_SINGLE_ENTRY_BYTES) {
                        return Result.failure(
                            AnalysisError.QuotaExceededError("Entry $entryName exceeds size limit")
                        )
                    }
                    
                    totalExpandedSize += expandedSize
                    if (totalExpandedSize > AnalysisLimits.MAX_ARCHIVE_EXPANDED_BYTES) {
                        return Result.failure(
                            AnalysisError.QuotaExceededError("Total expanded size exceeds limit")
                        )
                    }
                    
                    analyzedEntries.add(
                        AnalyzedArchiveEntry(
                            originalPath = entryName,
                            normalizedPath = normalizedPath,
                            compressedSize = entry.getCompressedSize(),
                            expandedSize = expandedSize,
                            isDirectory = entry.isDirectory(),
                            isEncrypted = false // Encryption detection requires deeper inspection
                        )
                    )
                }
                
                Result.success(
                    ArchiveAnalysisResult(
                        entryCount = entries.size,
                        totalCompressedSize = archiveFile.length(),
                        totalExpandedSize = totalExpandedSize,
                        entries = analyzedEntries,
                        suspiciousPaths = suspiciousPaths,
                        isEncrypted = analyzedEntries.any { it.isEncrypted }
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to analyze archive")
            Result.failure(AnalysisError.ArchiveError("Failed to analyze archive", e))
        }
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
}

data class ArchiveAnalysisResult(
    val entryCount: Int,
    val totalCompressedSize: Long,
    val totalExpandedSize: Long,
    val entries: List<AnalyzedArchiveEntry>,
    val suspiciousPaths: List<String>,
    val isEncrypted: Boolean
)

data class AnalyzedArchiveEntry(
    val originalPath: String,
    val normalizedPath: String,
    val compressedSize: Long,
    val expandedSize: Long,
    val isDirectory: Boolean,
    val isEncrypted: Boolean
)

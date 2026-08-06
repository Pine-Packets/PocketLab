package com.pineandpackets.pocketlab.engine.archive

import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import timber.log.Timber

/**
 * Tracks decompression statistics and enforces limits to prevent ZIP bombs.
 * Monitors compressed bytes read, uncompressed bytes written, and compression ratios.
 */
class DecompressionCounter {
    
    private var totalCompressedBytes: Long = 0
    private var totalUncompressedBytes: Long = 0
    private var entryCount: Int = 0
    private var maxRatio: Double = 0.0
    private var aborted: Boolean = false
    private var abortReason: String? = null
    
    /**
     * Record compressed bytes read.
     */
    fun addCompressedBytes(bytes: Long) {
        if (aborted) return
        totalCompressedBytes += bytes
    }
    
    /**
     * Record uncompressed bytes written.
     */
    fun addUncompressedBytes(bytes: Long): Result<Unit> {
        if (aborted) {
            return Result.failure(IllegalStateException("Decompression aborted: $abortReason"))
        }
        
        totalUncompressedBytes += bytes
        
        // Check total uncompressed limit
        if (totalUncompressedBytes > AnalysisLimits.MAX_ARCHIVE_EXPANDED_BYTES) {
            abort("Total uncompressed size exceeds limit: $totalUncompressedBytes > ${AnalysisLimits.MAX_ARCHIVE_EXPANDED_BYTES}")
            return Result.failure(IllegalStateException("Decompression aborted: $abortReason"))
        }
        
        return Result.success(Unit)
    }
    
    /**
     * Record a new entry being processed.
     */
    fun addEntry(): Result<Unit> {
        if (aborted) {
            return Result.failure(IllegalStateException("Decompression aborted: $abortReason"))
        }
        
        entryCount++
        
        // Check entry count limit
        if (entryCount > AnalysisLimits.MAX_ARCHIVE_ENTRIES) {
            abort("Entry count exceeds limit: $entryCount > ${AnalysisLimits.MAX_ARCHIVE_ENTRIES}")
            return Result.failure(IllegalStateException("Decompression aborted: $abortReason"))
        }
        
        return Result.success(Unit)
    }
    
    /**
     * Check compression ratio and abort if too high.
     * This helps detect ZIP bombs where a small compressed file expands to a huge size.
     */
    fun checkCompressionRatio(compressed: Long, uncompressed: Long): Result<Unit> {
        if (aborted) {
            return Result.failure(IllegalStateException("Decompression aborted: $abortReason"))
        }
        
        if (compressed == 0L) {
            return Result.success(Unit)
        }
        
        val ratio = uncompressed.toDouble() / compressed.toDouble()
        
        // Track maximum ratio
        if (ratio > maxRatio) {
            maxRatio = ratio
        }
        
        // Check if ratio exceeds limit
        if (ratio > AnalysisLimits.MAX_COMPRESSION_RATIO) {
            abort("Compression ratio too high: $ratio > ${AnalysisLimits.MAX_COMPRESSION_RATIO}")
            return Result.failure(IllegalStateException("Decompression aborted: $abortReason"))
        }
        
        return Result.success(Unit)
    }
    
    /**
     * Abort decompression with a reason.
     */
    private fun abort(reason: String) {
        if (!aborted) {
            Timber.w("Decompression aborted: $reason")
            aborted = true
            abortReason = reason
        }
    }
    
    /**
     * Check if decompression has been aborted.
     */
    fun isAborted(): Boolean = aborted
    
    /**
     * Get the reason for abort, if any.
     */
    fun getAbortReason(): String? = abortReason
    
    /**
     * Get decompression statistics.
     */
    fun getStats(): DecompressionStats {
        return DecompressionStats(
            totalCompressedBytes = totalCompressedBytes,
            totalUncompressedBytes = totalUncompressedBytes,
            entryCount = entryCount,
            maxCompressionRatio = maxRatio,
            aborted = aborted,
            abortReason = abortReason
        )
    }
    
    /**
     * Reset all counters.
     */
    fun reset() {
        totalCompressedBytes = 0
        totalUncompressedBytes = 0
        entryCount = 0
        maxRatio = 0.0
        aborted = false
        abortReason = null
    }
}

/**
 * Statistics about the decompression process.
 */
data class DecompressionStats(
    val totalCompressedBytes: Long,
    val totalUncompressedBytes: Long,
    val entryCount: Int,
    val maxCompressionRatio: Double,
    val aborted: Boolean,
    val abortReason: String?
) {
    /**
     * Get the overall compression ratio.
     */
    fun getOverallRatio(): Double {
        return if (totalCompressedBytes > 0) {
            totalUncompressedBytes.toDouble() / totalCompressedBytes.toDouble()
        } else {
            0.0
        }
    }
}

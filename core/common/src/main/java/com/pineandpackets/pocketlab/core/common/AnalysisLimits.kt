package com.pineandpackets.pocketlab.core.common

object AnalysisLimits {
    const val MAX_INPUT_SIZE_BYTES: Long = 512L * 1024 * 1024 // 512 MB
    const val MAX_ARCHIVE_ENTRIES: Int = 5000
    const val MAX_ARCHIVE_EXPANDED_BYTES: Long = 1024L * 1024 * 1024 // 1 GB
    const val MAX_SINGLE_ENTRY_BYTES: Long = 256L * 1024 * 1024 // 256 MB
    const val MAX_COMPRESSION_RATIO: Double = 100.0
    const val MAX_NESTING_DEPTH: Int = 2
    const val MAX_PATH_LENGTH: Int = 512
    const val MAX_FILENAME_LENGTH: Int = 255
    const val MAX_DEX_COUNT: Int = 10
    const val MAX_DEX_SIZE_BYTES: Long = 100L * 1024 * 1024 // 100 MB
    const val MAX_STRING_COUNT: Int = 100_000
    const val MAX_STRING_LENGTH: Int = 10_000
    const val MAX_METHOD_COUNT: Int = 100_000
    const val MAX_CLASS_COUNT: Int = 50_000
    const val MAX_IOC_COUNT: Int = 10_000
    const val MAX_FINDING_COUNT: Int = 1_000
    const val MAX_EVIDENCE_COUNT: Int = 10_000
    const val MAX_REPORT_SIZE_BYTES: Long = 50L * 1024 * 1024 // 50 MB
    const val MAX_ANALYSIS_DURATION_MS: Long = 10 * 60 * 1000 // 10 minutes
    
    const val BUFFER_SIZE: Int = 8192
}

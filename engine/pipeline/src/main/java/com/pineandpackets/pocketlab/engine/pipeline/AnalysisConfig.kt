package com.pineandpackets.pocketlab.engine.pipeline

import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import com.pineandpackets.pocketlab.engine.api.AnalysisProfile

data class AnalysisConfig(
    val analysisProfile: AnalysisProfile = AnalysisProfile.STANDARD,
    val hashAlgorithms: List<String> = listOf("SHA-256", "SHA-1", "MD5"),
    val nativeAnalysisEnabled: Boolean = true,
    val deepDexAnalysisEnabled: Boolean = true,
    val iocExtractionEnabled: Boolean = true,
    val maxBytesRead: Long = AnalysisLimits.MAX_INPUT_SIZE_BYTES,
    val maxArchiveEntries: Int = AnalysisLimits.MAX_ARCHIVE_ENTRIES,
    val maxArchiveExpandedBytes: Long = AnalysisLimits.MAX_ARCHIVE_EXPANDED_BYTES,
    val maxStringCount: Int = AnalysisLimits.MAX_STRING_COUNT,
    val maxMethodCount: Int = AnalysisLimits.MAX_METHOD_COUNT,
    val maxClassCount: Int = AnalysisLimits.MAX_CLASS_COUNT,
    val maxInstructionCount: Int = AnalysisLimits.MAX_INSTRUCTION_COUNT,
    val maxNestingDepth: Int = AnalysisLimits.MAX_NESTING_DEPTH,
    val maxAnalysisDurationMs: Long = AnalysisLimits.MAX_ANALYSIS_DURATION_MS,
    val maxReportSizeBytes: Long = AnalysisLimits.MAX_REPORT_SIZE_BYTES,
    val workerCount: Int = 2,
    val sourceDisplayName: String = "",
    val sourceMimeType: String? = null,
    val sourceSizeReported: Long? = null
) {
    val isAdvancedProfile: Boolean
        get() = analysisProfile == AnalysisProfile.ADVANCED

    companion object {
        fun fromRequest(
            request: com.pineandpackets.pocketlab.engine.api.AnalysisRequest
        ): AnalysisConfig {
            return AnalysisConfig(
                analysisProfile = request.analysisProfile,
                hashAlgorithms = request.hashAlgorithms.map { it.name },
                nativeAnalysisEnabled = request.nativeAnalysisEnabled,
                deepDexAnalysisEnabled = request.deepDexAnalysisEnabled,
                iocExtractionEnabled = request.iocExtractionEnabled,
                sourceDisplayName = request.sourceDisplayName,
                sourceMimeType = request.sourceMimeType,
                sourceSizeReported = request.sourceSizeReported
            )
        }

        fun withDeviceProfile(
            request: com.pineandpackets.pocketlab.engine.api.AnalysisRequest,
            memoryClassMb: Int,
            availableProcessors: Int,
            isLowRamDevice: Boolean
        ): AnalysisConfig {
            val base = fromRequest(request)

            val profile = when {
                isLowRamDevice || memoryClassMb < 128 -> DeviceProfile.LOW
                memoryClassMb >= 256 -> DeviceProfile.HIGH
                else -> DeviceProfile.STANDARD
            }

            val (maxExpanded, maxInput, workers) = when (profile) {
                DeviceProfile.LOW -> Triple(
                    512L * 1024 * 1024,
                    256L * 1024 * 1024,
                    1
                )
                DeviceProfile.STANDARD -> Triple(
                    AnalysisLimits.MAX_ARCHIVE_EXPANDED_BYTES,
                    AnalysisLimits.MAX_INPUT_SIZE_BYTES,
                    minOf(availableProcessors, 2)
                )
                DeviceProfile.HIGH -> Triple(
                    AnalysisLimits.MAX_ARCHIVE_EXPANDED_BYTES,
                    AnalysisLimits.MAX_INPUT_SIZE_BYTES,
                    minOf(availableProcessors, 4)
                )
            }

            return base.copy(
                maxBytesRead = maxInput,
                maxArchiveExpandedBytes = maxExpanded,
                workerCount = maxOf(1, workers)
            )
        }

        private enum class DeviceProfile {
            LOW, STANDARD, HIGH
        }
    }
}

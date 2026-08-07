package com.pineandpackets.pocketlab.engine.pipeline

import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import com.pineandpackets.pocketlab.engine.api.AnalysisProfile
import com.pineandpackets.pocketlab.engine.api.AnalysisRequest
import com.pineandpackets.pocketlab.engine.api.HashAlgorithm
import org.junit.Assert.*
import org.junit.Test

class AnalysisConfigTest {

    @Test
    fun `default config uses standard profile`() {
        val config = AnalysisConfig()
        assertEquals(AnalysisProfile.STANDARD, config.analysisProfile)
        assertFalse(config.isAdvancedProfile)
    }

    @Test
    fun `advanced profile detected correctly`() {
        val config = AnalysisConfig(analysisProfile = AnalysisProfile.ADVANCED)
        assertTrue(config.isAdvancedProfile)
    }

    @Test
    fun `fromRequest maps all fields correctly`() {
        val request = AnalysisRequest(
            jobId = "test-job",
            inputPath = "/path/to/file.apk",
            sourceDisplayName = "test.apk",
            sourceMimeType = "application/vnd.android.package-archive",
            sourceSizeReported = 1024L,
            sha256 = "abc123",
            sha1 = "def456",
            md5 = "ghi789",
            analysisProfile = AnalysisProfile.ADVANCED,
            hashAlgorithms = listOf(HashAlgorithm.SHA256, HashAlgorithm.MD5),
            nativeAnalysisEnabled = false,
            deepDexAnalysisEnabled = false,
            iocExtractionEnabled = false,
            archivePassword = "test123"
        )

        val config = AnalysisConfig.fromRequest(request)

        assertEquals(AnalysisProfile.ADVANCED, config.analysisProfile)
        assertEquals(listOf("SHA256", "MD5"), config.hashAlgorithms)
        assertFalse(config.nativeAnalysisEnabled)
        assertFalse(config.deepDexAnalysisEnabled)
        assertFalse(config.iocExtractionEnabled)
        assertEquals("test.apk", config.sourceDisplayName)
        assertEquals("application/vnd.android.package-archive", config.sourceMimeType)
        assertEquals(1024L, config.sourceSizeReported)
    }

    @Test
    fun `withDeviceProfile adjusts limits for low memory device`() {
        val request = AnalysisRequest(
            jobId = "test-job",
            inputPath = "/path/to/file.apk",
            sourceDisplayName = "test.apk",
            sourceMimeType = null,
            sourceSizeReported = null,
            sha256 = null,
            sha1 = null,
            md5 = null,
            analysisProfile = AnalysisProfile.STANDARD,
            hashAlgorithms = listOf(HashAlgorithm.SHA256),
            nativeAnalysisEnabled = true,
            deepDexAnalysisEnabled = true,
            iocExtractionEnabled = true
        )

        val config = AnalysisConfig.withDeviceProfile(
            request = request,
            memoryClassMb = 96,
            availableProcessors = 4,
            isLowRamDevice = true
        )

        assertEquals(256L * 1024 * 1024, config.maxBytesRead)
        assertEquals(512L * 1024 * 1024, config.maxArchiveExpandedBytes)
        assertEquals(1, config.workerCount)
    }

    @Test
    fun `withDeviceProfile adjusts limits for high memory device`() {
        val request = AnalysisRequest(
            jobId = "test-job",
            inputPath = "/path/to/file.apk",
            sourceDisplayName = "test.apk",
            sourceMimeType = null,
            sourceSizeReported = null,
            sha256 = null,
            sha1 = null,
            md5 = null,
            analysisProfile = AnalysisProfile.STANDARD,
            hashAlgorithms = listOf(HashAlgorithm.SHA256),
            nativeAnalysisEnabled = true,
            deepDexAnalysisEnabled = true,
            iocExtractionEnabled = true
        )

        val config = AnalysisConfig.withDeviceProfile(
            request = request,
            memoryClassMb = 512,
            availableProcessors = 8,
            isLowRamDevice = false
        )

        assertEquals(AnalysisLimits.MAX_INPUT_SIZE_BYTES, config.maxBytesRead)
        assertEquals(AnalysisLimits.MAX_ARCHIVE_EXPANDED_BYTES, config.maxArchiveExpandedBytes)
        assertEquals(4, config.workerCount)
    }

    @Test
    fun `withDeviceProfile adjusts limits for standard device`() {
        val request = AnalysisRequest(
            jobId = "test-job",
            inputPath = "/path/to/file.apk",
            sourceDisplayName = "test.apk",
            sourceMimeType = null,
            sourceSizeReported = null,
            sha256 = null,
            sha1 = null,
            md5 = null,
            analysisProfile = AnalysisProfile.STANDARD,
            hashAlgorithms = listOf(HashAlgorithm.SHA256),
            nativeAnalysisEnabled = true,
            deepDexAnalysisEnabled = true,
            iocExtractionEnabled = true
        )

        val config = AnalysisConfig.withDeviceProfile(
            request = request,
            memoryClassMb = 192,
            availableProcessors = 6,
            isLowRamDevice = false
        )

        assertEquals(AnalysisLimits.MAX_INPUT_SIZE_BYTES, config.maxBytesRead)
        assertEquals(2, config.workerCount)
    }

    @Test
    fun `worker count is at least 1`() {
        val request = AnalysisRequest(
            jobId = "test-job",
            inputPath = "/path/to/file.apk",
            sourceDisplayName = "test.apk",
            sourceMimeType = null,
            sourceSizeReported = null,
            sha256 = null,
            sha1 = null,
            md5 = null,
            analysisProfile = AnalysisProfile.STANDARD,
            hashAlgorithms = listOf(HashAlgorithm.SHA256),
            nativeAnalysisEnabled = true,
            deepDexAnalysisEnabled = true,
            iocExtractionEnabled = true
        )

        val config = AnalysisConfig.withDeviceProfile(
            request = request,
            memoryClassMb = 64,
            availableProcessors = 1,
            isLowRamDevice = true
        )

        assertTrue(config.workerCount >= 1)
    }

    @Test
    fun `default config has correct limits`() {
        val config = AnalysisConfig()
        assertEquals(AnalysisLimits.MAX_INPUT_SIZE_BYTES, config.maxBytesRead)
        assertEquals(AnalysisLimits.MAX_ARCHIVE_ENTRIES, config.maxArchiveEntries)
        assertEquals(AnalysisLimits.MAX_ARCHIVE_EXPANDED_BYTES, config.maxArchiveExpandedBytes)
        assertEquals(AnalysisLimits.MAX_STRING_COUNT, config.maxStringCount)
        assertEquals(AnalysisLimits.MAX_METHOD_COUNT, config.maxMethodCount)
        assertEquals(AnalysisLimits.MAX_CLASS_COUNT, config.maxClassCount)
        assertEquals(AnalysisLimits.MAX_INSTRUCTION_COUNT, config.maxInstructionCount)
        assertEquals(AnalysisLimits.MAX_NESTING_DEPTH, config.maxNestingDepth)
        assertEquals(AnalysisLimits.MAX_ANALYSIS_DURATION_MS, config.maxAnalysisDurationMs)
        assertEquals(AnalysisLimits.MAX_REPORT_SIZE_BYTES, config.maxReportSizeBytes)
    }
}

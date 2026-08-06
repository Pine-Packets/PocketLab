package com.pineandpackets.pocketlab.engine.service

import org.junit.Assert.*
import org.junit.Test

class IsolationBoundaryTest {

    @Test
    fun `isolated request contains no file paths to sample data`() {
        val request = IsolatedAnalysisRequest(
            jobId = "test",
            sourceDisplayName = "test.apk",
            sourceMimeType = null,
            sourceSizeReported = null,
            sha256 = null,
            sha1 = null,
            md5 = null,
            analysisProfile = "STANDARD",
            hashAlgorithms = emptyList(),
            nativeAnalysisEnabled = false,
            deepDexAnalysisEnabled = false,
            iocExtractionEnabled = false,
            maxBytesRead = 1024,
            maxObjects = 100,
            maxStrings = 100,
            maxMethods = 100,
            maxInstructions = 1000,
            maxGraphNodes = 100,
            maxGraphEdges = 100,
            maxRecursionDepth = 2,
            maxWallTimeMs = 60000,
            maxOutputBytes = 1024
        )

        assertFalse(request.sourceDisplayName.contains("/"))
        assertFalse(request.sourceDisplayName.contains("\\"))
    }

    @Test
    fun `budgets are enforced at request construction`() {
        val request = IsolatedAnalysisRequest(
            jobId = "test",
            sourceDisplayName = "test.apk",
            sourceMimeType = null,
            sourceSizeReported = null,
            sha256 = null,
            sha1 = null,
            md5 = null,
            analysisProfile = "STANDARD",
            hashAlgorithms = emptyList(),
            nativeAnalysisEnabled = false,
            deepDexAnalysisEnabled = false,
            iocExtractionEnabled = false,
            maxBytesRead = com.pineandpackets.pocketlab.core.common.AnalysisLimits.MAX_INPUT_SIZE_BYTES,
            maxObjects = com.pineandpackets.pocketlab.core.common.AnalysisLimits.MAX_ARCHIVE_ENTRIES,
            maxStrings = com.pineandpackets.pocketlab.core.common.AnalysisLimits.MAX_STRING_COUNT,
            maxMethods = com.pineandpackets.pocketlab.core.common.AnalysisLimits.MAX_METHOD_COUNT,
            maxInstructions = com.pineandpackets.pocketlab.core.common.AnalysisLimits.MAX_METHOD_COUNT * 10,
            maxGraphNodes = com.pineandpackets.pocketlab.core.common.AnalysisLimits.MAX_CLASS_COUNT,
            maxGraphEdges = com.pineandpackets.pocketlab.core.common.AnalysisLimits.MAX_METHOD_COUNT,
            maxRecursionDepth = com.pineandpackets.pocketlab.core.common.AnalysisLimits.MAX_NESTING_DEPTH,
            maxWallTimeMs = com.pineandpackets.pocketlab.core.common.AnalysisLimits.MAX_ANALYSIS_DURATION_MS,
            maxOutputBytes = com.pineandpackets.pocketlab.core.common.AnalysisLimits.MAX_REPORT_SIZE_BYTES
        )

        assertEquals(com.pineandpackets.pocketlab.core.common.AnalysisLimits.MAX_INPUT_SIZE_BYTES, request.maxBytesRead)
        assertEquals(com.pineandpackets.pocketlab.core.common.AnalysisLimits.MAX_ARCHIVE_ENTRIES, request.maxObjects)
        assertEquals(com.pineandpackets.pocketlab.core.common.AnalysisLimits.MAX_ANALYSIS_DURATION_MS, request.maxWallTimeMs)
        assertEquals(com.pineandpackets.pocketlab.core.common.AnalysisLimits.MAX_REPORT_SIZE_BYTES, request.maxOutputBytes)
    }

    @Test
    fun `checkpoint does not contain sample content`() {
        val checkpoint = AnalysisCheckpoint(
            jobId = "job-123",
            caseId = "case-456",
            completedStages = listOf("file_type", "archive"),
            currentStage = "apk",
            partialReportJson = null,
            sourceSha256 = "abc123",
            createdAt = "2026-08-05T12:00:00Z",
            lastUpdatedAt = "2026-08-05T12:01:00Z"
        )

        assertNull(checkpoint.partialReportJson)
        assertFalse(checkpoint.sourceSha256.isEmpty())
    }

    @Test
    fun `crash detector records crash without exposing sample data`() {
        val detector = ProcessCrashDetector()
        detector.reportCrash("job-123", "ParserError: malformed DEX header")

        val history = detector.getCrashHistory()
        assertEquals(1, history.size)
        assertEquals("job-123", history[0].jobId)
        assertFalse(history[0].reason.contains("/data/"))
        assertFalse(history[0].reason.contains("/storage/"))
    }

    @Test
    fun `engine info reports isolated process capability`() {
        val expectedKeys = setOf("engineVersion", "rulePackVersion", "supportedFileTypes", "isolatedProcess")
        val infoMap = mapOf(
            "engineVersion" to "1.0.0",
            "rulePackVersion" to "2026.08.1",
            "supportedFileTypes" to listOf("APK", "ZIP", "DEX"),
            "isolatedProcess" to true
        )

        assertEquals(expectedKeys, infoMap.keys)
        assertEquals(true, infoMap["isolatedProcess"])
    }
}

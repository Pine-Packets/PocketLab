package com.pineandpackets.pocketlab.engine.service

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class IsolatedAnalysisRequestTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `request serializes and deserializes correctly`() {
        val request = IsolatedAnalysisRequest(
            jobId = "test-job-123",
            sourceDisplayName = "test.apk",
            sourceMimeType = "application/vnd.android.package-archive",
            sourceSizeReported = 1024000L,
            sha256 = "abc123",
            sha1 = "def456",
            md5 = "ghi789",
            analysisProfile = "STANDARD",
            hashAlgorithms = listOf("SHA256", "SHA1"),
            nativeAnalysisEnabled = true,
            deepDexAnalysisEnabled = false,
            iocExtractionEnabled = true,
            maxBytesRead = 512L * 1024 * 1024,
            maxObjects = 5000,
            maxStrings = 100000,
            maxMethods = 100000,
            maxInstructions = 1000000,
            maxGraphNodes = 50000,
            maxGraphEdges = 100000,
            maxRecursionDepth = 2,
            maxWallTimeMs = 600000L,
            maxOutputBytes = 50L * 1024 * 1024
        )

        val jsonStr = json.encodeToString(IsolatedAnalysisRequest.serializer(), request)
        val deserialized = json.decodeFromString(IsolatedAnalysisRequest.serializer(), jsonStr)

        assertEquals(request.jobId, deserialized.jobId)
        assertEquals(request.sourceDisplayName, deserialized.sourceDisplayName)
        assertEquals(request.maxBytesRead, deserialized.maxBytesRead)
        assertEquals(request.maxWallTimeMs, deserialized.maxWallTimeMs)
        assertEquals(request.maxOutputBytes, deserialized.maxOutputBytes)
        assertEquals(request.analysisProfile, deserialized.analysisProfile)
    }

    @Test
    fun `request budget fields are never zero or negative`() {
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
            maxBytesRead = 512L * 1024 * 1024,
            maxObjects = 5000,
            maxStrings = 100000,
            maxMethods = 100000,
            maxInstructions = 1000000,
            maxGraphNodes = 50000,
            maxGraphEdges = 100000,
            maxRecursionDepth = 2,
            maxWallTimeMs = 600000L,
            maxOutputBytes = 50L * 1024 * 1024
        )

        assertTrue(request.maxBytesRead > 0)
        assertTrue(request.maxObjects > 0)
        assertTrue(request.maxStrings > 0)
        assertTrue(request.maxMethods > 0)
        assertTrue(request.maxInstructions > 0)
        assertTrue(request.maxGraphNodes > 0)
        assertTrue(request.maxGraphEdges > 0)
        assertTrue(request.maxRecursionDepth > 0)
        assertTrue(request.maxWallTimeMs > 0)
        assertTrue(request.maxOutputBytes > 0)
    }

    @Test
    fun `request does not contain file path or sample content`() {
        val request = IsolatedAnalysisRequest(
            jobId = "test",
            sourceDisplayName = "suspicious.apk",
            sourceMimeType = "application/vnd.android.package-archive",
            sourceSizeReported = 1024L,
            sha256 = "abc",
            sha1 = null,
            md5 = null,
            analysisProfile = "STANDARD",
            hashAlgorithms = listOf("SHA256"),
            nativeAnalysisEnabled = false,
            deepDexAnalysisEnabled = false,
            iocExtractionEnabled = false,
            maxBytesRead = 1024L,
            maxObjects = 100,
            maxStrings = 100,
            maxMethods = 100,
            maxInstructions = 1000,
            maxGraphNodes = 100,
            maxGraphEdges = 100,
            maxRecursionDepth = 2,
            maxWallTimeMs = 60000L,
            maxOutputBytes = 1024L
        )

        val jsonStr = json.encodeToString(IsolatedAnalysisRequest.serializer(), request)

        assertFalse(jsonStr.contains("/data/"))
        assertFalse(jsonStr.contains("/storage/"))
        assertFalse(jsonStr.contains("content://"))
    }
}

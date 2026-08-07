package com.pineandpackets.pocketlab.core.io

import com.pineandpackets.pocketlab.core.model.CaseId
import com.pineandpackets.pocketlab.core.model.CaseMetadata
import com.pineandpackets.pocketlab.core.model.CaseStatus
import com.pineandpackets.pocketlab.core.model.RetentionMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CaseCleanupWorkerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var workspace: CaseWorkspace
    private lateinit var cleanupWorker: CaseCleanupWorker

    private fun createTestWorkspace(): CaseWorkspace {
        return CaseWorkspace(tempFolder.root)
    }

    @Test
    fun `delete expired session-only cases`() = runTest {
        workspace = createTestWorkspace()
        cleanupWorker = CaseCleanupWorker(workspace)

        val caseId = CaseId("test-case-1")
        val caseDir = workspace.getCaseDir(caseId.value)
        File(caseDir, "test.bin").writeText("test data")

        val caseMeta = CaseMetadata(
            id = caseId,
            createdAt = System.currentTimeMillis() - 5 * 3600 * 1000,
            updatedAt = System.currentTimeMillis() - 5 * 3600 * 1000,
            status = CaseStatus.COMPLETE,
            sourceDisplayName = "test.apk",
            sourceMimeType = "application/vnd.android.package-archive",
            sourceSizeReported = 1000,
            sourceSizeActual = 1000,
            sha256 = "abc123",
            sha1 = null,
            md5 = null,
            primaryDetectedType = "APK",
            containerType = null,
            engineVersion = "1.0.0",
            rulePackVersion = "1.0.0",
            reportSchemaVersion = "1.0.0",
            riskBand = null,
            maxSeverity = null,
            findingCount = 0,
            retentionMode = RetentionMode.SESSION_ONLY,
            samplePresent = true,
            reportPresent = true,
            lastErrorCode = null
        )

        assertTrue(caseDir.exists())

        val result = cleanupWorker.cleanupExpiredCases(listOf(caseMeta))

        assertEquals(1, result.casesDeleted)
        assertTrue(result.bytesFreed > 0)
        assertFalse(caseDir.exists())
    }

    @Test
    fun `do not delete active cases`() = runTest {
        workspace = createTestWorkspace()
        cleanupWorker = CaseCleanupWorker(workspace)

        val caseId = CaseId("test-case-2")
        val caseDir = workspace.getCaseDir(caseId.value)
        File(caseDir, "test.bin").writeText("test data")

        val caseMeta = CaseMetadata(
            id = caseId,
            createdAt = System.currentTimeMillis() - 5 * 3600 * 1000,
            updatedAt = System.currentTimeMillis() - 5 * 3600 * 1000,
            status = CaseStatus.ANALYZING,
            sourceDisplayName = "test.apk",
            sourceMimeType = null,
            sourceSizeReported = null,
            sourceSizeActual = null,
            sha256 = null,
            sha1 = null,
            md5 = null,
            primaryDetectedType = null,
            containerType = null,
            engineVersion = null,
            rulePackVersion = null,
            reportSchemaVersion = null,
            riskBand = null,
            maxSeverity = null,
            findingCount = null,
            retentionMode = RetentionMode.SESSION_ONLY,
            samplePresent = true,
            reportPresent = false,
            lastErrorCode = null
        )

        val result = cleanupWorker.cleanupExpiredCases(listOf(caseMeta))

        assertEquals(0, result.casesDeleted)
        assertTrue(caseDir.exists())

        caseDir.deleteRecursively()
    }

    @Test
    fun `do not delete retain-sample cases`() = runTest {
        workspace = createTestWorkspace()
        cleanupWorker = CaseCleanupWorker(workspace)

        val caseId = CaseId("test-case-3")
        val caseDir = workspace.getCaseDir(caseId.value)
        File(caseDir, "test.bin").writeText("test data")

        val caseMeta = CaseMetadata(
            id = caseId,
            createdAt = System.currentTimeMillis() - 365 * 24 * 3600 * 1000L,
            updatedAt = System.currentTimeMillis() - 365 * 24 * 3600 * 1000L,
            status = CaseStatus.COMPLETE,
            sourceDisplayName = "test.apk",
            sourceMimeType = null,
            sourceSizeReported = null,
            sourceSizeActual = null,
            sha256 = null,
            sha1 = null,
            md5 = null,
            primaryDetectedType = null,
            containerType = null,
            engineVersion = null,
            rulePackVersion = null,
            reportSchemaVersion = null,
            riskBand = null,
            maxSeverity = null,
            findingCount = null,
            retentionMode = RetentionMode.RETAIN_SAMPLE,
            samplePresent = true,
            reportPresent = true,
            lastErrorCode = null
        )

        val result = cleanupWorker.cleanupExpiredCases(listOf(caseMeta))

        assertEquals(0, result.casesDeleted)
        assertTrue(caseDir.exists())

        caseDir.deleteRecursively()
    }

    @Test
    fun `delete auto-1-day cases older than 1 day`() = runTest {
        workspace = createTestWorkspace()
        cleanupWorker = CaseCleanupWorker(workspace)

        val caseId = CaseId("test-case-4")
        val caseDir = workspace.getCaseDir(caseId.value)
        File(caseDir, "test.bin").writeText("test data")

        val caseMeta = CaseMetadata(
            id = caseId,
            createdAt = System.currentTimeMillis() - 2 * 24 * 3600 * 1000L,
            updatedAt = System.currentTimeMillis() - 2 * 24 * 3600 * 1000L,
            status = CaseStatus.COMPLETE,
            sourceDisplayName = "test.apk",
            sourceMimeType = null,
            sourceSizeReported = null,
            sourceSizeActual = null,
            sha256 = null,
            sha1 = null,
            md5 = null,
            primaryDetectedType = null,
            containerType = null,
            engineVersion = null,
            rulePackVersion = null,
            reportSchemaVersion = null,
            riskBand = null,
            maxSeverity = null,
            findingCount = null,
            retentionMode = RetentionMode.AUTO_DELETE_1_DAY,
            samplePresent = false,
            reportPresent = true,
            lastErrorCode = null
        )

        val result = cleanupWorker.cleanupExpiredCases(listOf(caseMeta))

        assertEquals(1, result.casesDeleted)
        assertFalse(caseDir.exists())
    }

    @Test
    fun `do not delete auto-1-day cases newer than 1 day`() = runTest {
        workspace = createTestWorkspace()
        cleanupWorker = CaseCleanupWorker(workspace)

        val caseId = CaseId("test-case-5")
        val caseDir = workspace.getCaseDir(caseId.value)
        File(caseDir, "test.bin").writeText("test data")

        val caseMeta = CaseMetadata(
            id = caseId,
            createdAt = System.currentTimeMillis() - 12 * 3600 * 1000,
            updatedAt = System.currentTimeMillis() - 12 * 3600 * 1000,
            status = CaseStatus.COMPLETE,
            sourceDisplayName = "test.apk",
            sourceMimeType = null,
            sourceSizeReported = null,
            sourceSizeActual = null,
            sha256 = null,
            sha1 = null,
            md5 = null,
            primaryDetectedType = null,
            containerType = null,
            engineVersion = null,
            rulePackVersion = null,
            reportSchemaVersion = null,
            riskBand = null,
            maxSeverity = null,
            findingCount = null,
            retentionMode = RetentionMode.AUTO_DELETE_1_DAY,
            samplePresent = false,
            reportPresent = true,
            lastErrorCode = null
        )

        val result = cleanupWorker.cleanupExpiredCases(listOf(caseMeta))

        assertEquals(0, result.casesDeleted)
        assertTrue(caseDir.exists())

        caseDir.deleteRecursively()
    }

    @Test
    fun `deleteAllCases removes all non-deleted cases`() = runTest {
        workspace = createTestWorkspace()
        cleanupWorker = CaseCleanupWorker(workspace)

        val cases = (1..3).map { i ->
            val caseId = CaseId("case-$i")
            val caseDir = workspace.getCaseDir(caseId.value)
            File(caseDir, "test.bin").writeText("data $i")

            CaseMetadata(
                id = caseId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                status = CaseStatus.COMPLETE,
                sourceDisplayName = "test$i.apk",
                sourceMimeType = null,
                sourceSizeReported = null,
                sourceSizeActual = null,
                sha256 = null,
                sha1 = null,
                md5 = null,
                primaryDetectedType = null,
                containerType = null,
                engineVersion = null,
                rulePackVersion = null,
                reportSchemaVersion = null,
                riskBand = null,
                maxSeverity = null,
                findingCount = null,
                retentionMode = RetentionMode.TEMPORARY,
                samplePresent = true,
                reportPresent = true,
                lastErrorCode = null
            )
        }

        val result = cleanupWorker.deleteAllCases(cases)

        assertEquals(3, result.casesDeleted)
        assertTrue(result.bytesFreed > 0)
    }

    @Test
    fun `cleanup scratch data removes scratch directory`() {
        workspace = createTestWorkspace()
        cleanupWorker = CaseCleanupWorker(workspace)

        val caseId = "test-case-scratch"
        val scratchDir = workspace.getScratchDir(caseId)
        File(scratchDir, "temp.bin").writeText("scratch data")

        assertTrue(scratchDir.exists())

        cleanupWorker.cleanupScratchData(caseId)

        assertFalse(scratchDir.exists())
    }

    @Test
    fun `cleanup handles empty case list`() = runTest {
        workspace = createTestWorkspace()
        cleanupWorker = CaseCleanupWorker(workspace)

        val result = cleanupWorker.cleanupExpiredCases(emptyList())

        assertEquals(0, result.casesDeleted)
        assertEquals(0L, result.bytesFreed)
        assertTrue(result.errors.isEmpty())
    }
}

package com.pineandpackets.pocketlab.engine.service

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class CheckpointManagerTest {

    private lateinit var checkpointDir: File
    private lateinit var manager: CheckpointManager

    @Before
    fun setUp() {
        checkpointDir = File(System.getProperty("java.io.tmpdir"), "pocketlab_test_checkpoints_${System.currentTimeMillis()}")
        checkpointDir.mkdirs()
        manager = CheckpointManager(checkpointDir)
    }

    @Test
    fun `save and load checkpoint round trip`() {
        val checkpoint = AnalysisCheckpoint(
            jobId = "job-123",
            caseId = "case-456",
            completedStages = listOf("file_type", "archive", "apk"),
            currentStage = "dex",
            partialReportJson = null,
            sourceSha256 = "abc123def456",
            createdAt = "2026-08-05T12:00:00Z",
            lastUpdatedAt = "2026-08-05T12:05:00Z"
        )

        val saved = manager.saveCheckpoint(checkpoint)
        assertTrue(saved)

        val loaded = manager.loadCheckpoint("job-123")
        assertNotNull(loaded)
        assertEquals("job-123", loaded!!.jobId)
        assertEquals("case-456", loaded.caseId)
        assertEquals(3, loaded.completedStages.size)
        assertEquals("dex", loaded.currentStage)
        assertEquals("abc123def456", loaded.sourceSha256)
    }

    @Test
    fun `load nonexistent checkpoint returns null`() {
        val loaded = manager.loadCheckpoint("nonexistent")
        assertNull(loaded)
    }

    @Test
    fun `delete checkpoint removes file`() {
        val checkpoint = AnalysisCheckpoint(
            jobId = "job-to-delete",
            caseId = "case-789",
            completedStages = emptyList(),
            currentStage = "file_type",
            partialReportJson = null,
            sourceSha256 = "hash",
            createdAt = "2026-08-05T12:00:00Z",
            lastUpdatedAt = "2026-08-05T12:00:00Z"
        )

        manager.saveCheckpoint(checkpoint)
        assertNotNull(manager.loadCheckpoint("job-to-delete"))

        val deleted = manager.deleteCheckpoint("job-to-delete")
        assertTrue(deleted)
        assertNull(manager.loadCheckpoint("job-to-delete"))
    }

    @Test
    fun `list checkpoints returns all saved checkpoints`() {
        val cp1 = AnalysisCheckpoint("job-1", "case-1", listOf("file_type"), "archive", null, "hash1", "2026-08-05T12:00:00Z", "2026-08-05T12:00:00Z")
        val cp2 = AnalysisCheckpoint("job-2", "case-2", listOf("file_type", "archive"), "apk", null, "hash2", "2026-08-05T12:01:00Z", "2026-08-05T12:01:00Z")

        manager.saveCheckpoint(cp1)
        manager.saveCheckpoint(cp2)

        val all = manager.listCheckpoints()
        assertEquals(2, all.size)
        assertTrue(all.any { it.jobId == "job-1" })
        assertTrue(all.any { it.jobId == "job-2" })
    }

    @Test
    fun `clear all checkpoints removes everything`() {
        val cp1 = AnalysisCheckpoint("job-a", "case-a", emptyList(), null, null, "hash", "2026-08-05T12:00:00Z", "2026-08-05T12:00:00Z")
        val cp2 = AnalysisCheckpoint("job-b", "case-b", emptyList(), null, null, "hash", "2026-08-05T12:00:00Z", "2026-08-05T12:00:00Z")

        manager.saveCheckpoint(cp1)
        manager.saveCheckpoint(cp2)
        assertEquals(2, manager.listCheckpoints().size)

        manager.clearAllCheckpoints()
        assertEquals(0, manager.listCheckpoints().size)
    }

    @Test
    fun `checkpoint directory is created if missing`() {
        val newDir = File(System.getProperty("java.io.tmpdir"), "pocketlab_new_dir_${System.currentTimeMillis()}")
        assertFalse(newDir.exists())

        CheckpointManager(newDir)
        assertTrue(newDir.exists())

        newDir.deleteRecursively()
    }
}

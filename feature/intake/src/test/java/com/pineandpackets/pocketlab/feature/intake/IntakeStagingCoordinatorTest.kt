package com.pineandpackets.pocketlab.feature.intake

import android.net.Uri
import com.pineandpackets.pocketlab.core.io.CaseWorkspace
import com.pineandpackets.pocketlab.core.io.FileStager
import com.pineandpackets.pocketlab.engine.archive.SplitApkSetBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class IntakeStagingCoordinatorTest {

    private lateinit var tempRoot: File
    private lateinit var scratchDir: File
    private lateinit var caseDir: File

    private val uriA = mockk<Uri>()
    private val uriB = mockk<Uri>()

    @Before
    fun setUp() {
        tempRoot = createTempDir(prefix = "intake-test")
        scratchDir = File(tempRoot, "workspace/scratch").apply { mkdirs() }
        caseDir = File(tempRoot, "case").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    @Test
    fun `empty uri list returns failure without staging`() = runTest {
        val stager = mockk<FileStager>(relaxed = true)
        val workspace = mockk<CaseWorkspace>(relaxed = true)
        val builder = mockk<SplitApkSetBuilder>(relaxed = true)
        val coordinator = IntakeStagingCoordinator(stager, workspace, builder)

        val result = coordinator.stage(emptyList(), "case-1")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { stager.stageFile(any(), any()) }
        coVerify(exactly = 0) { stager.stageFileTo(any(), any()) }
        verify(exactly = 0) { builder.build(any(), any(), any()) }
    }

    @Test
    fun `single file stages to original without bundling`() = runTest {
        val stager = mockk<FileStager>(relaxed = true)
        val workspace = mockk<CaseWorkspace>(relaxed = true)
        val builder = mockk<SplitApkSetBuilder>(relaxed = true)
        coEvery { stager.stageFile(any(), any()) } returns Result.success(
            com.pineandpackets.pocketlab.core.io.StagingResult("abc", null, null, 10L)
        )
        val coordinator = IntakeStagingCoordinator(stager, workspace, builder)

        val result = coordinator.stage(listOf(uriA), "case-1")

        assertTrue(result.isSuccess)
        val staged = result.getOrThrow()
        assertEquals("case-1", staged.caseId)
        assertEquals(1, staged.uriCount)
        assertFalse(staged.bundledIntoApks)
        coVerify(exactly = 1) { stager.stageFile(any(), "case-1") }
        coVerify(exactly = 0) { stager.stageFileTo(any(), any()) }
        verify(exactly = 0) { builder.build(any(), any(), any()) }
    }

    @Test
    fun `multiple files are bundled into synthetic apks container`() = runTest {
        val stager = mockk<FileStager>(relaxed = true)
        val workspace = mockk<CaseWorkspace>(relaxed = true)
        every { workspace.getScratchDir("case-1") } returns scratchDir
        every { workspace.getCaseDir("case-1") } returns caseDir
        coEvery { stager.stageFileTo(any(), any()) } returns Result.success(
            com.pineandpackets.pocketlab.core.io.StagingResult("def", null, null, 20L)
        )
        val builder = mockk<SplitApkSetBuilder>(relaxed = true)
        coEvery { builder.build(any(), any(), any()) } returns Result.success(
            SplitApkSetBuilder.BuildResult(File(caseDir, "original.bin"), 2, 40L)
        )
        val coordinator = IntakeStagingCoordinator(stager, workspace, builder)

        val result = coordinator.stage(
            listOf(uriA, uriB),
            "case-1"
        )

        assertTrue(result.isSuccess)
        val staged = result.getOrThrow()
        assertEquals(2, staged.uriCount)
        assertTrue(staged.bundledIntoApks)

        coVerify(exactly = 2) { stager.stageFileTo(any(), any()) }
        verify(exactly = 1) {
            builder.build(
                apkFiles = match { it.size == 2 && it[0].name == "part_0.apk" && it[1].name == "part_1.apk" },
                outputDir = caseDir,
                containerName = "original.bin"
            )
        }
        assertFalse(File(scratchDir, "part_0.apk").exists())
        assertFalse(scratchDir.exists())
    }

    @Test
    fun `staging failure cleans up case workspace and returns error`() = runTest {
        val stager = mockk<FileStager>(relaxed = true)
        val workspace = mockk<CaseWorkspace>(relaxed = true)
        every { workspace.getScratchDir("case-1") } returns scratchDir
        coEvery { stager.stageFileTo(any(), any()) } returns Result.failure(
            com.pineandpackets.pocketlab.core.common.AnalysisError.IntakeError("provider timeout")
        )
        val builder = mockk<SplitApkSetBuilder>(relaxed = true)
        val coordinator = IntakeStagingCoordinator(stager, workspace, builder)

        val result = coordinator.stage(
            listOf(uriA, uriB),
            "case-1"
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { workspace.deleteCaseWorkspace("case-1") }
        verify(exactly = 0) { builder.build(any(), any(), any()) }
    }

    @Test
    fun `bundle failure cleans up case workspace and returns error`() = runTest {
        val stager = mockk<FileStager>(relaxed = true)
        val workspace = mockk<CaseWorkspace>(relaxed = true)
        every { workspace.getScratchDir("case-1") } returns scratchDir
        coEvery { stager.stageFileTo(any(), any()) } returns Result.success(
            com.pineandpackets.pocketlab.core.io.StagingResult("xyz", null, null, 10L)
        )
        val builder = mockk<SplitApkSetBuilder>(relaxed = true)
        coEvery { builder.build(any(), any(), any()) } returns Result.failure(
            com.pineandpackets.pocketlab.core.common.AnalysisError.QuotaExceededError("too large")
        )
        val coordinator = IntakeStagingCoordinator(stager, workspace, builder)

        val result = coordinator.stage(
            listOf(uriA, uriB),
            "case-1"
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { workspace.deleteCaseWorkspace("case-1") }
    }

    @Test
    fun `staging failure for single file returns error without bundling`() = runTest {
        val stager = mockk<FileStager>(relaxed = true)
        val workspace = mockk<CaseWorkspace>(relaxed = true)
        coEvery { stager.stageFile(any(), any()) } returns Result.failure(
            com.pineandpackets.pocketlab.core.common.AnalysisError.IntakeError("cannot open")
        )
        val builder = mockk<SplitApkSetBuilder>(relaxed = true)
        val coordinator = IntakeStagingCoordinator(stager, workspace, builder)

        val result = coordinator.stage(listOf(uriA), "case-1")

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
        verify(exactly = 0) { builder.build(any(), any(), any()) }
    }
}

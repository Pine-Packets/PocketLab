package com.pineandpackets.pocketlab.feature.analysis

import com.pineandpackets.pocketlab.core.crypto.EncryptedReportStorage
import com.pineandpackets.pocketlab.core.database.CaseRepository
import com.pineandpackets.pocketlab.core.io.CaseWorkspace
import com.pineandpackets.pocketlab.core.model.AnalysisReport
import com.pineandpackets.pocketlab.core.model.AnalysisSettings
import com.pineandpackets.pocketlab.core.model.CaseId
import com.pineandpackets.pocketlab.core.model.CaseMetadata
import com.pineandpackets.pocketlab.core.model.CaseStatus
import com.pineandpackets.pocketlab.core.model.Confidence
import com.pineandpackets.pocketlab.core.model.EngineInfo
import com.pineandpackets.pocketlab.core.model.IntegrityBlock
import com.pineandpackets.pocketlab.core.model.ReportSummary
import com.pineandpackets.pocketlab.core.model.RetentionMode
import com.pineandpackets.pocketlab.core.model.RiskBand
import com.pineandpackets.pocketlab.core.model.Severity
import com.pineandpackets.pocketlab.core.model.SourceInfo
import com.pineandpackets.pocketlab.engine.api.AnalysisEngine
import com.pineandpackets.pocketlab.engine.api.AnalysisProgress
import com.pineandpackets.pocketlab.engine.api.AnalysisProfile
import com.pineandpackets.pocketlab.engine.api.AnalysisRequest
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModelTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var workspace: CaseWorkspace
    private lateinit var engine: AnalysisEngine
    private lateinit var caseRepository: CaseRepository
    private lateinit var reportStorage: EncryptedReportStorage
    private lateinit var viewModel: AnalysisViewModel
    private lateinit var originalFile: File

    private val dispatcher = StandardTestDispatcher()

    private fun sampleReport(): AnalysisReport {
        return AnalysisReport(
            schemaVersion = "1.0.0",
            reportId = "report-1",
            caseId = "case-1",
            createdAt = "2026-01-01T00:00:00Z",
            analysisStartedAt = "2026-01-01T00:00:00Z",
            analysisCompletedAt = "2026-01-01T00:00:01Z",
            engine = EngineInfo(
                appVersion = "1.0.0",
                engineVersion = "1.0.0",
                reportSchemaVersion = "1.0.0",
                rulePackVersion = "2026.01.1"
            ),
            settings = AnalysisSettings(
                analysisProfile = "STANDARD",
                hashAlgorithms = listOf("SHA-256"),
                nativeAnalysisEnabled = true,
                deepDexAnalysisEnabled = true,
                iocExtractionEnabled = true
            ),
            source = SourceInfo(
                displayName = "original.bin",
                mimeType = null,
                sizeReported = 5L,
                sizeActual = 5L,
                sha256 = "abc",
                sha1 = null,
                md5 = null
            ),
            summary = ReportSummary(
                riskBand = RiskBand.HIGH_RISK_INDICATORS,
                confidence = Confidence.HIGH,
                completeness = 0.92,
                findingCount = 3,
                maxSeverity = Severity.HIGH,
                topFindings = listOf("Sample finding")
            ),
            integrity = IntegrityBlock(
                sourceSha256 = "abc",
                reportSha256 = "def",
                engineVersion = "1.0.0",
                rulePackVersion = "2026.01.1",
                sampleRetained = false
            )
        )
    }

    private fun caseMetadata(): CaseMetadata {
        return CaseMetadata(
            id = CaseId("case-1"),
            createdAt = 0L,
            updatedAt = 0L,
            status = CaseStatus.CREATED,
            sourceDisplayName = "original.bin",
            sourceMimeType = null,
            sourceSizeReported = 5L,
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
            samplePresent = false,
            reportPresent = false,
            lastErrorCode = null
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val baseDir = temporaryFolder.newFolder("cases-root")
        workspace = CaseWorkspace(baseDir)
        originalFile = workspace.getOriginalFile("case-1")
        originalFile.parentFile?.mkdirs()
        originalFile.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00))

        engine = mockk()
        caseRepository = mockk()
        reportStorage = mockk()

        coEvery { caseRepository.getCaseById(CaseId("case-1")) } returns null
        coEvery { caseRepository.createCaseWithId(any(), any(), any(), any()) } returns caseMetadata()
        coEvery { caseRepository.updateCaseStatus(any(), any()) } returns Unit
        coEvery { caseRepository.updateCaseWithReport(any(), any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { reportStorage.saveReport(any(), any()) } returns Result.success(Unit)

        viewModel = AnalysisViewModel(
            engine = engine,
            caseRepository = caseRepository,
            reportStorage = reportStorage,
            workspace = workspace
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `completing analysis persists report and exposes completed case id`() = runTest(dispatcher) {
        val report = sampleReport()
        coEvery { engine.analyze(any()) } returns flow {
            emit(AnalysisProgress.StageStarted("ARCHIVE", "Inspect container"))
            emit(AnalysisProgress.StageComplete("ARCHIVE", 0))
            emit(AnalysisProgress.ReportReady(report))
        }

        viewModel.analyze("case-1")
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { reportStorage.saveReport("case-1", any()) }
        coVerify {
            caseRepository.updateCaseWithReport(
                CaseId("case-1"),
                RiskBand.HIGH_RISK_INDICATORS,
                Severity.HIGH,
                3,
                "1.0.0",
                "2026.01.1",
                "1.0.0"
            )
        }
        assertEquals("case-1", viewModel.uiState.value.completedCaseId)
        assertEquals(false, viewModel.uiState.value.isAnalyzing)
        assertEquals(StageStatus.COMPLETE, viewModel.uiState.value.stages.first { it.stageId == "ARCHIVE" }.status)
    }

    @Test
    fun `engine error produces error state without persisting report`() = runTest(dispatcher) {
        coEvery { engine.analyze(any()) } returns flow {
            emit(AnalysisProgress.Error("ANALYSIS_FAILED", "Parser crashed"))
        }

        viewModel.analyze("case-1")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("Parser crashed") == true)
        assertNull(viewModel.uiState.value.completedCaseId)
        coVerify { reportStorage wasNot Called }
    }

    @Test
    fun `missing staged file produces error without starting analysis`() = runTest(dispatcher) {
        originalFile.delete()

        viewModel.analyze("case-1")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("not found") == true)
        coVerify { engine wasNot Called }
    }

    @Test
    fun `staged failure marks stage failed but analysis continues`() = runTest(dispatcher) {
        coEvery { engine.analyze(any()) } returns flow {
            emit(AnalysisProgress.StageStarted("APK", "Parse APK"))
            emit(AnalysisProgress.StageFailed("APK", "APK_MALFORMED", "Truncated manifest"))
            emit(AnalysisProgress.ReportReady(sampleReport()))
        }

        viewModel.analyze("case-1")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(StageStatus.FAILED, viewModel.uiState.value.stages.first { it.stageId == "APK" }.status)
        assertEquals("case-1", viewModel.uiState.value.completedCaseId)
    }

    @Test
    fun `duplicate analyze call does not start a second job`() = runTest(dispatcher) {
        coEvery { engine.analyze(any()) } returns flow {
            emit(AnalysisProgress.ReportReady(sampleReport()))
        }

        viewModel.analyze("case-1")
        viewModel.analyze("case-1")
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { engine.analyze(any()) }
    }

    @Test
    fun `request includes staged input path and standard profile`() = runTest(dispatcher) {
        val requestSlot = slot<AnalysisRequest>()
        coEvery { engine.analyze(capture(requestSlot)) } returns flow {
            emit(AnalysisProgress.ReportReady(sampleReport()))
        }

        viewModel.analyze("case-1")
        dispatcher.scheduler.advanceUntilIdle()

        val request = requestSlot.captured
        assertEquals(originalFile.absolutePath, request.inputPath)
        assertEquals("case-1", request.jobId)
        assertEquals(AnalysisProfile.STANDARD, request.analysisProfile)
        assertEquals(true, request.iocExtractionEnabled)
        assertEquals(true, request.nativeAnalysisEnabled)
    }
}

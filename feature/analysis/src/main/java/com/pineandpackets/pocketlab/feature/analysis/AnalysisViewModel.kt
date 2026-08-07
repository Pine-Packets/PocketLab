package com.pineandpackets.pocketlab.feature.analysis

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pineandpackets.pocketlab.core.crypto.EncryptedReportStorage
import com.pineandpackets.pocketlab.core.crypto.EncryptionManager
import com.pineandpackets.pocketlab.core.database.CaseRepository
import com.pineandpackets.pocketlab.core.database.PocketLabDatabase
import com.pineandpackets.pocketlab.core.io.CaseWorkspace
import com.pineandpackets.pocketlab.core.model.AnalysisReport
import com.pineandpackets.pocketlab.core.model.CaseId
import com.pineandpackets.pocketlab.engine.api.AnalysisEngine
import com.pineandpackets.pocketlab.engine.api.AnalysisProfile
import com.pineandpackets.pocketlab.engine.api.AnalysisProgress
import com.pineandpackets.pocketlab.engine.api.AnalysisRequest
import com.pineandpackets.pocketlab.engine.api.HashAlgorithm
import com.pineandpackets.pocketlab.engine.orchestrator.AnalysisOrchestrator
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

data class AnalysisStageState(
    val stageId: String,
    val stageName: String,
    val status: StageStatus
)

enum class StageStatus {
    RUNNING,
    COMPLETE,
    FAILED
}

data class AnalysisUiState(
    val isAnalyzing: Boolean = false,
    val stages: List<AnalysisStageState> = emptyList(),
    val error: String? = null,
    val completedCaseId: String? = null
)

class AnalysisViewModel(
    private val engine: AnalysisEngine,
    private val caseRepository: CaseRepository,
    private val reportStorage: EncryptedReportStorage,
    private val workspace: CaseWorkspace
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null

    fun analyze(caseId: String) {
        if (analysisJob?.isActive == true) return
        _uiState.value = AnalysisUiState(isAnalyzing = true)
        analysisJob = viewModelScope.launch {
            try {
                val inputFile = workspace.getOriginalFile(caseId)
                if (!inputFile.exists() || !inputFile.isFile) {
                    fail("Staged sample not found for case $caseId")
                    return@launch
                }
                ensureCaseExists(caseId, inputFile)

                val request = AnalysisRequest(
                    jobId = caseId,
                    inputPath = inputFile.absolutePath,
                    sourceDisplayName = caseId,
                    sourceMimeType = null,
                    sourceSizeReported = inputFile.length(),
                    sha256 = null,
                    sha1 = null,
                    md5 = null,
                    analysisProfile = AnalysisProfile.STANDARD,
                    hashAlgorithms = listOf(HashAlgorithm.SHA256),
                    nativeAnalysisEnabled = true,
                    deepDexAnalysisEnabled = true,
                    iocExtractionEnabled = true
                )

                caseRepository.updateCaseStatus(CaseId(caseId), com.pineandpackets.pocketlab.core.model.CaseStatus.ANALYZING)

                engine.analyze(request).collect { progress ->
                    handleProgress(caseId, progress)
                }
            } catch (e: Exception) {
                Timber.e(e, "Analysis failed for case $caseId")
                fail(e.message ?: "Analysis failed")
            }
        }
    }

    fun cancel(caseId: String) {
        viewModelScope.launch {
            runCatching { engine.cancel(caseId) }
            analysisJob?.cancel()
            _uiState.value = AnalysisUiState(isAnalyzing = false, error = "Analysis cancelled")
        }
    }

    private suspend fun ensureCaseExists(caseId: String, inputFile: File) {
        if (caseRepository.getCaseById(CaseId(caseId)) == null) {
            caseRepository.createCaseWithId(
                caseId = CaseId(caseId),
                sourceDisplayName = inputFile.name,
                sourceMimeType = null,
                sourceSizeReported = inputFile.length()
            )
        }
    }

    private suspend fun handleProgress(caseId: String, progress: AnalysisProgress) {
        when (progress) {
            is AnalysisProgress.StageStarted -> {
                val stages = _uiState.value.stages.filterNot { it.stageId == progress.stageId }
                _uiState.value = _uiState.value.copy(
                    stages = stages + AnalysisStageState(progress.stageId, progress.stageName, StageStatus.RUNNING)
                )
            }

            is AnalysisProgress.StageProgress -> Unit

            is AnalysisProgress.StageComplete -> {
                _uiState.value = _uiState.value.copy(
                    stages = _uiState.value.stages.map { stage ->
                        if (stage.stageId == progress.stageId) stage.copy(status = StageStatus.COMPLETE) else stage
                    }
                )
            }

            is AnalysisProgress.StageFailed -> {
                _uiState.value = _uiState.value.copy(
                    stages = _uiState.value.stages.map { stage ->
                        if (stage.stageId == progress.stageId) stage.copy(status = StageStatus.FAILED) else stage
                    }
                )
            }

            is AnalysisProgress.ReportReady -> persistReport(caseId, progress.report)

            is AnalysisProgress.Error -> fail(progress.message)
        }
    }

    private suspend fun persistReport(caseId: String, report: AnalysisReport) {
        reportStorage.saveReport(caseId, json.encodeToString(AnalysisReport.serializer(), report))
        caseRepository.updateCaseWithReport(
            caseId = CaseId(caseId),
            riskBand = report.summary.riskBand,
            maxSeverity = report.summary.maxSeverity,
            findingCount = report.summary.findingCount,
            engineVersion = report.engine.engineVersion,
            rulePackVersion = report.engine.rulePackVersion,
            reportSchemaVersion = report.engine.reportSchemaVersion
        )
        _uiState.value = _uiState.value.copy(isAnalyzing = false, completedCaseId = caseId)
    }

    private fun fail(message: String) {
        _uiState.value = AnalysisUiState(isAnalyzing = false, error = message)
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    val database = PocketLabDatabase.getDatabase(application)
                    AnalysisViewModel(
                        engine = AnalysisOrchestrator(),
                        caseRepository = CaseRepository(database),
                        reportStorage = EncryptedReportStorage(application, EncryptionManager(application)),
                        workspace = CaseWorkspace(application)
                    )
                }
            }
        }
    }
}

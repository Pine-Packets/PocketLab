package com.pineandpackets.pocketlab.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pineandpackets.pocketlab.core.crypto.EncryptedReportStorage
import com.pineandpackets.pocketlab.core.database.CaseRepository
import com.pineandpackets.pocketlab.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber

data class ReportUiState(
    val isLoading: Boolean = true,
    val report: AnalysisReport? = null,
    val caseMetadata: CaseMetadata? = null,
    val error: String? = null,
    val viewMode: ReportViewMode = ReportViewMode.SIMPLE
)

enum class ReportViewMode {
    SIMPLE,
    ANALYST
}

class ReportViewModel(
    private val caseRepository: CaseRepository,
    private val reportStorage: EncryptedReportStorage
) : ViewModel() {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()
    
    fun loadReport(caseId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val caseMetadata = caseRepository.getCaseById(CaseId(caseId))
                if (caseMetadata == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Case not found"
                    )
                    return@launch
                }
                
                val reportResult = reportStorage.loadReport(caseId)
                reportResult.fold(
                    onSuccess = { reportJson ->
                        val report = json.decodeFromString<AnalysisReport>(reportJson)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            report = report,
                            caseMetadata = caseMetadata
                        )
                    },
                    onFailure = { e ->
                        Timber.e(e, "Failed to load report")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Failed to load report: ${e.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load report")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load report: ${e.message}"
                )
            }
        }
    }
    
    fun setViewMode(mode: ReportViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
    }
}

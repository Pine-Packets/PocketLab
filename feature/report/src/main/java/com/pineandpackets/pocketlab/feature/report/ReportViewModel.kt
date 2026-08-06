package com.pineandpackets.pocketlab.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pineandpackets.pocketlab.core.crypto.AnalystNote
import com.pineandpackets.pocketlab.core.crypto.EncryptedReportStorage
import com.pineandpackets.pocketlab.core.database.CaseRepository
import com.pineandpackets.pocketlab.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID

data class ReportUiState(
    val isLoading: Boolean = true,
    val report: AnalysisReport? = null,
    val caseMetadata: CaseMetadata? = null,
    val notes: List<AnalystNote> = emptyList(),
    val isSavingNote: Boolean = false,
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
                        val notesResult = reportStorage.loadNotes(caseId)
                        val notes = notesResult.getOrDefault(emptyList())
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            report = report,
                            caseMetadata = caseMetadata,
                            notes = notes
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

    /**
     * Add an analyst note to the current case. Notes are encrypted separately from
     * the canonical report and merged at render time.
     */
    fun addNote(caseId: String, content: String, findingIds: List<String> = emptyList()) {
        if (content.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingNote = true, error = null)

            try {
                val now = System.currentTimeMillis()
                val newNote = AnalystNote(
                    id = UUID.randomUUID().toString(),
                    caseId = caseId,
                    createdAt = now,
                    updatedAt = now,
                    author = null,
                    content = content.trim(),
                    findingIds = findingIds
                )

                val updatedNotes = _uiState.value.notes + newNote
                val saveResult = reportStorage.saveNotes(caseId, updatedNotes)

                saveResult.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            notes = updatedNotes,
                            isSavingNote = false
                        )
                    },
                    onFailure = { e ->
                        Timber.e(e, "Failed to save analyst note")
                        _uiState.value = _uiState.value.copy(
                            isSavingNote = false,
                            error = "Failed to save note: ${e.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to save analyst note")
                _uiState.value = _uiState.value.copy(
                    isSavingNote = false,
                    error = "Failed to save note: ${e.message}"
                )
            }
        }
    }

    /**
     * Delete an analyst note from the current case.
     */
    fun deleteNote(caseId: String, noteId: String) {
        viewModelScope.launch {
            val updatedNotes = _uiState.value.notes.filter { it.id != noteId }
            val saveResult = reportStorage.saveNotes(caseId, updatedNotes)

            saveResult.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(notes = updatedNotes)
                },
                onFailure = { e ->
                    Timber.e(e, "Failed to delete analyst note")
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to delete note: ${e.message}"
                    )
                }
            )
        }
    }
}

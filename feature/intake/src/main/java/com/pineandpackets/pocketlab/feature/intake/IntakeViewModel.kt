package com.pineandpackets.pocketlab.feature.intake

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pineandpackets.pocketlab.core.io.CaseWorkspace
import com.pineandpackets.pocketlab.core.io.FileStager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IntakeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<IntakeUiState>(IntakeUiState.Initial)
    val uiState: StateFlow<IntakeUiState> = _uiState.asStateFlow()

    private val coordinator: IntakeStagingCoordinator by lazy {
        val workspace = CaseWorkspace(getApplication())
        IntakeStagingCoordinator(
            stager = FileStager(getApplication(), workspace),
            workspace = workspace
        )
    }

    fun loadFiles(uris: List<String>) {
        viewModelScope.launch {
            _uiState.value = IntakeUiState.Loading
            _uiState.value = IntakeUiState.FileInfoLoaded(uris = uris, fileCount = uris.size)
        }
    }

    fun startAnalysis() {
        val current = _uiState.value
        if (current !is IntakeUiState.FileInfoLoaded) return

        viewModelScope.launch {
            try {
                _uiState.value = IntakeUiState.Staging
                val caseId = java.util.UUID.randomUUID().toString()
                val uris = current.uris.mapNotNull { runCatching { android.net.Uri.parse(it) }.getOrNull() }
                if (uris.size != current.uris.size) {
                    _uiState.value = IntakeUiState.Error("One or more file references are invalid")
                    return@launch
                }
                val result = coordinator.stage(uris, caseId)
                result.fold(
                    onSuccess = {
                        _uiState.value = IntakeUiState.ReadyForAnalysis(caseId)
                    },
                    onFailure = {
                        _uiState.value = IntakeUiState.Error(it.message ?: "Failed to stage files")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = IntakeUiState.Error("Failed to prepare files: ${e.message}")
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: error("Application not available")
                IntakeViewModel(app)
            }
        }
    }
}

sealed interface IntakeUiState {
    data object Initial : IntakeUiState
    data object Loading : IntakeUiState
    data class FileInfoLoaded(
        val uris: List<String>,
        val fileCount: Int
    ) : IntakeUiState
    data object Staging : IntakeUiState
    data class ReadyForAnalysis(val caseId: String) : IntakeUiState
    data class Error(val message: String) : IntakeUiState
}

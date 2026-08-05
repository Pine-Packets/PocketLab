package com.pineandpackets.pocketlab.feature.intake

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pineandpackets.pocketlab.core.model.CaseStatus
import com.pineandpackets.pocketlab.core.model.RetentionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IntakeViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow<IntakeUiState>(IntakeUiState.Initial)
    val uiState: StateFlow<IntakeUiState> = _uiState.asStateFlow()
    
    fun loadFileInfo(uri: Uri, displayName: String?, mimeType: String?, size: Long?) {
        viewModelScope.launch {
            try {
                _uiState.value = IntakeUiState.Loading
                
                val name = displayName ?: uri.lastPathSegment ?: "Unknown file"
                
                _uiState.value = IntakeUiState.FileInfoLoaded(
                    uri = uri,
                    displayName = name,
                    mimeType = mimeType,
                    size = size
                )
            } catch (e: Exception) {
                _uiState.value = IntakeUiState.Error("Failed to load file: ${e.message}")
            }
        }
    }
    
    fun startAnalysis() {
        val currentState = _uiState.value
        if (currentState !is IntakeUiState.FileInfoLoaded) return
        
        viewModelScope.launch {
            try {
                _uiState.value = IntakeUiState.Staging
                
                val caseId = java.util.UUID.randomUUID().toString()
                
                _uiState.value = IntakeUiState.ReadyForAnalysis(caseId)
            } catch (e: Exception) {
                _uiState.value = IntakeUiState.Error("Failed to prepare file: ${e.message}")
            }
        }
    }
}

sealed interface IntakeUiState {
    data object Initial : IntakeUiState
    data object Loading : IntakeUiState
    data class FileInfoLoaded(
        val uri: Uri,
        val displayName: String,
        val mimeType: String?,
        val size: Long?
    ) : IntakeUiState
    data object Staging : IntakeUiState
    data class ReadyForAnalysis(val caseId: String) : IntakeUiState
    data class Error(val message: String) : IntakeUiState
}

package com.pineandpackets.pocketlab.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings()
    )

    fun updateRetentionMode(mode: Int) {
        viewModelScope.launch { repository.updateRetentionMode(mode) }
    }

    fun updateReportRetentionDays(days: Int) {
        viewModelScope.launch { repository.updateReportRetentionDays(days) }
    }

    fun updateAnalysisProfile(profile: Int) {
        viewModelScope.launch { repository.updateAnalysisProfile(profile) }
    }

    fun updateNativeAnalysis(enabled: Boolean) {
        viewModelScope.launch { repository.updateNativeAnalysis(enabled) }
    }

    fun updateDeepDexAnalysis(enabled: Boolean) {
        viewModelScope.launch { repository.updateDeepDexAnalysis(enabled) }
    }

    fun updateIocExtraction(enabled: Boolean) {
        viewModelScope.launch { repository.updateIocExtraction(enabled) }
    }

    fun updateRedactSecrets(enabled: Boolean) {
        viewModelScope.launch { repository.updateRedactSecrets(enabled) }
    }

    fun updateRedactIoc(enabled: Boolean) {
        viewModelScope.launch { repository.updateRedactIoc(enabled) }
    }

    fun updateIncludeFilename(enabled: Boolean) {
        viewModelScope.launch { repository.updateIncludeFilename(enabled) }
    }

    fun updateThemeMode(mode: Int) {
        viewModelScope.launch { repository.updateThemeMode(mode) }
    }

    fun updateReduceMotion(enabled: Boolean) {
        viewModelScope.launch { repository.updateReduceMotion(enabled) }
    }
}

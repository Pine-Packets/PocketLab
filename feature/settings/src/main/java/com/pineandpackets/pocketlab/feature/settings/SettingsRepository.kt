package com.pineandpackets.pocketlab.feature.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val retentionMode: Int = 0,
    val reportRetentionDays: Int = 30,
    val analysisProfile: Int = 0,
    val hashAlgorithms: Set<String> = setOf("SHA-256", "SHA-1", "MD5"),
    val nativeAnalysisEnabled: Boolean = true,
    val deepDexAnalysisEnabled: Boolean = true,
    val iocExtractionEnabled: Boolean = true,
    val redactSecrets: Boolean = true,
    val redactIocValues: Boolean = false,
    val includeFilename: Boolean = true,
    val themeMode: Int = 0,
    val reduceMotion: Boolean = false,
    val onboardingCompleted: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private val dataStore = context.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            retentionMode = prefs[RETENTION_MODE] ?: 0,
            reportRetentionDays = prefs[REPORT_RETENTION_DAYS] ?: 30,
            analysisProfile = prefs[ANALYSIS_PROFILE] ?: 0,
            hashAlgorithms = prefs[HASH_ALGORITHMS]?.split(",")?.toSet() ?: setOf("SHA-256", "SHA-1", "MD5"),
            nativeAnalysisEnabled = prefs[NATIVE_ANALYSIS] ?: true,
            deepDexAnalysisEnabled = prefs[DEEP_DEX_ANALYSIS] ?: true,
            iocExtractionEnabled = prefs[IOC_EXTRACTION] ?: true,
            redactSecrets = prefs[REDACT_SECRETS] ?: true,
            redactIocValues = prefs[REDACT_IOC] ?: false,
            includeFilename = prefs[INCLUDE_FILENAME] ?: true,
            themeMode = prefs[THEME_MODE] ?: 0,
            reduceMotion = prefs[REDUCE_MOTION] ?: false,
            onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: false
        )
    }

    suspend fun updateRetentionMode(mode: Int) {
        dataStore.edit { it[RETENTION_MODE] = mode }
    }

    suspend fun updateReportRetentionDays(days: Int) {
        dataStore.edit { it[REPORT_RETENTION_DAYS] = days }
    }

    suspend fun updateAnalysisProfile(profile: Int) {
        dataStore.edit { it[ANALYSIS_PROFILE] = profile }
    }

    suspend fun updateNativeAnalysis(enabled: Boolean) {
        dataStore.edit { it[NATIVE_ANALYSIS] = enabled }
    }

    suspend fun updateDeepDexAnalysis(enabled: Boolean) {
        dataStore.edit { it[DEEP_DEX_ANALYSIS] = enabled }
    }

    suspend fun updateIocExtraction(enabled: Boolean) {
        dataStore.edit { it[IOC_EXTRACTION] = enabled }
    }

    suspend fun updateRedactSecrets(enabled: Boolean) {
        dataStore.edit { it[REDACT_SECRETS] = enabled }
    }

    suspend fun updateRedactIoc(enabled: Boolean) {
        dataStore.edit { it[REDACT_IOC] = enabled }
    }

    suspend fun updateIncludeFilename(enabled: Boolean) {
        dataStore.edit { it[INCLUDE_FILENAME] = enabled }
    }

    suspend fun updateThemeMode(mode: Int) {
        dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun updateReduceMotion(enabled: Boolean) {
        dataStore.edit { it[REDUCE_MOTION] = enabled }
    }

    suspend fun setOnboardingCompleted() {
        dataStore.edit { it[ONBOARDING_COMPLETED] = true }
    }

    companion object {
        private val RETENTION_MODE = intPreferencesKey("retention_mode")
        private val REPORT_RETENTION_DAYS = intPreferencesKey("report_retention_days")
        private val ANALYSIS_PROFILE = intPreferencesKey("analysis_profile")
        private val HASH_ALGORITHMS = stringPreferencesKey("hash_algorithms")
        private val NATIVE_ANALYSIS = booleanPreferencesKey("native_analysis")
        private val DEEP_DEX_ANALYSIS = booleanPreferencesKey("deep_dex_analysis")
        private val IOC_EXTRACTION = booleanPreferencesKey("ioc_extraction")
        private val REDACT_SECRETS = booleanPreferencesKey("redact_secrets")
        private val REDACT_IOC = booleanPreferencesKey("redact_ioc")
        private val INCLUDE_FILENAME = booleanPreferencesKey("include_filename")
        private val THEME_MODE = intPreferencesKey("theme_mode")
        private val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}

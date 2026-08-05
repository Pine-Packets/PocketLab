package com.pineandpackets.pocketlab.engine.api

import com.pineandpackets.pocketlab.core.model.AnalysisReport
import kotlinx.coroutines.flow.Flow

interface AnalysisEngine {
    suspend fun analyze(request: AnalysisRequest): Flow<AnalysisProgress>
    suspend fun cancel(jobId: String)
    suspend fun getEngineInfo(): EngineInfo
}

data class AnalysisRequest(
    val jobId: String,
    val inputFileDescriptor: Int,
    val sourceDisplayName: String,
    val sourceMimeType: String?,
    val sourceSizeReported: Long?,
    val analysisProfile: AnalysisProfile,
    val hashAlgorithms: List<HashAlgorithm>,
    val nativeAnalysisEnabled: Boolean,
    val deepDexAnalysisEnabled: Boolean,
    val iocExtractionEnabled: Boolean
)

enum class AnalysisProfile {
    STANDARD,
    ADVANCED
}

enum class HashAlgorithm {
    SHA256,
    SHA1,
    MD5
}

sealed class AnalysisProgress {
    data class StageStarted(val stageId: String, val stageName: String) : AnalysisProgress()
    data class StageProgress(val stageId: String, val current: Int, val total: Int?) : AnalysisProgress()
    data class StageComplete(val stageId: String, val warningCount: Int) : AnalysisProgress()
    data class StageFailed(val stageId: String, val errorCode: String, val message: String) : AnalysisProgress()
    data class ReportReady(val report: AnalysisReport) : AnalysisProgress()
    data class Error(val errorCode: String, val message: String) : AnalysisProgress()
}

data class EngineInfo(
    val engineVersion: String,
    val rulePackVersion: String,
    val supportedFileTypes: List<String>
)

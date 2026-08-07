package com.pineandpackets.pocketlab.engine.orchestrator

import com.pineandpackets.pocketlab.core.model.AnalysisReport
import com.pineandpackets.pocketlab.engine.api.AnalysisEngine
import com.pineandpackets.pocketlab.engine.api.AnalysisRequest
import com.pineandpackets.pocketlab.engine.api.EngineInfo
import com.pineandpackets.pocketlab.engine.pipeline.AnalysisConfig
import com.pineandpackets.pocketlab.engine.pipeline.AnalysisPipeline
import com.pineandpackets.pocketlab.engine.pipeline.HashResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.File
import java.util.UUID

class AnalysisOrchestrator(
    private val configOverrides: AnalysisConfig? = null
) : AnalysisEngine {
    
    override suspend fun analyze(request: AnalysisRequest): Flow<com.pineandpackets.pocketlab.engine.api.AnalysisProgress> {
        Timber.i("Starting analysis for job ${request.jobId}")
        
        val inputFile = File(request.inputPath)
        if (!inputFile.exists()) {
            throw IllegalArgumentException("Input file does not exist: ${request.inputPath}")
        }
        
        val config = configOverrides ?: AnalysisConfig.fromRequest(request)
        val pipeline = AnalysisPipeline(config)
        
        val hashes = HashResult(
            sha256 = request.sha256 ?: calculateHash(inputFile, "SHA-256"),
            sha1 = request.sha1 ?: calculateHash(inputFile, "SHA-1"),
            md5 = request.md5 ?: calculateHash(inputFile, "MD5")
        )
        
        return pipeline.analyze(request.jobId, inputFile, hashes, request.archivePassword).map { progress ->
            when (progress) {
                is com.pineandpackets.pocketlab.engine.pipeline.AnalysisProgress.StageStarted ->
                    com.pineandpackets.pocketlab.engine.api.AnalysisProgress.StageStarted(
                        progress.stageId,
                        progress.stageName
                    )
                is com.pineandpackets.pocketlab.engine.pipeline.AnalysisProgress.StageComplete ->
                    com.pineandpackets.pocketlab.engine.api.AnalysisProgress.StageComplete(
                        progress.stageId,
                        0
                    )
                is com.pineandpackets.pocketlab.engine.pipeline.AnalysisProgress.StageFailed ->
                    com.pineandpackets.pocketlab.engine.api.AnalysisProgress.StageFailed(
                        progress.stageId,
                        "STAGE_FAILED",
                        progress.error
                    )
                is com.pineandpackets.pocketlab.engine.pipeline.AnalysisProgress.Complete ->
                    com.pineandpackets.pocketlab.engine.api.AnalysisProgress.ReportReady(progress.report)
                is com.pineandpackets.pocketlab.engine.pipeline.AnalysisProgress.Error ->
                    com.pineandpackets.pocketlab.engine.api.AnalysisProgress.Error(
                        "ANALYSIS_FAILED",
                        progress.message
                    )
            }
        }
    }
    
    override suspend fun cancel(jobId: String) {
        Timber.i("Cancelling analysis for job $jobId")
    }
    
    override suspend fun getEngineInfo(): EngineInfo {
        return EngineInfo(
            engineVersion = "1.0.0",
            rulePackVersion = "2026.08.1",
            supportedFileTypes = listOf("APK", "ZIP", "DEX")
        )
    }
    
    private fun calculateHash(file: File, algorithm: String): String {
        val digest = java.security.MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

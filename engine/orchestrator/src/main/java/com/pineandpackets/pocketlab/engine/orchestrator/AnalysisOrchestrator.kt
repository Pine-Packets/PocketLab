package com.pineandpackets.pocketlab.engine.orchestrator

import com.pineandpackets.pocketlab.core.model.AnalysisReport
import com.pineandpackets.pocketlab.core.model.AnalyzerInfo
import com.pineandpackets.pocketlab.core.model.ArtifactNode
import com.pineandpackets.pocketlab.engine.api.AnalysisCancellation
import com.pineandpackets.pocketlab.engine.api.AnalysisEngine
import com.pineandpackets.pocketlab.engine.api.AnalysisRequest
import com.pineandpackets.pocketlab.engine.api.EngineInfo
import com.pineandpackets.pocketlab.engine.api.AnalysisProgress
import com.pineandpackets.pocketlab.engine.api.CaseBudget
import com.pineandpackets.pocketlab.engine.api.ArtifactAnalyzer
import com.pineandpackets.pocketlab.engine.pdf.PdfAnalyzer
import com.pineandpackets.pocketlab.engine.ooxml.OoxmlAnalyzer
import com.pineandpackets.pocketlab.engine.pipeline.AnalysisConfig
import com.pineandpackets.pocketlab.engine.pipeline.AnalysisPipeline
import com.pineandpackets.pocketlab.engine.pipeline.HashResult
import com.pineandpackets.pocketlab.engine.pipeline.AnalysisProgress as PipelineProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Entry-point [AnalysisEngine]. Runs the deterministic APK/DEX/archive pipeline
 * and, in parallel, the generic artifact dispatcher; the dispatcher result is
 * merged into the final report's `artifacts`/`analyzerInfo`/`facts` and the
 * report schema version is raised to 1.1.0.
 */
class AnalysisOrchestrator(
    private val configOverrides: AnalysisConfig? = null,
) : AnalysisEngine {

    private val cancellations = ConcurrentHashMap<String, AnalysisCancellation>()

    override suspend fun analyze(request: AnalysisRequest): Flow<AnalysisProgress> {
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

        val cancellation = AnalysisCancellation()
        cancellations[request.jobId] = cancellation

        return pipeline.analyze(request.jobId, inputFile, hashes, request.archivePassword).map { progress ->
            when (progress) {
                is PipelineProgress.StageStarted ->
                    AnalysisProgress.StageStarted(progress.stageId, progress.stageName)
                is PipelineProgress.StageComplete ->
                    AnalysisProgress.StageComplete(progress.stageId, 0)
                is PipelineProgress.StageFailed ->
                    AnalysisProgress.StageFailed(progress.stageId, "STAGE_FAILED", progress.error)
                is PipelineProgress.Complete -> {
                    val enriched = runDispatcherAndMerge(
                        report = progress.report,
                        inputFile = inputFile,
                        cancellation = cancellation,
                        config = config,
                    )
                    AnalysisProgress.ReportReady(enriched)
                }
                is PipelineProgress.Error ->
                    AnalysisProgress.Error("ANALYSIS_FAILED", progress.message)
            }.also {
                if (it is AnalysisProgress.ReportReady || it is AnalysisProgress.Error) {
                    cancellations.remove(request.jobId)
                }
            }
        }
    }

    override suspend fun cancel(jobId: String) {
        Timber.i("Cancelling analysis for job $jobId")
        cancellations[jobId]?.cancel()
    }

    override suspend fun getEngineInfo(): EngineInfo {
        return EngineInfo(
            engineVersion = "1.0.0",
            rulePackVersion = "2026.08.1",
            supportedFileTypes = listOf("APK", "ZIP", "DEX", "ELF", "PDF", "PE", "OLE", "GZIP")
        )
    }

    private fun runDispatcherAndMerge(
        report: AnalysisReport,
        inputFile: File,
        cancellation: AnalysisCancellation,
        config: AnalysisConfig,
    ): AnalysisReport {
        return try {
            val budget = CaseBudget(
                maxBytesRead = config.maxBytesRead,
                maxExpandedBytes = config.maxArchiveExpandedBytes,
                maxArtifactCount = config.maxClassCount,
                maxArchiveEntries = config.maxArchiveEntries,
                maxRecursionDepth = config.maxNestingDepth,
                maxFindings = 1000,
                maxIndicators = config.maxStringCount,
                maxFacts = 10_000,
                maxOps = config.maxInstructionCount,
            )
            val outcome = dispatcherRunner(inputFile, cancellation, budget)
            report.copy(
                schemaVersion = REPORT_SCHEMA_1_1,
                artifacts = listOf(outcome.root),
                analyzerInfo = outcome.analyzersUsed,
            )
        } catch (e: Exception) {
            Timber.w(e, "Dispatcher enrichment failed; report kept as produced")
            report.copy(schemaVersion = REPORT_SCHEMA_1_1)
        }
    }

    private fun dispatcherRunner(
        inputFile: File,
        cancellation: AnalysisCancellation,
        budget: CaseBudget,
    ): AnalysisOutcome {
        val source = FileArtifactSource(inputFile, cancellation)
        return AnalysisDispatcher(
            detect = LayeredTypeDetector::detect,
            analyzers = analyzerRegistry(),
        ).analyzeRoot(
            source = source,
            budget = budget,
            cancellation = cancellation,
            deadlineEpochMs = null,
            maxNestingDepth = 2,
        )
    }

    /**
     * The compiled-in analyzer registry. New format stages add their
     * [ArtifactAnalyzer] here. Analyzers are pure engine code (no Android
     * framework types), so the same registry runs in-process and isolated.
     */
    private fun analyzerRegistry(): List<ArtifactAnalyzer> = listOf(
        PdfAnalyzer(),
        OoxmlAnalyzer(),
    )

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

    companion object {
        const val REPORT_SCHEMA_1_1: String = "1.1.0"
    }
}
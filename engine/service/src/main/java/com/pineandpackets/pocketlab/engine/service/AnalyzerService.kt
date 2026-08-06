package com.pineandpackets.pocketlab.engine.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import com.pineandpackets.pocketlab.core.model.AnalysisReport
import com.pineandpackets.pocketlab.engine.service.aidl.IAnalyzerCallback
import com.pineandpackets.pocketlab.engine.service.aidl.IAnalyzerService
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AnalyzerService : Service() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val checkpoints = ConcurrentHashMap<String, AnalysisCheckpoint>()

    private val binder = object : IAnalyzerService.Stub() {

        override fun startAnalysis(
            requestJson: String,
            inputFd: ParcelFileDescriptor,
            outputFd: ParcelFileDescriptor,
            callback: IAnalyzerCallback
        ): String {
            val request = try {
                json.decodeFromString<IsolatedAnalysisRequest>(requestJson)
            } catch (e: Exception) {
                Timber.e(e, "Failed to deserialize analysis request")
                callback.onAnalysisError("", "INVALID_REQUEST", "Failed to parse request: ${e.message}")
                return ""
            }

            val jobId = request.jobId.ifBlank { UUID.randomUUID().toString() }

            Timber.i("Starting isolated analysis for job $jobId")

            val job = scope.launch {
                try {
                    executeAnalysis(jobId, request, inputFd, outputFd, callback)
                } catch (e: CancellationException) {
                    Timber.i("Analysis cancelled for job $jobId")
                    callback.onAnalysisError(jobId, "CANCELLED", "Analysis was cancelled")
                } catch (e: Exception) {
                    Timber.e(e, "Analysis failed for job $jobId")
                    callback.onAnalysisError(jobId, "ANALYSIS_FAILED", e.message ?: "Unknown error")
                } finally {
                    activeJobs.remove(jobId)
                    closeQuietly(inputFd)
                    closeQuietly(outputFd)
                }
            }

            activeJobs[jobId] = job
            return jobId
        }

        override fun cancelAnalysis(jobId: String) {
            Timber.i("Cancelling analysis for job $jobId")
            activeJobs[jobId]?.cancel()
            activeJobs.remove(jobId)
        }

        override fun getEngineInfo(): String {
            val info = mapOf(
                "engineVersion" to "1.0.0",
                "rulePackVersion" to "2026.08.1",
                "supportedFileTypes" to listOf("APK", "ZIP", "DEX", "ELF", "PE", "PDF"),
                "isolatedProcess" to true
            )
            return json.encodeToString(info)
        }

        override fun isAnalysisRunning(jobId: String): Boolean {
            return activeJobs[jobId]?.isActive == true
        }
    }

    private suspend fun executeAnalysis(
        jobId: String,
        request: IsolatedAnalysisRequest,
        inputFd: ParcelFileDescriptor,
        outputFd: ParcelFileDescriptor,
        callback: IAnalyzerCallback
    ) {
        val startTime = System.currentTimeMillis()
        val completedStages = mutableListOf<String>()

        sendProgress(callback, jobId, "INITIALIZATION", "Starting isolated analysis")

        val tempFile = File.createTempFile("analysis_", ".bin", cacheDir)
        try {
            sendProgress(callback, jobId, "STAGING", "Copying input to isolated workspace")

            val bytesRead = copyWithBudget(
                FileInputStream(inputFd.fileDescriptor),
                FileOutputStream(tempFile),
                request.maxBytesRead
            )

            sendProgress(callback, jobId, "STAGING", "Staged $bytesRead bytes")

            withContext(Dispatchers.IO) {
                val checkpoint = AnalysisCheckpoint(
                    jobId = jobId,
                    caseId = jobId,
                    completedStages = completedStages,
                    currentStage = "FILE_TYPE",
                    partialReportJson = null,
                    sourceSha256 = request.sha256 ?: "",
                    createdAt = java.time.Instant.now().toString(),
                    lastUpdatedAt = java.time.Instant.now().toString()
                )
                checkpoints[jobId] = checkpoint
            }

            val pipeline = com.pineandpackets.pocketlab.engine.pipeline.AnalysisPipeline()
            val hashes = com.pineandpackets.pocketlab.engine.pipeline.HashResult(
                sha256 = request.sha256 ?: "",
                sha1 = request.sha1 ?: "",
                md5 = request.md5 ?: ""
            )

            pipeline.analyze(jobId, tempFile, hashes, request.archivePassword).collect { progress ->
                when (progress) {
                    is com.pineandpackets.pocketlab.engine.pipeline.AnalysisProgress.StageStarted -> {
                        sendProgress(callback, jobId, progress.stageId, progress.stageName)
                    }
                    is com.pineandpackets.pocketlab.engine.pipeline.AnalysisProgress.StageComplete -> {
                        completedStages.add(progress.stageId)
                        updateCheckpoint(jobId, completedStages, progress.stageId)
                    }
                    is com.pineandpackets.pocketlab.engine.pipeline.AnalysisProgress.StageFailed -> {
                        sendProgress(callback, jobId, progress.stageId, "Failed: ${progress.error}")
                    }
                    is com.pineandpackets.pocketlab.engine.pipeline.AnalysisProgress.Complete -> {
                        val elapsed = System.currentTimeMillis() - startTime
                        if (elapsed > request.maxWallTimeMs) {
                            callback.onAnalysisError(jobId, "TIMEOUT", "Analysis exceeded time budget")
                            return@collect
                        }

                        val reportJson = json.encodeToString(progress.report)
                        if (reportJson.length > request.maxOutputBytes) {
                            callback.onAnalysisError(jobId, "OUTPUT_TOO_LARGE", "Report exceeds output budget")
                            return@collect
                        }

                        writeReportToOutput(outputFd, reportJson)
                        callback.onAnalysisComplete(jobId, reportJson)
                    }
                    is com.pineandpackets.pocketlab.engine.pipeline.AnalysisProgress.Error -> {
                        callback.onAnalysisError(jobId, "PIPELINE_ERROR", progress.message)
                    }
                }
            }
        } finally {
            tempFile.delete()
            checkpoints.remove(jobId)
        }
    }

    private fun copyWithBudget(
        input: FileInputStream,
        output: FileOutputStream,
        maxBytes: Long
    ): Long {
        val buffer = ByteArray(AnalysisLimits.BUFFER_SIZE)
        var totalBytesRead = 0L
        var bytesRead: Int

        input.use { inputStream ->
            output.use { outputStream ->
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    totalBytesRead += bytesRead
                    if (totalBytesRead > maxBytes) {
                        throw com.pineandpackets.pocketlab.core.common.AnalysisError.QuotaExceededError(
                            "Input exceeds maximum size of $maxBytes bytes"
                        )
                    }
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
            }
        }
        return totalBytesRead
    }

    private fun sendProgress(callback: IAnalyzerCallback, jobId: String, stageId: String, message: String) {
        try {
            val progressJson = json.encodeToString(
                mapOf("stageId" to stageId, "message" to message, "timestamp" to System.currentTimeMillis())
            )
            callback.onProgressUpdate(jobId, progressJson)
        } catch (e: Exception) {
            Timber.w(e, "Failed to send progress update for job $jobId")
        }
    }

    private fun updateCheckpoint(jobId: String, completedStages: List<String>, currentStage: String) {
        val existing = checkpoints[jobId]
        if (existing != null) {
            checkpoints[jobId] = existing.copy(
                completedStages = completedStages,
                currentStage = currentStage,
                lastUpdatedAt = java.time.Instant.now().toString()
            )
        }
    }

    private fun writeReportToOutput(outputFd: ParcelFileDescriptor, reportJson: String) {
        try {
            FileOutputStream(outputFd.fileDescriptor).use { outputStream ->
                val bytes = reportJson.toByteArray(Charsets.UTF_8)
                outputStream.write(bytes)
                outputStream.flush()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to write report to output")
        }
    }

    private fun closeQuietly(fd: ParcelFileDescriptor?) {
        try {
            fd?.close()
        } catch (_: Exception) {
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Timber.i("AnalyzerService bound from process ${android.os.Process.myPid()}")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Timber.i("AnalyzerService unbound, cleaning up")
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        checkpoints.clear()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        Timber.i("AnalyzerService destroyed")
    }

    fun getCheckpoint(jobId: String): AnalysisCheckpoint? = checkpoints[jobId]
}

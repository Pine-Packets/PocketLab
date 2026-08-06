package com.pineandpackets.pocketlab.engine.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.pineandpackets.pocketlab.core.common.AnalysisError
import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import com.pineandpackets.pocketlab.core.model.AnalysisReport
import com.pineandpackets.pocketlab.engine.api.AnalysisEngine
import com.pineandpackets.pocketlab.engine.api.AnalysisProfile
import com.pineandpackets.pocketlab.engine.api.AnalysisRequest
import com.pineandpackets.pocketlab.engine.api.EngineInfo
import com.pineandpackets.pocketlab.engine.service.aidl.IAnalyzerCallback
import com.pineandpackets.pocketlab.engine.service.aidl.IAnalyzerService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AnalysisClient(private val context: Context) : AnalysisEngine {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var serviceBinder: IAnalyzerService? = null
    private val connected = AtomicBoolean(false)
    private val connectionLatch = CountDownLatch(1)
    private val activeFlows = ConcurrentHashMap<String, MutableSharedFlow<com.pineandpackets.pocketlab.engine.api.AnalysisProgress>>()
    private val crashListeners = ConcurrentHashMap<String, (String) -> Unit>()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Timber.i("Connected to AnalyzerService")
            serviceBinder = IAnalyzerService.Stub.asInterface(binder)
            connected.set(true)
            connectionLatch.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.w("Disconnected from AnalyzerService (possible crash)")
            serviceBinder = null
            connected.set(false)
            notifyCrashListeners()
        }
    }

    fun bind() {
        val intent = Intent(context, AnalyzerService::class.java)
        try {
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!connectionLatch.await(5, TimeUnit.SECONDS)) {
                Timber.e("Timeout binding to AnalyzerService")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to bind to AnalyzerService")
        }
    }

    fun unbind() {
        try {
            context.unbindService(serviceConnection)
        } catch (_: Exception) {
        }
        serviceBinder = null
        connected.set(false)
        activeFlows.clear()
    }

    fun isBound(): Boolean = connected.get()

    fun registerCrashListener(jobId: String, listener: (String) -> Unit) {
        crashListeners[jobId] = listener
    }

    fun unregisterCrashListener(jobId: String) {
        crashListeners.remove(jobId)
    }

    private fun notifyCrashListeners() {
        crashListeners.values.forEach { listener ->
            try {
                listener("ISOLATED_PROCESS_CRASH")
            } catch (_: Exception) {
            }
        }
        crashListeners.clear()
    }

    override suspend fun analyze(request: AnalysisRequest): Flow<com.pineandpackets.pocketlab.engine.api.AnalysisProgress> {
        if (!connected.get() || serviceBinder == null) {
            throw AnalysisError.IntakeError("AnalyzerService not connected. Call bind() first.")
        }

        val jobId = request.jobId.ifBlank { UUID.randomUUID().toString() }
        val flow = MutableSharedFlow<com.pineandpackets.pocketlab.engine.api.AnalysisProgress>(
            replay = 0,
            extraBufferCapacity = 64
        )
        activeFlows[jobId] = flow

        val isolatedRequest = IsolatedAnalysisRequest(
            jobId = jobId,
            sourceDisplayName = request.sourceDisplayName,
            sourceMimeType = request.sourceMimeType,
            sourceSizeReported = request.sourceSizeReported,
            sha256 = request.sha256,
            sha1 = request.sha1,
            md5 = request.md5,
            analysisProfile = request.analysisProfile.name,
            hashAlgorithms = request.hashAlgorithms.map { it.name },
            nativeAnalysisEnabled = request.nativeAnalysisEnabled,
            deepDexAnalysisEnabled = request.deepDexAnalysisEnabled,
            iocExtractionEnabled = request.iocExtractionEnabled,
            archivePassword = request.archivePassword,
            maxBytesRead = AnalysisLimits.MAX_INPUT_SIZE_BYTES,
            maxObjects = AnalysisLimits.MAX_ARCHIVE_ENTRIES,
            maxStrings = AnalysisLimits.MAX_STRING_COUNT,
            maxMethods = AnalysisLimits.MAX_METHOD_COUNT,
            maxInstructions = AnalysisLimits.MAX_METHOD_COUNT * 10,
            maxGraphNodes = AnalysisLimits.MAX_CLASS_COUNT,
            maxGraphEdges = AnalysisLimits.MAX_METHOD_COUNT,
            maxRecursionDepth = AnalysisLimits.MAX_NESTING_DEPTH,
            maxWallTimeMs = AnalysisLimits.MAX_ANALYSIS_DURATION_MS,
            maxOutputBytes = AnalysisLimits.MAX_REPORT_SIZE_BYTES
        )

        val requestJson = json.encodeToString(IsolatedAnalysisRequest.serializer(), isolatedRequest)

        val inputFile = File(request.inputPath)
        if (!inputFile.exists()) {
            throw AnalysisError.IntakeError("Input file does not exist: ${request.inputPath}")
        }

        val inputFd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val outputFile = File.createTempFile("report_", ".json", context.cacheDir)
        val outputFd = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE)

        val callback = object : IAnalyzerCallback.Stub() {
            override fun onProgressUpdate(jobId: String, progressJson: String) {
                try {
                    val progress = json.decodeFromString<Map<String, String>>(progressJson)
                    val stageId = progress["stageId"] ?: "unknown"
                    val message = progress["message"] ?: ""
                    flow.tryEmit(
                        com.pineandpackets.pocketlab.engine.api.AnalysisProgress.StageStarted(stageId, message)
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse progress update")
                }
            }

            override fun onAnalysisComplete(jobId: String, reportJson: String) {
                try {
                    val report = json.decodeFromString<AnalysisReport>(reportJson)
                    flow.tryEmit(com.pineandpackets.pocketlab.engine.api.AnalysisProgress.ReportReady(report))
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse completed report")
                    flow.tryEmit(
                        com.pineandpackets.pocketlab.engine.api.AnalysisProgress.Error(
                            "PARSE_ERROR",
                            "Failed to parse report: ${e.message}"
                        )
                    )
                } finally {
                    activeFlows.remove(jobId)
                    outputFile.delete()
                }
            }

            override fun onAnalysisError(jobId: String, errorCode: String, message: String) {
                flow.tryEmit(com.pineandpackets.pocketlab.engine.api.AnalysisProgress.Error(errorCode, message))
                activeFlows.remove(jobId)
                outputFile.delete()
            }
        }

        try {
            serviceBinder?.startAnalysis(requestJson, inputFd, outputFd, callback)
        } catch (e: Exception) {
            inputFd.close()
            outputFd.close()
            outputFile.delete()
            throw AnalysisError.IntakeError("Failed to start analysis in isolated process: ${e.message}")
        }

        return flow.asSharedFlow()
    }

    override suspend fun cancel(jobId: String) {
        try {
            serviceBinder?.cancelAnalysis(jobId)
        } catch (e: Exception) {
            Timber.w(e, "Failed to cancel analysis for job $jobId")
        }
        activeFlows.remove(jobId)
    }

    override suspend fun getEngineInfo(): EngineInfo {
        if (!connected.get() || serviceBinder == null) {
            return EngineInfo(
                engineVersion = "unknown",
                rulePackVersion = "unknown",
                supportedFileTypes = emptyList()
            )
        }

        return try {
            val infoJson = serviceBinder!!.engineInfo
            val info = json.decodeFromString<Map<String, Any>>(infoJson)
            EngineInfo(
                engineVersion = info["engineVersion"] as? String ?: "unknown",
                rulePackVersion = info["rulePackVersion"] as? String ?: "unknown",
                supportedFileTypes = @Suppress("UNCHECKED_CAST") (info["supportedFileTypes"] as? List<String>) ?: emptyList()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get engine info from isolated process")
            EngineInfo(
                engineVersion = "unknown",
                rulePackVersion = "unknown",
                supportedFileTypes = emptyList()
            )
        }
    }

    suspend fun isAnalysisRunning(jobId: String): Boolean {
        return try {
            serviceBinder?.isAnalysisRunning(jobId) ?: false
        } catch (e: Exception) {
            false
        }
    }
}

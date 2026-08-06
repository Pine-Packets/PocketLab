package com.pineandpackets.pocketlab.engine.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

class CheckpointManager(private val checkpointDir: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    init {
        if (!checkpointDir.exists()) {
            checkpointDir.mkdirs()
        }
    }

    fun saveCheckpoint(checkpoint: AnalysisCheckpoint): Boolean {
        return try {
            val file = File(checkpointDir, "${checkpoint.jobId}.checkpoint")
            val jsonStr = json.encodeToString(checkpoint)
            file.writeText(jsonStr)
            Timber.d("Saved checkpoint for job ${checkpoint.jobId}")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to save checkpoint for job ${checkpoint.jobId}")
            false
        }
    }

    fun loadCheckpoint(jobId: String): AnalysisCheckpoint? {
        return try {
            val file = File(checkpointDir, "$jobId.checkpoint")
            if (!file.exists()) return null
            val jsonStr = file.readText()
            json.decodeFromString<AnalysisCheckpoint>(jsonStr)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load checkpoint for job $jobId")
            null
        }
    }

    fun deleteCheckpoint(jobId: String): Boolean {
        return try {
            val file = File(checkpointDir, "$jobId.checkpoint")
            file.delete()
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete checkpoint for job $jobId")
            false
        }
    }

    fun listCheckpoints(): List<AnalysisCheckpoint> {
        return checkpointDir.listFiles { _, name -> name.endsWith(".checkpoint") }
            ?.mapNotNull { file ->
                try {
                    val jsonStr = file.readText()
                    json.decodeFromString<AnalysisCheckpoint>(jsonStr)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to read checkpoint file ${file.name}")
                    null
                }
            }
            ?: emptyList()
    }

    fun clearAllCheckpoints() {
        checkpointDir.listFiles { _, name -> name.endsWith(".checkpoint") }
            ?.forEach { it.delete() }
    }
}

package com.pineandpackets.pocketlab.engine.service

import kotlinx.serialization.Serializable

@Serializable
data class IsolatedAnalysisRequest(
    val jobId: String,
    val sourceDisplayName: String,
    val sourceMimeType: String?,
    val sourceSizeReported: Long?,
    val sha256: String?,
    val sha1: String?,
    val md5: String?,
    val analysisProfile: String,
    val hashAlgorithms: List<String>,
    val nativeAnalysisEnabled: Boolean,
    val deepDexAnalysisEnabled: Boolean,
    val iocExtractionEnabled: Boolean,
    val archivePassword: String? = null,
    val maxBytesRead: Long,
    val maxObjects: Int,
    val maxStrings: Int,
    val maxMethods: Int,
    val maxInstructions: Int,
    val maxGraphNodes: Int,
    val maxGraphEdges: Int,
    val maxRecursionDepth: Int,
    val maxWallTimeMs: Long,
    val maxOutputBytes: Long
)

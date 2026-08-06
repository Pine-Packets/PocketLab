package com.pineandpackets.pocketlab.engine.service

import kotlinx.serialization.Serializable

@Serializable
data class AnalysisCheckpoint(
    val jobId: String,
    val caseId: String,
    val completedStages: List<String>,
    val currentStage: String?,
    val partialReportJson: String?,
    val sourceSha256: String,
    val createdAt: String,
    val lastUpdatedAt: String
)

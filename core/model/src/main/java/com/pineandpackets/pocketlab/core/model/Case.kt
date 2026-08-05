package com.pineandpackets.pocketlab.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CaseId(val value: String = UUID.randomUUID().toString())

@Serializable
enum class CaseStatus {
    CREATED,
    STAGING,
    READY,
    ANALYZING,
    PARTIAL,
    COMPLETE,
    EXPORTING,
    STAGING_FAILED,
    ANALYSIS_FAILED,
    CANCELLED,
    CORRUPT_RESULT,
    DELETION_PENDING,
    DELETED
}

@Serializable
enum class RiskBand {
    NO_MAJOR_CONCERNS,
    REVIEW_RECOMMENDED,
    SUSPICIOUS_CAPABILITIES,
    HIGH_RISK_INDICATORS,
    ANALYSIS_INCOMPLETE
}

@Serializable
data class CaseMetadata(
    val id: CaseId,
    val createdAt: Long,
    val updatedAt: Long,
    val status: CaseStatus,
    val sourceDisplayName: String,
    val sourceMimeType: String?,
    val sourceSizeReported: Long?,
    val sourceSizeActual: Long?,
    val sha256: String?,
    val sha1: String?,
    val md5: String?,
    val primaryDetectedType: String?,
    val containerType: String?,
    val engineVersion: String?,
    val rulePackVersion: String?,
    val reportSchemaVersion: String?,
    val riskBand: RiskBand?,
    val maxSeverity: Severity?,
    val findingCount: Int?,
    val retentionMode: RetentionMode,
    val samplePresent: Boolean,
    val reportPresent: Boolean,
    val lastErrorCode: String?
)

@Serializable
enum class RetentionMode {
    TEMPORARY,
    SESSION_ONLY,
    RETAIN_SAMPLE,
    AUTO_DELETE_1_DAY,
    AUTO_DELETE_7_DAYS,
    AUTO_DELETE_30_DAYS
}

@Serializable
enum class Severity {
    INFORMATIONAL,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

@Serializable
enum class Confidence {
    LOW,
    MEDIUM,
    HIGH
}

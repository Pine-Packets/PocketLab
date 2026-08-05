package com.pineandpackets.pocketlab.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Finding(
    val id: String,
    val ruleId: String,
    val title: String,
    val category: String,
    val severity: Severity,
    val confidence: Confidence,
    val status: FindingStatus = FindingStatus.ACTIVE,
    val simpleExplanation: String,
    val analystExplanation: String,
    val evidence: List<Evidence> = emptyList(),
    val limitations: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val mappings: List<FrameworkMapping> = emptyList(),
    val references: List<String> = emptyList()
)

@Serializable
enum class FindingStatus {
    ACTIVE,
    SUPPRESSED,
    INFORMATIONAL
}

@Serializable
data class Evidence(
    val type: EvidenceType,
    val fileId: String? = null,
    val dexName: String? = null,
    val className: String? = null,
    val method: String? = null,
    val offset: Long? = null,
    val excerpt: String? = null,
    val excerptEncoding: String = "escaped-text",
    val additionalData: Map<String, String> = emptyMap()
)

@Serializable
enum class EvidenceType {
    DEX_CALL_SITE,
    DEX_STRING,
    DEX_FIELD_ACCESS,
    MANIFEST_DECLARATION,
    RESOURCE_VALUE,
    ARCHIVE_ENTRY,
    NATIVE_SYMBOL,
    IOC_MATCH,
    CERTIFICATE_FIELD,
    CONFIGURATION_VALUE
}

@Serializable
data class FrameworkMapping(
    val framework: String,
    val technique: String,
    val techniqueName: String? = null
)

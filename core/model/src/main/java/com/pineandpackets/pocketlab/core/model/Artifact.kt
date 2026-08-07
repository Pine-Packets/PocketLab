package com.pineandpackets.pocketlab.core.model

import kotlinx.serialization.Serializable

/**
 * A versioned description of a single analysis unit (parser revision). Every
 * finding, fact, indicator, and decoded output is attributable to the analyzer
 * that produced it via [analyzerId] and [analyzerVersion].
 */
@Serializable
data class AnalyzerInfo(
    val analyzerId: String,
    val analyzerVersion: String,
    val supportedFormats: List<String> = emptyList(),
    val capabilities: List<String> = emptyList(),
    val limitations: List<String> = emptyList()
)

/**
 * Generic artifact node. Every analyzed input (root and nested/container child)
 * is represented by an [ArtifactNode]. The tree is acyclic: children link to a
 * parent via [parentId]. A nested container consumes the same case-level budget
 * as its parent and is never granted a fresh budget.
 *
 * @param artifactId stable, case-unique identifier (random UUID).
 * @param parentId id of the container that produced this artifact, or null for
 *   the root artifact.
 * @param relation how this artifact relates to its parent (ROOT, CONTAINED,
 *   EXTRACTED, CHILD_CONTAINER, ...).
 * @param originalName the source-provided display name (escaped when rendered).
 * @param sanitizedName a display-safe name with control characters removed.
 * @param claimedMimeType advisory MIME from the provider; never trusted.
 * @param detectedType detected top-level format (e.g. ZIP, APK, PDF, DEX).
 * @param detectedSubtype finer structural subtype (e.g. multidex, xapk).
 * @param sizeBytes observed size in bytes.
 * @param sha256 SHA-256 of the artifact bytes, when computed.
 * @param metadata bounded metadata extracted by analyzers.
 * @param indicators indicators observed in this artifact only.
 * @param findings findings observed in this artifact only.
 * @param facts facts observed in this artifact only.
 * @param children nested artifact nodes.
 * @param parserErrors parser-error records marking this artifact incomplete.
 * @param incomplete true when analysis of this artifact was cut short by a
 *   quota, timeout, cancellation, or parser failure.
 * @param completeness 0.0..1.0 proportion of planned analysis completed.
 * @param limitations human-readable limitations specific to this artifact.
 * @param analyzers analyzers that inspected this artifact (id and version).
 */
@Serializable
data class ArtifactNode(
    val artifactId: String,
    val parentId: String? = null,
    val relation: ArtifactRelation,
    val originalName: String,
    val sanitizedName: String,
    val claimedMimeType: String? = null,
    val detectedType: String? = null,
    val detectedSubtype: String? = null,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
    val metadata: List<ArtifactMetadataEntry> = emptyList(),
    val indicators: List<Indicator> = emptyList(),
    val findings: List<Finding> = emptyList(),
    val facts: List<Fact> = emptyList(),
    val children: List<ArtifactNode> = emptyList(),
    val parserErrors: List<ParserErrorRecord> = emptyList(),
    val incomplete: Boolean = false,
    val completeness: Double = 1.0,
    val limitations: List<String> = emptyList(),
    val analyzers: List<AnalyzerUse> = emptyList()
)

@Serializable
enum class ArtifactRelation {
    ROOT,
    CONTAINED,
    EXTRACTED,
    CHILD_CONTAINER,
    NESTED_EMBEDDED
}

@Serializable
data class ArtifactMetadataEntry(
    val key: String,
    val value: String
)

@Serializable
data class AnalyzerUse(
    val analyzerId: String,
    val analyzerVersion: String
)

/**
 * A parser failure that did not crash the process but prevented complete
 * analysis of an artifact. Producing a [ParserErrorRecord] marks the affected
 * artifact incomplete; it must never be classified as clean.
 */
@Serializable
data class ParserErrorRecord(
    val code: String,
    val message: String,
    val analyzerId: String? = null,
    val stage: String? = null
)

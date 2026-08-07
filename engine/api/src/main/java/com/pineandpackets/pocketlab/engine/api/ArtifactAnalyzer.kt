package com.pineandpackets.pocketlab.engine.api

import com.pineandpackets.pocketlab.core.model.ArtifactMetadataEntry
import com.pineandpackets.pocketlab.core.model.DetectedType
import com.pineandpackets.pocketlab.core.model.Fact
import com.pineandpackets.pocketlab.core.model.Finding
import com.pineandpackets.pocketlab.core.model.Indicator
import com.pineandpackets.pocketlab.core.model.ParserErrorRecord

/**
 * Layer at which a format signal was observed, ordered from most to least
 * reliable. The dispatcher evaluates signals from strongest to weakest and
 * reports mismatches.
 */
enum class DetectionLayer {
    CONTENT_SIGNATURE,
    STRUCTURAL,
    CONTAINER_CHARACTERISTIC,
    ADVISORY_MIME,
    ADVISORY_EXTENSION,
}

/**
 * A single analyzer (parser) registered with the dispatcher. Analyzers are
 * pure engine code with no Android framework types, no networking, and no
 * dynamic code loading, so they run both in-process and in the isolated
 * `:analyzer` process.
 */
interface ArtifactAnalyzer {
    val analyzerId: String
    val analyzerVersion: String

    /** Formats (DetectedType) this analyzer can inspect. */
    val supported: Set<DetectedType>

    /** Capabilities for report metadata. */
    val capabilities: List<String>

    /**
     * Analyze the artifact produced by the dispatcher. The analyzer writes its
     * own findings/indicators/facts into [context] scope outputs and returns a
     * bounded summary. Quotas and cancellation must be honored via
     * [AnalysisContext.checkCancelled] and [AnalysisContext.budget].
     */
    fun analyze(context: AnalysisContext, artifact: ArtifactRef): AnalyzerResult
}

/**
 * A minimal read-only view of the artifact bytes plus its detection metadata,
 * passed to analyzers. Bound reads through [readNBytes] and [readRange], which
 * consult the shared budget before consuming. Implementations never return more
 * than the requested [count] and return an empty array when the read would
 * exceed budget or be invalid.
 */
interface ArtifactRef {
    val artifactId: String
    val parentId: String?
    val name: String
    val detectedType: DetectedType
    val detectedSubtype: String?
    val sizeBytes: Long

    /** Read the first [count] bytes (bounded; empty when budget exceeded). */
    fun readNBytes(count: Int): ByteArray

    /** Read [count] bytes starting at [offset] (bounded; empty when invalid). */
    fun readRange(offset: Long, count: Int): ByteArray
}

/** Structured, bounded output produced by a single analyzer run. */
data class AnalyzerResult(
    val analyzerId: String,
    val analyzerVersion: String,
    val findings: List<Finding> = emptyList(),
    val indicators: List<Indicator> = emptyList(),
    val facts: List<Fact> = emptyList(),
    val metadata: List<ArtifactMetadataEntry> = emptyList(),
    val parsedChildren: List<ParsedChild> = emptyList(),
    val incomplete: Boolean = false,
    val parserErrors: List<ParserErrorRecord> = emptyList(),
    val limitations: List<String> = emptyList(),
)

/** A child artifact detected by a container analyzer (e.g. an archive entry). */
data class ParsedChild(
    val name: String,
    val detectedType: DetectedType?,
    val detectedSubtype: String?,
    val sizeBytes: Long?,
)
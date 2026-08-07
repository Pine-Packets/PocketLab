package com.pineandpackets.pocketlab.engine.orchestrator

import com.pineandpackets.pocketlab.core.model.AnalyzerInfo
import com.pineandpackets.pocketlab.core.model.AnalyzerUse
import com.pineandpackets.pocketlab.core.model.ArtifactMetadataEntry
import com.pineandpackets.pocketlab.core.model.ArtifactNode
import com.pineandpackets.pocketlab.core.model.ArtifactRelation
import com.pineandpackets.pocketlab.core.model.DetectedType
import com.pineandpackets.pocketlab.core.model.Fact
import com.pineandpackets.pocketlab.core.model.Finding
import com.pineandpackets.pocketlab.core.model.Indicator
import com.pineandpackets.pocketlab.core.model.ParserErrorRecord
import com.pineandpackets.pocketlab.engine.api.AnalysisCancellation
import com.pineandpackets.pocketlab.engine.api.AnalysisContext
import com.pineandpackets.pocketlab.engine.api.AnalyzerResult
import com.pineandpackets.pocketlab.engine.api.ArtifactAnalyzer
import com.pineandpackets.pocketlab.engine.api.ArtifactRef
import com.pineandpackets.pocketlab.engine.api.CaseBudget
import com.pineandpackets.pocketlab.engine.api.DetectionLayer
import com.pineandpackets.pocketlab.engine.api.ParsedChild
import java.util.UUID
import kotlinx.coroutines.CancellationException

/**
 * A bounded, random-access view of an artifact's bytes plus advisory metadata.
 * Implementations guard reads by the shared [CaseBudget] and honor
 * [AnalysisCancellation] in read loops.
 */
interface ArtifactSource {
    val name: String
    val sizeBytes: Long
    val claimedMimeType: String?
    val advisoryExtension: String?
    fun readNBytes(count: Int): ByteArray
    fun readRange(offset: Long, count: Int): ByteArray
    fun computeSha256(): String?
}

/** A single format detection signal observed at a given trust layer. */
data class DetectedArtifact(
    val type: DetectedType,
    val subtype: String? = null,
    val byLayer: DetectionLayer,
    val mismatchFlags: List<String> = emptyList(),
)

/** Outcome of a dispatcher run: the artifact tree plus analyzer registry. */
data class AnalysisOutcome(
    val root: ArtifactNode,
    val analyzersUsed: List<AnalyzerInfo>,
    val cancelled: Boolean = false,
    val timedOut: Boolean = false,
)

/**
 * Registry-driven dispatcher: layered format detection, multi-analyzer
 * fan-out (polyglots), and recursive child dispatch, all under one shared
 * [CaseBudget] and [AnalysisCancellation] with an optional wall-clock deadline.
 *
 * Quota exhaustion and per-analyzer crashes mark the affected nodes incomplete
 * while preserving already-collected evidence. Cancellation and timeout are
 * captured in the returned [AnalysisOutcome]; they never corrupt partial nodes.
 */
class AnalysisDispatcher(
    private val detect: (ArtifactSource) -> DetectedArtifact,
    private val analyzers: List<ArtifactAnalyzer>,
) {
    /**
     * Analyze the root artifact and every child reachable within budget.
     * Produces the artifact tree and analyzer registry. Never throws for
     * parser failures, quotas, cancellation, or timeout.
     */
    fun analyzeRoot(
        source: ArtifactSource,
        budget: CaseBudget,
        cancellation: AnalysisCancellation,
        deadlineEpochMs: Long? = null,
        maxNestingDepth: Int = 2,
    ): AnalysisOutcome {
        val ctx = AnalysisContext(budget, cancellation, deadlineEpochMs)
        return try {
            val root = analyzeSubtree(
                id = UUID.randomUUID().toString(),
                parentId = null,
                source = source,
                ctx = ctx,
                depth = 0,
                maxNestingDepth = maxNestingDepth,
            )
            AnalysisOutcome(root, analyzersInfo())
        } catch (e: CancellationException) {
            val timedOut = (e.message ?: "").contains("timeout")
            val root = incompleteRoot(source, if (timedOut) "ANALYSIS_TIMEOUT" else "ANALYSIS_CANCELLED")
            AnalysisOutcome(root, analyzersInfo(), cancelled = !timedOut, timedOut = timedOut)
        }
    }

    private fun analyzersInfo(): List<AnalyzerInfo> =
        analyzers.map { analyzer ->
            AnalyzerInfo(
                analyzerId = analyzer.analyzerId,
                analyzerVersion = analyzer.analyzerVersion,
                supportedFormats = analyzer.supported.map { it.name },
                capabilities = analyzer.capabilities,
            )
        }

    private fun incompleteRoot(source: ArtifactSource, reason: String): ArtifactNode =
        ArtifactNode(
            artifactId = UUID.randomUUID().toString(),
            parentId = null,
            relation = ArtifactRelation.ROOT,
            originalName = source.name,
            sanitizedName = sanitizeName(source.name),
            sizeBytes = source.sizeBytes,
            incomplete = true,
            completeness = 0.0,
            limitations = listOf(reason),
            parserErrors = listOf(ParserErrorRecord(code = reason, message = reason)),
        )

    private fun analyzeSubtree(
        id: String,
        parentId: String?,
        source: ArtifactSource,
        ctx: AnalysisContext,
        depth: Int,
        maxNestingDepth: Int,
        declaredType: DetectedType? = null,
    ): ArtifactNode {
        ctx.checkCancelled()
        if (!ctx.budget.tryEnterRecursion()) {
            return incompleteNode(id, parentId, source, "MAX_RECURSION_DEPTH")
        }
        return try {
            analyzeInner(id, parentId, source, ctx, depth, maxNestingDepth, declaredType)
        } finally {
            ctx.budget.leaveRecursion()
        }
    }

    private fun analyzeInner(
        id: String,
        parentId: String?,
        source: ArtifactSource,
        ctx: AnalysisContext,
        depth: Int,
        maxNestingDepth: Int,
        declaredType: DetectedType? = null,
    ): ArtifactNode {
        if (!ctx.budget.tryEnterArtifact()) {
            return incompleteNode(id, parentId, source, "MAX_ARTIFACT_COUNT")
        }
        try {
            ctx.checkCancelled()
            val detected = detect(source).let {
                val effective = declaredType ?: it.type
                DetectedArtifact(effective, declaredType?.name ?: it.subtype, it.byLayer, it.mismatchFlags)
            }
            val relevant = analyzers.filter { detected.type in it.supported }

            val analyzersUsed = mutableListOf<AnalyzerUse>()
            val findings = mutableListOf<Finding>()
            val indicators = mutableListOf<Indicator>()
            val facts = mutableListOf<Fact>()
            val metadata = mutableListOf<ArtifactMetadataEntry>()
            val parserErrors = mutableListOf<ParserErrorRecord>()
            val limitations = mutableListOf<String>()
            val children = mutableListOf<ArtifactNode>()
            var nodeIncomplete = false

            for (analyzer in relevant) {
                ctx.checkCancelled()
                ctx.budget.tryAddOp()
                analyzersUsed += AnalyzerUse(analyzer.analyzerId, analyzer.analyzerVersion)
                val result = runAnalyzerSafely(analyzer, ctx, id, parentId, source, detected)
                findings += result.findings
                indicators += result.indicators
                facts += result.facts
                metadata += result.metadata
                parserErrors += result.parserErrors
                limitations += result.limitations
                if (result.incomplete) nodeIncomplete = true

                if (depth + 1 <= maxNestingDepth) {
                    for (child in result.parsedChildren) {
                        ctx.checkCancelled()
                        if (!ctx.budget.tryAddEntry()) {
                            nodeIncomplete = true
                            limitations += "archive entry quota reached"
                            break
                        }
                        val childId = UUID.randomUUID().toString()
                        children += analyzeSubtree(
                            id = childId,
                            parentId = id,
                            source = ChildSource(child, source),
                            ctx = ctx,
                            depth = depth + 1,
                            maxNestingDepth = maxNestingDepth,
                            declaredType = child.detectedType,
                        )
                    }
                }
            }

            return ArtifactNode(
                artifactId = id,
                parentId = parentId,
                relation = if (parentId == null) ArtifactRelation.ROOT else ArtifactRelation.CONTAINED,
                originalName = source.name,
                sanitizedName = sanitizeName(source.name),
                claimedMimeType = source.claimedMimeType,
                detectedType = detected.type.name,
                detectedSubtype = detected.subtype,
                sizeBytes = source.sizeBytes,
                sha256 = source.computeSha256(),
                metadata = metadata,
                indicators = indicators,
                findings = findings,
                facts = facts,
                children = children,
                parserErrors = parserErrors,
                incomplete = nodeIncomplete,
                completeness = if (nodeIncomplete) 0.5 else 1.0,
                limitations = limitations,
                analyzers = analyzersUsed,
            )
        } finally {
            ctx.budget.leaveArtifact()
        }
    }

    private fun runAnalyzerSafely(
        analyzer: ArtifactAnalyzer,
        ctx: AnalysisContext,
        id: String,
        parentId: String?,
        source: ArtifactSource,
        detected: DetectedArtifact,
    ): AnalyzerResult {
        return try {
            analyzer.analyze(ctx, SourceArtifactRef(id, parentId, source, detected))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AnalyzerResult(
                analyzerId = analyzer.analyzerId,
                analyzerVersion = analyzer.analyzerVersion,
                incomplete = true,
                parserErrors = listOf(
                    ParserErrorRecord(
                        code = "ANALYZER_CRASH",
                        message = e.javaClass.simpleName,
                        analyzerId = analyzer.analyzerId,
                    )
                ),
            )
        }
    }

    private fun incompleteNode(id: String, parentId: String?, source: ArtifactSource, reason: String): ArtifactNode =
        ArtifactNode(
            artifactId = id,
            parentId = parentId,
            relation = if (parentId == null) ArtifactRelation.ROOT else ArtifactRelation.CONTAINED,
            originalName = source.name,
            sanitizedName = sanitizeName(source.name),
            sizeBytes = source.sizeBytes,
            incomplete = true,
            completeness = 0.0,
            limitations = listOf(reason),
            parserErrors = listOf(ParserErrorRecord(code = reason, message = reason)),
        )

    private fun sanitizeName(name: String): String =
        name.replace(Regex("[\\p{Cntrl}\u200e\u200f\u202a\u202b\u202c\u202d\u202e\u2066\u2067\u2068\u2069]"), "\uFFFD")
}

/** Wraps a parsed child as an [ArtifactSource] backed by its parent's range reads. */
private class ChildSource(
    private val child: ParsedChild,
    private val parent: ArtifactSource,
) : ArtifactSource {
    override val name: String get() = child.name
    override val sizeBytes: Long get() = child.sizeBytes ?: parent.sizeBytes
    override val claimedMimeType: String? get() = null
    override val advisoryExtension: String?
        get() = child.name.substringAfterLast('.', "").ifEmpty { null }

    override fun readNBytes(count: Int): ByteArray = parent.readRange(0, count)
    override fun readRange(offset: Long, count: Int): ByteArray = parent.readRange(offset, count)
    override fun computeSha256(): String? = null
}

private class SourceArtifactRef(
    override val artifactId: String,
    override val parentId: String?,
    private val source: ArtifactSource,
    private val detected: DetectedArtifact,
) : ArtifactRef {
    override val name: String get() = source.name
    override val detectedType: DetectedType get() = detected.type
    override val detectedSubtype: String? get() = detected.subtype
    override val sizeBytes: Long get() = source.sizeBytes

    fun readNBytes(count: Int): ByteArray = source.readNBytes(count)
    fun readRange(offset: Long, count: Int): ByteArray = source.readRange(offset, count)
    fun computeSha256(): String? = source.computeSha256()
    override fun toString(): String = "ArtifactRef($artifactId, $name)"
}
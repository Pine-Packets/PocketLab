package com.pineandpackets.pocketlab.engine.orchestrator

import com.pineandpackets.pocketlab.core.model.DetectedType
import com.pineandpackets.pocketlab.engine.api.AnalysisCancellation
import com.pineandpackets.pocketlab.engine.api.AnalysisContext
import com.pineandpackets.pocketlab.engine.api.AnalyzerResult
import com.pineandpackets.pocketlab.engine.api.ArtifactAnalyzer
import com.pineandpackets.pocketlab.engine.api.ArtifactRef
import com.pineandpackets.pocketlab.engine.api.CaseBudget
import com.pineandpackets.pocketlab.engine.api.DetectionLayer
import com.pineandpackets.pocketlab.engine.api.ParsedChild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisDispatcherTest {

    private fun source(
        name: String = "sample.bin",
        bytes: ByteArray = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3),
    ): ArtifactSource = object : ArtifactSource {
        override val name: String = name
        override val sizeBytes: Long = bytes.size.toLong()
        override val claimedMimeType: String? = null
        override val advisoryExtension: String? = name.substringAfterLast('.', "").ifEmpty { null }
        override fun readNBytes(count: Int): ByteArray = bytes.copyOf(count.coerceAtMost(bytes.size))
        override fun readRange(offset: Long, count: Int): ByteArray {
            val start = offset.coerceIn(0, bytes.size.toLong()).toInt()
            return bytes.copyOfRange(start, (start + count).coerceAtMost(bytes.size))
        }
        override fun computeSha256(): String? = null
    }

    private fun detectAs(type: DetectedType, layer: DetectionLayer = DetectionLayer.CONTENT_SIGNATURE): (ArtifactSource) -> DetectedArtifact =
        { DetectedArtifact(type = type, byLayer = layer) }

    @Test
    fun `routes to analyzers supporting the detected type`() {
        val seen = mutableListOf<String>()
        val zipAnalyzer = FakeAnalyzer("zip.analyzer", setOf(DetectedType.ZIP)) { seen += "zip" }
        val dexAnalyzer = FakeAnalyzer("dex.analyzer", setOf(DetectedType.DEX)) { seen += "dex" }
        val dispatcher = AnalysisDispatcher(detectAs(DetectedType.ZIP), listOf(zipAnalyzer, dexAnalyzer))

        val outcome = dispatcher.analyzeRoot(
            source = source(),
            budget = budget(),
            cancellation = AnalysisCancellation(),
        )

        assertEquals(listOf("zip"), seen)
        assertEquals(DetectedType.ZIP.name, outcome.root.detectedType)
        assertEquals(listOf("zip.analyzer"), outcome.root.analyzers.map { it.analyzerId })
        assertEquals(2, outcome.analyzersUsed.size)
    }

    @Test
    fun `fans out to multiple analyzers for polyglot artifacts`() {
        val seen = mutableListOf<String>()
        val a1 = FakeAnalyzer("poly.a", setOf(DetectedType.ZIP)) { seen += "a" }
        val a2 = FakeAnalyzer("poly.b", setOf(DetectedType.ZIP)) { seen += "b" }
        val dispatcher = AnalysisDispatcher(detectAs(DetectedType.ZIP), listOf(a1, a2))

        dispatcher.analyzeRoot(source(), budget(), AnalysisCancellation())

        assertEquals(listOf("a", "b"), seen)
    }

    @Test
    fun `recursively dispatches parsed children within nesting budget`() {
        val childSeen = mutableListOf<String>()
        val containerAnalyzer = object : FakeAnalyzer("container", setOf(DetectedType.ZIP)) {
            override fun analyze(context: AnalysisContext, artifact: ArtifactRef): AnalyzerResult {
                return AnalyzerResult(
                    analyzerId = analyzerId,
                    analyzerVersion = analyzerVersion,
                    parsedChildren = listOf(ParsedChild("child.dex", DetectedType.DEX, null, 4L)),
                )
            }
        }
        val dexAnalyzer = object : FakeAnalyzer("dex.analyzer", setOf(DetectedType.DEX)) {
            override fun analyze(context: AnalysisContext, artifact: ArtifactRef): AnalyzerResult {
                childSeen += artifact.name
                return AnalyzerResult(analyzerId, analyzerVersion)
            }
        }
        val dispatcher = AnalysisDispatcher(detectAs(DetectedType.ZIP), listOf(containerAnalyzer, dexAnalyzer))

        val outcome = dispatcher.analyzeRoot(source(), budget(), AnalysisCancellation())

        assertEquals(listOf("child.dex"), childSeen)
        assertEquals(1, outcome.root.children.size)
        assertEquals(DetectedType.DEX.name, outcome.root.children.first().detectedType)
        assertNotNull(outcome.root.children.first().parentId)
    }

    @Test
    fun `does not descend beyond max nesting depth`() {
        val deep = mutableListOf<String>()
        val zipper = object : FakeAnalyzer("zip", setOf(DetectedType.ZIP)) {
            override fun analyze(context: AnalysisContext, artifact: ArtifactRef): AnalyzerResult =
                AnalyzerResult(analyzerId, analyzerVersion, parsedChildren = listOf(ParsedChild("nested.zip", DetectedType.ZIP, null, 1L)))
        }
        val recorder = object : FakeAnalyzer("recorder", setOf(DetectedType.ZIP)) {
            override fun analyze(context: AnalysisContext, artifact: ArtifactRef): AnalyzerResult {
                deep += artifact.name
                return AnalyzerResult(analyzerId, analyzerVersion)
            }
        }
        val dispatcher = AnalysisDispatcher(detectAs(DetectedType.ZIP), listOf(zipper, recorder))

        dispatcher.analyzeRoot(source("root.zip"), budget(), AnalysisCancellation(), maxNestingDepth = 1)

        assertEquals(setOf("root.zip", "nested.zip"), deep.toSet())
    }

    @Test
    fun `analyzer crash produces parser error and marks node incomplete not clean`() {
        val crashing = object : FakeAnalyzer("crashy", setOf(DetectedType.ZIP)) {
            override fun analyze(context: AnalysisContext, artifact: ArtifactRef): AnalyzerResult {
                throw IllegalStateException("boom")
            }
        }
        val dispatcher = AnalysisDispatcher(detectAs(DetectedType.ZIP), listOf(crashing))

        val outcome = dispatcher.analyzeRoot(source(), budget(), AnalysisCancellation())

        assertTrue(outcome.root.incomplete)
        assertTrue(outcome.root.completeness < 1.0)
        assertEquals(1, outcome.root.parserErrors.size)
        assertEquals("ANALYZER_CRASH", outcome.root.parserErrors.first().code)
    }

    @Test
    fun `cancellation captures user cancel and returns partial tree`() {
        val slow = object : FakeAnalyzer("slow", setOf(DetectedType.ZIP)) {
            override fun analyze(context: AnalysisContext, artifact: ArtifactRef): AnalyzerResult {
                context.cancellation.cancel("user")
                context.checkCancelled()
                return AnalyzerResult(analyzerId, analyzerVersion)
            }
        }
        val dispatcher = AnalysisDispatcher(detectAs(DetectedType.ZIP), listOf(slow))

        val outcome = dispatcher.analyzeRoot(source(), budget(), AnalysisCancellation())

        assertTrue(outcome.cancelled)
        assertFalse(outcome.timedOut)
        assertTrue(outcome.root.incomplete)
    }

    @Test
    fun `deadline past produces timeout outcome`() {
        val fast = object : FakeAnalyzer("fast", setOf(DetectedType.ZIP)) {
            override fun analyze(context: AnalysisContext, artifact: ArtifactRef): AnalyzerResult {
                Thread.sleep(20)
                context.checkCancelled()
                return AnalyzerResult(analyzerId, analyzerVersion)
            }
        }
        val dispatcher = AnalysisDispatcher(detectAs(DetectedType.ZIP), listOf(fast))

        val outcome = dispatcher.analyzeRoot(
            source = source(),
            budget = budget(),
            cancellation = AnalysisCancellation(),
            deadlineEpochMs = System.currentTimeMillis() + 5,
        )

        assertTrue(outcome.timedOut)
        assertTrue(outcome.root.incomplete)
        assertTrue(outcome.root.limitations.any { it.contains("TIMEOUT") })
    }

    @Test
    fun `content signature detection reports extension mismatch`() {
        val detected = LayeredTypeDetector.detect(source("evil.pdf", byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertEquals(DetectedType.ZIP, detected.type)
        assertEquals(DetectionLayer.CONTENT_SIGNATURE, detected.byLayer)
        assertTrue(detected.mismatchFlags.contains("MAGIC_EXTENSION_MISMATCH"))
    }

    @Test
    fun `unknown content falls back to extension layer`() {
        val detected = LayeredTypeDetector.detect(source("sample.dex", byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
        assertEquals(DetectedType.DEX, detected.type)
        assertEquals(DetectionLayer.ADVISORY_EXTENSION, detected.byLayer)
    }

    private fun budget() = CaseBudget(
        maxBytesRead = 1024,
        maxExpandedBytes = 4096,
        maxArtifactCount = 50,
        maxArchiveEntries = 100,
        maxRecursionDepth = 4,
        maxFindings = 100,
        maxIndicators = 100,
        maxFacts = 100,
        maxOps = 1000,
    )

    open class FakeAnalyzer(
        override val analyzerId: String,
        override val supported: Set<DetectedType>,
        private val onAnalyze: (() -> Unit)? = null,
    ) : ArtifactAnalyzer {
        override val analyzerVersion: String = "1.0.0"
        override val capabilities: List<String> = listOf("test")
        override fun analyze(context: AnalysisContext, artifact: ArtifactRef): AnalyzerResult {
            onAnalyze?.invoke()
            return AnalyzerResult(analyzerId, analyzerVersion)
        }
    }
}

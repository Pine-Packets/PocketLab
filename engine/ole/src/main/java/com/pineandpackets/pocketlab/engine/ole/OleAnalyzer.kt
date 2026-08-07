package com.pineandpackets.pocketlab.engine.ole

import com.pineandpackets.pocketlab.core.model.ArtifactMetadataEntry
import com.pineandpackets.pocketlab.core.model.Confidence
import com.pineandpackets.pocketlab.core.model.DetectedType
import com.pineandpackets.pocketlab.core.model.Evidence
import com.pineandpackets.pocketlab.core.model.EvidenceType
import com.pineandpackets.pocketlab.core.model.Fact
import com.pineandpackets.pocketlab.core.model.Finding
import com.pineandpackets.pocketlab.core.model.FrameworkMapping
import com.pineandpackets.pocketlab.core.model.ParserErrorRecord
import com.pineandpackets.pocketlab.core.model.Severity
import com.pineandpackets.pocketlab.engine.api.AnalysisContext
import com.pineandpackets.pocketlab.engine.api.AnalyzerResult
import com.pineandpackets.pocketlab.engine.api.ArtifactAnalyzer
import com.pineandpackets.pocketlab.engine.api.ArtifactRef
import java.util.UUID

/**
 * Bounded, read-only legacy OLE/CFB analyzer (Stage 3 of the format-expansion
 * program).
 *
 * Safety: never executes, extracts, or opens embedded objects; never parses a
 * "binary" stream as active content; all directory/chain reads are bounded and
 * cancellation-aware; a truncated or malformed container is marked incomplete,
 * never clean.
 */
class OleAnalyzer : ArtifactAnalyzer {

    override val analyzerId: String = ANALYZER_ID
    override val analyzerVersion: String = ANALYZER_VERSION
    override val supported: Set<DetectedType> = setOf(DetectedType.OLE)
    override val capabilities: List<String> = listOf(
        "cfb-header-validation",
        "cfb-directory-inventory",
        "macro-vba-detection",
        "embedded-ole-object-detection",
        "suspicious-stream-detection",
        "indicator-extraction",
        "structural-abnormality-detection",
    )

    override fun analyze(context: AnalysisContext, artifact: ArtifactRef): AnalyzerResult {
        context.checkCancelled()

        if (artifact.detectedType !in supported) {
            return AnalyzerResult(
                analyzerId = analyzerId,
                analyzerVersion = analyzerVersion,
                findings = emptyList(),
                indicators = emptyList(),
                facts = emptyList(),
                metadata = emptyList(),
                parsedChildren = emptyList(),
                incomplete = true,
                parserErrors = listOf(
                    ParserErrorRecord(
                        code = "OLE_TYPE_MISMATCH",
                        message = "Artifact type ${artifact.detectedType} is not supported by this analyzer.",
                    ),
                ),
                limitations = emptyList(),
            )
        }

        val report = OleScanner(artifact, context).scan()

        val facts = mutableListOf<Fact>()
        val findings = mutableListOf<Finding>()
        val metadata = mutableListOf<ArtifactMetadataEntry>()

        facts += Fact(UUID.randomUUID().toString(), "OLE_MAJOR_VERSION", report.majorVersion.toString(), artifact.name)
        facts += Fact(UUID.randomUUID().toString(), "OLE_STREAM_COUNT", report.streamCount.toString(), artifact.name)
        facts += Fact(UUID.randomUUID().toString(), "OLE_STORAGE_COUNT", report.storageCount.toString(), artifact.name)
        facts += Fact(UUID.randomUUID().toString(), "OLE_SECTOR_SIZE", report.sectorSize.toString(), artifact.name)
        for (ab in report.abnormalities) {
            facts += Fact(UUID.randomUUID().toString(), "OLE_ABNORMALITY", ab, artifact.name)
        }

        if (report.macroStreamsPresent) {
            facts += Fact(UUID.randomUUID().toString(), "OLE_HAS_VBA_MACRO", "1", artifact.name)
            findings += macroFinding(artifact, report.macroStreamNames)
        }
        if (report.embeddedOlePresent) {
            facts += Fact(UUID.randomUUID().toString(), "OLE_HAS_EMBEDDED_OBJECTS", "1", artifact.name)
            findings += embeddedOleFinding(artifact, report.embeddedOleNames)
        }
        if (report.suspiciousStreamNames.isNotEmpty()) {
            facts += Fact(
                UUID.randomUUID().toString(),
                "OLE_SUSPICIOUS_STREAM_COUNT",
                report.suspiciousStreamNames.size.toString(),
                artifact.name,
            )
        }
        if (report.indicators.isNotEmpty()) {
            facts += Fact(
                UUID.randomUUID().toString(),
                "OLE_NETWORK_INDICATORS",
                report.indicators.size.toString(),
                artifact.name,
            )
            findings += remoteIndicatorFinding(artifact)
        }

        metadata += ArtifactMetadataEntry("cfbMajorVersion", report.majorVersion.toString())
        metadata += ArtifactMetadataEntry("cfbSectorSize", report.sectorSize.toString())
        metadata += ArtifactMetadataEntry("streamCount", report.streamCount.toString())
        metadata += ArtifactMetadataEntry("storageCount", report.storageCount.toString())

        return AnalyzerResult(
            analyzerId = analyzerId,
            analyzerVersion = analyzerVersion,
            findings = findings,
            indicators = report.indicators,
            facts = facts,
            metadata = metadata,
            parsedChildren = emptyList(),
            incomplete = report.scanTruncated || report.parserErrors.isNotEmpty(),
            parserErrors = report.parserErrors,
            limitations = if (report.parserErrors.isNotEmpty()) {
                listOf(
                    "OLE/CFB structure could not be fully parsed; results may be incomplete. " +
                        "Embedded objects and macro bodies are never extracted or opened.",
                )
            } else {
                listOf(
                    "Only the CFB header, directory inventory, stream names and a bounded set of " +
                        "small streams are examined. Mini-stream and deep property-set (e.g. " +
                        "SummaryInformation) decoding is not performed, so metadata and macro bodies " +
                        "may not be fully enumerated.",
                )
            },
        )
    }

    private fun macroFinding(artifact: ArtifactRef, macroStreamNames: List<String>): Finding = Finding(
        id = UUID.randomUUID().toString(),
        ruleId = "OLE-MACRO-001",
        title = "Compound document contains a VBA macro project",
        category = "macro_content",
        severity = Severity.HIGH,
        confidence = Confidence.HIGH,
        simpleExplanation = "The document contains macro-related streams, which can run code when macros are enabled.",
        analystExplanation = "The CFB directory contains VBA project streams (e.g. VBA, _VBA_PROJECT, Project, Module1). Legacy macro documents are a very common malware delivery vehicle. The macro code is never extracted, executed, or opened by this analyzer.",
        evidence = listOf(
            Evidence(
                EvidenceType.CONFIGURATION_VALUE,
                fileId = artifact.artifactId,
                excerpt = "macro stream(s): ${macroStreamNames.take(6).joinToString(", ")}",
            ),
        ),
        limitations = listOf(
            "Macro bodies are not parsed or executed.",
            "Macro presence is common in legitimate template and automation documents.",
        ),
        recommendations = listOf(
            "Do not enable content unless the document source is trusted.",
            "Extract and review the VBA project in a controlled environment if triage continues.",
        ),
        mappings = listOf(FrameworkMapping("MITRE_ATTACK", "T1204.002")),
    )

    private fun embeddedOleFinding(artifact: ArtifactRef, names: List<String>): Finding = Finding(
        id = UUID.randomUUID().toString(),
        ruleId = "OLE-EMBEDDED-001",
        title = "Compound document contains embedded OLE objects",
        category = "embedded_object",
        severity = Severity.MEDIUM,
        confidence = Confidence.MEDIUM,
        simpleExplanation = "The document embeds OLE objects, which may themselves be dangerous documents or executables.",
        analystExplanation = "The CFB directory references embedded-object streams (e.g. ObjectPool, Embedding, OLE10Native, Package). Embedded OLE objects can hide payloads launched when the user interacts with them. The embedded bytes are never extracted or opened.",
        evidence = listOf(
            Evidence(
                EvidenceType.CONFIGURATION_VALUE,
                fileId = artifact.artifactId,
                excerpt = "embedded object stream(s): ${names.take(6).joinToString(", ")}",
            ),
        ),
        limitations = listOf("Embedded OLE payloads are not extracted or opened."),
        references = listOf("https://attack.mitre.org/techniques/T1204/"),
    )

    private fun remoteIndicatorFinding(artifact: ArtifactRef): Finding = Finding(
        id = UUID.randomUUID().toString(),
        ruleId = "OLE-REMOTE-001",
        title = "Compound document contains network indicators",
        category = "external_connection",
        severity = Severity.MEDIUM,
        confidence = Confidence.MEDIUM,
        simpleExplanation = "Network addresses (URLs, domains, IPs, or emails) were found in the document's streams.",
        analystExplanation = "URL/domain/IP/email indicators were extracted from bounded reads of small streams. Legacy documents can carry external-link, autolink or embedded hostname data used to reach infrastructure. No network contact is initiated.",
        evidence = listOf(
            Evidence(
                EvidenceType.IOC_MATCH,
                fileId = artifact.artifactId,
                excerpt = "network indicator(s) referenced in stream content",
            ),
        ),
        limitations = listOf("Indicators are stored for triage only; no contact is made."),
        recommendations = listOf("Review the listed indicators in a closed environment."),
        mappings = listOf(FrameworkMapping("MITRE_ATTACK", "T1557")),
    )

    companion object {
        const val ANALYZER_ID: String = "ole.analyzer"
        const val ANALYZER_VERSION: String = "1.0.0"
    }
}
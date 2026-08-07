package com.pineandpackets.pocketlab.engine.ooxml

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
 * Bounded, read-only OOXML analyzer (Stage 2 of the format-expansion program).
 *
 * Safety: never executes macros or scripts, never extracts embedded binary
 * parts, never contacts external targets, never renders parts as active
 * content. Reads are bounded by the scanner's per-part and total caps, and
 * cooperative cancellation is honored on every loop iteration.
 */
class OoxmlAnalyzer : ArtifactAnalyzer {

    override val analyzerId: String = ANALYZER_ID
    override val analyzerVersion: String = ANALYZER_VERSION
    override val supported: Set<DetectedType> = setOf(DetectedType.OOXML)
    override val capabilities: List<String> = listOf(
        "package-part-inventory",
        "macro-vba-detection",
        "activex-detection",
        "embedded-ole-detection",
        "external-link-detection",
        "hyperlink-extraction",
        "remote-template-detection",
        "custom-xml-detection",
        "signature-part-detection",
        "indicator-extraction",
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
                        code = "OOXML_TYPE_MISMATCH",
                        message = "Artifact type ${artifact.detectedType} is not supported by this analyzer.",
                    ),
                ),
                limitations = emptyList(),
            )
        }

        val report = OoxmlScanner(artifact, context).scan()

        val facts = mutableListOf<Fact>()
        val findings = mutableListOf<Finding>()
        val metadata = mutableListOf<ArtifactMetadataEntry>()

        facts += Fact(UUID.randomUUID().toString(), "OOXML_PART_COUNT", report.partCount.toString(), artifact.name)
        metadata += ArtifactMetadataEntry("partCount", report.partCount.toString())
        if (report.contentTypesPresent) {
            facts += Fact(UUID.randomUUID().toString(), "OOXML_CONTENT_TYPES", "1", artifact.name)
        }
        for (ab in report.abnormalities) {
            facts += Fact(UUID.randomUUID().toString(), "OOXML_ABNORMALITY", ab, artifact.name)
        }

        if (report.macroProjectPresent) {
            facts += Fact(UUID.randomUUID().toString(), "OOXML_HAS_VBA", "1", artifact.name)
            findings += macroFinding(artifact)
        }
        if (report.activeXPresent) {
            facts += Fact(UUID.randomUUID().toString(), "OOXML_HAS_ACTIVEX", "1", artifact.name)
            findings += activeXFinding(artifact)
        }
        if (report.embeddedOlePresent) {
            facts += Fact(UUID.randomUUID().toString(), "OOXML_HAS_EMBEDDED_OLE", "1", artifact.name)
            findings += embeddedOleFinding(artifact)
        }
        if (report.externalLinksPresent) {
            facts += Fact(UUID.randomUUID().toString(), "OOXML_HAS_EXTERNAL_LINKS", "1", artifact.name)
            findings += externalLinkFinding(artifact)
        }
        if (report.externalTargets.isNotEmpty()) {
            facts += Fact(UUID.randomUUID().toString(), "OOXML_EXTERNAL_TARGETS", report.externalTargets.size.toString(), artifact.name)
        }
        if (report.hyperlinkTargets.isNotEmpty()) {
            facts += Fact(UUID.randomUUID().toString(), "OOXML_HYPERLINK_TARGETS", report.hyperlinkTargets.size.toString(), artifact.name)
        }
        if (report.customXmlPresent) {
            facts += Fact(UUID.randomUUID().toString(), "OOXML_HAS_CUSTOM_XML", "1", artifact.name)
        }
        if (report.signaturesPresent) {
            facts += Fact(UUID.randomUUID().toString(), "OOXML_HAS_SIGNATURES", "1", artifact.name)
        }
        metadata += ArtifactMetadataEntry("externalTargetCount", report.externalTargets.size.toString())
        metadata += ArtifactMetadataEntry("hyperlinkTargetCount", report.hyperlinkTargets.size.toString())

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
            limitations = if (report.scanTruncated) {
                listOf(
                    "OOXML scan hit an analysis limit (entry count, part size, or expanded size); " +
                        "results may be incomplete. Embedded parts are never extracted to disk.",
                )
            } else {
                emptyList()
            },
        )
    }

    private fun macroFinding(artifact: ArtifactRef): Finding = Finding(
        id = UUID.randomUUID().toString(),
        ruleId = "OOXML-MACRO-001",
        title = "Document package contains a VBA macro project",
        category = "macro_content",
        severity = Severity.HIGH,
        confidence = Confidence.HIGH,
        simpleExplanation = "The document contains embedded macros, which can run code when the document is opened or enabled.",
        analystExplanation = "A vbaProject.bin part (or vbaData.xml) was observed. Macro-bearing documents are a common malware delivery vehicle. The macro binary is not extracted or executed by this analyzer, and presence alone does not prove the macro is malicious.",
        evidence = listOf(
            Evidence(
                EvidenceType.CONFIGURATION_VALUE,
                fileId = artifact.artifactId,
                excerpt = "vbaProject.bin / vbaData.xml part present",
            ),
        ),
        limitations = listOf(
            "The VBA project is not parsed or executed.",
            "Macro presence is common in legitimate template and automation documents.",
        ),
        recommendations = listOf(
            "Do not enable content unless the document source is trusted.",
            "Extract and review the VBA project in a controlled environment if triage continues.",
        ),
        mappings = listOf(FrameworkMapping("MITRE_ATTACK", "T1204.002")),
    )

    private fun activeXFinding(artifact: ArtifactRef): Finding = Finding(
        id = UUID.randomUUID().toString(),
        ruleId = "OOXML-ACTIVEX-001",
        title = "Document package contains ActiveX control parts",
        category = "activex_control",
        severity = Severity.MEDIUM,
        confidence = Confidence.MEDIUM,
        simpleExplanation = "The document contains ActiveX control components, which are executable objects loaded by some Office hosts.",
        analystExplanation = "activeX part names were observed in the package. ActiveX controls can carry binary payloads; they are loaded by the host application, never by this analyzer.",
        evidence = listOf(
            Evidence(
                EvidenceType.CONFIGURATION_VALUE,
                fileId = artifact.artifactId,
                excerpt = "activeX part present",
            ),
        ),
        limitations = listOf("The control binary is not loaded, extracted, or executed."),
        recommendations = listOf("Inspect the ActiveX part in an isolated environment if the source is untrusted."),
    )

    private fun embeddedOleFinding(artifact: ArtifactRef): Finding = Finding(
        id = UUID.randomUUID().toString(),
        ruleId = "OOXML-EMBEDDED-001",
        title = "Document package embeds OLE objects",
        category = "embedded_object",
        severity = Severity.MEDIUM,
        confidence = Confidence.MEDIUM,
        simpleExplanation = "The document embeds OLE objects, which may themselves be dangerous documents or executables.",
        analystExplanation = "embeddings/ or oleObject parts were observed. Embedded OLE objects can hide payloads that launch when the user interacts with them. The embedded bytes are never extracted or executed.",
        evidence = listOf(
            Evidence(
                EvidenceType.CONFIGURATION_VALUE,
                fileId = artifact.artifactId,
                excerpt = "embeddings/oleObject part present",
            ),
        ),
        limitations = listOf("Embedded OLE payloads are not extracted or opened."),
        references = listOf("https://attack.mitre.org/techniques/T1204/"),
    )

    private fun externalLinkFinding(artifact: ArtifactRef): Finding = Finding(
        id = UUID.randomUUID().toString(),
        ruleId = "OOXML-EXTLINK-001",
        title = "Document package contains external data links",
        category = "external_connection",
        severity = Severity.MEDIUM,
        confidence = Confidence.MEDIUM,
        simpleExplanation = "The document references external data connections, which could contact remote servers when refreshed.",
        analystExplanation = "xl/externalLinks/ parts or external relationships were observed. Spreadsheet external links and connections can contact network endpoints on open or refresh; they are a known phishing/credential-theft vector. No network contact is initiated by this analyzer.",
        evidence = listOf(
            Evidence(
                EvidenceType.CONFIGURATION_VALUE,
                fileId = artifact.artifactId,
                excerpt = "externalLinks / external relationship present",
            ),
        ),
        limitations = listOf("No network contact is made and none is initiated."),
        recommendations = listOf("Review or remove external connections before sharing the file."),
        mappings = listOf(FrameworkMapping("MITRE_ATTACK", "T1557")),
    )

    companion object {
        const val ANALYZER_ID: String = "ooxml.analyzer"
        const val ANALYZER_VERSION: String = "1.0.0"
    }
}

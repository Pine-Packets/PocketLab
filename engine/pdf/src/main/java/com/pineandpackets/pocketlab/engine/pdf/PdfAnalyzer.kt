package com.pineandpackets.pocketlab.engine.pdf

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
 * Bounded, read-only PDF analyzer (Stage 1 of the format-expansion program).
 *
 * Safety: never executes JavaScript, never follows a URI, never extracts or
 * opens embedded files, never attaches external content. Reads are bounded by
 * [MAX_SCAN_BYTES]; larger files are scanned partially and marked incomplete.
 * Honors cooperative cancellation via [AnalysisContext.checkCancelled].
 */
class PdfAnalyzer : ArtifactAnalyzer {

    override val analyzerId: String = ANALYZER_ID
    override val analyzerVersion: String = ANALYZER_VERSION
    override val supported: Set<DetectedType> = setOf(DetectedType.PDF)
    override val capabilities: List<String> = listOf(
        "header-version",
        "object-dictionary-scan",
        "javascript-detection",
        "action-detection",
        "form-xfa-detection",
        "embedded-file-detection",
        "encryption-detection",
        "signature-detection",
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
                    ParserErrorRecord(code = "PDF_TYPE_MISMATCH", message = "Artifact type ${artifact.detectedType} is not supported by this analyzer."),
                ),
                limitations = emptyList(),
            )
        }

        val report = PdfScanner(artifact, context).scan()

        val facts = mutableListOf<Fact>()
        val findings = mutableListOf<Finding>()
        val metadata = mutableListOf<ArtifactMetadataEntry>()

        facts += Fact(UUID.randomUUID().toString(), "PDF_OBJECT_COUNT", report.objectCount.toString(), artifact.name)
        facts += Fact(UUID.randomUUID().toString(), "PDF_STREAM_COUNT", report.streamCount.toString(), artifact.name)
        metadata += ArtifactMetadataEntry("objectCount", report.objectCount.toString())
        metadata += ArtifactMetadataEntry("streamCount", report.streamCount.toString())

        if (report.header != null) {
            facts += Fact(UUID.randomUUID().toString(), "PDF_HEADER", report.header, artifact.name)
            metadata += ArtifactMetadataEntry("header", report.header)
        } else {
            facts += Fact(UUID.randomUUID().toString(), "PDF_BAD_HEADER", "1", artifact.name)
        }

        for (ab in report.abnormalities) {
            facts += Fact(UUID.randomUUID().toString(), "PDF_ABNORMALITY", ab, artifact.name)
        }

        val acts = report.actions
        if (acts.hasJavaScript) {
            facts += Fact(UUID.randomUUID().toString(), "PDF_HAS_JAVASCRIPT", "1", artifact.name)
            findings += javascriptFinding(artifact)
        }
        if (acts.hasOpenAction) {
            facts += Fact(UUID.randomUUID().toString(), "PDF_HAS_OPENACTION", "1", artifact.name)
            findings += openActionFinding(artifact)
        }
        if (acts.hasLaunchAction) {
            facts += Fact(UUID.randomUUID().toString(), "PDF_HAS_LAUNCH_ACTION", "1", artifact.name)
            findings += launchActionFinding(artifact)
        }
        metadata += ArtifactMetadataEntry("actionKeyCount", acts.actionKeyCount.toString())
        metadata += ArtifactMetadataEntry("launchTargetCount", acts.launchTargetCount.toString())

        val feat = report.features
        if (feat.hasAcroForm) facts += Fact(UUID.randomUUID().toString(), "PDF_HAS_ACROFORM", "1", artifact.name)
        if (feat.hasXfa) {
            facts += Fact(UUID.randomUUID().toString(), "PDF_HAS_XFA", "1", artifact.name)
            findings += xfaFinding(artifact)
        }
        if (feat.hasEmbeddedFiles) {
            facts += Fact(UUID.randomUUID().toString(), "PDF_HAS_EMBEDDED_FILES", "1", artifact.name)
            findings += embeddedFilesFinding(artifact)
        }
        if (feat.hasAnnotations) facts += Fact(UUID.randomUUID().toString(), "PDF_HAS_ANNOTATIONS", "1", artifact.name)
        if (feat.hasRichMedia) facts += Fact(UUID.randomUUID().toString(), "PDF_HAS_RICHMEDIA", "1", artifact.name)
        if (feat.hasRemoteResources) {
            facts += Fact(UUID.randomUUID().toString(), "PDF_HAS_REMOTE_RESOURCES", "1", artifact.name)
            findings += remoteResourceFinding(artifact)
        }
        metadata += ArtifactMetadataEntry("imageCount", feat.imageCount.toString())

        if (report.encrypted) facts += Fact(UUID.randomUUID().toString(), "PDF_ENCRYPTED", "1", artifact.name)
        if (report.metadataPresent) facts += Fact(UUID.randomUUID().toString(), "PDF_HAS_METADATA", "1", artifact.name)
        if (report.signatureDetected) facts += Fact(UUID.randomUUID().toString(), "PDF_HAS_SIGNATURE", "1", artifact.name)

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
                listOf("PDF scan exceeded the analysis limit of $MAX_SCAN_BYTES bytes; results may be incomplete.")
            } else {
                emptyList()
            },
        )
    }

    private fun javascriptFinding(artifact: ArtifactRef): Finding = Finding(
        id = UUID.randomUUID().toString(),
        ruleId = "PDF-JS-001",
        title = "PDF contains embedded JavaScript",
        category = "embedded_script",
        severity = Severity.HIGH,
        confidence = Confidence.MEDIUM,
        simpleExplanation = "The PDF contains JavaScript that a reader could execute while opening or interacting with the document.",
        analystExplanation = "A /JavaScript or /JS entry, or a script trigger (OpenAction, additional-action /AA, or form event) was observed. Embedded JavaScript in PDFs is commonly associated with exploit attempts and credential phishing. Static inspection cannot prove the script executes or is malicious.",
        evidence = listOf(Evidence(EvidenceType.CONFIGURATION_VALUE, fileId = artifact.artifactId, excerpt = "/JavaScript or /JS token present")),
        limitations = listOf(
            "Static detection cannot prove the script executes or is malicious.",
            "The script body is not decoded, evaluated, or executed.",
        ),
        recommendations = listOf(
            "Treat the file as suspicious; open only in an isolated, sandboxed environment.",
            "Extract and review the script body statically if further triage is needed.",
        ),
        mappings = listOf(FrameworkMapping("MITRE_ATTACK_MOBILE", "T1521"), FrameworkMapping("MITRE_ATTACK", "T1204.002")),
    )

    private fun openActionFinding(artifact: ArtifactRef): Finding = Finding(
        id = UUID.randomUUID().toString(),
        ruleId = "PDF-OPENACTION-001",
        title = "PDF specifies an automatic OpenAction",
        category = "auto_trigger",
        severity = Severity.MEDIUM,
        confidence = Confidence.MEDIUM,
        simpleExplanation = "The PDF defines an action that runs automatically when the document is opened.",
        analystExplanation = "An /OpenAction entry was observed, meaning a reader will perform the referenced action on open. This is a common delivery vector for PDF malware that wants code or a URI handled without user interaction.",
        evidence = listOf(Evidence(EvidenceType.CONFIGURATION_VALUE, fileId = artifact.artifactId, excerpt = "/OpenAction token present")),
        limitations = listOf("Static analysis cannot confirm what the referenced target does."),
        recommendations = listOf("Verify the OpenAction target before opening in a standard reader."),
    )

    private fun launchActionFinding(artifact: ArtifactRef): Finding = Finding(
        id = UUID.randomUUID().toString(),
        ruleId = "PDF-LAUNCH-001",
        title = "PDF contains a Launch action",
        category = "launch_action",
        severity = Severity.HIGH,
        confidence = Confidence.MEDIUM,
        simpleExplanation = "The PDF defines a Launch action, which asks a reader to open a separate application or file.",
        analystExplanation = "A /Launch action was observed. Launch actions in PDF are frequently abused to open executables or companion files from an embedded stream or over the network. Static analysis does not run the target.",
        evidence = listOf(Evidence(EvidenceType.CONFIGURATION_VALUE, excerpt = "/Launch token present")),
        limitations = listOf("Static analysis cannot confirm the referenced target or whether it executes."),
        recommendations = listOf("Do not open the target referenced by the Launch action."),
        mappings = listOf(FrameworkMapping("MITRE_ATTACK", "T1204.002")),
    )

    private fun xfaFinding(artifact: ArtifactRef): Finding = Finding(
        id = UUID.randomUUID().toString(),
        ruleId = "PDF-XFA-001",
        title = "PDF contains an XFA form",
        category = "form_scripting",
        severity = Severity.MEDIUM,
        confidence = Confidence.LOW,
        simpleExplanation = "The PDF uses an XFA form, which can include scripting.",
        analystExplanation = "An /XFA entry was observed. XFA forms are XML (and optionally script) fragments that some readers process at render time and can be used for phishing or logic abuse. Static analysis does not process the XFA.",
        evidence = listOf(Evidence(EvidenceType.CONFIGURATION_VALUE, excerpt = "/XFA token present")),
        limitations = listOf("The XFA payload is not parsed; presence alone is not proof of malice."),
    )

    private fun embeddedFilesFinding(artifact: ArtifactRef): Finding = Finding(
        id = UUID.randomUUID().toString(),
        ruleId = "PDF-EMBEDDED-001",
        title = "PDF embeds one or more files",
        category = "embedded_file",
        severity = Severity.MEDIUM,
        confidence = Confidence.HIGH,
        simpleExplanation = "The PDF carries an embedded attachment, which may itself be a dangerous document or executable.",
        analystExplanation = "An /EmbeddedFiles or /Filespec /FileAttachment entry was observed. Embedded files are frequently the payload in PDF-borne attacks. The embedded bytes are never extracted or opened by this analyzer.",
        evidence = listOf(Evidence(EvidenceType.CONFIGURATION_VALUE, excerpt = "/EmbeddedFiles or /Filespec token present")),
        limitations = listOf("The embedded payload is not extracted or executed."),
        references = listOf("https://attack.mitre.org/techniques/T1204/"),
    )

    private fun remoteResourceFinding(artifact: ArtifactRef): Finding = Finding(
        id = UUID.randomUUID().toString(),
        ruleId = "PDF-REMOTE-001",
        title = "PDF references remote or external resources",
        category = "external_resource",
        severity = Severity.MEDIUM,
        confidence = Confidence.MEDIUM,
        simpleExplanation = "The PDF references network URIs or remote resources that a reader could contact or fetch.",
        analystExplanation = "A /URI or /GoToR (remote go-to) entry was observed. Such actions can trigger network contact when a reader processes the document. This analyzer never contacts any address; URLs are reported as defanged indicators only.",
        evidence = listOf(Evidence(EvidenceType.CONFIGURATION_VALUE, excerpt = "/URI or /GoToR token present")),
        limitations = listOf("No network contact is made and none is initiated."),
        mappings = listOf(FrameworkMapping("MITRE_ATTACK", "T1557")),
    )

    companion object {
        const val ANALYZER_ID: String = "pdf.analyzer"
        const val ANALYZER_VERSION: String = "1.0.0"
        const val MAX_SCAN_BYTES: Long = 16L * 1024 * 1024 // 16 MB
    }
}
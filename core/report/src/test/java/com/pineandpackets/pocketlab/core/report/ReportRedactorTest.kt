package com.pineandpackets.pocketlab.core.report

import com.pineandpackets.pocketlab.core.model.AnalysisReport
import com.pineandpackets.pocketlab.core.model.AnalysisSettings
import com.pineandpackets.pocketlab.core.model.ApkInfo
import com.pineandpackets.pocketlab.core.model.CertificateInfo
import com.pineandpackets.pocketlab.core.model.Confidence
import com.pineandpackets.pocketlab.core.model.EngineInfo
import com.pineandpackets.pocketlab.core.model.IntegrityBlock
import com.pineandpackets.pocketlab.core.model.RedactionSettings
import com.pineandpackets.pocketlab.core.model.ReportSummary
import com.pineandpackets.pocketlab.core.model.RiskBand
import com.pineandpackets.pocketlab.core.model.SigningInfo
import com.pineandpackets.pocketlab.core.model.SourceInfo
import org.junit.Assert.*
import org.junit.Test

class ReportRedactorTest {

    private val redactor = ReportRedactor()

    @Test
    fun `redact source filename when disabled`() {
        val report = createMinimalReport()
        val settings = RedactionSettings(includeSourceFilename = false)

        val redacted = redactor.redact(report, settings)

        assertEquals("[REDACTED]", redacted.source.displayName)
    }

    @Test
    fun `keep source filename when enabled`() {
        val report = createMinimalReport()
        val settings = RedactionSettings(includeSourceFilename = true)

        val redacted = redactor.redact(report, settings)

        assertEquals("test.apk", redacted.source.displayName)
    }

    @Test
    fun `redact certificate fingerprint and serial when secrets enabled`() {
        val report = createMinimalReport().copy(
            apk = ApkInfo(
                packageName = "com.example",
                versionName = "1.0",
                versionCode = 1,
                minSdk = 21,
                targetSdk = 34,
                compileSdk = 34,
                applicationLabel = "Test",
                debuggable = false,
                backupAllowed = false,
                usesCleartextTraffic = false,
                permissions = emptyList(),
                components = emptyList(),
                signingInfo = SigningInfo(
                    signatureSchemes = listOf("v1"),
                    verified = false,
                    signerCount = 1,
                    certificates = listOf(
                        CertificateInfo(
                            subject = "CN=Test",
                            issuer = "CN=Test",
                            serialNumber = "1234567890ABCDEF",
                            validFrom = "2024-01-01",
                            validTo = "2025-01-01",
                            algorithm = "SHA256withRSA",
                            keySize = 2048,
                            fingerprint = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99",
                            selfSigned = true
                        )
                    )
                )
            )
        )

        val settings = RedactionSettings(redactPossibleSecrets = true)
        val redacted = redactor.redact(report, settings)

        val cert = redacted.apk?.signingInfo?.certificates?.first()
        assertNotNull(cert)
        assertTrue(cert!!.fingerprint.contains("[REDACTED]"))
        assertTrue(cert.serialNumber.contains("[REDACTED]"))
    }

    @Test
    fun `redact IOC values when enabled`() {
        val report = createMinimalReport().copy(
            indicators = listOf(
                com.pineandpackets.pocketlab.core.model.Indicator(
                    type = com.pineandpackets.pocketlab.core.model.IndicatorType.DOMAIN,
                    displayValue = "example.com",
                    canonicalValue = "example.com",
                    defangedValue = "example[.]com",
                    confidence = Confidence.HIGH,
                    context = "network"
                )
            )
        )

        val settings = RedactionSettings(redactIocValues = true)
        val redacted = redactor.redact(report, settings)

        val indicator = redacted.indicators.first()
        assertEquals("[REDACTED]", indicator.displayValue)
        assertEquals("[REDACTED]", indicator.canonicalValue)
        assertEquals("[REDACTED]", indicator.defangedValue)
    }

    @Test
    fun `add redaction limitations to report`() {
        val report = createMinimalReport()
        val settings = RedactionSettings(
            redactPossibleSecrets = true,
            redactIocValues = true,
            includeSourceFilename = false
        )

        val redacted = redactor.redact(report, settings)

        assertTrue(redacted.limitations.any { it.contains("secrets") })
        assertTrue(redacted.limitations.any { it.contains("IOC") })
        assertTrue(redacted.limitations.any { it.contains("filename") })
    }

    private fun createMinimalReport(): AnalysisReport {
        return AnalysisReport(
            schemaVersion = "1.0.0",
            reportId = "report-1",
            caseId = "case-1",
            createdAt = "2026-01-01T00:00:00Z",
            analysisStartedAt = "2026-01-01T00:00:00Z",
            analysisCompletedAt = "2026-01-01T00:00:01Z",
            engine = EngineInfo("1.0.0", "1.0.0", "1.0.0", "1.0.0"),
            settings = AnalysisSettings("standard", listOf("SHA-256"), true, true, true),
            source = SourceInfo("test.apk", "application/zip", 1024, 1024, "sha256", "sha1", "md5"),
            summary = ReportSummary(RiskBand.NO_MAJOR_CONCERNS, Confidence.HIGH, 1.0, 0, null, emptyList()),
            integrity = IntegrityBlock("sha256", "report-sha256", "1.0.0", "1.0.0", false)
        )
    }
}

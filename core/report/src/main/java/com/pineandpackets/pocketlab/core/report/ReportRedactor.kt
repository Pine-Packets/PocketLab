package com.pineandpackets.pocketlab.core.report

import com.pineandpackets.pocketlab.core.model.AnalysisReport
import com.pineandpackets.pocketlab.core.model.CertificateInfo
import com.pineandpackets.pocketlab.core.model.DexInfo
import com.pineandpackets.pocketlab.core.model.Finding
import com.pineandpackets.pocketlab.core.model.Indicator
import com.pineandpackets.pocketlab.core.model.RedactionSettings
import com.pineandpackets.pocketlab.core.model.ReconstructedString
import com.pineandpackets.pocketlab.core.model.SourceInfo

/**
 * Applies user-configurable redaction to a canonical report before export.
 *
 * Redaction is a projection: the canonical report in storage is never modified.
 * Instead, a sanitized copy is produced for the specific export destination.
 */
class ReportRedactor {

    /**
     * Apply redaction settings to a report and return a redacted copy.
     */
    fun redact(report: AnalysisReport, settings: RedactionSettings): AnalysisReport {
        val apk = report.apk
        val redactedApk = apk?.let { apkInfo ->
            val signingInfo = apkInfo.signingInfo
            apkInfo.copy(
                signingInfo = signingInfo?.copy(
                    certificates = redactCertificates(signingInfo.certificates, settings)
                )
            )
        }

        return report.copy(
            source = redactSource(report.source, settings),
            apk = redactedApk,
            dex = redactDex(report.dex, settings),
            indicators = redactIndicators(report.indicators, settings),
            findings = redactFindings(report.findings, settings),
            limitations = report.limitations + redactionLimitations(settings)
        )
    }

    private fun redactSource(source: SourceInfo, settings: RedactionSettings): SourceInfo {
        return if (settings.includeSourceFilename) {
            source
        } else {
            source.copy(displayName = "[REDACTED]")
        }
    }

    private fun redactCertificates(
        certificates: List<CertificateInfo>,
        settings: RedactionSettings
    ): List<CertificateInfo> {
        if (!settings.redactPossibleSecrets) return certificates

        return certificates.map { cert ->
            cert.copy(
                serialNumber = redactMiddle(cert.serialNumber),
                fingerprint = redactMiddle(cert.fingerprint)
            )
        }
    }

    private fun redactDex(dexList: List<DexInfo>, settings: RedactionSettings): List<DexInfo> {
        if (!settings.redactPossibleSecrets) return dexList

        return dexList.map { dex ->
            dex.copy(
                reconstructedStrings = dex.reconstructedStrings.map { redactReconstructedString(it) }
            )
        }
    }

    private fun redactReconstructedString(str: ReconstructedString): ReconstructedString {
        if (!looksLikeSecret(str.value)) return str

        return str.copy(
            value = redactMiddle(str.value),
            sourceStrings = str.sourceStrings.map { if (looksLikeSecret(it)) redactMiddle(it) else it }
        )
    }

    private fun redactIndicators(
        indicators: List<Indicator>,
        settings: RedactionSettings
    ): List<Indicator> {
        if (!settings.redactIocValues) return indicators

        return indicators.map { indicator ->
            indicator.copy(
                displayValue = "[REDACTED]",
                canonicalValue = "[REDACTED]",
                defangedValue = "[REDACTED]"
            )
        }
    }

    private fun redactFindings(findings: List<Finding>, settings: RedactionSettings): List<Finding> {
        if (!settings.redactPossibleSecrets) return findings

        return findings.map { finding ->
            finding.copy(
                evidence = finding.evidence.map { evidence ->
                    evidence.copy(
                        excerpt = evidence.excerpt?.let { excerpt ->
                            if (looksLikeSecret(excerpt)) redactMiddle(excerpt) else excerpt
                        }
                    )
                }
            )
        }
    }

    /**
     * Heuristic check for values that are likely secrets and should be redacted
     * when redactPossibleSecrets is enabled.
     */
    private fun looksLikeSecret(value: String): Boolean {
        // High-entropy tokens
        if (value.matches(Regex("[A-Za-z0-9+/=]{32,}"))) return true

        // AWS-style access keys
        if (value.matches(Regex("AKIA[0-9A-Z]{16}"))) return true

        // Private key markers
        if (value.contains("BEGIN PRIVATE KEY") || value.contains("BEGIN RSA PRIVATE KEY")) return true

        // Common secret keywords
        val secretKeywords = listOf(
            "api_key", "apikey", "api-key",
            "secret", "token", "password", "passwd",
            "private_key", "privatekey"
        )
        if (secretKeywords.any { value.lowercase().contains(it) }) return true

        // URLs with credentials
        if (value.matches(Regex(".*://[^/:@]+:[^/:@]+@.*"))) return true

        return false
    }

    private fun redactMiddle(value: String): String {
        if (value.length <= 8) return "[REDACTED]"
        val prefix = value.take(4)
        val suffix = value.takeLast(4)
        return "$prefix...[REDACTED]...$suffix"
    }

    private fun redactionLimitations(settings: RedactionSettings): List<String> {
        val limitations = mutableListOf<String>()
        if (settings.redactPossibleSecrets) {
            limitations.add("Possible secrets have been redacted from this export.")
        }
        if (settings.redactIocValues) {
            limitations.add("IOC values have been redacted from this export.")
        }
        if (!settings.includeSourceFilename) {
            limitations.add("Source filename has been redacted from this export.")
        }
        return limitations
    }
}

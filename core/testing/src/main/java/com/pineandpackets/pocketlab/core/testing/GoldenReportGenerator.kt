package com.pineandpackets.pocketlab.core.testing

import com.pineandpackets.pocketlab.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Generates golden report snapshots for regression testing.
 * Golden reports are reference outputs used to detect unintended changes in analysis behavior.
 */
object GoldenReportGenerator {
    
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    
    /**
     * Generate a golden report for a minimal APK.
     */
    fun generateMinimalApkReport(): AnalysisReport {
        return AnalysisReport(
            schemaVersion = "1.0.0",
            reportId = "golden-minimal-001",
            caseId = "case-minimal-001",
            createdAt = "2026-01-01T00:00:00Z",
            analysisStartedAt = "2026-01-01T00:00:00Z",
            analysisCompletedAt = "2026-01-01T00:00:01Z",
            engine = EngineInfo(
                appVersion = "1.0.0",
                engineVersion = "1.0.0",
                reportSchemaVersion = "1.0.0",
                rulePackVersion = "1.0.0"
            ),
            settings = AnalysisSettings(
                analysisProfile = "standard",
                hashAlgorithms = listOf("SHA-256", "SHA-1", "MD5"),
                nativeAnalysisEnabled = true,
                deepDexAnalysisEnabled = true,
                iocExtractionEnabled = true
            ),
            source = SourceInfo(
                displayName = "minimal.apk",
                mimeType = "application/vnd.android.package-archive",
                sizeReported = 1024,
                sizeActual = 1024,
                sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                sha1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709",
                md5 = "d41d8cd98f00b204e9800998ecf8427e"
            ),
            containers = emptyList(),
            files = listOf(
                FileInfo(
                    id = "file-1",
                    containerId = null,
                    virtualPath = "AndroidManifest.xml",
                    compressedSize = 512,
                    expandedSize = 1024,
                    compressionMethod = "DEFLATE",
                    magicType = "AXML",
                    sha256 = null
                ),
                FileInfo(
                    id = "file-2",
                    containerId = null,
                    virtualPath = "classes.dex",
                    compressedSize = 256,
                    expandedSize = 512,
                    compressionMethod = "DEFLATE",
                    magicType = "DEX",
                    sha256 = null
                )
            ),
            apk = ApkInfo(
                packageName = "com.example.minimal",
                versionName = "1.0.0",
                versionCode = 1,
                minSdk = 21,
                targetSdk = 34,
                compileSdk = 34,
                applicationLabel = "Minimal App",
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
                            serialNumber = "1234567890",
                            validFrom = "2024-01-01T00:00:00Z",
                            validTo = "2050-01-01T00:00:00Z",
                            algorithm = "SHA256withRSA",
                            keySize = 2048,
                            fingerprint = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99",
                            selfSigned = true
                        )
                    )
                )
            ),
            dex = listOf(
                DexInfo(
                    name = "classes.dex",
                    version = "035",
                    classCount = 10,
                    methodCount = 50,
                    stringCount = 100,
                    size = 512,
                    strings = emptyList(),
                    typeIds = emptyList(),
                    methodIds = emptyList(),
                    fieldIds = emptyList(),
                    classDefs = emptyList(),
                    apiReferences = emptyList()
                )
            ),
            nativeLibraries = emptyList(),
            indicators = emptyList(),
            facts = emptyList(),
            findings = emptyList(),
            summary = ReportSummary(
                riskBand = RiskBand.NO_MAJOR_CONCERNS,
                confidence = Confidence.HIGH,
                completeness = 1.0,
                findingCount = 0,
                maxSeverity = null,
                topFindings = emptyList()
            ),
            stageResults = listOf(
                StageResult(
                    stageId = "file_type",
                    state = StageState.COMPLETE,
                    startedAt = "2026-01-01T00:00:00Z",
                    completedAt = "2026-01-01T00:00:00Z",
                    progressCurrent = 100,
                    progressTotal = 100,
                    warningCount = 0,
                    errorCode = null
                ),
                StageResult(
                    stageId = "archive",
                    state = StageState.COMPLETE,
                    startedAt = "2026-01-01T00:00:00Z",
                    completedAt = "2026-01-01T00:00:00Z",
                    progressCurrent = 100,
                    progressTotal = 100,
                    warningCount = 0,
                    errorCode = null
                ),
                StageResult(
                    stageId = "apk",
                    state = StageState.COMPLETE,
                    startedAt = "2026-01-01T00:00:00Z",
                    completedAt = "2026-01-01T00:00:01Z",
                    progressCurrent = 100,
                    progressTotal = 100,
                    warningCount = 0,
                    errorCode = null
                )
            ),
            limitations = listOf(
                "Signature verification not performed",
                "Static analysis cannot prove runtime behavior"
            ),
            errors = emptyList(),
            integrity = IntegrityBlock(
                sourceSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                reportSha256 = "golden-report-hash-placeholder",
                engineVersion = "1.0.0",
                rulePackVersion = "1.0.0",
                sampleRetained = false
            )
        )
    }
    
    /**
     * Generate a golden report for an APK with suspicious permissions.
     */
    fun generateSuspiciousPermissionsReport(): AnalysisReport {
        val baseReport = generateMinimalApkReport()
        
        return baseReport.copy(
            reportId = "golden-suspicious-001",
            caseId = "case-suspicious-001",
            source = baseReport.source.copy(displayName = "suspicious.apk"),
            apk = baseReport.apk?.copy(
                packageName = "com.example.suspicious",
                permissions = listOf(
                    PermissionInfo(
                        name = "android.permission.SEND_SMS",
                        protectionLevel = "dangerous",
                        declared = true,
                        used = null
                    ),
                    PermissionInfo(
                        name = "android.permission.READ_CONTACTS",
                        protectionLevel = "dangerous",
                        declared = true,
                        used = null
                    ),
                    PermissionInfo(
                        name = "android.permission.INTERNET",
                        protectionLevel = "normal",
                        declared = true,
                        used = null
                    )
                )
            ),
            findings = listOf(
                Finding(
                    id = "finding-1",
                    ruleId = "PERM_SEND_SMS",
                    title = "Dangerous permission: SEND_SMS",
                    category = "permission",
                    severity = Severity.HIGH,
                    confidence = Confidence.HIGH,
                    status = FindingStatus.ACTIVE,
                    simpleExplanation = "App can send SMS messages",
                    analystExplanation = "The app declares SEND_SMS permission, allowing it to send SMS messages without user interaction. This is commonly used by malware for premium SMS fraud.",
                    evidence = listOf(
                        Evidence(
                            type = EvidenceType.MANIFEST_DECLARATION,
                            excerpt = "<uses-permission android:name=\"android.permission.SEND_SMS\"/>"
                        )
                    ),
                    limitations = listOf("Permission declaration does not guarantee usage"),
                    recommendations = listOf("Verify SMS functionality is necessary"),
                    mappings = emptyList(),
                    references = emptyList()
                ),
                Finding(
                    id = "finding-2",
                    ruleId = "PERM_READ_CONTACTS",
                    title = "Dangerous permission: READ_CONTACTS",
                    category = "permission",
                    severity = Severity.MEDIUM,
                    confidence = Confidence.HIGH,
                    status = FindingStatus.ACTIVE,
                    simpleExplanation = "App can read contacts",
                    analystExplanation = "The app declares READ_CONTACTS permission, allowing it to access the user's contact list.",
                    evidence = listOf(
                        Evidence(
                            type = EvidenceType.MANIFEST_DECLARATION,
                            excerpt = "<uses-permission android:name=\"android.permission.READ_CONTACTS\"/>"
                        )
                    ),
                    limitations = listOf("Permission declaration does not guarantee usage"),
                    recommendations = listOf("Verify contact access is necessary"),
                    mappings = emptyList(),
                    references = emptyList()
                )
            ),
            summary = ReportSummary(
                riskBand = RiskBand.SUSPICIOUS_CAPABILITIES,
                confidence = Confidence.HIGH,
                completeness = 1.0,
                findingCount = 2,
                maxSeverity = Severity.HIGH,
                topFindings = listOf(
                    "Dangerous permission: SEND_SMS",
                    "Dangerous permission: READ_CONTACTS"
                )
            )
        )
    }
    
    /**
     * Generate a golden report for a debuggable APK.
     */
    fun generateDebuggableApkReport(): AnalysisReport {
        val baseReport = generateMinimalApkReport()
        
        return baseReport.copy(
            reportId = "golden-debuggable-001",
            caseId = "case-debuggable-001",
            source = baseReport.source.copy(displayName = "debuggable.apk"),
            apk = baseReport.apk?.copy(
                packageName = "com.example.debuggable",
                debuggable = true
            ),
            findings = listOf(
                Finding(
                    id = "finding-1",
                    ruleId = "MANIFEST_DEBUGGABLE",
                    title = "Application is debuggable",
                    category = "manifest",
                    severity = Severity.HIGH,
                    confidence = Confidence.HIGH,
                    status = FindingStatus.ACTIVE,
                    simpleExplanation = "App is marked as debuggable",
                    analystExplanation = "The application has android:debuggable set to true. This allows debuggers to attach to the process and should never be present in production builds.",
                    evidence = listOf(
                        Evidence(
                            type = EvidenceType.MANIFEST_DECLARATION,
                            excerpt = "android:debuggable=\"true\""
                        )
                    ),
                    limitations = emptyList(),
                    recommendations = listOf("Remove debuggable flag for production releases"),
                    mappings = emptyList(),
                    references = emptyList()
                )
            ),
            summary = ReportSummary(
                riskBand = RiskBand.SUSPICIOUS_CAPABILITIES,
                confidence = Confidence.HIGH,
                completeness = 1.0,
                findingCount = 1,
                maxSeverity = Severity.HIGH,
                topFindings = listOf("Application is debuggable")
            )
        )
    }
    
    /**
     * Serialize a report to JSON for storage.
     */
    fun serializeReport(report: AnalysisReport): String {
        return json.encodeToString(report)
    }
    
    /**
     * Deserialize a report from JSON.
     */
    fun deserializeReport(jsonString: String): AnalysisReport {
        return json.decodeFromString(jsonString)
    }
}

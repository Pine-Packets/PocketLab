package com.pineandpackets.pocketlab.core.testing

import com.pineandpackets.pocketlab.core.model.*
import java.util.UUID

object DemoFixtureGenerator {
    
    fun generateDemoReport(): AnalysisReport {
        return AnalysisReport(
            schemaVersion = "1.0.0",
            reportId = UUID.randomUUID().toString(),
            caseId = UUID.randomUUID().toString(),
            createdAt = "2026-08-06T12:00:00Z",
            analysisStartedAt = "2026-08-06T11:55:00Z",
            analysisCompletedAt = "2026-08-06T11:58:00Z",
            engine = EngineInfo(
                appVersion = "1.0.0",
                engineVersion = "1.0.0",
                reportSchemaVersion = "1.0.0",
                rulePackVersion = "2026.08.1"
            ),
            settings = AnalysisSettings(
                analysisProfile = "STANDARD",
                hashAlgorithms = listOf("SHA-256", "SHA-1", "MD5"),
                nativeAnalysisEnabled = true,
                deepDexAnalysisEnabled = true,
                iocExtractionEnabled = true
            ),
            source = SourceInfo(
                displayName = "suspicious_app.apk",
                mimeType = "application/vnd.android.package-archive",
                sizeReported = 5242880L,
                sizeActual = 5242880L,
                sha256 = "a1b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef123456",
                sha1 = "a1b2c3d4e5f6789012345678901234567890abcd",
                md5 = "a1b2c3d4e5f6789012345678901234ab"
            ),
            containers = emptyList(),
            files = listOf(
                FileInfo(
                    id = "file-1",
                    containerId = null,
                    virtualPath = "classes.dex",
                    compressedSize = 1048576L,
                    expandedSize = 2097152L,
                    compressionMethod = "DEFLATE",
                    magicType = "DEX",
                    sha256 = "b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef1234567"
                ),
                FileInfo(
                    id = "file-2",
                    containerId = null,
                    virtualPath = "AndroidManifest.xml",
                    compressedSize = 4096L,
                    expandedSize = 8192L,
                    compressionMethod = "DEFLATE",
                    magicType = "AXML",
                    sha256 = "c3d4e5f6789012345678901234567890abcdef1234567890abcdef12345678"
                )
            ),
            apk = ApkInfo(
                packageName = "com.example.suspicious",
                versionName = "1.0.0",
                versionCode = 1L,
                minSdk = 21,
                targetSdk = 34,
                compileSdk = 34,
                applicationLabel = "Suspicious App",
                debuggable = true,
                backupAllowed = false,
                usesCleartextTraffic = true,
                permissions = listOf(
                    PermissionInfo(
                        name = "android.permission.READ_CONTACTS",
                        protectionLevel = "DANGEROUS",
                        declared = true,
                        used = true
                    ),
                    PermissionInfo(
                        name = "android.permission.ACCESS_FINE_LOCATION",
                        protectionLevel = "DANGEROUS",
                        declared = true,
                        used = true
                    ),
                    PermissionInfo(
                        name = "android.permission.SEND_SMS",
                        protectionLevel = "DANGEROUS",
                        declared = true,
                        used = true
                    ),
                    PermissionInfo(
                        name = "android.permission.INTERNET",
                        protectionLevel = "NORMAL",
                        declared = true,
                        used = true
                    ),
                    PermissionInfo(
                        name = "android.permission.ACCESS_NETWORK_STATE",
                        protectionLevel = "NORMAL",
                        declared = true,
                        used = true
                    )
                ),
                components = listOf(
                    ComponentInfo(
                        name = "com.example.suspicious.MainActivity",
                        type = ComponentType.ACTIVITY,
                        exported = true,
                        permission = null,
                        intentFilters = listOf(
                            IntentFilterInfo(
                                actions = listOf("android.intent.action.MAIN"),
                                categories = listOf("android.intent.category.LAUNCHER"),
                                dataElements = emptyList(),
                                autoVerify = false,
                                priority = null
                            )
                        )
                    ),
                    ComponentInfo(
                        name = "com.example.suspicious.DataService",
                        type = ComponentType.SERVICE,
                        exported = true,
                        permission = null,
                        intentFilters = listOf(
                            IntentFilterInfo(
                                actions = listOf("com.example.suspicious.ACTION_SYNC"),
                                categories = emptyList(),
                                dataElements = emptyList(),
                                autoVerify = false,
                                priority = null
                            )
                        )
                    ),
                    ComponentInfo(
                        name = "com.example.suspicious.BootReceiver",
                        type = ComponentType.RECEIVER,
                        exported = true,
                        permission = null,
                        intentFilters = listOf(
                            IntentFilterInfo(
                                actions = listOf("android.intent.action.BOOT_COMPLETED"),
                                categories = emptyList(),
                                dataElements = emptyList(),
                                autoVerify = false,
                                priority = null
                            )
                        )
                    )
                ),
                signingInfo = SigningInfo(
                    signatureSchemes = listOf("v1"),
                    verified = false,
                    signerCount = 1,
                    certificates = listOf(
                        CertificateInfo(
                            subject = "CN=Android Debug,O=Android,C=US",
                            issuer = "CN=Android Debug,O=Android,C=US",
                            serialNumber = "1234567890",
                            validFrom = "2024-01-01T00:00:00Z",
                            validTo = "2054-01-01T00:00:00Z",
                            algorithm = "SHA256withRSA",
                            keySize = 2048,
                            fingerprint = "AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90",
                            selfSigned = true
                        )
                    )
                )
            ),
            dex = listOf(
                DexInfo(
                    name = "classes.dex",
                    version = "035",
                    classCount = 150,
                    methodCount = 1200,
                    stringCount = 3500,
                    size = 2097152L,
                    strings = emptyList(),
                    typeIds = emptyList(),
                    methodIds = emptyList(),
                    fieldIds = emptyList(),
                    classDefs = emptyList(),
                    apiReferences = listOf(
                        ApiReference(
                            className = "Landroid/telephony/SmsManager;",
                            methodName = "sendTextMessage",
                            callSites = listOf(
                                CallSite(
                                    className = "Lcom/example/suspicious/SmsSender;",
                                    methodName = "sendMessage",
                                    instructionOffset = 42,
                                    context = "Sending SMS to contacts"
                                )
                            )
                        ),
                        ApiReference(
                            className = "Landroid/location/LocationManager;",
                            methodName = "requestLocationUpdates",
                            callSites = listOf(
                                CallSite(
                                    className = "Lcom/example/suspicious/LocationTracker;",
                                    methodName = "startTracking",
                                    instructionOffset = 128,
                                    context = "Requesting GPS updates"
                                )
                            )
                        )
                    )
                )
            ),
            nativeLibraries = emptyList(),
            indicators = listOf(
                Indicator(
                    type = IndicatorType.DOMAIN,
                    displayValue = "suspicious-server.example.com",
                    canonicalValue = "suspicious-server.example.com",
                    defangedValue = "suspicious-server[.]example[.]com",
                    source = IndicatorSource(
                        container = null,
                        entry = "classes.dex",
                        className = "Lcom/example/suspicious/NetworkClient;",
                        method = "connect",
                        offset = 256
                    ),
                    confidence = Confidence.HIGH,
                    context = "Hardcoded server endpoint",
                    classification = listOf("NETWORK_DESTINATION", "C2_SERVER")
                ),
                Indicator(
                    type = IndicatorType.URL,
                    displayValue = "http://suspicious-server.example.com/api/data",
                    canonicalValue = "http://suspicious-server.example.com/api/data",
                    defangedValue = "hxxp://suspicious-server[.]example[.]com/api/data",
                    source = IndicatorSource(
                        container = null,
                        entry = "classes.dex",
                        className = "Lcom/example/suspicious/NetworkClient;",
                        method = "uploadData",
                        offset = 512
                    ),
                    confidence = Confidence.HIGH,
                    context = "Data exfiltration endpoint using cleartext HTTP",
                    classification = listOf("NETWORK_DESTINATION", "EXFILTRATION")
                ),
                Indicator(
                    type = IndicatorType.IPV4,
                    displayValue = "192.168.1.100",
                    canonicalValue = "192.168.1.100",
                    defangedValue = "192[.]168[.]1[.]100",
                    source = IndicatorSource(
                        container = null,
                        entry = "classes.dex",
                        className = "Lcom/example/suspicious/Config;",
                        method = "<clinit>",
                        offset = 64
                    ),
                    confidence = Confidence.MEDIUM,
                    context = "Hardcoded IP address",
                    classification = listOf("NETWORK_DESTINATION", "PRIVATE_IP")
                )
            ),
            facts = listOf(
                Fact(
                    id = "fact-1",
                    type = "MANIFEST_DEBUGGABLE",
                    value = "true",
                    source = "AndroidManifest.xml"
                ),
                Fact(
                    id = "fact-2",
                    type = "MANIFEST_CLEARTEXT_TRAFFIC",
                    value = "true",
                    source = "AndroidManifest.xml"
                ),
                Fact(
                    id = "fact-3",
                    type = "PERMISSION_DANGEROUS",
                    value = "android.permission.SEND_SMS",
                    source = "AndroidManifest.xml"
                ),
                Fact(
                    id = "fact-4",
                    type = "CODE_USES_SMS_API",
                    value = "SmsManager.sendTextMessage",
                    source = "classes.dex"
                ),
                Fact(
                    id = "fact-5",
                    type = "CODE_USES_LOCATION_API",
                    value = "LocationManager.requestLocationUpdates",
                    source = "classes.dex"
                ),
                Fact(
                    id = "fact-6",
                    type = "COMPONENT_EXPORTED",
                    value = "com.example.suspicious.DataService",
                    source = "AndroidManifest.xml"
                ),
                Fact(
                    id = "fact-7",
                    type = "COMPONENT_BOOT_RECEIVER",
                    value = "com.example.suspicious.BootReceiver",
                    source = "AndroidManifest.xml"
                )
            ),
            findings = listOf(
                Finding(
                    id = "finding-1",
                    ruleId = "MANIFEST-DEBUGGABLE-001",
                    title = "Application is debuggable",
                    category = "manifest_security",
                    severity = Severity.HIGH,
                    confidence = Confidence.HIGH,
                    status = FindingStatus.ACTIVE,
                    simpleExplanation = "The application has been built with debugging enabled. This allows attackers to attach debuggers and inspect the app's runtime behavior.",
                    analystExplanation = "The android:debuggable flag is set to true in the AndroidManifest.xml. This is a significant security risk as it allows attackers to attach debuggers, inspect memory, and modify runtime behavior. Debug builds should never be distributed to end users.",
                    evidence = listOf(
                        Evidence(
                            type = EvidenceType.MANIFEST_DECLARATION,
                            excerpt = "android:debuggable=\"true\"",
                            additionalData = mapOf("element" to "application")
                        )
                    ),
                    limitations = listOf("Static analysis cannot determine if debug features are actually exploitable."),
                    recommendations = listOf(
                        "Remove android:debuggable=\"true\" from the manifest",
                        "Ensure release builds have debugging disabled",
                        "Verify the build configuration"
                    ),
                    mappings = listOf(
                        FrameworkMapping(
                            framework = "MITRE_ATTACK_MOBILE",
                            technique = "T1439",
                            techniqueName = "Debugging"
                        )
                    ),
                    references = listOf("https://developer.android.com/guide/topics/manifest/application-element#debug")
                ),
                Finding(
                    id = "finding-2",
                    ruleId = "PERM-SEND-SMS-001",
                    title = "App can send SMS messages without user interaction",
                    category = "permission_risk",
                    severity = Severity.CRITICAL,
                    confidence = Confidence.HIGH,
                    status = FindingStatus.ACTIVE,
                    simpleExplanation = "The app has permission to send SMS messages and code that uses the SMS API. This could be used to send premium SMS or communicate with attackers.",
                    analystExplanation = "The app declares SEND_SMS permission and contains code that calls SmsManager.sendTextMessage(). This combination is commonly seen in malware that sends premium-rate SMS messages or uses SMS as a communication channel with command-and-control servers.",
                    evidence = listOf(
                        Evidence(
                            type = EvidenceType.MANIFEST_DECLARATION,
                            excerpt = "<uses-permission android:name=\"android.permission.SEND_SMS\"/>"
                        ),
                        Evidence(
                            type = EvidenceType.DEX_CALL_SITE,
                            dexName = "classes.dex",
                            className = "Lcom/example/suspicious/SmsSender;",
                            method = "sendMessage",
                            offset = 42,
                            excerpt = "SmsManager.sendTextMessage()"
                        )
                    ),
                    limitations = listOf("Static analysis cannot determine if SMS sending is user-initiated or automatic."),
                    recommendations = listOf(
                        "Verify SMS sending is user-initiated",
                        "Check for premium-rate numbers",
                        "Review SMS recipients"
                    ),
                    mappings = listOf(
                        FrameworkMapping(
                            framework = "MITRE_ATTACK_MOBILE",
                            technique = "T1453",
                            techniqueName = "Deliver Malicious Content via SMS"
                        )
                    ),
                    references = listOf("https://developer.android.com/reference/android/permission/Send_SMS")
                ),
                Finding(
                    id = "finding-3",
                    ruleId = "MANIFEST-CLEARTEXT-001",
                    title = "App permits cleartext network traffic",
                    category = "network_security",
                    severity = Severity.MEDIUM,
                    confidence = Confidence.HIGH,
                    status = FindingStatus.ACTIVE,
                    simpleExplanation = "The app allows unencrypted HTTP connections, which can expose sensitive data to network eavesdropping.",
                    analystExplanation = "The android:usesCleartextTraffic flag is set to true, allowing the app to make unencrypted HTTP connections. This can expose user data, credentials, and other sensitive information to network-level attackers.",
                    evidence = listOf(
                        Evidence(
                            type = EvidenceType.MANIFEST_DECLARATION,
                            excerpt = "android:usesCleartextTraffic=\"true\"",
                            additionalData = mapOf("element" to "application")
                        ),
                        Evidence(
                            type = EvidenceType.DEX_STRING,
                            dexName = "classes.dex",
                            excerpt = "http://suspicious-server.example.com/api/data"
                        )
                    ),
                    limitations = listOf("App may still use HTTPS for sensitive data."),
                    recommendations = listOf(
                        "Set usesCleartextTraffic to false",
                        "Use HTTPS for all network communication",
                        "Implement network security configuration"
                    ),
                    mappings = listOf(
                        FrameworkMapping(
                            framework = "MITRE_ATTACK_MOBILE",
                            technique = "T1451",
                            techniqueName = "Exploit SS7 to Redirection"
                        )
                    ),
                    references = listOf("https://developer.android.com/training/articles/security-config")
                ),
                Finding(
                    id = "finding-4",
                    ruleId = "COMP-EXPORTED-SERVICE-001",
                    title = "Exported service without permission protection",
                    category = "component_exposure",
                    severity = Severity.MEDIUM,
                    confidence = Confidence.HIGH,
                    status = FindingStatus.ACTIVE,
                    simpleExplanation = "The app has a service that can be accessed by other apps without requiring permission. This could allow unauthorized access to app functionality.",
                    analystExplanation = "The DataService component is exported (android:exported=\"true\") without requiring a permission. This allows any app on the device to bind to or start this service, potentially accessing sensitive functionality or data.",
                    evidence = listOf(
                        Evidence(
                            type = EvidenceType.MANIFEST_DECLARATION,
                            excerpt = "<service android:name=\".DataService\" android:exported=\"true\"/>"
                        )
                    ),
                    limitations = listOf("Service may validate caller identity internally."),
                    recommendations = listOf(
                        "Add android:permission to restrict access",
                        "Verify service validates caller identity",
                        "Consider making service non-exported if not needed"
                    ),
                    mappings = listOf(
                        FrameworkMapping(
                            framework = "MITRE_ATTACK_MOBILE",
                            technique = "T1453",
                            techniqueName = "Deliver Malicious Content via Application Layer"
                        )
                    ),
                    references = listOf("https://developer.android.com/guide/components/services")
                ),
                Finding(
                    id = "finding-5",
                    ruleId = "COMP-BOOT-RECEIVER-001",
                    title = "App starts automatically on device boot",
                    category = "persistence",
                    severity = Severity.MEDIUM,
                    confidence = Confidence.HIGH,
                    status = FindingStatus.ACTIVE,
                    simpleExplanation = "The app has a receiver that starts automatically when the device boots. This is commonly used for persistence.",
                    analystExplanation = "The BootReceiver component is registered to receive BOOT_COMPLETED intent, allowing the app to start automatically when the device boots. While this is legitimate for some apps, it's commonly used by malware for persistence.",
                    evidence = listOf(
                        Evidence(
                            type = EvidenceType.MANIFEST_DECLARATION,
                            excerpt = "<receiver android:name=\".BootReceiver\" android:exported=\"true\"><intent-filter><action android:name=\"android.intent.action.BOOT_COMPLETED\"/></intent-filter></receiver>"
                        )
                    ),
                    limitations = listOf("Boot receiver may be legitimate for app functionality."),
                    recommendations = listOf(
                        "Verify boot receiver is necessary",
                        "Document legitimate use case",
                        "Consider alternative startup mechanisms"
                    ),
                    mappings = listOf(
                        FrameworkMapping(
                            framework = "MITRE_ATTACK_MOBILE",
                            technique = "T1427",
                            techniqueName = "Boot or Logon Autostart Execution"
                        )
                    ),
                    references = listOf("https://developer.android.com/reference/android/content/Intent#ACTION_BOOT_COMPLETED")
                )
            ),
            summary = ReportSummary(
                riskBand = RiskBand.HIGH_RISK_INDICATORS,
                confidence = Confidence.HIGH,
                completeness = 0.92,
                findingCount = 5,
                maxSeverity = Severity.CRITICAL,
                topFindings = listOf(
                    "App can send SMS messages without user interaction",
                    "Application is debuggable",
                    "App permits cleartext network traffic",
                    "Exported service without permission protection",
                    "App starts automatically on device boot"
                )
            ),
            stageResults = listOf(
                StageResult(
                    stageId = "intake",
                    state = StageState.COMPLETE,
                    startedAt = "2026-08-06T11:55:00Z",
                    completedAt = "2026-08-06T11:55:30Z",
                    progressCurrent = 100,
                    progressTotal = 100,
                    warningCount = 0,
                    errorCode = null
                ),
                StageResult(
                    stageId = "archive",
                    state = StageState.COMPLETE,
                    startedAt = "2026-08-06T11:55:30Z",
                    completedAt = "2026-08-06T11:56:00Z",
                    progressCurrent = 100,
                    progressTotal = 100,
                    warningCount = 0,
                    errorCode = null
                ),
                StageResult(
                    stageId = "apk_metadata",
                    state = StageState.COMPLETE,
                    startedAt = "2026-08-06T11:56:00Z",
                    completedAt = "2026-08-06T11:56:30Z",
                    progressCurrent = 100,
                    progressTotal = 100,
                    warningCount = 2,
                    errorCode = null
                ),
                StageResult(
                    stageId = "dex_analysis",
                    state = StageState.COMPLETE,
                    startedAt = "2026-08-06T11:56:30Z",
                    completedAt = "2026-08-06T11:57:30Z",
                    progressCurrent = 100,
                    progressTotal = 100,
                    warningCount = 0,
                    errorCode = null
                ),
                StageResult(
                    stageId = "ioc_extraction",
                    state = StageState.COMPLETE,
                    startedAt = "2026-08-06T11:57:30Z",
                    completedAt = "2026-08-06T11:57:45Z",
                    progressCurrent = 100,
                    progressTotal = 100,
                    warningCount = 0,
                    errorCode = null
                ),
                StageResult(
                    stageId = "rules",
                    state = StageState.COMPLETE,
                    startedAt = "2026-08-06T11:57:45Z",
                    completedAt = "2026-08-06T11:58:00Z",
                    progressCurrent = 100,
                    progressTotal = 100,
                    warningCount = 0,
                    errorCode = null
                )
            ),
            limitations = listOf(
                "Static analysis cannot prove that detected code paths will execute",
                "Signature verification was not performed (no apksig integration)",
                "Native library analysis was not performed",
                "Some DEX strings may be obfuscated or encrypted"
            ),
            errors = emptyList(),
            integrity = IntegrityBlock(
                sourceSha256 = "a1b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef123456",
                reportSha256 = "d4e5f6789012345678901234567890abcdef1234567890abcdef1234567890ab",
                engineVersion = "1.0.0",
                rulePackVersion = "2026.08.1",
                sampleRetained = false
            )
        )
    }
}

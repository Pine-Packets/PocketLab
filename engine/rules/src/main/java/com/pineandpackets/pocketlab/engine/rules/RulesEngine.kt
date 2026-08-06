package com.pineandpackets.pocketlab.engine.rules

import com.pineandpackets.pocketlab.core.model.ApkInfo
import com.pineandpackets.pocketlab.core.model.ComponentInfo
import com.pineandpackets.pocketlab.core.model.Confidence
import com.pineandpackets.pocketlab.core.model.Evidence
import com.pineandpackets.pocketlab.core.model.EvidenceType
import com.pineandpackets.pocketlab.core.model.Finding
import com.pineandpackets.pocketlab.core.model.PermissionInfo
import com.pineandpackets.pocketlab.core.model.Severity
import com.pineandpackets.pocketlab.core.model.DexInfo
import timber.log.Timber
import java.util.UUID

class RulesEngine {
    
    private val factExtractor = FactExtractor()
    private val ruleInterpreter = RuleInterpreter()
    private var declarativeRules: List<Rule> = emptyList()
    
    init {
        loadDefaultRules()
    }
    
    /**
     * Load default rules from classpath resources.
     */
    private fun loadDefaultRules() {
        try {
            val classLoader = RulesEngine::class.java.classLoader
            val resourceUrl = classLoader?.getResource("rules/default-rules.json")
            if (resourceUrl != null) {
                val rulesFile = java.io.File(resourceUrl.toURI())
                declarativeRules = ruleInterpreter.loadRules(rulesFile)
                Timber.i("Loaded ${declarativeRules.size} declarative rules from classpath")
            } else {
                // Fallback to project-relative path for local development
                val fallbackFile = java.io.File("engine/rules/src/main/resources/rules/default-rules.json")
                if (fallbackFile.exists()) {
                    declarativeRules = ruleInterpreter.loadRules(fallbackFile)
                    Timber.i("Loaded ${declarativeRules.size} declarative rules from fallback path")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to load default rules, using hardcoded rules only")
        }
    }
    
    /**
     * Load rules from a custom file.
     */
    fun loadRulesFromFile(file: java.io.File) {
        declarativeRules = ruleInterpreter.loadRules(file)
    }
    
    fun evaluateRules(apkInfo: ApkInfo, dexInfoList: List<DexInfo> = emptyList()): List<Finding> {
        val findings = mutableListOf<Finding>()
        
        // Evaluate declarative rules
        val facts = factExtractor.extractFacts(apkInfo, dexInfoList)
        findings.addAll(ruleInterpreter.evaluateRules(declarativeRules, facts))
        
        // Evaluate hardcoded rules (for backwards compatibility)
        findings.addAll(checkDangerousPermissions(apkInfo))
        findings.addAll(checkExportedComponents(apkInfo))
        findings.addAll(checkDebuggableFlag(apkInfo))
        findings.addAll(checkBackupAllowed(apkInfo))
        findings.addAll(checkCleartextTraffic(apkInfo))
        
        // Deduplicate findings by ruleId
        return findings.distinctBy { it.ruleId }
    }
    
    private fun checkDangerousPermissions(apkInfo: ApkInfo): List<Finding> {
        val findings = mutableListOf<Finding>()
        
        val dangerousPermissions = mapOf(
            "android.permission.READ_SMS" to Severity.HIGH,
            "android.permission.SEND_SMS" to Severity.HIGH,
            "android.permission.RECEIVE_SMS" to Severity.HIGH,
            "android.permission.READ_CONTACTS" to Severity.MEDIUM,
            "android.permission.WRITE_CONTACTS" to Severity.MEDIUM,
            "android.permission.READ_CALL_LOG" to Severity.HIGH,
            "android.permission.WRITE_CALL_LOG" to Severity.HIGH,
            "android.permission.CAMERA" to Severity.MEDIUM,
            "android.permission.RECORD_AUDIO" to Severity.MEDIUM,
            "android.permission.ACCESS_FINE_LOCATION" to Severity.MEDIUM,
            "android.permission.ACCESS_COARSE_LOCATION" to Severity.MEDIUM,
            "android.permission.READ_EXTERNAL_STORAGE" to Severity.MEDIUM,
            "android.permission.WRITE_EXTERNAL_STORAGE" to Severity.MEDIUM,
            "android.permission.SYSTEM_ALERT_WINDOW" to Severity.HIGH,
            "android.permission.REQUEST_INSTALL_PACKAGES" to Severity.CRITICAL
        )
        
        apkInfo.permissions.forEach { perm ->
            val severity = dangerousPermissions[perm.name]
            if (severity != null) {
                findings.add(
                    Finding(
                        id = UUID.randomUUID().toString(),
                        ruleId = "PERM-${perm.name.substringAfterLast('.')}",
                        title = "Dangerous Permission: ${perm.name.substringAfterLast('.')}",
                        category = "permission",
                        severity = severity,
                        confidence = Confidence.HIGH,
                        simpleExplanation = "App requests ${perm.name.substringAfterLast('.')} permission",
                        analystExplanation = "The application declares the ${perm.name} permission, which provides access to sensitive user data or system capabilities.",
                        evidence = listOf(
                            Evidence(
                                type = EvidenceType.MANIFEST_DECLARATION,
                                excerpt = "<uses-permission android:name=\"${perm.name}\"/>",
                                excerptEncoding = "xml"
                            )
                        ),
                        limitations = listOf("Permission declaration does not guarantee usage"),
                        recommendations = listOf("Review if this permission is necessary for app functionality")
                    )
                )
            }
        }
        
        return findings
    }
    
    private fun checkExportedComponents(apkInfo: ApkInfo): List<Finding> {
        val findings = mutableListOf<Finding>()
        
        apkInfo.components.filter { it.exported }.forEach { component ->
            val severity = when (component.type) {
                com.pineandpackets.pocketlab.core.model.ComponentType.ACTIVITY -> Severity.LOW
                com.pineandpackets.pocketlab.core.model.ComponentType.SERVICE -> Severity.MEDIUM
                com.pineandpackets.pocketlab.core.model.ComponentType.RECEIVER -> Severity.MEDIUM
                com.pineandpackets.pocketlab.core.model.ComponentType.PROVIDER -> Severity.HIGH
            }
            
            findings.add(
                Finding(
                    id = UUID.randomUUID().toString(),
                    ruleId = "COMP-EXPORTED-${component.type}",
                    title = "Exported ${component.type.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    category = "component",
                    severity = severity,
                    confidence = Confidence.HIGH,
                    simpleExplanation = "App has an exported ${component.type.name.lowercase()}: ${component.name}",
                    analystExplanation = "The component ${component.name} is exported, making it accessible to other applications on the device.",
                    evidence = listOf(
                        Evidence(
                            type = EvidenceType.MANIFEST_DECLARATION,
                            excerpt = "<${component.type.name.lowercase()} android:name=\"${component.name}\" android:exported=\"true\"/>",
                            excerptEncoding = "xml"
                        )
                    ),
                    limitations = listOf("Exported components may be intentional for inter-app communication"),
                    recommendations = listOf("Verify that exported components have proper permission checks")
                )
            )
        }
        
        return findings
    }
    
    private fun checkDebuggableFlag(apkInfo: ApkInfo): List<Finding> {
        if (!apkInfo.debuggable) return emptyList()
        
        return listOf(
            Finding(
                id = UUID.randomUUID().toString(),
                ruleId = "MANIFEST-DEBUGGABLE",
                title = "Application is Debuggable",
                category = "manifest",
                severity = Severity.HIGH,
                confidence = Confidence.HIGH,
                simpleExplanation = "App is marked as debuggable, which should not be present in production",
                analystExplanation = "The android:debuggable flag is set to true in the manifest. This allows debuggers to attach to the process and should never be present in production builds.",
                evidence = listOf(
                    Evidence(
                        type = EvidenceType.MANIFEST_DECLARATION,
                        excerpt = "android:debuggable=\"true\"",
                        excerptEncoding = "xml"
                    )
                ),
                limitations = emptyList(),
                recommendations = listOf("Remove debuggable flag for production releases")
            )
        )
    }
    
    private fun checkBackupAllowed(apkInfo: ApkInfo): List<Finding> {
        if (!apkInfo.backupAllowed) return emptyList()
        
        return listOf(
            Finding(
                id = UUID.randomUUID().toString(),
                ruleId = "MANIFEST-BACKUP",
                title = "Application Data Can Be Backed Up",
                category = "manifest",
                severity = Severity.LOW,
                confidence = Confidence.HIGH,
                simpleExplanation = "App data can be backed up, potentially exposing sensitive data",
                analystExplanation = "The android:allowBackup flag is set to true, allowing application data to be backed up via ADB. This could expose sensitive data if the backup is not encrypted.",
                evidence = listOf(
                    Evidence(
                        type = EvidenceType.MANIFEST_DECLARATION,
                        excerpt = "android:allowBackup=\"true\"",
                        excerptEncoding = "xml"
                    )
                ),
                limitations = listOf("Backup may be encrypted depending on device configuration"),
                recommendations = listOf("Set allowBackup to false if app handles sensitive data")
            )
        )
    }
    
    private fun checkCleartextTraffic(apkInfo: ApkInfo): List<Finding> {
        if (!apkInfo.usesCleartextTraffic) return emptyList()
        
        return listOf(
            Finding(
                id = UUID.randomUUID().toString(),
                ruleId = "MANIFEST-CLEARTEXT",
                title = "Cleartext Traffic Permitted",
                category = "network",
                severity = Severity.MEDIUM,
                confidence = Confidence.HIGH,
                simpleExplanation = "App permits cleartext (unencrypted) network traffic",
                analystExplanation = "The android:usesCleartextTraffic flag is set to true, allowing unencrypted HTTP traffic. This can expose user data to network eavesdropping.",
                evidence = listOf(
                    Evidence(
                        type = EvidenceType.MANIFEST_DECLARATION,
                        excerpt = "android:usesCleartextTraffic=\"true\"",
                        excerptEncoding = "xml"
                    )
                ),
                limitations = listOf("May be intentional for development or specific use cases"),
                recommendations = listOf("Use HTTPS for all network communication")
            )
        )
    }
}

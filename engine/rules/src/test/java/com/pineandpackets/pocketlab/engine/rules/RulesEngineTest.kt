package com.pineandpackets.pocketlab.engine.rules

import com.pineandpackets.pocketlab.core.model.ApkInfo
import com.pineandpackets.pocketlab.core.model.ComponentInfo
import com.pineandpackets.pocketlab.core.model.Confidence
import com.pineandpackets.pocketlab.core.model.Evidence
import com.pineandpackets.pocketlab.core.model.EvidenceType
import com.pineandpackets.pocketlab.core.model.Finding
import com.pineandpackets.pocketlab.core.model.PermissionInfo
import com.pineandpackets.pocketlab.core.model.Severity
import org.junit.Assert.*
import org.junit.Test

class RulesEngineTest {
    
    private val rulesEngine = RulesEngine()
    
    @Test
    fun `detect dangerous SMS permissions`() {
        val apkInfo = createApkInfo(
            permissions = listOf(
                PermissionInfo("android.permission.SEND_SMS", "dangerous", true, null),
                PermissionInfo("android.permission.READ_SMS", "dangerous", true, null)
            )
        )
        
        val findings = rulesEngine.evaluateRules(apkInfo)
        
        assertTrue(findings.any { it.ruleId.contains("SEND_SMS") })
        assertTrue(findings.any { it.ruleId.contains("READ_SMS") })
        assertTrue(findings.all { it.severity == Severity.HIGH })
    }
    
    @Test
    fun `detect exported provider as high severity`() {
        val apkInfo = createApkInfo(
            components = listOf(
                ComponentInfo(
                    name = "com.example.DataProvider",
                    type = com.pineandpackets.pocketlab.core.model.ComponentType.PROVIDER,
                    exported = true,
                    permission = null
                )
            )
        )
        
        val findings = rulesEngine.evaluateRules(apkInfo)
        
        val providerFinding = findings.find { it.ruleId.contains("PROVIDER") }
        assertNotNull(providerFinding)
        assertEquals(Severity.HIGH, providerFinding?.severity)
    }
    
    @Test
    fun `detect debuggable flag`() {
        val apkInfo = createApkInfo(debuggable = true)
        
        val findings = rulesEngine.evaluateRules(apkInfo)
        
        assertTrue(findings.any { it.ruleId == "MANIFEST-DEBUGGABLE" })
        assertEquals(Severity.HIGH, findings.find { it.ruleId == "MANIFEST-DEBUGGABLE" }?.severity)
    }
    
    @Test
    fun `no findings for clean app`() {
        val apkInfo = createApkInfo(
            permissions = listOf(
                PermissionInfo("android.permission.INTERNET", "normal", true, null)
            ),
            components = emptyList(),
            debuggable = false
        )
        
        val findings = rulesEngine.evaluateRules(apkInfo)
        
        assertTrue(findings.isEmpty())
    }
    
    @Test
    fun `all findings have evidence`() {
        val apkInfo = createApkInfo(
            permissions = listOf(
                PermissionInfo("android.permission.CAMERA", "dangerous", true, null)
            ),
            debuggable = true
        )
        
        val findings = rulesEngine.evaluateRules(apkInfo)
        
        assertTrue(findings.isNotEmpty())
        assertTrue(findings.all { it.evidence.isNotEmpty() })
    }
    
    private fun createApkInfo(
        packageName: String = "com.example.app",
        versionName: String = "1.0.0",
        versionCode: Long = 1,
        minSdk: Int = 21,
        targetSdk: Int = 34,
        debuggable: Boolean = false,
        backupAllowed: Boolean = false,
        usesCleartextTraffic: Boolean = false,
        permissions: List<PermissionInfo> = emptyList(),
        components: List<ComponentInfo> = emptyList()
    ): ApkInfo {
        return ApkInfo(
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            compileSdk = null,
            applicationLabel = null,
            debuggable = debuggable,
            backupAllowed = backupAllowed,
            usesCleartextTraffic = usesCleartextTraffic,
            permissions = permissions,
            components = components,
            signingInfo = null
        )
    }
}

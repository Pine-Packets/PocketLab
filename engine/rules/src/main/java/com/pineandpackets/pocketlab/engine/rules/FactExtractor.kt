package com.pineandpackets.pocketlab.engine.rules

import com.pineandpackets.pocketlab.core.model.ApkInfo
import com.pineandpackets.pocketlab.core.model.ComponentType

/**
 * Extracts facts from APK analysis results.
 * Facts are atomic observations that can be matched against declarative rules.
 */
class FactExtractor {
    
    /**
     * Extract all facts from an APK analysis.
     */
    fun extractFacts(apkInfo: ApkInfo): Set<Fact> {
        val facts = mutableSetOf<Fact>()
        
        // Extract permission facts
        apkInfo.permissions.forEach { permission ->
            facts.add(Fact("PERMISSION_DECLARED", permission.name, "AndroidManifest.xml"))
            
            // Add specific permission facts for common dangerous permissions
            when (permission.name) {
                "android.permission.READ_SMS" -> facts.add(Fact("PERMISSION_SMS_READ", permission.name, "AndroidManifest.xml"))
                "android.permission.SEND_SMS" -> facts.add(Fact("PERMISSION_SMS_SEND", permission.name, "AndroidManifest.xml"))
                "android.permission.RECEIVE_SMS" -> facts.add(Fact("PERMISSION_SMS_RECEIVE", permission.name, "AndroidManifest.xml"))
                "android.permission.READ_CONTACTS" -> facts.add(Fact("PERMISSION_CONTACTS_READ", permission.name, "AndroidManifest.xml"))
                "android.permission.WRITE_CONTACTS" -> facts.add(Fact("PERMISSION_CONTACTS_WRITE", permission.name, "AndroidManifest.xml"))
                "android.permission.READ_CALL_LOG" -> facts.add(Fact("PERMISSION_CALL_LOG_READ", permission.name, "AndroidManifest.xml"))
                "android.permission.WRITE_CALL_LOG" -> facts.add(Fact("PERMISSION_CALL_LOG_WRITE", permission.name, "AndroidManifest.xml"))
                "android.permission.CAMERA" -> facts.add(Fact("PERMISSION_CAMERA", permission.name, "AndroidManifest.xml"))
                "android.permission.RECORD_AUDIO" -> facts.add(Fact("PERMISSION_MICROPHONE", permission.name, "AndroidManifest.xml"))
                "android.permission.ACCESS_FINE_LOCATION" -> facts.add(Fact("PERMISSION_LOCATION_FINE", permission.name, "AndroidManifest.xml"))
                "android.permission.ACCESS_COARSE_LOCATION" -> facts.add(Fact("PERMISSION_LOCATION_COARSE", permission.name, "AndroidManifest.xml"))
                "android.permission.READ_EXTERNAL_STORAGE" -> facts.add(Fact("PERMISSION_STORAGE_READ", permission.name, "AndroidManifest.xml"))
                "android.permission.WRITE_EXTERNAL_STORAGE" -> facts.add(Fact("PERMISSION_STORAGE_WRITE", permission.name, "AndroidManifest.xml"))
                "android.permission.SYSTEM_ALERT_WINDOW" -> facts.add(Fact("PERMISSION_OVERLAY", permission.name, "AndroidManifest.xml"))
                "android.permission.REQUEST_INSTALL_PACKAGES" -> facts.add(Fact("PERMISSION_INSTALL_PACKAGES", permission.name, "AndroidManifest.xml"))
            }
        }
        
        // Extract component facts
        apkInfo.components.forEach { component ->
            val componentType = component.type.name.lowercase()
            facts.add(Fact("COMPONENT_DECLARED", "${component.type}:${component.name}", "AndroidManifest.xml"))
            
            if (component.exported) {
                facts.add(Fact("COMPONENT_EXPORTED", "${component.type}:${component.name}", "AndroidManifest.xml"))
                
                when (component.type) {
                    ComponentType.ACTIVITY -> facts.add(Fact("ACTIVITY_EXPORTED", component.name, "AndroidManifest.xml"))
                    ComponentType.SERVICE -> facts.add(Fact("SERVICE_EXPORTED", component.name, "AndroidManifest.xml"))
                    ComponentType.RECEIVER -> facts.add(Fact("RECEIVER_EXPORTED", component.name, "AndroidManifest.xml"))
                    ComponentType.PROVIDER -> facts.add(Fact("PROVIDER_EXPORTED", component.name, "AndroidManifest.xml"))
                }
                
                // Check for exported components without permission
                if (component.permission.isNullOrEmpty()) {
                    facts.add(Fact("COMPONENT_EXPORTED_NO_PERMISSION", "${component.type}:${component.name}", "AndroidManifest.xml"))
                }
            }
        }
        
        // Extract manifest flag facts
        if (apkInfo.debuggable) {
            facts.add(Fact("MANIFEST_DEBUGGABLE", "true", "AndroidManifest.xml"))
        }
        
        if (apkInfo.backupAllowed) {
            facts.add(Fact("MANIFEST_ALLOW_BACKUP", "true", "AndroidManifest.xml"))
        }
        
        if (apkInfo.usesCleartextTraffic) {
            facts.add(Fact("MANIFEST_CLEARTEXT_TRAFFIC", "true", "AndroidManifest.xml"))
        }
        
        // Extract SDK version facts
        apkInfo.minSdk?.let { 
            facts.add(Fact("MANIFEST_MIN_SDK", it.toString(), "AndroidManifest.xml"))
            if (it < 21) {
                facts.add(Fact("MANIFEST_MIN_SDK_OLD", it.toString(), "AndroidManifest.xml"))
            }
        }
        
        apkInfo.targetSdk?.let {
            facts.add(Fact("MANIFEST_TARGET_SDK", it.toString(), "AndroidManifest.xml"))
            if (it < 30) {
                facts.add(Fact("MANIFEST_TARGET_SDK_OLD", it.toString(), "AndroidManifest.xml"))
            }
        }
        
        return facts
    }
}

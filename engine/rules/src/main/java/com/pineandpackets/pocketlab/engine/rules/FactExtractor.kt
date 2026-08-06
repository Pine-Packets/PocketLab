package com.pineandpackets.pocketlab.engine.rules

import com.pineandpackets.pocketlab.core.model.ApkInfo
import com.pineandpackets.pocketlab.core.model.ComponentType
import com.pineandpackets.pocketlab.core.model.DexInfo

/**
 * Extracts facts from APK and DEX analysis results.
 * Facts are atomic observations that can be matched against declarative rules.
 */
class FactExtractor {

    /**
     * Extract all facts from APK and DEX analysis.
     */
    fun extractFacts(apkInfo: ApkInfo, dexInfoList: List<DexInfo> = emptyList()): Set<Fact> {
        val facts = mutableSetOf<Fact>()

        facts.addAll(extractApkFacts(apkInfo))
        facts.addAll(extractDexFacts(dexInfoList))

        return facts
    }

    /**
     * Extract facts from APK manifest and components.
     */
    private fun extractApkFacts(apkInfo: ApkInfo): Set<Fact> {
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
                "android.permission.BIND_ACCESSIBILITY_SERVICE" -> facts.add(Fact("PERMISSION_ACCESSIBILITY_SERVICE", permission.name, "AndroidManifest.xml"))
                "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" -> facts.add(Fact("PERMISSION_NOTIFICATION_LISTENER", permission.name, "AndroidManifest.xml"))
                "android.permission.INTERNET" -> facts.add(Fact("PERMISSION_INTERNET", permission.name, "AndroidManifest.xml"))
            }
        }

        // Extract component facts
        apkInfo.components.forEach { component ->
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

            // Accessibility service component
            if (component.type == ComponentType.SERVICE && component.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE") {
                facts.add(Fact("COMPONENT_ACCESSIBILITY_SERVICE", component.name, "AndroidManifest.xml"))
            }
        }

        // Extract signing facts
        apkInfo.signingInfo?.certificates?.forEach { cert ->
            if (cert.debugCertificate) {
                facts.add(Fact("SIGNING_DEBUG_CERTIFICATE", cert.fingerprint, "META-INF/"))
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

    /**
     * Extract facts from DEX code analysis.
     */
    private fun extractDexFacts(dexInfoList: List<DexInfo>): Set<Fact> {
        val facts = mutableSetOf<Fact>()

        dexInfoList.forEach { dexInfo ->
            // API reference facts
            dexInfo.apiReferences.forEach { apiRef ->
                val descriptor = "${apiRef.className}->${apiRef.methodName}"
                facts.add(Fact("API_REFERENCE", descriptor, dexInfo.name))

                when {
                    descriptor.startsWith("Ldalvik/system/DexClassLoader") ->
                        facts.add(Fact("CODE_DYNAMIC_DEX_LOADING", descriptor, dexInfo.name))
                    descriptor.startsWith("Ldalvik/system/PathClassLoader") ->
                        facts.add(Fact("CODE_DYNAMIC_DEX_LOADING", descriptor, dexInfo.name))
                    descriptor.startsWith("Ldalvik/system/InMemoryDexClassLoader") ->
                        facts.add(Fact("CODE_IN_MEMORY_DEX_LOADING", descriptor, dexInfo.name))
                    descriptor.startsWith("Ljava/lang/Class;->forName") ->
                        facts.add(Fact("CODE_REFLECTION_CLASS_FOR_NAME", descriptor, dexInfo.name))
                    descriptor.startsWith("Ljava/lang/reflect/Method;->invoke") ->
                        facts.add(Fact("CODE_REFLECTION_METHOD_INVOKE", descriptor, dexInfo.name))
                    descriptor.startsWith("Ljava/lang/Runtime;->exec") ->
                        facts.add(Fact("CODE_SHELL_EXECUTION", descriptor, dexInfo.name))
                    descriptor.startsWith("Ljava/lang/ProcessBuilder") ->
                        facts.add(Fact("CODE_PROCESS_BUILDER", descriptor, dexInfo.name))
                    descriptor.startsWith("Ljava/lang/System;->loadLibrary") ->
                        facts.add(Fact("CODE_NATIVE_LOAD", descriptor, dexInfo.name))
                    descriptor.startsWith("Ljava/net/URL;-><init>") ->
                        facts.add(Fact("CODE_NETWORK_URL", descriptor, dexInfo.name))
                    descriptor.startsWith("Landroid/content/Context;->startService") ->
                        facts.add(Fact("CODE_START_SERVICE", descriptor, dexInfo.name))
                }
            }

            // Reconstructed string facts for sensitive APIs
            dexInfo.reconstructedStrings.forEach { reconstructed ->
                val factType = when {
                    reconstructed.targetApi?.contains("DexClassLoader") == true -> "CODE_DYNAMIC_DEX_PAYLOAD"
                    reconstructed.targetApi?.contains("PathClassLoader") == true -> "CODE_DYNAMIC_DEX_PAYLOAD"
                    reconstructed.targetApi?.contains("Runtime;->exec") == true -> "CODE_SHELL_COMMAND"
                    reconstructed.targetApi?.contains("ProcessBuilder") == true -> "CODE_SHELL_COMMAND"
                    reconstructed.targetApi?.contains("Class;->forName") == true -> "CODE_REFLECTED_CLASS_NAME"
                    reconstructed.targetApi?.contains("System;->load") == true -> "CODE_NATIVE_LIBRARY_NAME"
                    else -> "RECONSTRUCTED_STRING"
                }
                facts.add(Fact(factType, reconstructed.value, "${dexInfo.name}:${reconstructed.className}->${reconstructed.methodName}"))
            }
        }

        return facts
    }
}

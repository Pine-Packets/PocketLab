package com.pineandpackets.pocketlab.engine.dex

import com.pineandpackets.pocketlab.core.model.DexInfo
import com.pineandpackets.pocketlab.core.model.Finding
import com.pineandpackets.pocketlab.core.model.Severity
import com.pineandpackets.pocketlab.core.model.Confidence
import com.pineandpackets.pocketlab.core.model.Evidence
import com.pineandpackets.pocketlab.core.model.EvidenceType
import java.util.UUID

/**
 * Detects reflection and dynamic code loading patterns in DEX files.
 * These patterns are often used by malware to hide malicious behavior.
 */
class ReflectionDetector {
    
    data class ReflectionPattern(
        val patternType: String,
        val className: String,
        val methodName: String,
        val severity: Severity,
        val description: String,
        val evidence: String
    )
    
    private val reflectionPatterns = listOf(
        // Class.forName - dynamic class loading
        ReflectionPattern(
            "CLASS_FOR_NAME",
            "Ljava/lang/Class;",
            "forName",
            Severity.HIGH,
            "Dynamic class loading via Class.forName()",
            "Class.forName() can load arbitrary classes at runtime"
        ),
        
        // DexClassLoader - dynamic DEX loading
        ReflectionPattern(
            "DEX_CLASS_LOADER",
            "Ldalvik/system/DexClassLoader;",
            "<init>",
            Severity.CRITICAL,
            "Dynamic DEX file loading",
            "DexClassLoader can load and execute code from DEX files"
        ),
        
        // PathClassLoader - path-based code loading
        ReflectionPattern(
            "PATH_CLASS_LOADER",
            "Ldalvik/system/PathClassLoader;",
            "<init>",
            Severity.CRITICAL,
            "Path-based code loading",
            "PathClassLoader can load code from file paths"
        ),
        
        // InMemoryDexClassLoader - in-memory code loading
        ReflectionPattern(
            "IN_MEMORY_DEX_LOADER",
            "Ldalvik/system/InMemoryDexClassLoader;",
            "<init>",
            Severity.CRITICAL,
            "In-memory DEX loading",
            "InMemoryDexClassLoader can load DEX from memory buffers"
        ),
        
        // DelegateClassLoader - delegated loading
        ReflectionPattern(
            "DELEGATE_CLASS_LOADER",
            "Ldalvik/system/DelegateClassLoader;",
            "<init>",
            Severity.HIGH,
            "Delegated class loading",
            "DelegateClassLoader can delegate to other loaders"
        ),
        
        // Method.invoke - reflection invocation
        ReflectionPattern(
            "METHOD_INVOKE",
            "Ljava/lang/reflect/Method;",
            "invoke",
            Severity.HIGH,
            "Reflection method invocation",
            "Method.invoke() can call methods dynamically"
        ),
        
        // Constructor.newInstance - reflection instantiation
        ReflectionPattern(
            "CONSTRUCTOR_NEW_INSTANCE",
            "Ljava/lang/reflect/Constructor;",
            "newInstance",
            Severity.HIGH,
            "Reflection constructor invocation",
            "Constructor.newInstance() can create objects dynamically"
        ),
        
        // Field.get/set - reflection field access
        ReflectionPattern(
            "FIELD_ACCESS",
            "Ljava/lang/reflect/Field;",
            "get",
            Severity.MEDIUM,
            "Reflection field read",
            "Field.get() can read fields dynamically"
        ),
        ReflectionPattern(
            "FIELD_ACCESS",
            "Ljava/lang/reflect/Field;",
            "set",
            Severity.MEDIUM,
            "Reflection field write",
            "Field.set() can modify fields dynamically"
        ),
        
        // Runtime.exec - command execution
        ReflectionPattern(
            "RUNTIME_EXEC",
            "Ljava/lang/Runtime;",
            "exec",
            Severity.CRITICAL,
            "System command execution",
            "Runtime.exec() can execute system commands"
        ),
        
        // ProcessBuilder - process creation
        ReflectionPattern(
            "PROCESS_BUILDER",
            "Ljava/lang/ProcessBuilder;",
            "start",
            Severity.CRITICAL,
            "Process creation",
            "ProcessBuilder.start() can create new processes"
        ),
        
        // System.loadLibrary - native library loading
        ReflectionPattern(
            "SYSTEM_LOAD_LIBRARY",
            "Ljava/lang/System;",
            "loadLibrary",
            Severity.HIGH,
            "Native library loading",
            "System.loadLibrary() can load native libraries"
        ),
        
        // System.load - native library loading from path
        ReflectionPattern(
            "SYSTEM_LOAD",
            "Ljava/lang/System;",
            "load",
            Severity.HIGH,
            "Native library loading from path",
            "System.load() can load native libraries from paths"
        ),
        
        // Runtime.loadLibrary - native library loading
        ReflectionPattern(
            "RUNTIME_LOAD_LIBRARY",
            "Ljava/lang/Runtime;",
            "loadLibrary",
            Severity.HIGH,
            "Native library loading via Runtime",
            "Runtime.loadLibrary() can load native libraries"
        ),
        
        // Proxy.newProxyInstance - dynamic proxy creation
        ReflectionPattern(
            "PROXY_CREATION",
            "Ljava/lang/reflect/Proxy;",
            "newProxyInstance",
            Severity.MEDIUM,
            "Dynamic proxy creation",
            "Proxy.newProxyInstance() can create dynamic proxies"
        )
    )
    
    /**
     * Detect reflection and dynamic loading patterns in DEX method references.
     */
    fun detectPatterns(dexInfo: DexInfo): List<ReflectionFinding> {
        val findings = mutableListOf<ReflectionFinding>()
        
        // Check method references for reflection patterns
        dexInfo.methodIds.forEach { methodId ->
            reflectionPatterns.forEach { pattern ->
                if (methodId.className == pattern.className && methodId.methodName == pattern.methodName) {
                    findings.add(
                        ReflectionFinding(
                            id = UUID.randomUUID().toString(),
                            patternType = pattern.patternType,
                            severity = pattern.severity,
                            description = pattern.description,
                            evidence = pattern.evidence,
                            className = methodId.className,
                            methodName = methodId.methodName,
                            prototype = methodId.prototype
                        )
                    )
                }
            }
        }
        
        // Check for suspicious string patterns
        dexInfo.strings.forEach { dexString ->
            val suspiciousStrings = detectSuspiciousStrings(dexString.value)
            suspiciousStrings.forEach { suspicious ->
                findings.add(
                    ReflectionFinding(
                        id = UUID.randomUUID().toString(),
                        patternType = "SUSPICIOUS_STRING",
                        severity = Severity.MEDIUM,
                        description = "Suspicious string pattern detected",
                        evidence = suspicious,
                        className = "N/A",
                        methodName = "N/A",
                        prototype = "N/A"
                    )
                )
            }
        }
        
        return findings
    }
    
    /**
     * Detect suspicious string patterns that may indicate reflection or obfuscation.
     */
    private fun detectSuspiciousStrings(value: String): List<String> {
        val suspicious = mutableListOf<String>()
        
        // Check for class name patterns
        if (value.matches(Regex("^L[a-zA-Z0-9_/]+;$"))) {
            suspicious.add("Dalvik class descriptor: $value")
        }
        
        // Check for method signature patterns
        if (value.matches(Regex("^[a-zA-Z0-9_]+\\(.*\\).*$"))) {
            suspicious.add("Method signature: $value")
        }
        
        // Check for suspicious keywords
        val keywords = listOf("DexClassLoader", "PathClassLoader", "Runtime.exec", "ProcessBuilder")
        keywords.forEach { keyword ->
            if (value.contains(keyword)) {
                suspicious.add("Suspicious keyword: $keyword in string")
            }
        }
        
        // Check for base64-encoded patterns (potential obfuscation)
        if (value.matches(Regex("^[A-Za-z0-9+/]{20,}={0,2}$"))) {
            suspicious.add("Potential base64-encoded data")
        }
        
        return suspicious
    }
    
    data class ReflectionFinding(
        val id: String,
        val patternType: String,
        val severity: Severity,
        val description: String,
        val evidence: String,
        val className: String,
        val methodName: String,
        val prototype: String
    )
}

package com.pineandpackets.pocketlab.engine.apk

import com.pineandpackets.pocketlab.core.common.AnalysisError
import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile

/**
 * Validates APK structural integrity before deep analysis.
 * Checks for malformed structures, missing required files, and suspicious patterns.
 */
class ApkStructureValidator {
    
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val hasManifest: Boolean = false,
        val hasDex: Boolean = false,
        val dexCount: Int = 0,
        val nativeLibCount: Int = 0,
        val assetCount: Int = 0,
        val resourceCount: Int = 0
    )
    
    companion object {
        private val REQUIRED_FILES = setOf("AndroidManifest.xml")
        private val SUSPICIOUS_EXTENSIONS = setOf(
            ".exe", ".dll", ".so", ".bin", ".sh", ".bat", ".cmd", ".ps1"
        )
        private val DEX_PATTERN = Regex("^classes(\\d*)\\.dex$")
        private val NATIVE_LIB_PATTERN = Regex("^lib/[a-zA-Z0-9_-]+/[^/]+\\.so$")
    }
    
    fun validate(apkFile: File): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        if (!apkFile.exists()) {
            return ValidationResult(false, listOf("APK file does not exist"))
        }
        
        if (apkFile.length() > AnalysisLimits.MAX_INPUT_SIZE_BYTES) {
            return ValidationResult(
                false,
                listOf("APK file exceeds maximum size limit: ${apkFile.length()} > ${AnalysisLimits.MAX_INPUT_SIZE_BYTES}")
            )
        }
        
        if (apkFile.length() < 1024) {
            warnings.add("APK file is unusually small: ${apkFile.length()} bytes")
        }
        
        return try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries().toList()
                val entryNames = entries.map { it.name }.toSet()
                
                // Check for required files
                val hasManifest = entryNames.contains("AndroidManifest.xml")
                if (!hasManifest) {
                    errors.add("Missing required file: AndroidManifest.xml")
                }
                
                // Check for DEX files
                val dexFiles = entryNames.filter { DEX_PATTERN.matches(it) }
                val hasDex = dexFiles.isNotEmpty()
                if (!hasDex && hasManifest) {
                    warnings.add("No DEX files found - APK may not contain executable code")
                }
                
                // Check for classes.dex (required for standard APKs)
                if (hasDex && !entryNames.contains("classes.dex")) {
                    warnings.add("Missing classes.dex - only secondary DEX files found")
                }
                
                // Check for duplicate entries
                val duplicateCheck = mutableSetOf<String>()
                val duplicates = mutableSetOf<String>()
                for (name in entryNames) {
                    if (!duplicateCheck.add(name)) {
                        duplicates.add(name)
                    }
                }
                if (duplicates.isNotEmpty()) {
                    errors.add("Duplicate ZIP entries found: ${duplicates.joinToString(", ")}")
                }
                
                // Check for suspicious files
                val suspiciousFiles = entryNames.filter { name ->
                    SUSPICIOUS_EXTENSIONS.any { name.lowercase().endsWith(it) }
                }
                if (suspiciousFiles.isNotEmpty()) {
                    warnings.add("Suspicious executable files found: ${suspiciousFiles.joinToString(", ")}")
                }
                
                // Check for path traversal attempts
                val traversalAttempts = entryNames.filter { name ->
                    name.contains("..") || name.startsWith("/") || name.startsWith("\\")
                }
                if (traversalAttempts.isNotEmpty()) {
                    errors.add("Path traversal attempts detected: ${traversalAttempts.joinToString(", ")}")
                }
                
                // Check for native libraries
                val nativeLibs = entryNames.filter { NATIVE_LIB_PATTERN.matches(it) }
                
                // Check for assets
                val assets = entryNames.filter { it.startsWith("assets/") }
                
                // Check for resources
                val resources = entryNames.filter { it.startsWith("res/") }
                
                // Check for META-INF
                val hasSigningInfo = entryNames.any { it.startsWith("META-INF/") }
                if (!hasSigningInfo && hasManifest) {
                    warnings.add("No META-INF directory found - APK may not be signed")
                }
                
                // Check for unusually large entries
                val largeEntries = entries.filter { 
                    it.size > AnalysisLimits.MAX_SINGLE_ENTRY_BYTES 
                }
                if (largeEntries.isNotEmpty()) {
                    errors.add("Entries exceed size limit: ${largeEntries.map { it.name }.joinToString(", ")}")
                }
                
                // Check for encrypted entries
                val encryptedEntries = entries.filter { it.size > 0 && it.compressedSize > 0 && 
                    it.method == java.util.zip.ZipEntry.DEFLATED &&
                    it.crc == 0L && it.size != it.compressedSize }
                if (encryptedEntries.isNotEmpty()) {
                    warnings.add("Potentially encrypted entries found: ${encryptedEntries.size}")
                }
                
                ValidationResult(
                    isValid = errors.isEmpty(),
                    errors = errors,
                    warnings = warnings,
                    hasManifest = hasManifest,
                    hasDex = hasDex,
                    dexCount = dexFiles.size,
                    nativeLibCount = nativeLibs.size,
                    assetCount = assets.size,
                    resourceCount = resources.size
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to validate APK structure")
            ValidationResult(false, listOf("Failed to validate APK: ${e.message}"))
        }
    }
}

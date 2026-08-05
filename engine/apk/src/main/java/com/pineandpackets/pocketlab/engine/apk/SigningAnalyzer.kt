package com.pineandpackets.pocketlab.engine.apk

import com.pineandpackets.pocketlab.core.common.AnalysisError
import com.pineandpackets.pocketlab.core.model.CertificateInfo
import com.pineandpackets.pocketlab.core.model.SigningInfo
import timber.log.Timber
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.jar.JarFile
import java.util.zip.ZipFile

class SigningAnalyzer {
    
    fun analyzeSigning(apkFile: File): Result<SigningInfo> {
        return try {
            val certificates = extractCertificates(apkFile)
            
            if (certificates.isEmpty()) {
                return Result.failure(AnalysisError.ParserError("No signing certificates found"))
            }
            
            val certInfos = certificates.map { cert ->
                CertificateInfo(
                    subject = cert.subjectX500Principal.name,
                    issuer = cert.issuerX500Principal.name,
                    serialNumber = cert.serialNumber.toString(16),
                    validFrom = cert.notBefore.time.toString(),
                    validTo = cert.notAfter.time.toString(),
                    algorithm = cert.sigAlgName,
                    keySize = (cert.publicKey as? java.security.interfaces.RSAPublicKey)?.modulus?.bitLength() ?: 0,
                    fingerprint = calculateFingerprint(cert),
                    selfSigned = cert.subjectX500Principal == cert.issuerX500Principal
                )
            }
            
            val signatureSchemes = detectSignatureSchemes(apkFile)
            
            Result.success(
                SigningInfo(
                    signatureSchemes = signatureSchemes,
                    verified = true,
                    signerCount = certificates.size,
                    certificates = certInfos
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to analyze signing")
            Result.failure(AnalysisError.ParserError("Failed to analyze signing", e))
        }
    }
    
    private fun extractCertificates(apkFile: File): List<X509Certificate> {
        val certificates = mutableListOf<X509Certificate>()
        
        try {
            JarFile(apkFile).use { jar ->
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith("META-INF/") && entry.name.endsWith(".RSA") || 
                        entry.name.endsWith(".DSA") || entry.name.endsWith(".EC")) {
                        try {
                            val certFactory = CertificateFactory.getInstance("X.509")
                            jar.getInputStream(entry).use { input ->
                                val cert = certFactory.generateCertificate(input) as? X509Certificate
                                if (cert != null) {
                                    certificates.add(cert)
                                }
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to parse certificate from ${entry.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract certificates")
        }
        
        return certificates
    }
    
    private fun detectSignatureSchemes(apkFile: File): List<String> {
        val schemes = mutableListOf<String>()
        
        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toList()
                
                if (entries.any { it.startsWith("META-INF/") && it.endsWith(".SF") }) {
                    schemes.add("v1")
                }
                
                val apkSigningBlock = findApkSigningBlock(apkFile)
                if (apkSigningBlock != null) {
                    if (apkSigningBlock.hasV2Signature) {
                        schemes.add("v2")
                    }
                    if (apkSigningBlock.hasV3Signature) {
                        schemes.add("v3")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to detect signature schemes")
        }
        
        return schemes
    }
    
    private fun findApkSigningBlock(apkFile: File): ApkSigningBlock? {
        return try {
            apkFile.inputStream().use { input ->
                val buffer = ByteArray(24)
                
                val fileSize = apkFile.length()
                if (fileSize < 24) return null
                
                input.skip(fileSize - 24)
                input.read(buffer)
                
                val blockSize = java.nio.ByteBuffer.wrap(buffer, 0, 8)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    .long
                
                if (blockSize > 0 && blockSize < fileSize) {
                    input.skip(-(blockSize + 8))
                    val magic = ByteArray(16)
                    input.read(magic)
                    
                    val magicString = String(magic, Charsets.US_ASCII)
                    if (magicString == "APK Sig Block 42") {
                        return ApkSigningBlock(hasV2Signature = true, hasV3Signature = false)
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun calculateFingerprint(cert: X509Certificate): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val encoded = cert.encoded
        val hashBytes = digest.digest(encoded)
        return hashBytes.joinToString(":") { "%02X".format(it) }
    }
    
    private data class ApkSigningBlock(
        val hasV2Signature: Boolean,
        val hasV3Signature: Boolean
    )
}

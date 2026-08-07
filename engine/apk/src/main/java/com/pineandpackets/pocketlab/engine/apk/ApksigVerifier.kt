package com.pineandpackets.pocketlab.engine.apk

import com.android.apksig.ApkVerifier
import com.pineandpackets.pocketlab.core.model.CertificateInfo
import com.pineandpackets.pocketlab.core.model.SigningInfo
import com.pineandpackets.pocketlab.core.model.SigningLineageEntry
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.security.cert.X509Certificate

data class SigningVerificationResult(
    val signingInfo: SigningInfo?,
    val verified: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)

class ApksigVerifier {

    fun verify(apkFile: File): SigningVerificationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        return try {
            val verifier = ApkVerifier.Builder(apkFile).build()
            val result = verifier.verify()

            val verified = result.isVerified

            if (!verified) {
                for (error in result.errors) {
                    errors.add("APKsig error: ${error}")
                }
            }

            for (warning in result.warnings) {
                warnings.add("APKsig warning: ${warning}")
            }

            val signerCertificates = mutableListOf<CertificateInfo>()
            val signatureSchemes = mutableListOf<String>()
            val signingLineage = mutableListOf<SigningLineageEntry>()

            if (verified) {
                val signerCerts = result.signerCertificates
                for (cert in signerCerts) {
                    if (cert is X509Certificate) {
                        signerCertificates.add(extractCertificateInfo(cert))
                    }
                }

                if (result.isVerifiedUsingV1Scheme) {
                    signatureSchemes.add("v1")
                }
                if (result.isVerifiedUsingV2Scheme) {
                    signatureSchemes.add("v2")
                }
                if (result.isVerifiedUsingV3Scheme) {
                    signatureSchemes.add("v3")
                }
                if (result.isVerifiedUsingV31Scheme) {
                    signatureSchemes.add("v3.1")
                }
                if (result.isVerifiedUsingV4Scheme) {
                    signatureSchemes.add("v4")
                }

                // Extract signing lineage if available
                try {
                    val lineage = extractSigningLineage(result)
                    signingLineage.addAll(lineage)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to extract signing lineage")
                    warnings.add("Could not extract signing lineage: ${e.message}")
                }
            }

            val signingInfo = if (signerCertificates.isNotEmpty()) {
                SigningInfo(
                    signatureSchemes = signatureSchemes,
                    verified = true,
                    signerCount = signerCertificates.size,
                    certificates = signerCertificates,
                    signingLineage = signingLineage,
                    hasLineage = signingLineage.isNotEmpty()
                )
            } else {
                null
            }

            SigningVerificationResult(
                signingInfo = signingInfo,
                verified = verified,
                errors = errors,
                warnings = warnings
            )
        } catch (e: Exception) {
            Timber.e(e, "Apksig verification failed")
            errors.add("Apksig verification failed: ${e.javaClass.simpleName}: ${e.message}")
            SigningVerificationResult(
                signingInfo = null,
                verified = false,
                errors = errors,
                warnings = warnings
            )
        }
    }

    private fun extractSigningLineage(result: ApkVerifier.Result): List<SigningLineageEntry> {
        val lineage = mutableListOf<SigningLineageEntry>()
        
        // Try to get signing lineage from the result
        // The apksig library provides this through the signing certificate chain
        try {
            val signerCerts = result.signerCertificates
            if (signerCerts.size > 1) {
                // Multiple signers indicate potential lineage
                signerCerts.forEachIndexed { index, cert ->
                    if (cert is X509Certificate) {
                        val certInfo = extractCertificateInfo(cert)
                        val isCurrent = index == signerCerts.size - 1
                        val rotationTarget = if (!isCurrent && index + 1 < signerCerts.size) {
                            val nextCert = signerCerts[index + 1] as? X509Certificate
                            nextCert?.let { calculateFingerprint(it.encoded) }
                        } else null
                        
                        lineage.add(
                            SigningLineageEntry(
                                index = index,
                                certificate = certInfo,
                                rotationTarget = rotationTarget,
                                isCurrentSigner = isCurrent,
                                proofOfRotation = if (!isCurrent) "Certificate chain present" else null
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error extracting signing lineage details")
        }
        
        return lineage
    }

    private fun extractCertificateInfo(cert: X509Certificate): CertificateInfo {
        val fingerprint = calculateFingerprint(cert.encoded)
        val keySize = try {
            (cert.publicKey as? java.security.interfaces.RSAPublicKey)?.modulus?.bitLength()
                ?: (cert.publicKey as? java.security.interfaces.ECPublicKey)?.params?.order?.bitLength()
                ?: 0
        } catch (e: Exception) {
            0
        }

        val subject = cert.subjectX500Principal?.name ?: "Unknown"
        val issuer = cert.issuerX500Principal?.name ?: "Unknown"
        val selfSigned = subject == issuer

        val debugCert = isDebugCertificate(cert)

        return CertificateInfo(
            subject = subject,
            issuer = issuer,
            serialNumber = cert.serialNumber?.toString(16)?.uppercase() ?: "Unknown",
            validFrom = cert.notBefore?.toInstant()?.toString() ?: "Unknown",
            validTo = cert.notAfter?.toInstant()?.toString() ?: "Unknown",
            algorithm = cert.sigAlgName ?: "Unknown",
            keySize = keySize,
            fingerprint = fingerprint,
            selfSigned = selfSigned,
            debugCertificate = debugCert
        )
    }

    private fun calculateFingerprint(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString(":") { "%02X".format(it) }
    }

    private fun isDebugCertificate(cert: X509Certificate): Boolean {
        val subject = cert.subjectX500Principal?.name ?: return false
        val commonDebugIndicators = listOf(
            "CN=Android Debug",
            "CN=debug",
            "O=Android",
            "OU=debug"
        )
        return commonDebugIndicators.any { subject.contains(it, ignoreCase = true) }
    }
}

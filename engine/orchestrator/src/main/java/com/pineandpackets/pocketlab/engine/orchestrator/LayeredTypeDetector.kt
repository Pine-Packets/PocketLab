package com.pineandpackets.pocketlab.engine.orchestrator

import com.pineandpackets.pocketlab.core.model.DetectedType
import com.pineandpackets.pocketlab.engine.api.DetectionLayer

/**
 * Layered, content-first format detection using budgeted reads on an
 * [ArtifactSource]. Layer priority: content signature, then advisory
 * extension (advisory MIME is echoed by callers as context only). Mismatches
 * are surfaced as flags, never as a hard error, and a parser is never chosen
 * from the filename extension alone when the signature disagrees.
 */
object LayeredTypeDetector {
    fun detect(source: ArtifactSource): DetectedArtifact {
        val signature = detectBySignature(source)
        val extension = detectByExtension(source.advisoryExtension)

        val byLayer: DetectionLayer = when {
            signature != null -> DetectionLayer.CONTENT_SIGNATURE
            extension != null -> DetectionLayer.ADVISORY_EXTENSION
            else -> DetectionLayer.ADVISORY_EXTENSION
        }

        val mismatches = mutableListOf<String>()
        if (signature != null && extension != null && signature != extension) {
            mismatches += "MAGIC_EXTENSION_MISMATCH"
        }
        val type = signature ?: extension ?: DetectedType.UNKNOWN
        return DetectedArtifact(type = type, byLayer = byLayer, mismatchFlags = mismatches)
    }

    private fun detectBySignature(source: ArtifactSource): DetectedType? {
        val bytes = source.readNBytes(8)
        return when {
            bytes.startsWith(byteArrayOf(0x7F, 0x45, 0x4C, 0x46)) -> DetectedType.ELF
            bytes.startsWith("PK\u0003\u0004".toByteArray()) -> DetectedType.ZIP
            bytes.startsWith("dex\n".toByteArray()) -> DetectedType.DEX
            bytes.startsWith("%PDF-".toByteArray()) -> DetectedType.PDF
            bytes.startsWith(OLE_MAGIC) -> DetectedType.OLE
            bytes.startsWith(byteArrayOf(0x1F, 0x8B.toByte())) -> DetectedType.GZIP
            bytes.startsWith("MZ".toByteArray()) -> DetectedType.PE
            else -> null
        }
    }

    private fun detectByExtension(extension: String?): DetectedType? =
        when (extension?.lowercase()) {
            "apk", "zip" -> DetectedType.ZIP
            "dex" -> DetectedType.DEX
            "pdf" -> DetectedType.PDF
            "doc", "xls", "ppt" -> DetectedType.OLE
            "elf", "so" -> DetectedType.ELF
            "exe", "dll" -> DetectedType.PE
            else -> null
        }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    private val OLE_MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
    )
}

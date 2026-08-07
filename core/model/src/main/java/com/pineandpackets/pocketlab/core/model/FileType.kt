package com.pineandpackets.pocketlab.core.model

import kotlinx.serialization.Serializable

@Serializable
data class FileTypeResult(
    val reportedType: String?,
    val extensionType: String?,
    val magicType: String?,
    val structuralType: String?,
    val confidence: FileTypeConfidence,
    val mismatchFlags: List<String> = emptyList()
)

@Serializable
enum class FileTypeConfidence {
    UNKNOWN,
    LOW,
    MEDIUM,
    HIGH,
    DEFINITIVE
}

@Serializable
enum class DetectedType {
    UNKNOWN,
    ZIP,
    APK,
    DEX,
    ELF,
    PE,
    PDF,
    OLE,
    GZIP,
    SEVEN_Z,
    RAR,
    TEXT,
    SCRIPT,
    APKS,
    XAPK,
    OOXML
}

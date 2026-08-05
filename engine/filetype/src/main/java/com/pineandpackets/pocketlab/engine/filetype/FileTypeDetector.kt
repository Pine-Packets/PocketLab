package com.pineandpackets.pocketlab.engine.filetype

import com.pineandpackets.pocketlab.core.model.DetectedType
import com.pineandpackets.pocketlab.core.model.FileTypeConfidence
import com.pineandpackets.pocketlab.core.model.FileTypeResult

object FileTypeDetector {
    
    fun detect(magicBytes: ByteArray, extension: String?, mimeType: String?): FileTypeResult {
        val magicType = detectByMagic(magicBytes)
        val extensionType = detectByExtension(extension)
        
        val confidence = when {
            magicType != null && extensionType != null && magicType == extensionType -> 
                FileTypeConfidence.DEFINITIVE
            magicType != null -> FileTypeConfidence.HIGH
            extensionType != null -> FileTypeConfidence.LOW
            else -> FileTypeConfidence.UNKNOWN
        }
        
        val mismatchFlags = mutableListOf<String>()
        if (magicType != null && extensionType != null && magicType != extensionType) {
            mismatchFlags.add("MAGIC_EXTENSION_MISMATCH")
        }
        
        return FileTypeResult(
            reportedType = mimeType,
            extensionType = extensionType?.name,
            magicType = magicType?.name,
            structuralType = null,
            confidence = confidence,
            mismatchFlags = mismatchFlags
        )
    }
    
    private fun detectByMagic(bytes: ByteArray): DetectedType? {
        if (bytes.size < 2) return null
        
        return when {
            bytes.isPe() -> DetectedType.PE
            bytes.size >= 4 && bytes.isZip() -> DetectedType.ZIP
            bytes.size >= 4 && bytes.isDex() -> DetectedType.DEX
            bytes.size >= 4 && bytes.isElf() -> DetectedType.ELF
            bytes.size >= 4 && bytes.isPdf() -> DetectedType.PDF
            bytes.size >= 8 && bytes.isOle() -> DetectedType.OLE
            bytes.size >= 2 && bytes.isGzip() -> DetectedType.GZIP
            bytes.size >= 6 && bytes.is7z() -> DetectedType.SEVEN_Z
            bytes.size >= 7 && bytes.isRar() -> DetectedType.RAR
            else -> null
        }
    }
    
    private fun ByteArray.isZip(): Boolean {
        if (size < 4) return false
        // ZIP local file header signature: PK\x03\x04
        // ZIP empty archive: PK\x05\x06
        // ZIP spanning archive: PK\x07\x08
        return this[0] == 0x50.toByte() && 
               this[1] == 0x4B.toByte() &&
               ((this[2] == 0x03.toByte() && this[3] == 0x04.toByte()) ||
                (this[2] == 0x05.toByte() && this[3] == 0x06.toByte()) ||
                (this[2] == 0x07.toByte() && this[3] == 0x08.toByte()))
    }
    
    private fun ByteArray.isDex(): Boolean {
        return size >= 4 &&
               this[0] == 'd'.code.toByte() &&
               this[1] == 'e'.code.toByte() &&
               this[2] == 'x'.code.toByte() &&
               this[3] == '\n'.code.toByte()
    }
    
    private fun ByteArray.isElf(): Boolean {
        return size >= 4 &&
               this[0] == 0x7F.toByte() &&
               this[1] == 'E'.code.toByte() &&
               this[2] == 'L'.code.toByte() &&
               this[3] == 'F'.code.toByte()
    }
    
    private fun ByteArray.isPe(): Boolean {
        return size >= 2 &&
               this[0] == 'M'.code.toByte() &&
               this[1] == 'Z'.code.toByte()
    }
    
    private fun ByteArray.isPdf(): Boolean {
        return size >= 4 &&
               this[0] == '%'.code.toByte() &&
               this[1] == 'P'.code.toByte() &&
               this[2] == 'D'.code.toByte() &&
               this[3] == 'F'.code.toByte()
    }
    
    private fun ByteArray.isOle(): Boolean {
        return size >= 8 &&
               this[0] == 0xD0.toByte() &&
               this[1] == 0xCF.toByte() &&
               this[2] == 0x11.toByte() &&
               this[3] == 0xE0.toByte() &&
               this[4] == 0xA1.toByte() &&
               this[5] == 0xB1.toByte() &&
               this[6] == 0x1A.toByte() &&
               this[7] == 0xE1.toByte()
    }
    
    private fun ByteArray.isGzip(): Boolean {
        return size >= 2 &&
               this[0] == 0x1F.toByte() &&
               this[1] == 0x8B.toByte()
    }
    
    private fun ByteArray.is7z(): Boolean {
        return size >= 6 &&
               this[0] == 0x37.toByte() &&
               this[1] == 0x7A.toByte() &&
               this[2] == 0xBC.toByte() &&
               this[3] == 0xAF.toByte() &&
               this[4] == 0x27.toByte() &&
               this[5] == 0x1C.toByte()
    }
    
    private fun ByteArray.isRar(): Boolean {
        return size >= 7 &&
               this[0] == 'R'.code.toByte() &&
               this[1] == 'a'.code.toByte() &&
               this[2] == 'r'.code.toByte() &&
               this[3] == '!'.code.toByte() &&
               this[4] == 0x1A.toByte() &&
               this[5] == 0x07.toByte() &&
               (this[6] == 0x00.toByte() || this[6] == 0x01.toByte())
    }
    
    private fun detectByExtension(extension: String?): DetectedType? {
        return when (extension?.lowercase()) {
            "apk" -> DetectedType.APK
            "zip" -> DetectedType.ZIP
            "dex" -> DetectedType.DEX
            "so", "elf" -> DetectedType.ELF
            "exe", "dll" -> DetectedType.PE
            "pdf" -> DetectedType.PDF
            "doc", "xls", "ppt" -> DetectedType.OLE
            "gz", "gzip" -> DetectedType.GZIP
            "7z" -> DetectedType.SEVEN_Z
            "rar" -> DetectedType.RAR
            "apks" -> DetectedType.APKS
            "xapk" -> DetectedType.XAPK
            else -> null
        }
    }
}

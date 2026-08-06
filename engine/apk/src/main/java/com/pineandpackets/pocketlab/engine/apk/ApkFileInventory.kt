package com.pineandpackets.pocketlab.engine.apk

import com.pineandpackets.pocketlab.core.model.FileInfo
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

class ApkFileInventory {
    
    fun inventoryApk(apkFile: File): Result<List<FileInfo>> {
        return try {
            ZipFile(apkFile).use { zip ->
                val files = mutableListOf<FileInfo>()
                
                zip.entries().asSequence().forEach { entry ->
                    if (!entry.isDirectory) {
                        val inputStream = zip.getInputStream(entry)
                        val bytes = inputStream.readBytes()
                        inputStream.close()
                        
                        val detectedType = detectFileType(bytes)
                        val sha256 = calculateSha256(bytes)
                        
                        files.add(
                            FileInfo(
                                id = "file-${files.size + 1}",
                                containerId = null,
                                virtualPath = entry.name,
                                compressedSize = entry.compressedSize,
                                expandedSize = entry.size,
                                compressionMethod = getCompressionMethodName(entry.method),
                                magicType = detectedType,
                                sha256 = sha256
                            )
                        )
                    }
                }
                
                Result.success(files)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to inventory APK files")
            Result.failure(e)
        }
    }
    
    private fun detectFileType(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "EMPTY"
        
        return when {
            // DEX files
            bytes.size >= 4 && bytes[0] == 'd'.toByte() && bytes[1] == 'e'.toByte() && 
            bytes[2] == 'x'.toByte() && bytes[3] == '\n'.toByte() -> "DEX"
            
            // ELF files
            bytes.size >= 4 && bytes[0] == 0x7F.toByte() && bytes[1] == 'E'.toByte() && 
            bytes[2] == 'L'.toByte() && bytes[3] == 'F'.toByte() -> "ELF"
            
            // PE files
            bytes.size >= 2 && bytes[0] == 'M'.toByte() && bytes[1] == 'Z'.toByte() -> "PE"
            
            // ZIP files
            bytes.size >= 4 && bytes[0] == 'P'.toByte() && bytes[1] == 'K'.toByte() && 
            (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte()) &&
            (bytes[3] == 0x04.toByte() || bytes[3] == 0x06.toByte() || bytes[3] == 0x08.toByte()) -> "ZIP"
            
            // XML files (text)
            bytes.size >= 5 && bytes[0] == '<'.toByte() && bytes[1] == '?'.toByte() && 
            bytes[2] == 'x'.toByte() && bytes[3] == 'm'.toByte() && bytes[4] == 'l'.toByte() -> "XML"
            
            // Binary XML (AXML)
            bytes.size >= 8 && bytes[0] == 0x03.toByte() && bytes[1] == 0x00.toByte() -> "AXML"
            
            // PNG files
            bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.toByte() && 
            bytes[2] == 'N'.toByte() && bytes[3] == 'G'.toByte() -> "PNG"
            
            // JPEG files
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && 
            bytes[2] == 0xFF.toByte() -> "JPEG"
            
            // GIF files
            bytes.size >= 6 && bytes[0] == 'G'.toByte() && bytes[1] == 'I'.toByte() && 
            bytes[2] == 'F'.toByte() && bytes[3] == '8'.toByte() -> "GIF"
            
            // WebP files
            bytes.size >= 12 && bytes[8] == 'W'.toByte() && bytes[9] == 'E'.toByte() && 
            bytes[10] == 'B'.toByte() && bytes[11] == 'P'.toByte() -> "WEBP"
            
            // OGG files
            bytes.size >= 4 && bytes[0] == 'O'.toByte() && bytes[1] == 'g'.toByte() && 
            bytes[2] == 'g'.toByte() && bytes[3] == 'S'.toByte() -> "OGG"
            
            // MP3 files
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0 -> "MP3"
            bytes.size >= 3 && bytes[0] == 'I'.toByte() && bytes[1] == 'D'.toByte() && 
            bytes[2] == '3'.toByte() -> "MP3"
            
            // PDF files
            bytes.size >= 4 && bytes[0] == '%'.toByte() && bytes[1] == 'P'.toByte() && 
            bytes[2] == 'D'.toByte() && bytes[3] == 'F'.toByte() -> "PDF"
            
            // JavaScript files (heuristic)
            bytes.size >= 10 && isLikelyJavaScript(bytes) -> "JAVASCRIPT"
            
            // JSON files (heuristic)
            bytes.size >= 1 && (bytes[0] == '{'.toByte() || bytes[0] == '['.toByte()) && 
            isLikelyJson(bytes) -> "JSON"
            
            // Text files (heuristic)
            isLikelyText(bytes) -> "TEXT"
            
            // High entropy (possibly encrypted/compressed)
            calculateEntropy(bytes) > 7.5 -> "HIGH_ENTROPY"
            
            else -> "UNKNOWN"
        }
    }
    
    private fun isLikelyText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        
        val sampleSize = minOf(bytes.size, 1024)
        var nonPrintable = 0
        
        for (i in 0 until sampleSize) {
            val b = bytes[i].toInt() and 0xFF
            if (b < 32 && b != 9 && b != 10 && b != 13) { // Not tab, newline, or carriage return
                nonPrintable++
            }
        }
        
        return nonPrintable.toDouble() / sampleSize < 0.1
    }
    
    private fun isLikelyJson(bytes: ByteArray): Boolean {
        val text = String(bytes, Charsets.UTF_8)
        return text.trimStart().let { it.startsWith("{") || it.startsWith("[") }
    }
    
    private fun isLikelyJavaScript(bytes: ByteArray): Boolean {
        val text = String(bytes.take(1024).toByteArray(), Charsets.UTF_8)
        return text.contains("function") || text.contains("var ") || 
               text.contains("const ") || text.contains("let ")
    }
    
    private fun calculateEntropy(bytes: ByteArray): Double {
        if (bytes.isEmpty()) return 0.0
        
        val frequency = IntArray(256)
        bytes.forEach { frequency[it.toInt() and 0xFF]++ }
        
        var entropy = 0.0
        val length = bytes.size.toDouble()
        
        frequency.forEach { count ->
            if (count > 0) {
                val probability = count / length
                entropy -= probability * Math.log(probability) / Math.log(2.0)
            }
        }
        
        return entropy
    }
    
    private fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    private fun getCompressionMethodName(method: Int): String {
        return when (method) {
            0 -> "STORED"
            8 -> "DEFLATE"
            else -> "METHOD_$method"
        }
    }
}

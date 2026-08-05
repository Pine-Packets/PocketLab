package com.pineandpackets.pocketlab.core.crypto

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

class EncryptedReportStorage(
    context: Context,
    private val encryptionManager: EncryptionManager
) {
    private val reportsDir = File(context.noBackupFilesDir, "reports").apply {
        if (!exists()) mkdirs()
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    suspend fun saveReport(caseId: String, report: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val reportFile = getReportFile(caseId)
            val plaintext = report.toByteArray(Charsets.UTF_8)
            val encrypted = encryptionManager.encrypt(plaintext)
            
            val encryptedData = EncryptedDataFile(
                ciphertext = encrypted.ciphertext,
                iv = encrypted.iv,
                version = encrypted.version
            )
            
            val serialized = json.encodeToString(encryptedData)
            reportFile.writeText(serialized)
            
            Timber.i("Saved encrypted report for case $caseId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save encrypted report for case $caseId")
            Result.failure(e)
        }
    }
    
    suspend fun loadReport(caseId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val reportFile = getReportFile(caseId)
            if (!reportFile.exists()) {
                return@withContext Result.failure(IllegalStateException("Report not found for case $caseId"))
            }
            
            val serialized = reportFile.readText()
            val encryptedData = json.decodeFromString<EncryptedDataFile>(serialized)
            
            val encrypted = EncryptedData(
                ciphertext = encryptedData.ciphertext,
                iv = encryptedData.iv,
                version = encryptedData.version
            )
            
            val plaintext = encryptionManager.decrypt(encrypted)
            val report = String(plaintext, Charsets.UTF_8)
            
            Timber.i("Loaded encrypted report for case $caseId")
            Result.success(report)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load encrypted report for case $caseId")
            Result.failure(e)
        }
    }
    
    suspend fun deleteReport(caseId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val reportFile = getReportFile(caseId)
            if (reportFile.exists()) {
                reportFile.delete()
                Timber.i("Deleted encrypted report for case $caseId")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete encrypted report for case $caseId")
            Result.failure(e)
        }
    }
    
    suspend fun reportExists(caseId: String): Boolean = withContext(Dispatchers.IO) {
        getReportFile(caseId).exists()
    }
    
    private fun getReportFile(caseId: String): File {
        return File(reportsDir, "$caseId.enc")
    }
}

@kotlinx.serialization.Serializable
data class EncryptedDataFile(
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val version: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedDataFile) return false
        
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (version != other.version) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + version
        return result
    }
}

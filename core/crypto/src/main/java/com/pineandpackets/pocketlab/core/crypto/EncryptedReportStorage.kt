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

    /**
     * Save analyst notes for a case. Notes are encrypted separately from the report
     * so the canonical report remains immutable while user annotations can change.
     */
    suspend fun saveNotes(caseId: String, notes: List<AnalystNote>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val notesFile = getNotesFile(caseId)
            val plaintext = json.encodeToString(notes).toByteArray(Charsets.UTF_8)
            val encrypted = encryptionManager.encrypt(plaintext)

            val encryptedData = EncryptedDataFile(
                ciphertext = encrypted.ciphertext,
                iv = encrypted.iv,
                version = encrypted.version
            )

            val serialized = json.encodeToString(encryptedData)
            notesFile.writeText(serialized)

            Timber.i("Saved encrypted analyst notes for case $caseId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save analyst notes for case $caseId")
            Result.failure(e)
        }
    }

    /**
     * Load analyst notes for a case.
     */
    suspend fun loadNotes(caseId: String): Result<List<AnalystNote>> = withContext(Dispatchers.IO) {
        try {
            val notesFile = getNotesFile(caseId)
            if (!notesFile.exists()) {
                return@withContext Result.success(emptyList())
            }

            val serialized = notesFile.readText()
            val encryptedData = json.decodeFromString<EncryptedDataFile>(serialized)

            val encrypted = EncryptedData(
                ciphertext = encryptedData.ciphertext,
                iv = encryptedData.iv,
                version = encryptedData.version
            )

            val plaintext = encryptionManager.decrypt(encrypted)
            val notes = json.decodeFromString<List<AnalystNote>>(String(plaintext, Charsets.UTF_8))

            Timber.i("Loaded encrypted analyst notes for case $caseId")
            Result.success(notes)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load analyst notes for case $caseId")
            Result.failure(e)
        }
    }

    /**
     * Delete analyst notes for a case.
     */
    suspend fun deleteNotes(caseId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val notesFile = getNotesFile(caseId)
            if (notesFile.exists()) {
                notesFile.delete()
                Timber.i("Deleted analyst notes for case $caseId")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete analyst notes for case $caseId")
            Result.failure(e)
        }
    }

    private fun getReportFile(caseId: String): File {
        return File(reportsDir, "$caseId.enc")
    }

    private fun getNotesFile(caseId: String): File {
        return File(reportsDir, "$caseId.notes.enc")
    }
}

@kotlinx.serialization.Serializable
data class AnalystNote(
    val id: String,
    val caseId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val author: String?,
    val content: String,
    val findingIds: List<String> = emptyList()
)

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

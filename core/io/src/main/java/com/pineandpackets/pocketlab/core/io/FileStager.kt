package com.pineandpackets.pocketlab.core.io

import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.pineandpackets.pocketlab.core.common.AnalysisError
import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import com.pineandpackets.pocketlab.core.common.HashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class StagingResult(
    val sha256: String,
    val sha1: String?,
    val md5: String?,
    val actualSize: Long
)

class FileStager(
    context: Context,
    private val workspace: CaseWorkspace
) {
    private val contentResolver = context.contentResolver
    private val noBackupDir = context.noBackupFilesDir
    
    suspend fun stageFile(
        uri: Uri,
        caseId: String,
        computeSha1: Boolean = true,
        computeMd5: Boolean = true
    ): Result<StagingResult> = withContext(Dispatchers.IO) {
        try {
            val storageCheck = checkAvailableStorage()
            if (storageCheck.isFailure) {
                val error = storageCheck.exceptionOrNull()
                return@withContext Result.failure(
                    if (error is AnalysisError) error
                    else AnalysisError.IntakeError("Insufficient storage space")
                )
            }

            val outputFile = workspace.getOriginalFile(caseId)
            
            val inputStream = contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(
                    AnalysisError.IntakeError("Cannot open input stream for URI: $uri")
                )
            
            inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val sha256Digest = MessageDigest.getInstance("SHA-256")
                    val sha1Digest = if (computeSha1) MessageDigest.getInstance("SHA-1") else null
                    val md5Digest = if (computeMd5) MessageDigest.getInstance("MD5") else null
                    
                    val buffer = ByteArray(AnalysisLimits.BUFFER_SIZE)
                    var totalBytesRead = 0L
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!coroutineContext.isActive) {
                            outputFile.delete()
                            return@withContext Result.failure(
                                AnalysisError.CancellationError("Staging cancelled")
                            )
                        }
                        
                        totalBytesRead += bytesRead
                        if (totalBytesRead > AnalysisLimits.MAX_INPUT_SIZE_BYTES) {
                            outputFile.delete()
                            return@withContext Result.failure(
                                AnalysisError.QuotaExceededError("File exceeds maximum size limit")
                            )
                        }
                        
                        output.write(buffer, 0, bytesRead)
                        sha256Digest.update(buffer, 0, bytesRead)
                        sha1Digest?.update(buffer, 0, bytesRead)
                        md5Digest?.update(buffer, 0, bytesRead)
                    }
                    
                    output.flush()
                    
                    val sha256 = sha256Digest.digest().toHex()
                    val sha1 = sha1Digest?.digest()?.toHex()
                    val md5 = md5Digest?.digest()?.toHex()
                    
                    Timber.i("Staged file for case $caseId: $totalBytesRead bytes, SHA-256: $sha256")
                    
                    Result.success(
                        StagingResult(
                            sha256 = sha256,
                            sha1 = sha1,
                            md5 = md5,
                            actualSize = totalBytesRead
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to stage file for case $caseId")
            workspace.getOriginalFile(caseId).delete()
            Result.failure(AnalysisError.IntakeError("Failed to stage file", e))
        }
    }

    fun checkAvailableStorage(): Result<Unit> {
        return try {
            val statFs = StatFs(noBackupDir.absolutePath)
            val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
            val minRequiredBytes = 200L * 1024 * 1024

            if (availableBytes < minRequiredBytes) {
                Timber.w("Insufficient storage: ${availableBytes / (1024 * 1024)}MB available, " +
                    "${minRequiredBytes / (1024 * 1024)}MB required")
                return Result.failure(
                    AnalysisError.IntakeError(
                        "Insufficient storage space. Available: ${availableBytes / (1024 * 1024)}MB, " +
                        "Required: ${minRequiredBytes / (1024 * 1024)}MB"
                    )
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.w(e, "Failed to check available storage, proceeding cautiously")
            Result.success(Unit)
        }
    }
    
    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }
}

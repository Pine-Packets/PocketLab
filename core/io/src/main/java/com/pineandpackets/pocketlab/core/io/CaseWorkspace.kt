package com.pineandpackets.pocketlab.core.io

import android.content.Context
import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import com.pineandpackets.pocketlab.core.common.HashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

class CaseWorkspace(context: Context) {
    
    private val casesDir = File(context.noBackupFilesDir, "cases").apply {
        if (!exists()) mkdirs()
    }
    
    fun getCaseDir(caseId: String): File {
        return File(casesDir, caseId).apply {
            if (!exists()) mkdirs()
        }
    }
    
    fun getOriginalFile(caseId: String): File {
        return File(getCaseDir(caseId), "original.bin")
    }
    
    fun getWorkspaceDir(caseId: String): File {
        return File(getCaseDir(caseId), "workspace").apply {
            if (!exists()) mkdirs()
        }
    }
    
    fun getExtractedDir(caseId: String): File {
        return File(getWorkspaceDir(caseId), "extracted").apply {
            if (!exists()) mkdirs()
        }
    }
    
    fun getScratchDir(caseId: String): File {
        return File(getWorkspaceDir(caseId), "scratch").apply {
            if (!exists()) mkdirs()
        }
    }
    
    suspend fun deleteCaseWorkspace(caseId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val caseDir = getCaseDir(caseId)
            if (caseDir.exists()) {
                caseDir.deleteRecursively()
                Timber.i("Deleted workspace for case $caseId")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete workspace for case $caseId")
            Result.failure(e)
        }
    }
    
    fun getCaseWorkspaceSize(caseId: String): Long {
        val caseDir = getCaseDir(caseId)
        return if (caseDir.exists()) {
            caseDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else {
            0L
        }
    }
}

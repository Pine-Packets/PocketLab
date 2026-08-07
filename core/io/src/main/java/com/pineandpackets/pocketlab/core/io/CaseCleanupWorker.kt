package com.pineandpackets.pocketlab.core.io

import com.pineandpackets.pocketlab.core.model.CaseMetadata
import com.pineandpackets.pocketlab.core.model.CaseStatus
import com.pineandpackets.pocketlab.core.model.RetentionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

data class CleanupResult(
    val casesDeleted: Int,
    val bytesFreed: Long,
    val errors: List<String>
)

class CaseCleanupWorker(
    private val workspace: CaseWorkspace
) {

    suspend fun cleanupExpiredCases(
        cases: List<CaseMetadata>,
        currentTimeMs: Long = System.currentTimeMillis()
    ): CleanupResult = withContext(Dispatchers.IO) {
        var deleted = 0
        var bytesFreed = 0L
        val errors = mutableListOf<String>()

        for (case in cases) {
            if (shouldDeleteCase(case, currentTimeMs)) {
                try {
                    val sizeBefore = workspace.getCaseWorkspaceSize(case.id.value)
                    val deleteResult = workspace.deleteCaseWorkspace(case.id.value)
                    if (deleteResult.isSuccess) {
                        deleted++
                        bytesFreed += sizeBefore
                        Timber.i("Cleaned up expired case ${case.id.value}")
                    } else {
                        errors.add("Failed to delete case ${case.id.value}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error cleaning up case ${case.id.value}")
                    errors.add("Error deleting case ${case.id.value}: ${e.message}")
                }
            }
        }

        Timber.i("Cleanup complete: $deleted cases deleted, ${bytesFreed / 1024}KB freed")
        CleanupResult(casesDeleted = deleted, bytesFreed = bytesFreed, errors = errors)
    }

    suspend fun deleteAllCases(cases: List<CaseMetadata>): CleanupResult = withContext(Dispatchers.IO) {
        var deleted = 0
        var bytesFreed = 0L
        val errors = mutableListOf<String>()

        for (case in cases) {
            if (case.status == CaseStatus.DELETED || case.status == CaseStatus.DELETION_PENDING) {
                continue
            }
            try {
                val sizeBefore = workspace.getCaseWorkspaceSize(case.id.value)
                val deleteResult = workspace.deleteCaseWorkspace(case.id.value)
                if (deleteResult.isSuccess) {
                    deleted++
                    bytesFreed += sizeBefore
                } else {
                    errors.add("Failed to delete case ${case.id.value}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting case ${case.id.value}")
                errors.add("Error deleting case ${case.id.value}: ${e.message}")
            }
        }

        Timber.i("Delete all complete: $deleted cases deleted, ${bytesFreed / 1024}KB freed")
        CleanupResult(casesDeleted = deleted, bytesFreed = bytesFreed, errors = errors)
    }

    fun cleanupScratchData(caseId: String) {
        try {
            val scratchDir = workspace.getScratchDir(caseId)
            if (scratchDir.exists()) {
                scratchDir.deleteRecursively()
                Timber.i("Cleaned scratch data for case $caseId")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to clean scratch data for case $caseId")
        }
    }

    private fun shouldDeleteCase(case: CaseMetadata, currentTimeMs: Long): Boolean {
        if (case.status == CaseStatus.DELETED || case.status == CaseStatus.DELETION_PENDING) {
            return false
        }

        if (case.status == CaseStatus.ANALYZING || case.status == CaseStatus.STAGING) {
            return false
        }

        val ageMs = currentTimeMs - case.updatedAt

        return when (case.retentionMode) {
            RetentionMode.SESSION_ONLY -> {
                ageMs > TimeUnit.HOURS.toMillis(4)
            }
            RetentionMode.TEMPORARY -> {
                !case.samplePresent && ageMs > TimeUnit.DAYS.toMillis(30)
            }
            RetentionMode.AUTO_DELETE_1_DAY -> {
                ageMs > TimeUnit.DAYS.toMillis(1)
            }
            RetentionMode.AUTO_DELETE_7_DAYS -> {
                ageMs > TimeUnit.DAYS.toMillis(7)
            }
            RetentionMode.AUTO_DELETE_30_DAYS -> {
                ageMs > TimeUnit.DAYS.toMillis(30)
            }
            RetentionMode.RETAIN_SAMPLE -> {
                false
            }
        }
    }
}

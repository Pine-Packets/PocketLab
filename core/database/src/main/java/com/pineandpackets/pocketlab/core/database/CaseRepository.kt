package com.pineandpackets.pocketlab.core.database

import com.pineandpackets.pocketlab.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID

class CaseRepository(
    private val database: PocketLabDatabase
) {
    private val caseDao = database.caseDao()
    
    fun getAllCases(): Flow<List<CaseMetadata>> {
        return caseDao.getAllCases().map { entities ->
            entities.map { it.toMetadata() }
        }
    }
    
    suspend fun getCaseById(caseId: CaseId): CaseMetadata? {
        return caseDao.getCaseById(caseId.value)?.toMetadata()
    }
    
    suspend fun getCaseBySha256(sha256: String): CaseMetadata? {
        return caseDao.getCaseBySha256(sha256)?.toMetadata()
    }
    
    fun getCasesByStatus(status: CaseStatus): Flow<List<CaseMetadata>> {
        return caseDao.getCasesByStatus(status).map { entities ->
            entities.map { it.toMetadata() }
        }
    }
    
    suspend fun createCase(
        sourceDisplayName: String,
        sourceMimeType: String?,
        sourceSizeReported: Long?,
        retentionMode: RetentionMode = RetentionMode.TEMPORARY
    ): CaseMetadata {
        return createCaseWithId(
            caseId = CaseId(UUID.randomUUID().toString()),
            sourceDisplayName = sourceDisplayName,
            sourceMimeType = sourceMimeType,
            sourceSizeReported = sourceSizeReported,
            retentionMode = retentionMode
        )
    }

    suspend fun createCaseWithId(
        caseId: CaseId,
        sourceDisplayName: String,
        sourceMimeType: String?,
        sourceSizeReported: Long?,
        retentionMode: RetentionMode = RetentionMode.TEMPORARY
    ): CaseMetadata {
        val now = System.currentTimeMillis()

        val case = CaseMetadata(
            id = caseId,
            createdAt = now,
            updatedAt = now,
            status = CaseStatus.CREATED,
            sourceDisplayName = sourceDisplayName,
            sourceMimeType = sourceMimeType,
            sourceSizeReported = sourceSizeReported,
            sourceSizeActual = null,
            sha256 = null,
            sha1 = null,
            md5 = null,
            primaryDetectedType = null,
            containerType = null,
            engineVersion = null,
            rulePackVersion = null,
            reportSchemaVersion = null,
            riskBand = null,
            maxSeverity = null,
            findingCount = null,
            retentionMode = retentionMode,
            samplePresent = false,
            reportPresent = false,
            lastErrorCode = null
        )

        caseDao.insertCase(case.toEntity())
        Timber.i("Created case ${caseId.value} for $sourceDisplayName")
        return case
    }
    
    suspend fun updateCase(case: CaseMetadata) {
        caseDao.updateCase(case.toEntity())
        Timber.d("Updated case ${case.id.value}")
    }
    
    suspend fun updateCaseStatus(caseId: CaseId, status: CaseStatus) {
        val case = getCaseById(caseId) ?: return
        val updated = case.copy(
            status = status,
            updatedAt = System.currentTimeMillis()
        )
        caseDao.updateCase(updated.toEntity())
        Timber.i("Updated case ${caseId.value} status to $status")
    }
    
    suspend fun updateCaseWithHashes(
        caseId: CaseId,
        sha256: String,
        sha1: String?,
        md5: String?,
        actualSize: Long
    ) {
        val case = getCaseById(caseId) ?: return
        val updated = case.copy(
            sha256 = sha256,
            sha1 = sha1,
            md5 = md5,
            sourceSizeActual = actualSize,
            samplePresent = true,
            updatedAt = System.currentTimeMillis()
        )
        caseDao.updateCase(updated.toEntity())
        Timber.i("Updated case ${caseId.value} with hashes")
    }
    
    suspend fun updateCaseWithReport(
        caseId: CaseId,
        riskBand: RiskBand,
        maxSeverity: Severity?,
        findingCount: Int,
        engineVersion: String,
        rulePackVersion: String,
        reportSchemaVersion: String
    ) {
        val case = getCaseById(caseId) ?: return
        val updated = case.copy(
            riskBand = riskBand,
            maxSeverity = maxSeverity,
            findingCount = findingCount,
            engineVersion = engineVersion,
            rulePackVersion = rulePackVersion,
            reportSchemaVersion = reportSchemaVersion,
            reportPresent = true,
            status = CaseStatus.COMPLETE,
            updatedAt = System.currentTimeMillis()
        )
        caseDao.updateCase(updated.toEntity())
        Timber.i("Updated case ${caseId.value} with report")
    }
    
    suspend fun deleteCase(caseId: CaseId) {
        caseDao.deleteCaseById(caseId.value)
        Timber.i("Deleted case ${caseId.value}")
    }
    
    suspend fun deleteAllCases() {
        caseDao.deleteAllCases()
        Timber.i("Deleted all cases")
    }
    
    suspend fun getCaseCount(): Int {
        return caseDao.getCaseCount()
    }
}

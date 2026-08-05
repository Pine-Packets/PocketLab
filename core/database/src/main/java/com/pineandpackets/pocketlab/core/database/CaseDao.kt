package com.pineandpackets.pocketlab.core.database

import androidx.room.*
import com.pineandpackets.pocketlab.core.model.CaseMetadata
import com.pineandpackets.pocketlab.core.model.CaseStatus
import com.pineandpackets.pocketlab.core.model.Confidence
import com.pineandpackets.pocketlab.core.model.RetentionMode
import com.pineandpackets.pocketlab.core.model.RiskBand
import com.pineandpackets.pocketlab.core.model.Severity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaseDao {
    @Query("SELECT * FROM cases ORDER BY createdAt DESC")
    fun getAllCases(): Flow<List<CaseEntity>>
    
    @Query("SELECT * FROM cases WHERE id = :caseId")
    suspend fun getCaseById(caseId: String): CaseEntity?
    
    @Query("SELECT * FROM cases WHERE sha256 = :sha256")
    suspend fun getCaseBySha256(sha256: String): CaseEntity?
    
    @Query("SELECT * FROM cases WHERE status = :status ORDER BY createdAt DESC")
    fun getCasesByStatus(status: CaseStatus): Flow<List<CaseEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCase(caseEntity: CaseEntity)
    
    @Update
    suspend fun updateCase(caseEntity: CaseEntity)
    
    @Query("DELETE FROM cases WHERE id = :caseId")
    suspend fun deleteCaseById(caseId: String)
    
    @Query("DELETE FROM cases")
    suspend fun deleteAllCases()
    
    @Query("SELECT COUNT(*) FROM cases")
    suspend fun getCaseCount(): Int
}

@Entity(tableName = "cases")
data class CaseEntity(
    @PrimaryKey
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: CaseStatus,
    val sourceDisplayName: String,
    val sourceMimeType: String?,
    val sourceSizeReported: Long?,
    val sourceSizeActual: Long?,
    val sha256: String?,
    val sha1: String?,
    val md5: String?,
    val primaryDetectedType: String?,
    val containerType: String?,
    val engineVersion: String?,
    val rulePackVersion: String?,
    val reportSchemaVersion: String?,
    val riskBand: RiskBand?,
    val maxSeverity: Severity?,
    val findingCount: Int?,
    val retentionMode: RetentionMode,
    val samplePresent: Boolean,
    val reportPresent: Boolean,
    val lastErrorCode: String?
)

fun CaseEntity.toMetadata(): CaseMetadata {
    return CaseMetadata(
        id = com.pineandpackets.pocketlab.core.model.CaseId(id),
        createdAt = createdAt,
        updatedAt = updatedAt,
        status = status,
        sourceDisplayName = sourceDisplayName,
        sourceMimeType = sourceMimeType,
        sourceSizeReported = sourceSizeReported,
        sourceSizeActual = sourceSizeActual,
        sha256 = sha256,
        sha1 = sha1,
        md5 = md5,
        primaryDetectedType = primaryDetectedType,
        containerType = containerType,
        engineVersion = engineVersion,
        rulePackVersion = rulePackVersion,
        reportSchemaVersion = reportSchemaVersion,
        riskBand = riskBand,
        maxSeverity = maxSeverity,
        findingCount = findingCount,
        retentionMode = retentionMode,
        samplePresent = samplePresent,
        reportPresent = reportPresent,
        lastErrorCode = lastErrorCode
    )
}

fun CaseMetadata.toEntity(): CaseEntity {
    return CaseEntity(
        id = id.value,
        createdAt = createdAt,
        updatedAt = updatedAt,
        status = status,
        sourceDisplayName = sourceDisplayName,
        sourceMimeType = sourceMimeType,
        sourceSizeReported = sourceSizeReported,
        sourceSizeActual = sourceSizeActual,
        sha256 = sha256,
        sha1 = sha1,
        md5 = md5,
        primaryDetectedType = primaryDetectedType,
        containerType = containerType,
        engineVersion = engineVersion,
        rulePackVersion = rulePackVersion,
        reportSchemaVersion = reportSchemaVersion,
        riskBand = riskBand,
        maxSeverity = maxSeverity,
        findingCount = findingCount,
        retentionMode = retentionMode,
        samplePresent = samplePresent,
        reportPresent = reportPresent,
        lastErrorCode = lastErrorCode
    )
}

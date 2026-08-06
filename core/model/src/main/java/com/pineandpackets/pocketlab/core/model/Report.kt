package com.pineandpackets.pocketlab.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AnalysisReport(
    val schemaVersion: String,
    val reportId: String,
    val caseId: String,
    val createdAt: String,
    val analysisStartedAt: String?,
    val analysisCompletedAt: String?,
    val engine: EngineInfo,
    val settings: AnalysisSettings,
    val source: SourceInfo,
    val containers: List<ContainerInfo> = emptyList(),
    val files: List<FileInfo> = emptyList(),
    val apk: ApkInfo? = null,
    val dex: List<DexInfo> = emptyList(),
    val nativeLibraries: List<NativeLibraryInfo> = emptyList(),
    val indicators: List<Indicator> = emptyList(),
    val facts: List<Fact> = emptyList(),
    val findings: List<Finding> = emptyList(),
    val summary: ReportSummary,
    val stageResults: List<StageResult> = emptyList(),
    val limitations: List<String> = emptyList(),
    val errors: List<AnalysisError> = emptyList(),
    val integrity: IntegrityBlock
)

@Serializable
data class EngineInfo(
    val appVersion: String,
    val engineVersion: String,
    val reportSchemaVersion: String,
    val rulePackVersion: String
)

@Serializable
data class AnalysisSettings(
    val analysisProfile: String,
    val hashAlgorithms: List<String>,
    val nativeAnalysisEnabled: Boolean,
    val deepDexAnalysisEnabled: Boolean,
    val iocExtractionEnabled: Boolean
)

@Serializable
data class SourceInfo(
    val displayName: String,
    val mimeType: String?,
    val sizeReported: Long?,
    val sizeActual: Long?,
    val sha256: String,
    val sha1: String?,
    val md5: String?
)

@Serializable
data class ContainerInfo(
    val id: String,
    val parentId: String? = null,
    val type: String,
    val path: String,
    val compressedSize: Long?,
    val expandedSize: Long?,
    val encrypted: Boolean,
    val entryCount: Int?
)

@Serializable
data class FileInfo(
    val id: String,
    val containerId: String? = null,
    val virtualPath: String,
    val compressedSize: Long?,
    val expandedSize: Long?,
    val compressionMethod: String?,
    val magicType: String?,
    val sha256: String?
)

@Serializable
data class ApkInfo(
    val packageName: String?,
    val versionName: String?,
    val versionCode: Long?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val compileSdk: Int?,
    val applicationLabel: String?,
    val debuggable: Boolean,
    val backupAllowed: Boolean = false,
    val usesCleartextTraffic: Boolean = false,
    val permissions: List<PermissionInfo> = emptyList(),
    val components: List<ComponentInfo> = emptyList(),
    val signingInfo: SigningInfo? = null
)

@Serializable
data class PermissionInfo(
    val name: String,
    val protectionLevel: String?,
    val declared: Boolean,
    val used: Boolean?
)

@Serializable
data class ComponentInfo(
    val name: String,
    val type: ComponentType,
    val exported: Boolean,
    val permission: String? = null,
    val intentFilters: List<IntentFilterInfo> = emptyList()
)

@Serializable
data class IntentFilterInfo(
    val actions: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val dataElements: List<DataElement> = emptyList(),
    val autoVerify: Boolean = false,
    val priority: Int? = null
)

@Serializable
data class DataElement(
    val scheme: String? = null,
    val host: String? = null,
    val port: String? = null,
    val path: String? = null,
    val pathPattern: String? = null,
    val pathPrefix: String? = null,
    val mimeType: String? = null
)

@Serializable
enum class ComponentType {
    ACTIVITY,
    SERVICE,
    RECEIVER,
    PROVIDER
}

@Serializable
data class SigningInfo(
    val signatureSchemes: List<String>,
    val verified: Boolean,
    val signerCount: Int,
    val certificates: List<CertificateInfo>
)

@Serializable
data class CertificateInfo(
    val subject: String,
    val issuer: String,
    val serialNumber: String,
    val validFrom: String,
    val validTo: String,
    val algorithm: String,
    val keySize: Int,
    val fingerprint: String,
    val selfSigned: Boolean
)

@Serializable
data class DexInfo(
    val name: String,
    val version: String,
    val classCount: Int,
    val methodCount: Int,
    val stringCount: Int,
    val size: Long,
    val strings: List<DexString> = emptyList(),
    val typeIds: List<DexTypeId> = emptyList(),
    val methodIds: List<DexMethodId> = emptyList(),
    val fieldIds: List<DexFieldId> = emptyList(),
    val classDefs: List<DexClassDef> = emptyList(),
    val apiReferences: List<ApiReference> = emptyList()
)

@Serializable
data class DexString(
    val index: Int,
    val value: String,
    val length: Int
)

@Serializable
data class DexTypeId(
    val index: Int,
    val descriptorIdx: Int,
    val descriptor: String
)

@Serializable
data class DexMethodId(
    val index: Int,
    val classIdx: Int,
    val protoIdx: Int,
    val nameIdx: Int,
    val className: String,
    val methodName: String,
    val prototype: String
)

@Serializable
data class DexFieldId(
    val index: Int,
    val classIdx: Int,
    val typeIdx: Int,
    val nameIdx: Int,
    val className: String,
    val fieldName: String,
    val fieldType: String
)

@Serializable
data class DexClassDef(
    val index: Int,
    val classIdx: Int,
    val accessFlags: Int,
    val superclassIdx: Int,
    val interfacesOff: Int,
    val sourceFileIdx: Int,
    val annotationsOff: Int,
    val classDataOff: Int,
    val staticValuesOff: Int,
    val className: String,
    val superclass: String?,
    val interfaces: List<String> = emptyList(),
    val sourceFile: String?,
    val methods: List<DexMethod> = emptyList()
)

@Serializable
data class DexMethod(
    val methodIdx: Int,
    val accessFlags: Int,
    val codeOff: Int,
    val name: String,
    val prototype: String,
    val instructionCount: Int = 0,
    val referencedStrings: List<String> = emptyList(),
    val referencedMethods: List<String> = emptyList()
)

@Serializable
data class ApiReference(
    val className: String,
    val methodName: String,
    val callSites: List<CallSite> = emptyList()
)

@Serializable
data class CallSite(
    val className: String,
    val methodName: String,
    val instructionOffset: Int,
    val context: String?
)

@Serializable
data class NativeLibraryInfo(
    val path: String,
    val abi: String,
    val size: Long,
    val sha256: String?,
    val architecture: String?
)

@Serializable
data class Fact(
    val id: String,
    val type: String,
    val value: String,
    val source: String?
)

@Serializable
data class StageResult(
    val stageId: String,
    val state: StageState,
    val startedAt: String?,
    val completedAt: String?,
    val progressCurrent: Int?,
    val progressTotal: Int?,
    val warningCount: Int,
    val errorCode: String?
)

@Serializable
enum class StageState {
    PENDING,
    RUNNING,
    COMPLETE,
    FAILED,
    SKIPPED
}

@Serializable
data class AnalysisError(
    val code: String,
    val message: String,
    val stage: String?
)

@Serializable
data class ReportSummary(
    val riskBand: RiskBand,
    val confidence: Confidence,
    val completeness: Double,
    val findingCount: Int,
    val maxSeverity: Severity?,
    val topFindings: List<String>
)

@Serializable
data class IntegrityBlock(
    val sourceSha256: String,
    val reportSha256: String,
    val engineVersion: String,
    val rulePackVersion: String,
    val sampleRetained: Boolean
)

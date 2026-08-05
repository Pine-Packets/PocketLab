package com.pineandpackets.pocketlab.engine.pipeline

import com.pineandpackets.pocketlab.core.common.AnalysisError
import com.pineandpackets.pocketlab.core.model.*
import com.pineandpackets.pocketlab.engine.apk.ApkAnalyzer
import com.pineandpackets.pocketlab.engine.archive.ArchiveAnalyzer
import com.pineandpackets.pocketlab.engine.archive.ArchiveAnalysisResult
import com.pineandpackets.pocketlab.engine.dex.DexAnalyzer
import com.pineandpackets.pocketlab.engine.filetype.FileTypeDetector
import com.pineandpackets.pocketlab.engine.ioc.IocExtractor
import com.pineandpackets.pocketlab.engine.rules.RulesEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

class AnalysisPipeline {
    
    private val archiveAnalyzer = ArchiveAnalyzer()
    private val apkAnalyzer = ApkAnalyzer()
    private val dexAnalyzer = DexAnalyzer()
    private val iocExtractor = IocExtractor()
    private val rulesEngine = RulesEngine()
    
    fun analyze(
        caseId: String,
        inputFile: File,
        hashes: HashResult
    ): Flow<AnalysisProgress> = flow {
        val startTime = System.currentTimeMillis()
        val findings = mutableListOf<Finding>()
        val indicators = mutableListOf<Indicator>()
        var apkInfo: ApkInfo? = null
        var archiveResult: ArchiveAnalysisResult? = null
        val dexInfos = mutableListOf<DexInfo>()
        
        try {
            emit(AnalysisProgress.StageStarted("file_type", "Detecting file type"))
            
            val fileBytes = inputFile.inputStream().use { it.readNBytes(8) }
            val extension = inputFile.extension
            val fileTypeResult = FileTypeDetector.detect(fileBytes, extension, null)
            val detectedType = fileTypeResult.magicType?.let { 
                try { DetectedType.valueOf(it) } catch (e: Exception) { null }
            }
            
            emit(AnalysisProgress.StageComplete("file_type"))
            
            if (detectedType == DetectedType.ZIP || detectedType == DetectedType.APK) {
                emit(AnalysisProgress.StageStarted("archive", "Analyzing archive"))
                val result = archiveAnalyzer.analyzeArchive(inputFile)
                if (result.isSuccess) {
                    archiveResult = result.getOrNull()
                    emit(AnalysisProgress.StageComplete("archive"))
                    
                    if (detectedType == DetectedType.APK || archiveResult?.entries?.any { it.normalizedPath.endsWith(".apk") } == true) {
                        emit(AnalysisProgress.StageStarted("apk", "Analyzing APK"))
                        val apkResult = apkAnalyzer.analyzeApk(inputFile)
                        if (apkResult.isSuccess) {
                            apkInfo = apkResult.getOrNull()
                            emit(AnalysisProgress.StageComplete("apk"))
                            
                            emit(AnalysisProgress.StageStarted("dex", "Analyzing DEX files"))
                            val dexFiles = extractDexFiles(inputFile)
                            dexFiles.forEach { dexFile ->
                                val dexResult = dexAnalyzer.analyzeDex(dexFile)
                                if (dexResult.isSuccess) {
                                    dexResult.getOrNull()?.let { dexInfos.add(it) }
                                }
                                dexFile.delete()
                            }
                            emit(AnalysisProgress.StageComplete("dex"))
                        } else {
                            emit(AnalysisProgress.StageFailed("apk", apkResult.exceptionOrNull()?.message ?: "Unknown error"))
                        }
                    }
                } else {
                    emit(AnalysisProgress.StageFailed("archive", result.exceptionOrNull()?.message ?: "Unknown error"))
                }
            } else if (detectedType == DetectedType.DEX) {
                emit(AnalysisProgress.StageStarted("dex", "Analyzing DEX file"))
                val dexResult = dexAnalyzer.analyzeDex(inputFile)
                if (dexResult.isSuccess) {
                    dexResult.getOrNull()?.let { dexInfos.add(it) }
                    emit(AnalysisProgress.StageComplete("dex"))
                } else {
                    emit(AnalysisProgress.StageFailed("dex", dexResult.exceptionOrNull()?.message ?: "Unknown error"))
                }
            }
            
            if (apkInfo != null) {
                emit(AnalysisProgress.StageStarted("rules", "Evaluating rules"))
                findings.addAll(rulesEngine.evaluateRules(apkInfo))
                emit(AnalysisProgress.StageComplete("rules"))
            }
            
            emit(AnalysisProgress.StageStarted("ioc", "Extracting indicators"))
            val strings = extractStrings(inputFile)
            strings.forEach { str ->
                indicators.addAll(iocExtractor.extractIndicators(str))
            }
            emit(AnalysisProgress.StageComplete("ioc"))
            
            val duration = System.currentTimeMillis() - startTime
            val report = buildReport(
                caseId = caseId,
                hashes = hashes,
                apkInfo = apkInfo,
                archiveResult = archiveResult,
                dexInfos = dexInfos,
                findings = findings,
                indicators = indicators,
                duration = duration
            )
            
            emit(AnalysisProgress.Complete(report))
            
        } catch (e: Exception) {
            Timber.e(e, "Analysis failed")
            emit(AnalysisProgress.Error(e.message ?: "Analysis failed"))
        }
    }
    
    private fun extractDexFiles(apkFile: File): List<File> {
        val dexFiles = mutableListOf<File>()
        try {
            ZipFile(apkFile).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.endsWith(".dex") }
                    .forEach { entry ->
                        val tempFile = File.createTempFile("dex_", ".dex")
                        zip.getInputStream(entry).use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        dexFiles.add(tempFile)
                    }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract DEX files")
        }
        return dexFiles
    }
    
    private fun extractStrings(file: File): List<String> {
        val strings = mutableListOf<String>()
        try {
            val bytes = file.readBytes()
            val stringRegex = Regex("[\\x20-\\x7E]{8,}")
            strings.addAll(stringRegex.findAll(String(bytes)).map { it.value })
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract strings")
        }
        return strings
    }
    
    private fun buildReport(
        caseId: String,
        hashes: HashResult,
        apkInfo: ApkInfo?,
        archiveResult: ArchiveAnalysisResult?,
        dexInfos: List<DexInfo>,
        findings: List<Finding>,
        indicators: List<Indicator>,
        duration: Long
    ): AnalysisReport {
        val riskBand = calculateRiskBand(findings)
        val maxSeverity = findings.maxByOrNull { it.severity.ordinal }?.severity
        
        return AnalysisReport(
            schemaVersion = "1.0.0",
            reportId = UUID.randomUUID().toString(),
            caseId = caseId,
            createdAt = System.currentTimeMillis().toString(),
            analysisStartedAt = (System.currentTimeMillis() - duration).toString(),
            analysisCompletedAt = System.currentTimeMillis().toString(),
            engine = EngineInfo(
                appVersion = "1.0.0",
                engineVersion = "1.0.0",
                reportSchemaVersion = "1.0.0",
                rulePackVersion = "1.0.0"
            ),
            settings = AnalysisSettings(
                analysisProfile = "standard",
                hashAlgorithms = listOf("SHA-256", "SHA-1", "MD5"),
                nativeAnalysisEnabled = true,
                deepDexAnalysisEnabled = true,
                iocExtractionEnabled = true
            ),
            source = SourceInfo(
                displayName = "",
                mimeType = null,
                sizeReported = null,
                sizeActual = null,
                sha256 = hashes.sha256,
                sha1 = hashes.sha1,
                md5 = hashes.md5
            ),
            containers = emptyList(),
            files = emptyList(),
            apk = apkInfo,
            dex = dexInfos,
            nativeLibraries = emptyList(),
            indicators = indicators,
            facts = emptyList(),
            findings = findings,
            summary = ReportSummary(
                riskBand = riskBand,
                confidence = Confidence.HIGH,
                completeness = 1.0,
                findingCount = findings.size,
                maxSeverity = maxSeverity,
                topFindings = findings.take(3).map { it.title }
            ),
            stageResults = emptyList(),
            limitations = emptyList(),
            errors = emptyList(),
            integrity = IntegrityBlock(
                sourceSha256 = hashes.sha256,
                reportSha256 = "",
                engineVersion = "1.0.0",
                rulePackVersion = "1.0.0",
                sampleRetained = false
            )
        )
    }
    
    private fun calculateRiskBand(findings: List<Finding>): RiskBand {
        if (findings.isEmpty()) return RiskBand.NO_MAJOR_CONCERNS
        
        val maxSeverity = findings.maxByOrNull { it.severity.ordinal }?.severity
        
        return when (maxSeverity) {
            Severity.CRITICAL -> RiskBand.HIGH_RISK_INDICATORS
            Severity.HIGH -> RiskBand.HIGH_RISK_INDICATORS
            Severity.MEDIUM -> RiskBand.SUSPICIOUS_CAPABILITIES
            Severity.LOW -> RiskBand.REVIEW_RECOMMENDED
            Severity.INFORMATIONAL -> RiskBand.NO_MAJOR_CONCERNS
            null -> RiskBand.NO_MAJOR_CONCERNS
        }
    }
}

sealed class AnalysisProgress {
    data class StageStarted(val stageId: String, val stageName: String) : AnalysisProgress()
    data class StageComplete(val stageId: String) : AnalysisProgress()
    data class StageFailed(val stageId: String, val error: String) : AnalysisProgress()
    data class Complete(val report: AnalysisReport) : AnalysisProgress()
    data class Error(val message: String) : AnalysisProgress()
}

data class HashResult(
    val sha256: String,
    val sha1: String,
    val md5: String
)

package com.pineandpackets.pocketlab.engine.pipeline

import com.pineandpackets.pocketlab.core.common.AnalysisError as CommonAnalysisError
import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import com.pineandpackets.pocketlab.core.model.AnalysisError as ModelAnalysisError
import com.pineandpackets.pocketlab.core.model.*
import com.pineandpackets.pocketlab.engine.apk.ApkAnalyzer
import com.pineandpackets.pocketlab.engine.apk.ApksigVerifier
import com.pineandpackets.pocketlab.engine.apk.ApkStructureValidator
import com.pineandpackets.pocketlab.engine.archive.ArchiveAnalyzer
import com.pineandpackets.pocketlab.engine.archive.ArchiveAnalysisResult
import com.pineandpackets.pocketlab.engine.archive.CaseZipTextScanner
import com.pineandpackets.pocketlab.engine.archive.PackageSetAnalyzer
import com.pineandpackets.pocketlab.engine.dex.DexAnalyzer
import com.pineandpackets.pocketlab.engine.dex.ReflectionDetector
import com.pineandpackets.pocketlab.engine.filetype.FileTypeDetector
import com.pineandpackets.pocketlab.engine.ioc.IocExtractor
import com.pineandpackets.pocketlab.engine.rules.RulesEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

class AnalysisPipeline(
    private val config: AnalysisConfig = AnalysisConfig()
) {
    
    private val archiveAnalyzer = ArchiveAnalyzer()
    private val packageSetAnalyzer = PackageSetAnalyzer()
    private val apkAnalyzer = ApkAnalyzer()
    private val apkStructureValidator = ApkStructureValidator()
    private val apksigVerifier = ApksigVerifier()
    private val dexAnalyzer = DexAnalyzer()
    private val iocExtractor = IocExtractor()
    private val caseZipTextScanner = CaseZipTextScanner()
    private val rulesEngine = RulesEngine()
    private val reflectionDetector = ReflectionDetector()
    
    fun analyze(
        caseId: String,
        inputFile: File,
        hashes: HashResult,
        archivePassword: String? = null
    ): Flow<AnalysisProgress> = flow {
        val startTime = System.currentTimeMillis()
        val findings = mutableListOf<Finding>()
        val indicators = mutableListOf<Indicator>()
        var apkInfo: ApkInfo? = null
        var archiveResult: ArchiveAnalysisResult? = null
        val dexInfos = mutableListOf<DexInfo>()
        val caseTextInventory = mutableListOf<CaseTextEntry>()
        val stageResults = mutableListOf<StageResult>()
        val limitations = mutableListOf<String>()
        val errors = mutableListOf<ModelAnalysisError>()
        
        try {
            emit(AnalysisProgress.StageStarted("file_type", "Detecting file type"))
            val stageStart = System.currentTimeMillis()
            
            val fileBytes = inputFile.inputStream().use { it.readNBytes(8) }
            val extension = inputFile.extension
            val fileTypeResult = FileTypeDetector.detect(fileBytes, extension, null)
            val detectedType = fileTypeResult.magicType?.let { 
                try { DetectedType.valueOf(it) } catch (e: Exception) { null }
            }
            
            stageResults.add(StageResult(
                stageId = "file_type",
                state = StageState.COMPLETE,
                startedAt = stageStart.toString(),
                completedAt = System.currentTimeMillis().toString(),
                progressCurrent = 100,
                progressTotal = 100,
                warningCount = 0,
                errorCode = null
            ))
            emit(AnalysisProgress.StageComplete("file_type"))
            
            if (detectedType == DetectedType.ZIP || detectedType == DetectedType.APK) {
                emit(AnalysisProgress.StageStarted("archive", "Analyzing archive"))
                val archiveStart = System.currentTimeMillis()
                val result = if (archivePassword != null) {
                    archiveAnalyzer.analyzeArchive(inputFile, archivePassword)
                } else {
                    archiveAnalyzer.analyzeArchiveWithPasswordAttempts(inputFile)
                }
                if (result.isSuccess) {
                    archiveResult = result.getOrNull()
                    stageResults.add(StageResult(
                        stageId = "archive",
                        state = StageState.COMPLETE,
                        startedAt = archiveStart.toString(),
                        completedAt = System.currentTimeMillis().toString(),
                        progressCurrent = 100,
                        progressTotal = 100,
                        warningCount = archiveResult?.suspiciousPaths?.size ?: 0,
                        errorCode = null
                    ))
                    emit(AnalysisProgress.StageComplete("archive"))

                    // Case archive text/notes inventory (WF-004): stream bounded
                    // text entries and extract indicators with container provenance.
                    if (config.iocExtractionEnabled) {
                        caseZipTextScanner.scan(inputFile).getOrNull().orEmpty().forEach { textEntry ->
                            caseTextInventory.add(textEntry)
                            textEntry.indicators.forEach { indicators.add(it) }
                        }
                    }

                    if (archiveResult?.passwordRequired == true) {
                        emit(AnalysisProgress.StageFailed("archive", "Archive is encrypted and requires password"))
                    } else if (packageSetAnalyzer.detectPackageSetType(inputFile) != null) {
                        emit(AnalysisProgress.StageStarted("packageset", "Analyzing package set"))
                        val pkgStart = System.currentTimeMillis()
                        val pkgResult = packageSetAnalyzer.analyzePackageSet(inputFile)
                        if (pkgResult.isSuccess) {
                            val pkgAnalysis = pkgResult.getOrNull()!!
                            apkInfo = pkgAnalysis.mergedApkInfo
                            pkgAnalysis.warnings.forEach { warnings ->
                                limitations.add("Package set warning: $warnings")
                            }
                            pkgAnalysis.errors.forEach { errorMsg ->
                                errors.add(ModelAnalysisError(
                                    code = "PACKAGE_SET_ENTRY_ERROR",
                                    message = errorMsg,
                                    stage = "packageset"
                                ))
                            }
                            stageResults.add(StageResult(
                                stageId = "packageset",
                                state = StageState.COMPLETE,
                                startedAt = pkgStart.toString(),
                                completedAt = System.currentTimeMillis().toString(),
                                progressCurrent = pkgAnalysis.analyzedApkCount,
                                progressTotal = pkgAnalysis.apkCount,
                                warningCount = pkgAnalysis.warnings.size,
                                errorCode = null
                            ))
                            emit(AnalysisProgress.StageComplete("packageset"))
                            
                            emit(AnalysisProgress.StageStarted("dex", "Analyzing DEX files"))
                            val dexStart = System.currentTimeMillis()
                            val dexFiles = extractDexFilesFromPackageSet(inputFile)
                            var dexWarnings = 0
                            dexFiles.forEach { dexFile ->
                                val dexResult = dexAnalyzer.analyzeDex(dexFile)
                                if (dexResult.isSuccess) {
                                    dexResult.getOrNull()?.let { dexInfos.add(it) }
                                } else {
                                    dexWarnings++
                                    errors.add(ModelAnalysisError(
                                        code = "DEX_PARSE_ERROR",
                                        message = dexResult.exceptionOrNull()?.message ?: "Unknown DEX parse error",
                                        stage = "dex"
                                    ))
                                }
                                dexFile.delete()
                            }
                            stageResults.add(StageResult(
                                stageId = "dex",
                                state = StageState.COMPLETE,
                                startedAt = dexStart.toString(),
                                completedAt = System.currentTimeMillis().toString(),
                                progressCurrent = dexInfos.size,
                                progressTotal = dexFiles.size,
                                warningCount = dexWarnings,
                                errorCode = null
                            ))
                            emit(AnalysisProgress.StageComplete("dex"))
                            
                            if (config.deepDexAnalysisEnabled) {
                                emit(AnalysisProgress.StageStarted("code_analysis", "Analyzing code patterns"))
                                val codeStart = System.currentTimeMillis()
                                dexInfos.forEach { dexInfo ->
                                    val reflectionFindings = reflectionDetector.detectPatterns(dexInfo)
                                    reflectionFindings.forEach { refFinding ->
                                        findings.add(
                                            Finding(
                                                id = UUID.randomUUID().toString(),
                                                ruleId = "REFLECTION_${refFinding.patternType}",
                                                title = refFinding.description,
                                                category = "code_pattern",
                                                severity = refFinding.severity,
                                                confidence = Confidence.HIGH,
                                                simpleExplanation = refFinding.evidence,
                                                analystExplanation = "Detected ${refFinding.patternType} pattern in ${refFinding.className}.${refFinding.methodName}",
                                                evidence = listOf(
                                                    Evidence(
                                                        type = EvidenceType.DEX_CALL_SITE,
                                                        fileId = null,
                                                        dexName = dexInfo.name,
                                                        className = refFinding.className,
                                                        method = refFinding.methodName,
                                                        offset = null,
                                                        excerpt = refFinding.evidence,
                                                        excerptEncoding = "text"
                                                    )
                                                ),
                                                limitations = listOf("Static analysis cannot determine if this code path is executed"),
                                                recommendations = listOf("Review if this pattern is necessary for app functionality"),
                                                mappings = emptyList(),
                                                references = emptyList()
                                            )
                                        )
                                    }
                                }
                                stageResults.add(StageResult(
                                    stageId = "code_analysis",
                                    state = StageState.COMPLETE,
                                    startedAt = codeStart.toString(),
                                    completedAt = System.currentTimeMillis().toString(),
                                    progressCurrent = findings.size,
                                    progressTotal = null,
                                    warningCount = 0,
                                    errorCode = null
                                ))
                                emit(AnalysisProgress.StageComplete("code_analysis"))
                            } else {
                                stageResults.add(StageResult(
                                    stageId = "code_analysis",
                                    state = StageState.SKIPPED,
                                    startedAt = null,
                                    completedAt = null,
                                    progressCurrent = null,
                                    progressTotal = null,
                                    warningCount = 0,
                                    errorCode = null
                                ))
                                limitations.add("Deep DEX analysis disabled by configuration")
                            }
                            
                            emit(AnalysisProgress.StageStarted("signing", "Verifying signatures"))
                            val signingStart = System.currentTimeMillis()
                            val baseApk = extractBaseApk(inputFile)
                            if (baseApk != null) {
                                val signingResult = apksigVerifier.verify(baseApk)
                                if (signingResult.verified && signingResult.signingInfo != null) {
                                    apkInfo = apkInfo?.copy(signingInfo = signingResult.signingInfo)
                                    stageResults.add(StageResult(
                                        stageId = "signing",
                                        state = StageState.COMPLETE,
                                        startedAt = signingStart.toString(),
                                        completedAt = System.currentTimeMillis().toString(),
                                        progressCurrent = 100,
                                        progressTotal = 100,
                                        warningCount = signingResult.warnings.size,
                                        errorCode = null
                                    ))
                                } else {
                                    stageResults.add(StageResult(
                                        stageId = "signing",
                                        state = StageState.FAILED,
                                        startedAt = signingStart.toString(),
                                        completedAt = System.currentTimeMillis().toString(),
                                        progressCurrent = 0,
                                        progressTotal = 100,
                                        warningCount = 0,
                                        errorCode = "SIGNING_VERIFICATION_FAILED"
                                    ))
                                    if (signingResult.errors.isNotEmpty()) {
                                        errors.add(ModelAnalysisError(
                                            code = "SIGNING_VERIFICATION_FAILED",
                                            message = signingResult.errors.joinToString("; "),
                                            stage = "signing"
                                        ))
                                    }
                                }
                                emit(AnalysisProgress.StageComplete("signing"))
                                baseApk.delete()
                            } else {
                                stageResults.add(StageResult(
                                    stageId = "signing",
                                    state = StageState.SKIPPED,
                                    startedAt = null,
                                    completedAt = null,
                                    progressCurrent = null,
                                    progressTotal = null,
                                    warningCount = 0,
                                    errorCode = null
                                ))
                                emit(AnalysisProgress.StageComplete("signing"))
                            }
                        } else {
                            val errorMsg = pkgResult.exceptionOrNull()?.message ?: "Unknown error"
                            stageResults.add(StageResult(
                                stageId = "packageset",
                                state = StageState.FAILED,
                                startedAt = pkgStart.toString(),
                                completedAt = System.currentTimeMillis().toString(),
                                progressCurrent = 0,
                                progressTotal = 100,
                                warningCount = 0,
                                errorCode = "PACKAGE_SET_ERROR"
                            ))
                            errors.add(ModelAnalysisError(
                                code = "PACKAGE_SET_ERROR",
                                message = errorMsg,
                                stage = "packageset"
                            ))
                            emit(AnalysisProgress.StageFailed("packageset", errorMsg))
                        }
                    } else {
                        val hasApkEntries = archiveResult?.entries?.any { it.normalizedPath.endsWith(".apk") } == true
                        if (hasApkEntries) {
                            emit(AnalysisProgress.StageStarted("apk", "Analyzing APK contained in archive"))
                            val apkStart = System.currentTimeMillis()
                            val containedApk = extractPrimaryApk(inputFile)
                            if (containedApk != null) {
                                var containedInfo: ApkInfo? = null
                                try {
                                    containedInfo = analyzeApkFile(
                                        containedApk, dexInfos, findings, errors, limitations, stageResults
                                    )
                                } finally {
                                    containedApk.delete()
                                }
                                apkInfo = containedInfo
                                if (apkInfo != null) {
                                    limitations.add(
                                        "APK was analyzed from inside an archive container; " +
                                        "container entry inventory is recorded in the archive section"
                                    )
                                }
                            } else {
                                stageResults.add(StageResult(
                                    stageId = "apk",
                                    state = StageState.FAILED,
                                    startedAt = apkStart.toString(),
                                    completedAt = System.currentTimeMillis().toString(),
                                    progressCurrent = 0,
                                    progressTotal = 100,
                                    warningCount = 0,
                                    errorCode = "APK_ENTRY_EXTRACTION_FAILED"
                                ))
                                errors.add(ModelAnalysisError(
                                    code = "APK_ENTRY_EXTRACTION_FAILED",
                                    message = "Archive contains APK entries but no analyzable APK could be extracted",
                                    stage = "apk"
                                ))
                                emit(AnalysisProgress.StageFailed("apk", "No analyzable APK entry could be extracted"))
                                limitations.add("Archive contained APK entries that could not be extracted for analysis")
                            }
                        } else if (isApkContainer(inputFile)) {
                            apkInfo = analyzeApkFile(inputFile, dexInfos, findings, errors, limitations, stageResults)
                        } else {
                            limitations.add("Archive contains no analyzable APK content; all entries were inventoried only")
                        }
                    }
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    stageResults.add(StageResult(
                        stageId = "archive",
                        state = StageState.FAILED,
                        startedAt = archiveStart.toString(),
                        completedAt = System.currentTimeMillis().toString(),
                        progressCurrent = 0,
                        progressTotal = 100,
                        warningCount = 0,
                        errorCode = "ARCHIVE_ERROR"
                    ))
                    errors.add(ModelAnalysisError(
                        code = "ARCHIVE_ERROR",
                        message = errorMsg,
                        stage = "archive"
                    ))
                    emit(AnalysisProgress.StageFailed("archive", errorMsg))
                }
            } else if (detectedType == DetectedType.DEX) {
                emit(AnalysisProgress.StageStarted("dex", "Analyzing DEX file"))
                val dexStart = System.currentTimeMillis()
                val dexResult = dexAnalyzer.analyzeDex(inputFile)
                if (dexResult.isSuccess) {
                    dexResult.getOrNull()?.let { dexInfos.add(it) }
                    stageResults.add(StageResult(
                        stageId = "dex",
                        state = StageState.COMPLETE,
                        startedAt = dexStart.toString(),
                        completedAt = System.currentTimeMillis().toString(),
                        progressCurrent = 1,
                        progressTotal = 1,
                        warningCount = 0,
                        errorCode = null
                    ))
                    emit(AnalysisProgress.StageComplete("dex"))
                } else {
                    val errorMsg = dexResult.exceptionOrNull()?.message ?: "Unknown error"
                    stageResults.add(StageResult(
                        stageId = "dex",
                        state = StageState.FAILED,
                        startedAt = dexStart.toString(),
                        completedAt = System.currentTimeMillis().toString(),
                        progressCurrent = 0,
                        progressTotal = 1,
                        warningCount = 0,
                        errorCode = "DEX_PARSE_ERROR"
                    ))
                    errors.add(ModelAnalysisError(
                        code = "DEX_PARSE_ERROR",
                        message = errorMsg,
                        stage = "dex"
                    ))
                    emit(AnalysisProgress.StageFailed("dex", errorMsg))
                }
            }
            
            if (apkInfo != null) {
                emit(AnalysisProgress.StageStarted("rules", "Evaluating rules"))
                val rulesStart = System.currentTimeMillis()
                findings.addAll(rulesEngine.evaluateRules(apkInfo, dexInfos))
                stageResults.add(StageResult(
                    stageId = "rules",
                    state = StageState.COMPLETE,
                    startedAt = rulesStart.toString(),
                    completedAt = System.currentTimeMillis().toString(),
                    progressCurrent = findings.size,
                    progressTotal = null,
                    warningCount = 0,
                    errorCode = null
                ))
                emit(AnalysisProgress.StageComplete("rules"))
            }
            
            if (config.iocExtractionEnabled) {
                emit(AnalysisProgress.StageStarted("ioc", "Extracting indicators"))
                val iocStart = System.currentTimeMillis()
                val strings = extractStrings(inputFile)
                strings.forEach { str ->
                    indicators.addAll(iocExtractor.extractIndicators(str))
                }
                stageResults.add(StageResult(
                    stageId = "ioc",
                    state = StageState.COMPLETE,
                    startedAt = iocStart.toString(),
                    completedAt = System.currentTimeMillis().toString(),
                    progressCurrent = indicators.size,
                    progressTotal = null,
                    warningCount = 0,
                    errorCode = null
                ))
                emit(AnalysisProgress.StageComplete("ioc"))
            } else {
                stageResults.add(StageResult(
                    stageId = "ioc",
                    state = StageState.SKIPPED,
                    startedAt = null,
                    completedAt = null,
                    progressCurrent = null,
                    progressTotal = null,
                    warningCount = 0,
                    errorCode = null
                ))
                limitations.add("IOC extraction disabled by configuration")
            }
            
            if (apkInfo?.signingInfo == null) {
                limitations.add("Signature verification not performed")
            }
            limitations.add("Static analysis cannot prove runtime behavior")
            
            val duration = System.currentTimeMillis() - startTime
            val report = buildReport(
                caseId = caseId,
                hashes = hashes,
                apkInfo = apkInfo,
                archiveResult = archiveResult,
                caseTextInventory = caseTextInventory,
                dexInfos = dexInfos,
                findings = findings,
                indicators = indicators,
                stageResults = stageResults,
                limitations = limitations,
                errors = errors,
                duration = duration
            )
            
            emit(AnalysisProgress.Complete(report))
            
        } catch (e: Exception) {
            Timber.e(e, "Analysis failed")
            emit(AnalysisProgress.Error(e.message ?: "Analysis failed"))
        }
    }
    
    /**
     * Runs the single-APK analysis stages (structure, metadata, signing, DEX,
     * code patterns) against an APK file and returns the parsed [ApkInfo].
     * Used for raw APKs and for APK entries extracted from case archives.
     */
    private suspend fun FlowCollector<AnalysisProgress>.analyzeApkFile(
        apkFile: File,
        dexInfos: MutableList<DexInfo>,
        findings: MutableList<Finding>,
        errors: MutableList<ModelAnalysisError>,
        limitations: MutableList<String>,
        stageResults: MutableList<StageResult>
    ): ApkInfo? {
        var apkInfo: ApkInfo? = null
        
        emit(AnalysisProgress.StageStarted("apk_structure", "Validating APK structure"))
        val structureStart = System.currentTimeMillis()
        val structureResult = apkStructureValidator.validate(apkFile)
        if (structureResult.isValid) {
            stageResults.add(StageResult(
                stageId = "apk_structure",
                state = StageState.COMPLETE,
                startedAt = structureStart.toString(),
                completedAt = System.currentTimeMillis().toString(),
                progressCurrent = 100,
                progressTotal = 100,
                warningCount = structureResult.warnings.size,
                errorCode = null
            ))
            emit(AnalysisProgress.StageComplete("apk_structure"))
        } else {
            stageResults.add(StageResult(
                stageId = "apk_structure",
                state = StageState.FAILED,
                startedAt = structureStart.toString(),
                completedAt = System.currentTimeMillis().toString(),
                progressCurrent = 0,
                progressTotal = 100,
                warningCount = 0,
                errorCode = "APK_STRUCTURE_INVALID"
            ))
            errors.add(ModelAnalysisError(
                code = "APK_STRUCTURE_INVALID",
                message = structureResult.errors.joinToString("; "),
                stage = "apk_structure"
            ))
            emit(AnalysisProgress.StageFailed("apk_structure", structureResult.errors.joinToString("; ")))
        }
        
        emit(AnalysisProgress.StageStarted("apk", "Analyzing APK"))
        val apkStart = System.currentTimeMillis()
        val apkResult = apkAnalyzer.analyzeApk(apkFile)
        if (apkResult.isSuccess) {
            val apkAnalysisResult = apkResult.getOrNull()
            apkInfo = apkAnalysisResult?.apkInfo
            stageResults.add(StageResult(
                stageId = "apk",
                state = StageState.COMPLETE,
                startedAt = apkStart.toString(),
                completedAt = System.currentTimeMillis().toString(),
                progressCurrent = 100,
                progressTotal = 100,
                warningCount = 0,
                errorCode = null
            ))
            emit(AnalysisProgress.StageComplete("apk"))
            
            emit(AnalysisProgress.StageStarted("signing", "Verifying signatures"))
            val signingStart = System.currentTimeMillis()
            val signingResult = apksigVerifier.verify(apkFile)
            if (signingResult.verified && signingResult.signingInfo != null) {
                apkInfo = apkInfo?.copy(signingInfo = signingResult.signingInfo)
                stageResults.add(StageResult(
                    stageId = "signing",
                    state = StageState.COMPLETE,
                    startedAt = signingStart.toString(),
                    completedAt = System.currentTimeMillis().toString(),
                    progressCurrent = 100,
                    progressTotal = 100,
                    warningCount = signingResult.warnings.size,
                    errorCode = null
                ))
            } else {
                stageResults.add(StageResult(
                    stageId = "signing",
                    state = StageState.FAILED,
                    startedAt = signingStart.toString(),
                    completedAt = System.currentTimeMillis().toString(),
                    progressCurrent = 0,
                    progressTotal = 100,
                    warningCount = 0,
                    errorCode = "SIGNING_VERIFICATION_FAILED"
                ))
                if (signingResult.errors.isNotEmpty()) {
                    errors.add(ModelAnalysisError(
                        code = "SIGNING_VERIFICATION_FAILED",
                        message = signingResult.errors.joinToString("; "),
                        stage = "signing"
                    ))
                }
            }
            emit(AnalysisProgress.StageComplete("signing"))
            
            emit(AnalysisProgress.StageStarted("dex", "Analyzing DEX files"))
            val dexStart = System.currentTimeMillis()
            val dexFiles = extractDexFiles(apkFile)
            var dexWarnings = 0
            dexFiles.forEach { dexFile ->
                val dexResult = dexAnalyzer.analyzeDex(dexFile)
                if (dexResult.isSuccess) {
                    dexResult.getOrNull()?.let { dexInfos.add(it) }
                } else {
                    dexWarnings++
                    errors.add(ModelAnalysisError(
                        code = "DEX_PARSE_ERROR",
                        message = dexResult.exceptionOrNull()?.message ?: "Unknown DEX parse error",
                        stage = "dex"
                    ))
                }
                dexFile.delete()
            }
            stageResults.add(StageResult(
                stageId = "dex",
                state = StageState.COMPLETE,
                startedAt = dexStart.toString(),
                completedAt = System.currentTimeMillis().toString(),
                progressCurrent = dexInfos.size,
                progressTotal = dexFiles.size,
                warningCount = dexWarnings,
                errorCode = null
            ))
            emit(AnalysisProgress.StageComplete("dex"))
            
            if (config.deepDexAnalysisEnabled) {
                emit(AnalysisProgress.StageStarted("code_analysis", "Analyzing code patterns"))
                val codeStart = System.currentTimeMillis()
                dexInfos.forEach { dexInfo ->
                    val reflectionFindings = reflectionDetector.detectPatterns(dexInfo)
                    reflectionFindings.forEach { refFinding ->
                        findings.add(
                            Finding(
                                id = UUID.randomUUID().toString(),
                                ruleId = "REFLECTION_${refFinding.patternType}",
                                title = refFinding.description,
                                category = "code_pattern",
                                severity = refFinding.severity,
                                confidence = Confidence.HIGH,
                                simpleExplanation = refFinding.evidence,
                                analystExplanation = "Detected ${refFinding.patternType} pattern in ${refFinding.className}.${refFinding.methodName}",
                                evidence = listOf(
                                    Evidence(
                                        type = EvidenceType.DEX_CALL_SITE,
                                        fileId = null,
                                        dexName = dexInfo.name,
                                        className = refFinding.className,
                                        method = refFinding.methodName,
                                        offset = null,
                                        excerpt = refFinding.evidence,
                                        excerptEncoding = "text"
                                    )
                                ),
                                limitations = listOf("Static analysis cannot determine if this code path is executed"),
                                recommendations = listOf("Review if this pattern is necessary for app functionality"),
                                mappings = emptyList(),
                                references = emptyList()
                            )
                        )
                    }
                }
                stageResults.add(StageResult(
                    stageId = "code_analysis",
                    state = StageState.COMPLETE,
                    startedAt = codeStart.toString(),
                    completedAt = System.currentTimeMillis().toString(),
                    progressCurrent = findings.size,
                    progressTotal = null,
                    warningCount = 0,
                    errorCode = null
                ))
                emit(AnalysisProgress.StageComplete("code_analysis"))
            } else {
                stageResults.add(StageResult(
                    stageId = "code_analysis",
                    state = StageState.SKIPPED,
                    startedAt = null,
                    completedAt = null,
                    progressCurrent = null,
                    progressTotal = null,
                    warningCount = 0,
                    errorCode = null
                ))
                limitations.add("Deep DEX analysis disabled by configuration")
            }
        } else {
            val errorMsg = apkResult.exceptionOrNull()?.message ?: "Unknown error"
            stageResults.add(StageResult(
                stageId = "apk",
                state = StageState.FAILED,
                startedAt = apkStart.toString(),
                completedAt = System.currentTimeMillis().toString(),
                progressCurrent = 0,
                progressTotal = 100,
                warningCount = 0,
                errorCode = "APK_PARSE_ERROR"
            ))
            errors.add(ModelAnalysisError(
                code = "APK_PARSE_ERROR",
                message = errorMsg,
                stage = "apk"
            ))
            emit(AnalysisProgress.StageFailed("apk", errorMsg))
        }
        
        return apkInfo
    }
    
    /**
     * Returns true when a ZIP archive structurally looks like an APK
     * (contains a top-level AndroidManifest.xml or classes*.dex) rather than
     * being a generic container. Magic bytes alone cannot distinguish APK from
     * ZIP, so structural markers are required.
     */
    private fun isApkContainer(file: File): Boolean {
        return try {
            ZipFile(file).use { zip ->
                zip.getEntry("AndroidManifest.xml") != null ||
                    zip.entries().asSequence().any { it.name.matches(Regex("^classes\\d*\\.dex$")) }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to inspect archive for APK markers")
            false
        }
    }
    
    /**
     * Extracts the primary (preferably base) APK entry from a case archive to a
     * bounded temp file. Returns null when no usable APK entry exists.
     */
    private fun extractPrimaryApk(containerFile: File): File? {
        return try {
            ZipFile(containerFile).use { zip ->
                val apkEntries = zip.entries().asSequence()
                    .filter { it.name.endsWith(".apk", ignoreCase = true) }
                    .sortedBy { if (it.name.contains("base", ignoreCase = true)) 0 else 1 }
                    .toList()
                if (apkEntries.isEmpty()) return null
                val entry = apkEntries.first()
                val tempFile = File.createTempFile("case_apk_", ".apk")
                try {
                    zip.getInputStream(entry).use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.length() > AnalysisLimits.MAX_INPUT_SIZE_BYTES) {
                        tempFile.delete()
                        return null
                    }
                    tempFile
                } catch (e: Exception) {
                    tempFile.delete()
                    Timber.w(e, "Failed to extract APK entry ${entry.name}")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract APK from archive")
            null
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
    
    private fun extractDexFilesFromPackageSet(packageSetFile: File): List<File> {
        val dexFiles = mutableListOf<File>()
        try {
            ZipFile(packageSetFile).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.endsWith(".apk") }
                    .forEach { entry ->
                        val apkFile = File.createTempFile("pkg_apk_", ".apk")
                        try {
                            zip.getInputStream(entry).use { input ->
                                apkFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            dexFiles.addAll(extractDexFiles(apkFile))
                        } finally {
                            apkFile.delete()
                        }
                    }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract DEX files from package set")
        }
        return dexFiles
    }
    
    private fun extractBaseApk(packageSetFile: File): File? {
        return try {
            ZipFile(packageSetFile).use { zip ->
                val baseEntry = zip.entries().asSequence()
                    .filter { it.name.endsWith(".apk") }
                    .sortedBy { if (it.name.contains("base", ignoreCase = true)) 0 else 1 }
                    .firstOrNull() ?: return null
                val baseFile = File.createTempFile("pkg_base_", ".apk")
                zip.getInputStream(baseEntry).use { input ->
                    baseFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                baseFile
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract base APK from package set")
            null
        }
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
        caseTextInventory: List<CaseTextEntry>,
        dexInfos: List<DexInfo>,
        findings: List<Finding>,
        indicators: List<Indicator>,
        stageResults: List<StageResult>,
        limitations: List<String>,
        errors: List<ModelAnalysisError>,
        duration: Long
    ): AnalysisReport {
        val riskBand = calculateRiskBand(findings)
        val maxSeverity = findings.maxByOrNull { it.severity.ordinal }?.severity
        
        val completeness = calculateCompleteness(stageResults)
        
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
                rulePackVersion = "2026.08.1"
            ),
            settings = AnalysisSettings(
                analysisProfile = config.analysisProfile.name.lowercase(),
                hashAlgorithms = config.hashAlgorithms,
                nativeAnalysisEnabled = config.nativeAnalysisEnabled,
                deepDexAnalysisEnabled = config.deepDexAnalysisEnabled,
                iocExtractionEnabled = config.iocExtractionEnabled
            ),
            source = SourceInfo(
                displayName = config.sourceDisplayName,
                mimeType = config.sourceMimeType,
                sizeReported = config.sourceSizeReported,
                sizeActual = null,
                sha256 = hashes.sha256,
                sha1 = hashes.sha1,
                md5 = hashes.md5
            ),
            containers = emptyList(),
            archive = archiveResult?.let { buildArchiveSection(it, caseTextInventory) },
            files = emptyList(),
            apk = apkInfo,
            dex = dexInfos,
            nativeLibraries = emptyList(),
            indicators = indicators,
            facts = emptyList(),
            findings = findings,
            summary = ReportSummary(
                riskBand = riskBand,
                confidence = calculateConfidence(stageResults, findings),
                completeness = completeness,
                findingCount = findings.size,
                maxSeverity = maxSeverity,
                topFindings = findings.take(3).map { it.title }
            ),
            stageResults = stageResults,
            limitations = limitations,
            errors = errors,
            integrity = IntegrityBlock(
                sourceSha256 = hashes.sha256,
                reportSha256 = "",
                engineVersion = "1.0.0",
                rulePackVersion = "2026.08.1",
                sampleRetained = false
            )
        )
    }
    
    private fun calculateCompleteness(stageResults: List<StageResult>): Double {
        if (stageResults.isEmpty()) return 0.0
        val totalStages = stageResults.size
        val completedStages = stageResults.count { it.state == StageState.COMPLETE }
        val failedStages = stageResults.count { it.state == StageState.FAILED }
        return (completedStages.toDouble() / totalStages.toDouble()).coerceIn(0.0, 1.0)
    }
    
    private fun calculateConfidence(stageResults: List<StageResult>, findings: List<Finding>): Confidence {
        val failedStages = stageResults.count { it.state == StageState.FAILED }
        val skippedStages = stageResults.count { it.state == StageState.SKIPPED }
        
        if (failedStages > 2 || skippedStages > 2) return Confidence.LOW
        if (failedStages > 0 || skippedStages > 0) return Confidence.MEDIUM
        return Confidence.HIGH
    }
    
    private fun buildArchiveSection(
        archiveResult: ArchiveAnalysisResult,
        caseTextInventory: List<CaseTextEntry> = emptyList()
    ): ArchiveReportSection {
        val analyzedChildren = archiveResult.entries.map { entry ->
            ArchiveChildEntry(
                path = entry.normalizedPath,
                compressedSize = entry.compressedSize,
                expandedSize = entry.expandedSize,
                detectedType = detectEntryType(entry.normalizedPath),
                sha256 = null,
                status = when {
                    entry.isEncrypted -> "encrypted"
                    entry.isNestedArchive && entry.nestedArchiveResult != null -> "nested_analyzed"
                    entry.isNestedArchive -> "nested_failed"
                    else -> "available"
                },
                parentContainerId = entry.nestedArchiveResult?.let { archiveResult.entries.firstOrNull { it.normalizedPath == entry.normalizedPath }?.normalizedPath }
            )
        }
        
        val skippedChildren = archiveResult.entries
            .filter { it.isEncrypted }
            .map { ArchiveSkippedEntry(it.normalizedPath, "encrypted entry not analyzable") }
        
        val duplicatePaths = archiveResult.entries
            .groupBy { it.normalizedPath }
            .filter { it.value.size > 1 }
            .keys.toList()
        
        val quotaEvents = if (archiveResult.quotaEvents.isNotEmpty()) {
            archiveResult.quotaEvents
        } else {
            emptyList()
        }
        
        val integrityStatus = when {
            archiveResult.suspiciousPaths.isNotEmpty() -> ArchiveIntegrityStatus.VALID_WITH_WARNINGS
            duplicatePaths.isNotEmpty() -> ArchiveIntegrityStatus.VALID_WITH_WARNINGS
            archiveResult.quotaEvents.isNotEmpty() -> ArchiveIntegrityStatus.PARTIAL
            else -> ArchiveIntegrityStatus.VALID
        }
        
        return ArchiveReportSection(
            archiveType = "ZIP",
            encrypted = archiveResult.isEncrypted,
            entryCount = archiveResult.entryCount,
            declaredCompressedSize = archiveResult.totalCompressedSize,
            declaredExpandedSize = archiveResult.totalExpandedSize,
            observedExpandedSize = archiveResult.totalExpandedSize,
            maxObservedRatio = archiveResult.maxObservedRatio,
            nestedDepth = archiveResult.nestedDepth,
            suspiciousPaths = archiveResult.suspiciousPaths,
            duplicateEntries = duplicatePaths,
            unsupportedEntries = archiveResult.unsupportedEntries,
            analyzedChildren = analyzedChildren,
            skippedChildren = skippedChildren,
            quotaEvents = quotaEvents,
            integrityStatus = integrityStatus,
            textEntryInventory = caseTextInventory
        )
    }
    
    private fun detectEntryType(path: String): String? {
        if (path.isBlank()) return null
        val lowerPath = path.lowercase()
        return when {
            lowerPath.endsWith(".apk") -> "APK"
            lowerPath.endsWith(".dex") -> "DEX"
            lowerPath.endsWith(".zip") || lowerPath.endsWith(".jar") || lowerPath.endsWith(".aar") -> "ARCHIVE"
            lowerPath.endsWith(".so") -> "NATIVE_LIBRARY"
            lowerPath.endsWith(".xml") -> "XML"
            lowerPath.endsWith(".png") || lowerPath.endsWith(".jpg") || lowerPath.endsWith(".webp") -> "IMAGE"
            lowerPath.endsWith(".arsc") -> "RESOURCES"
            else -> null
        }
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

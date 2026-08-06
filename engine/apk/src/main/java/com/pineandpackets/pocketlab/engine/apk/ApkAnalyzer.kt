package com.pineandpackets.pocketlab.engine.apk

import com.pineandpackets.pocketlab.core.common.AnalysisError
import com.pineandpackets.pocketlab.core.model.*
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile

data class ApkAnalysisResult(
    val apkInfo: ApkInfo,
    val files: List<FileInfo>
)

class ApkAnalyzer {
    
    private val binaryXmlParser = BinaryXmlParser()
    private val fileInventory = ApkFileInventory()
    private val resourceTableParser = ResourceTableParser()
    private val structureValidator = ApkStructureValidator()
    
    fun analyzeApk(apkFile: File): Result<ApkAnalysisResult> {
        return try {
            // Validate APK structure first
            val validationResult = structureValidator.validate(apkFile)
            if (!validationResult.isValid) {
                return Result.failure(
                    AnalysisError.ParserError("Invalid APK structure: ${validationResult.errors.joinToString(", ")}")
                )
            }
            
            if (validationResult.warnings.isNotEmpty()) {
                Timber.w("APK structure warnings: ${validationResult.warnings.joinToString(", ")}")
            }
            
            ZipFile(apkFile).use { zip ->
                val manifestEntry = zip.getEntry("AndroidManifest.xml")
                    ?: return Result.failure(AnalysisError.ParserError("No AndroidManifest.xml found"))
                
                val manifestBytes = zip.getInputStream(manifestEntry).readBytes()
                
                // Parse resources.arsc if present
                val resourceTable = zip.getEntry("resources.arsc")?.let { entry ->
                    val resourceBytes = zip.getInputStream(entry).readBytes()
                    resourceTableParser.parse(resourceBytes).getOrNull()
                }
                
                val apkInfoResult = parseManifest(manifestBytes, resourceTable)
                if (apkInfoResult.isFailure) {
                    return Result.failure(apkInfoResult.exceptionOrNull()!!)
                }
                
                val apkInfo = apkInfoResult.getOrNull()!!
                
                // Get file inventory
                val filesResult = fileInventory.inventoryApk(apkFile)
                val files = if (filesResult.isSuccess) {
                    filesResult.getOrNull() ?: emptyList()
                } else {
                    Timber.w(filesResult.exceptionOrNull(), "Failed to get file inventory")
                    emptyList()
                }
                
                Result.success(ApkAnalysisResult(apkInfo, files))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to analyze APK")
            Result.failure(AnalysisError.ParserError("Failed to analyze APK", e))
        }
    }
    
    private fun parseManifest(manifestBytes: ByteArray, resourceTable: ResourceTableParser.ResourceTable?): Result<ApkInfo> {
        return try {
            val parseResult = binaryXmlParser.parse(manifestBytes)
            if (parseResult.isFailure) {
                return Result.failure(parseResult.exceptionOrNull()!!)
            }
            
            val elements = parseResult.getOrThrow()
            
            var packageName: String? = null
            var versionName: String? = null
            var versionCode: Long? = null
            var minSdk: Int? = null
            var targetSdk: Int? = null
            var debuggable = false
            var backupAllowed = false
            var usesCleartextTraffic = false
            var applicationLabel: String? = null
            
            val permissions = mutableListOf<PermissionInfo>()
            val components = mutableListOf<ComponentInfo>()
            
            var currentComponent: ComponentInfo? = null
            val currentIntentFilters = mutableListOf<IntentFilterInfo>()
            var currentIntentFilter: IntentFilterInfo? = null
            val currentDataElements = mutableListOf<DataElement>()
            
            for (element in elements) {
                when (element.name) {
                    "manifest" -> {
                        packageName = element.attributes["package"]?.rawValue
                        versionName = element.attributes["android:versionName"]?.rawValue
                        versionCode = element.attributes["android:versionCode"]?.rawValue?.toLongOrNull()
                    }
                    "uses-sdk" -> {
                        minSdk = element.attributes["android:minSdkVersion"]?.rawValue?.toIntOrNull()
                        targetSdk = element.attributes["android:targetSdkVersion"]?.rawValue?.toIntOrNull()
                    }
                    "uses-permission" -> {
                        val permName = element.attributes["android:name"]?.rawValue
                        if (permName != null) {
                            val knowledge = PermissionKnowledgeBase.getPermissionKnowledge(permName)
                            permissions.add(
                                PermissionInfo(
                                    name = permName,
                                    protectionLevel = knowledge.protectionLevel.name,
                                    declared = true,
                                    used = null
                                )
                            )
                        }
                    }
                    "application" -> {
                        debuggable = element.attributes["android:debuggable"]?.rawValue == "true"
                        backupAllowed = element.attributes["android:allowBackup"]?.rawValue != "false"
                        usesCleartextTraffic = element.attributes["android:usesCleartextTraffic"]?.rawValue == "true"
                        
                        // Extract application label
                        val labelAttr = element.attributes["android:label"]
                        if (labelAttr != null) {
                            applicationLabel = when {
                                labelAttr.typedValue.type == ResourceTableParser.TYPE_STRING && resourceTable != null -> {
                                    // Resolve string resource
                                    resourceTableParser.resolveStringResource(resourceTable, labelAttr.typedValue.data)
                                }
                                labelAttr.rawValue != null -> {
                                    // Direct string value
                                    labelAttr.rawValue
                                }
                                else -> null
                            }
                        }
                    }
                    "activity" -> {
                        currentComponent?.let { components.add(it) }
                        currentComponent = null
                        
                        val name = element.attributes["android:name"]?.rawValue
                        val exported = element.attributes["android:exported"]?.rawValue == "true"
                        val permission = element.attributes["android:permission"]?.rawValue
                        if (name != null) {
                            currentComponent = ComponentInfo(
                                name = name,
                                type = ComponentType.ACTIVITY,
                                exported = exported,
                                permission = permission
                            )
                        }
                    }
                    "service" -> {
                        currentComponent?.let { components.add(it) }
                        currentComponent = null
                        
                        val name = element.attributes["android:name"]?.rawValue
                        val exported = element.attributes["android:exported"]?.rawValue == "true"
                        val permission = element.attributes["android:permission"]?.rawValue
                        if (name != null) {
                            currentComponent = ComponentInfo(
                                name = name,
                                type = ComponentType.SERVICE,
                                exported = exported,
                                permission = permission
                            )
                        }
                    }
                    "receiver" -> {
                        currentComponent?.let { components.add(it) }
                        currentComponent = null
                        
                        val name = element.attributes["android:name"]?.rawValue
                        val exported = element.attributes["android:exported"]?.rawValue == "true"
                        val permission = element.attributes["android:permission"]?.rawValue
                        if (name != null) {
                            currentComponent = ComponentInfo(
                                name = name,
                                type = ComponentType.RECEIVER,
                                exported = exported,
                                permission = permission
                            )
                        }
                    }
                    "provider" -> {
                        currentComponent?.let { components.add(it) }
                        currentComponent = null
                        
                        val name = element.attributes["android:name"]?.rawValue
                        val exported = element.attributes["android:exported"]?.rawValue == "true"
                        val permission = element.attributes["android:permission"]?.rawValue
                        if (name != null) {
                            currentComponent = ComponentInfo(
                                name = name,
                                type = ComponentType.PROVIDER,
                                exported = exported,
                                permission = permission
                            )
                        }
                    }
                    "intent-filter" -> {
                        currentIntentFilter?.let {
                            currentIntentFilters.add(it.copy(dataElements = currentDataElements.toList()))
                        }
                        currentDataElements.clear()
                        
                        val autoVerify = element.attributes["android:autoVerify"]?.rawValue == "true"
                        val priority = element.attributes["android:priority"]?.rawValue?.toIntOrNull()
                        
                        currentIntentFilter = IntentFilterInfo(
                            autoVerify = autoVerify,
                            priority = priority
                        )
                    }
                    "action" -> {
                        currentIntentFilter?.let { filter ->
                            val actionName = element.attributes["android:name"]?.rawValue
                            if (actionName != null) {
                                currentIntentFilter = filter.copy(
                                    actions = filter.actions + actionName
                                )
                            }
                        }
                    }
                    "category" -> {
                        currentIntentFilter?.let { filter ->
                            val categoryName = element.attributes["android:name"]?.rawValue
                            if (categoryName != null) {
                                currentIntentFilter = filter.copy(
                                    categories = filter.categories + categoryName
                                )
                            }
                        }
                    }
                    "data" -> {
                        val scheme = element.attributes["android:scheme"]?.rawValue
                        val host = element.attributes["android:host"]?.rawValue
                        val port = element.attributes["android:port"]?.rawValue
                        val path = element.attributes["android:path"]?.rawValue
                        val pathPattern = element.attributes["android:pathPattern"]?.rawValue
                        val pathPrefix = element.attributes["android:pathPrefix"]?.rawValue
                        val mimeType = element.attributes["android:mimeType"]?.rawValue
                        
                        currentDataElements.add(
                            DataElement(
                                scheme = scheme,
                                host = host,
                                port = port,
                                path = path,
                                pathPattern = pathPattern,
                                pathPrefix = pathPrefix,
                                mimeType = mimeType
                            )
                        )
                    }
                }
            }
            
            currentIntentFilter?.let {
                currentIntentFilters.add(it.copy(dataElements = currentDataElements.toList()))
            }
            
            currentComponent?.let {
                components.add(it.copy(intentFilters = currentIntentFilters.toList()))
            }
            
            Result.success(
                ApkInfo(
                    packageName = packageName,
                    versionName = versionName,
                    versionCode = versionCode,
                    minSdk = minSdk,
                    targetSdk = targetSdk,
                    compileSdk = null,
                    applicationLabel = applicationLabel,
                    debuggable = debuggable,
                    backupAllowed = backupAllowed,
                    usesCleartextTraffic = usesCleartextTraffic,
                    permissions = permissions,
                    components = components,
                    signingInfo = null
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse manifest")
            Result.failure(AnalysisError.ParserError("Failed to parse manifest", e))
        }
    }
}

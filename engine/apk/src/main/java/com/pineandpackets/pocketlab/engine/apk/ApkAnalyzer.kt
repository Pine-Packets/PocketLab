package com.pineandpackets.pocketlab.engine.apk

import com.pineandpackets.pocketlab.core.common.AnalysisError
import com.pineandpackets.pocketlab.core.model.ApkInfo
import com.pineandpackets.pocketlab.core.model.ComponentInfo
import com.pineandpackets.pocketlab.core.model.ComponentType
import com.pineandpackets.pocketlab.core.model.PermissionInfo
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile

class ApkAnalyzer {
    
    fun analyzeApk(apkFile: File): Result<ApkInfo> {
        return try {
            ZipFile(apkFile).use { zip ->
                val manifestEntry = zip.getEntry("AndroidManifest.xml")
                    ?: return Result.failure(AnalysisError.ParserError("No AndroidManifest.xml found"))
                
                val manifestBytes = zip.getInputStream(manifestEntry).readBytes()
                
                parseManifest(manifestBytes)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to analyze APK")
            Result.failure(AnalysisError.ParserError("Failed to analyze APK", e))
        }
    }
    
    private fun parseManifest(manifestBytes: ByteArray): Result<ApkInfo> {
        return try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            
            parser.setInput(manifestBytes.inputStream(), "UTF-8")
            
            var packageName: String? = null
            var versionName: String? = null
            var versionCode: Long? = null
            var minSdk: Int? = null
            var targetSdk: Int? = null
            var debuggable = false
            
            val permissions = mutableListOf<PermissionInfo>()
            val components = mutableListOf<ComponentInfo>()
            
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "manifest" -> {
                                packageName = parser.getAttributeValue(null, "package")
                                versionName = parser.getAttributeValue(null, "android:versionName")
                                versionCode = parser.getAttributeValue(null, "android:versionCode")?.toLongOrNull()
                            }
                            "uses-sdk" -> {
                                minSdk = parser.getAttributeValue(null, "android:minSdkVersion")?.toIntOrNull()
                                targetSdk = parser.getAttributeValue(null, "android:targetSdkVersion")?.toIntOrNull()
                            }
                            "uses-permission" -> {
                                val permName = parser.getAttributeValue(null, "android:name")
                                if (permName != null) {
                                    permissions.add(
                                        PermissionInfo(
                                            name = permName,
                                            protectionLevel = null,
                                            declared = true,
                                            used = null
                                        )
                                    )
                                }
                            }
                            "application" -> {
                                debuggable = parser.getAttributeValue(null, "android:debuggable") == "true"
                            }
                            "activity" -> {
                                val name = parser.getAttributeValue(null, "android:name")
                                val exported = parser.getAttributeValue(null, "android:exported") == "true"
                                if (name != null) {
                                    components.add(
                                        ComponentInfo(
                                            name = name,
                                            type = ComponentType.ACTIVITY,
                                            exported = exported
                                        )
                                    )
                                }
                            }
                            "service" -> {
                                val name = parser.getAttributeValue(null, "android:name")
                                val exported = parser.getAttributeValue(null, "android:exported") == "true"
                                if (name != null) {
                                    components.add(
                                        ComponentInfo(
                                            name = name,
                                            type = ComponentType.SERVICE,
                                            exported = exported
                                        )
                                    )
                                }
                            }
                            "receiver" -> {
                                val name = parser.getAttributeValue(null, "android:name")
                                val exported = parser.getAttributeValue(null, "android:exported") == "true"
                                if (name != null) {
                                    components.add(
                                        ComponentInfo(
                                            name = name,
                                            type = ComponentType.RECEIVER,
                                            exported = exported
                                        )
                                    )
                                }
                            }
                            "provider" -> {
                                val name = parser.getAttributeValue(null, "android:name")
                                val exported = parser.getAttributeValue(null, "android:exported") == "true"
                                if (name != null) {
                                    components.add(
                                        ComponentInfo(
                                            name = name,
                                            type = ComponentType.PROVIDER,
                                            exported = exported
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            
            Result.success(
                ApkInfo(
                    packageName = packageName,
                    versionName = versionName,
                    versionCode = versionCode,
                    minSdk = minSdk,
                    targetSdk = targetSdk,
                    compileSdk = null,
                    applicationLabel = null,
                    debuggable = debuggable,
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

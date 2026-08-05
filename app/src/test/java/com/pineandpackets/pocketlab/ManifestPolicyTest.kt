package com.pineandpackets.pocketlab

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ManifestPolicyTest {
    
    @Test
    fun `manifest does not contain INTERNET permission`() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            val content = manifestFile.readText()
            assertFalse(
                "Manifest should not contain INTERNET permission",
                content.contains("android.permission.INTERNET")
            )
        }
    }
    
    @Test
    fun `manifest does not contain dangerous permissions`() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            val content = manifestFile.readText()
            
            val dangerousPermissions = listOf(
                "READ_EXTERNAL_STORAGE",
                "WRITE_EXTERNAL_STORAGE",
                "READ_MEDIA_IMAGES",
                "READ_MEDIA_VIDEO",
                "READ_MEDIA_AUDIO",
                "CAMERA",
                "RECORD_AUDIO",
                "ACCESS_FINE_LOCATION",
                "ACCESS_COARSE_LOCATION",
                "READ_CONTACTS",
                "READ_SMS",
                "READ_CALL_LOG",
                "ACCESSIBILITY_SERVICE",
                "BIND_ACCESSIBILITY_SERVICE",
                "REQUEST_INSTALL_PACKAGES",
                "QUERY_ALL_PACKAGES"
            )
            
            dangerousPermissions.forEach { permission ->
                assertFalse(
                    "Manifest should not contain $permission",
                    content.contains(permission)
                )
            }
        }
    }
    
    @Test
    fun `manifest does not export components unnecessarily`() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            val content = manifestFile.readText()
            
            val exportedComponents = Regex("android:exported=\"true\"").findAll(content).count()
            
            assertTrue(
                "Should have at most 2 exported components (launcher activity and optional share target), found $exportedComponents",
                exportedComponents <= 2
            )
        }
    }
    
    @Test
    fun `manifest disables backup`() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            val content = manifestFile.readText()
            assertTrue(
                "Manifest should have allowBackup=\"false\"",
                content.contains("android:allowBackup=\"false\"")
            )
        }
    }
}

package com.pineandpackets.pocketlab

import org.junit.Assert.*
import org.junit.Test

class ForbiddenApiTest {
    
    @Test
    fun `source code does not contain forbidden execution APIs`() {
        val sourceDir = java.io.File("src/main/java")
        if (sourceDir.exists()) {
            val forbiddenPatterns = listOf(
                "PackageInstaller",
                "Runtime.getRuntime().exec",
                "ProcessBuilder",
                "DexClassLoader",
                "PathClassLoader",
                "startActivity.*Intent.*ACTION_VIEW.*package:",
                "WebView.*loadUrl.*file://"
            )
            
            sourceDir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val content = file.readText()
                    forbiddenPatterns.forEach { pattern ->
                        assertFalse(
                            "File ${file.name} should not contain forbidden pattern: $pattern",
                            Regex(pattern).containsMatchIn(content)
                        )
                    }
                }
        }
    }
}

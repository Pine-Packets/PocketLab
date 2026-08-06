package com.pineandpackets.pocketlab.core.report

import com.pineandpackets.pocketlab.core.model.AnalysisReport
import com.pineandpackets.pocketlab.core.testing.GoldenReportGenerator
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class GoldenReportTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `minimal apk golden report matches snapshot`() {
        val report = GoldenReportGenerator.generateMinimalApkReport()
        val serialized = json.encodeToString(AnalysisReport.serializer(), report)

        val resource = GoldenReportTest::class.java.classLoader?.getResource("golden/minimal-apk-report.json")
            ?: throw IllegalStateException("Golden report not found in test resources")
        val goldenContent = resource.readText(Charsets.UTF_8)

        // Normalize both to compact JSON to avoid whitespace differences
        val normalizedGenerated = Json.parseToJsonElement(serialized).toString()
        val normalizedGolden = Json.parseToJsonElement(goldenContent).toString()

        assertEquals(
            "Generated report does not match golden snapshot. " +
                "If the change is intentional, update test-corpus/golden/minimal-apk-report.json.",
            normalizedGolden,
            normalizedGenerated
        )
    }
}

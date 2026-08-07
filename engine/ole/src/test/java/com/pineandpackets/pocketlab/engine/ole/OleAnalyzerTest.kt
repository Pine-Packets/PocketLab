package com.pineandpackets.pocketlab.engine.ole

import com.pineandpackets.pocketlab.core.model.DetectedType
import com.pineandpackets.pocketlab.core.model.IndicatorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OleAnalyzerTest {

    private val analyzer = OleAnalyzer()

    @Test
    fun `plain doc records structure and no spurious findings`() {
        val ref = OleArtifactRef(OleFixture.plainDoc(), "plain.doc")
        val result = analyzer.analyze(ref.context(), ref)

        assertFalse(result.incomplete)
        assertTrue(result.parserErrors.isEmpty())
        assertTrue(result.facts.any { it.type == "OLE_STREAM_COUNT" })
        assertEquals(3, result.facts.first { it.type == "OLE_STREAM_COUNT" }.value.toInt())
        assertFalse(result.findings.any { it.ruleId == "OLE-MACRO-001" })
        assertFalse(result.findings.any { it.ruleId == "OLE-EMBEDDED-001" })
    }

    @Test
    fun `detects vba macro project`() {
        val ref = OleArtifactRef(OleFixture.macroDoc(), "macro.doc")
        val result = analyzer.analyze(ref.context(), ref)

        assertTrue(result.facts.any { it.type == "OLE_HAS_VBA_MACRO" })
        val finding = result.findings.first { it.ruleId == "OLE-MACRO-001" }
        assertEquals("HIGH", finding.severity.name)
    }

    @Test
    fun `detects embedded ole objects`() {
        val ref = OleArtifactRef(OleFixture.embeddedDoc(), "embedded.doc")
        val result = analyzer.analyze(ref.context(), ref)

        assertTrue(result.facts.any { it.type == "OLE_HAS_EMBEDDED_OBJECTS" })
        assertTrue(result.findings.any { it.ruleId == "OLE-EMBEDDED-001" })
    }

    @Test
    fun `extracts url indicators from stream content and defangs them`() {
        val url = "https://phishing.example.net/login"
        val ref = OleArtifactRef(OleFixture.docWithIndicators(url), "remote.doc")
        val result = analyzer.analyze(ref.context(), ref)

        assertTrue(result.indicators.any { it.type == IndicatorType.URL && it.canonicalValue.contains("phishing.example.net") })
        val ind = result.indicators.first { it.type == IndicatorType.URL && it.canonicalValue.contains("phishing.example.net") }
        assertTrue(ind.defangedValue.contains("[.]"))
        assertTrue(result.findings.any { it.ruleId == "OLE-REMOTE-001" })
    }

    @Test
    fun `not a cfb produces a parser error and is incomplete`() {
        val ref = OleArtifactRef(OleFixture.notCfb(), "fake.doc")
        val result = analyzer.analyze(ref.context(), ref)

        assertTrue(result.incomplete)
        assertTrue(result.parserErrors.isNotEmpty())
    }

    @Test
    fun `truncated header is incomplete and never clean`() {
        val ref = OleArtifactRef(OleFixture.truncatedHeader(), "tiny.doc")
        val result = analyzer.analyze(ref.context(), ref)

        assertTrue(result.incomplete)
        assertTrue(result.parserErrors.any { it.code == "OLE_HEADER_TRUNCATED" })
    }

    @Test
    fun `rejects non-ole artifact with type mismatch`() {
        val ref = OleArtifactRef(byteArrayOf(1, 2, 3), "x.pdf", detectedType = DetectedType.PDF)
        val result = analyzer.analyze(ref.context(), ref)

        assertTrue(result.incomplete)
        assertTrue(result.parserErrors.any { it.code == "OLE_TYPE_MISMATCH" })
    }
}
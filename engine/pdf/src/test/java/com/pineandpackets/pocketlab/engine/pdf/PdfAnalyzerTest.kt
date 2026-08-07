package com.pineandpackets.pocketlab.engine.pdf

import com.pineandpackets.pocketlab.core.model.DetectedType
import com.pineandpackets.pocketlab.core.model.IndicatorType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAnalyzerTest {

    private val analyzer = PdfAnalyzer()

    @Test
    fun `parses minimal pdf without false findings`() {
        val ref = ByteArtifactRef(PdfFixture.minimalPdf())
        val result = analyzer.analyze(ref.context(), ref)

        assertFalse(result.incomplete)
        assertTrue(result.parserErrors.isEmpty())
        assertTrue(result.facts.any { it.type == "PDF_HEADER" && it.value == "%PDF-1.7" })
        assertTrue(result.facts.any { it.type == "PDF_OBJECT_COUNT" })
        assertTrue(result.findings.isEmpty())
    }

    @Test
    fun `detects embedded javascript and openaction`() {
        val ref = ByteArtifactRef(PdfFixture.javascriptPdf(), "js.pdf")
        val result = analyzer.analyze(ref.context(), ref)

        assertTrue(result.facts.any { it.type == "PDF_HAS_JAVASCRIPT" })
        assertTrue(result.facts.any { it.type == "PDF_HAS_OPENACTION" })
        val js = result.findings.first { it.ruleId == "PDF-JS-001" }
        assertTrue(js.severity.name == "HIGH")
    }

    @Test
    fun `extracts url indicator with defanging`() {
        val ref = ByteArtifactRef(PdfFixture.javascriptPdf(), "js.pdf")
        val result = analyzer.analyze(ref.context(), ref)

        val urlIoc = result.indicators.firstOrNull { it.type == IndicatorType.URL }
        assertNotNull(urlIoc)
        assertTrue(urlIoc!!.defangedValue.contains("[.]"))
        assertTrue(urlIoc.canonicalValue.contains("example.net"))
    }

    @Test
    fun `detects launch action`() {
        val ref = ByteArtifactRef(PdfFixture.launchPdf(), "launch.pdf")
        val result = analyzer.analyze(ref.context(), ref)

        assertTrue(result.facts.any { it.type == "PDF_HAS_LAUNCH_ACTION" })
        assertTrue(result.findings.any { it.ruleId == "PDF-LAUNCH-001" })
    }

    @Test
    fun `detects xfa and embedded files`() {
        val ref = ByteArtifactRef(PdfFixture.xfaEmbeddedPdf(), "xfa.pdf")
        val result = analyzer.analyze(ref.context(), ref)

        assertTrue(result.facts.any { it.type == "PDF_HAS_XFA" })
        assertTrue(result.facts.any { it.type == "PDF_HAS_ACROFORM" })
        assertTrue(result.facts.any { it.type == "PDF_HAS_EMBEDDED_FILES" })
        assertTrue(result.findings.any { it.ruleId == "PDF-EMBEDDED-001" })
    }

    @Test
    fun `truncated pdf raises abnormality and is never clean`() {
        val ref = ByteArtifactRef(PdfFixture.truncatedPdf(), "trunc.pdf")
        val result = analyzer.analyze(ref.context(), ref)

        assertTrue(result.facts.any { it.type == "PDF_ABNORMALITY" && it.value.contains("MISSING_EOF") })
        assertTrue(result.findings.none { it.ruleId == "PDF-JS-001" })
        // Absence of findings must never be presented as safe; the report carries
        // the completeness/incomplete markers.
    }

    @Test
    fun `rejects non-pdf artifact as type mismatch`() {
        val other = ByteArtifactRef(byteArrayOf(1, 2, 3), "x.bin", detectedType = DetectedType.ZIP)
        val result = analyzer.analyze(other.context(), other)
        assertTrue(result.incomplete)
        assertTrue(result.parserErrors.any { it.code == "PDF_TYPE_MISMATCH" })
    }

    @Test
    fun `oversized pdf is truncated without throwing and never false clean`() {
        val big = ByteArray((PdfAnalyzer.MAX_SCAN_BYTES + 1000).toInt()) { 0 }
        val ref = ByteArtifactRef(big, "big.pdf")
        val result = analyzer.analyze(ref.context(), ref)
        assertTrue(result.incomplete)
        assertTrue(result.limitations.any { it.contains("analysis limit") })
    }
}

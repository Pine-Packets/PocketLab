package com.pineandpackets.pocketlab.engine.ooxml

import com.pineandpackets.pocketlab.core.model.DetectedType
import com.pineandpackets.pocketlab.core.model.IndicatorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OoxmlAnalyzerTest {

    private val analyzer = OoxmlAnalyzer()

    @Test
    fun `plain docx produces no findings and records parts`() {
        val ref = ByteArtifactRef(OoxmlFixture.plainDocx(), "plain.docx")
        val result = analyzer.analyze(ref.context(), ref)

        assertFalse(result.incomplete)
        assertTrue(result.parserErrors.isEmpty())
        assertTrue(result.facts.any { it.type == "OOXML_PART_COUNT" })
        assertTrue(result.facts.any { it.type == "OOXML_CONTENT_TYPES" })
        assertTrue(result.findings.isEmpty())
    }

    @Test
    fun `detects macro vba project`() {
        val ref = ByteArtifactRef(OoxmlFixture.macroDocm(), "macro.docm")
        val result = analyzer.analyze(ref.context(), ref)

        assertTrue(result.facts.any { it.type == "OOXML_HAS_VBA" })
        val finding = result.findings.first { it.ruleId == "OOXML-MACRO-001" }
        assertEquals("HIGH", finding.severity.name)
    }

    @Test
    fun `detects external links and hyperlink indicators`() {
        val ref = ByteArtifactRef(OoxmlFixture.xlsxExternalLinks(), "links.xlsx")
        val result = analyzer.analyze(ref.context(), ref)

        assertTrue(result.facts.any { it.type == "OOXML_HAS_EXTERNAL_LINKS" })
        assertTrue(result.findings.any { it.ruleId == "OOXML-EXTLINK-001" })
        val urlIoc = result.indicators.firstOrNull { it.type == IndicatorType.URL && it.canonicalValue.contains("phishing.example.net") }
        assertTrue("expected phishing URL indicator", urlIoc != null)
        assertTrue(urlIoc!!.defangedValue.contains("[.]"))
        assertEquals("Document package contains external data links", result.findings.first { it.ruleId == "OOXML-EXTLINK-001" }.title)
    }

    @Test
    fun `detects embedded OLE`() {
        val ref = ByteArtifactRef(OoxmlFixture.embeddedOleDocx(), "ole.docx")
        val result = analyzer.analyze(ref.context(), ref)

        assertTrue(result.facts.any { it.type == "OOXML_HAS_EMBEDDED_OLE" })
        assertTrue(result.findings.any { it.ruleId == "OOXML-EMBEDDED-001" })
    }

    @Test
    fun `detects custom xml and signatures`() {
        val ref = ByteArtifactRef(OoxmlFixture.customXmlSignedDocx(), "signed.docx")
        val result = analyzer.analyze(ref.context(), ref)

        assertTrue(result.facts.any { it.type == "OOXML_HAS_CUSTOM_XML" })
        assertTrue(result.facts.any { it.type == "OOXML_HAS_SIGNATURES" })
    }

    @Test
    fun `plain zip without ooxml parts is not falsely flagged`() {
        val ref = ByteArtifactRef(OoxmlFixture.plainZip(), "plain.zip")
        val result = analyzer.analyze(ref.context(), ref)

        assertFalse(result.incomplete)
        assertTrue(result.findings.isEmpty())
        assertTrue(result.facts.any { it.type == "OOXML_ABNORMALITY" && it.value == "MISSING_CONTENT_TYPES" })
    }

    @Test
    fun `not-a-zip produces a parser error and is not clean`() {
        val ref = ByteArtifactRef(OoxmlFixture.notZip(), "fake.docx")
        val result = analyzer.analyze(ref.context(), ref)

        assertTrue(result.incomplete)
        assertTrue(result.parserErrors.isNotEmpty())
    }

    @Test
    fun `rejects non-ooxml artifact with type mismatch`() {
        val ref = ByteArtifactRef(byteArrayOf(1, 2, 3), "x.pdf", detectedType = DetectedType.PDF)
        val result = analyzer.analyze(ref.context(), ref)

        assertTrue(result.incomplete)
        assertTrue(result.parserErrors.any { it.code == "OOXML_TYPE_MISMATCH" })
    }
}
package com.pineandpackets.pocketlab.engine.ooxml

import com.pineandpackets.pocketlab.core.model.Indicator
import com.pineandpackets.pocketlab.core.model.ParserErrorRecord

/**
 * Bounded scan result for an OOXML package. Holds factual observations only;
 * interpretations are produced by [OoxmlAnalyzer]. Content bytes are never
 * retained; only signatures, relationship targets, and structural facts.
 */
data class OoxmlScanReport(
    val partCount: Int = 0,
    val contentTypesPresent: Boolean = false,
    val macroProjectPresent: Boolean = false,
    val activeXPresent: Boolean = false,
    val embeddedOlePresent: Boolean = false,
    val externalLinksPresent: Boolean = false,
    val customXmlPresent: Boolean = false,
    val signaturesPresent: Boolean = false,
    val hyperlinkTargets: List<String> = emptyList(),
    val externalTargets: List<String> = emptyList(),
    val indicators: List<Indicator> = emptyList(),
    val abnormalities: List<String> = emptyList(),
    val parserErrors: List<ParserErrorRecord> = emptyList(),
    val scanTruncated: Boolean = false,
)

/** Root names that characterize an OOXML document/excel/presentation package. */
internal enum class OoxmlFamily { WORD, EXCEL, PRESENTATION, UNKNOWN }
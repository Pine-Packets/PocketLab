package com.pineandpackets.pocketlab.engine.ole

import com.pineandpackets.pocketlab.core.model.Indicator
import com.pineandpackets.pocketlab.core.model.ParserErrorRecord

/**
 * Bounded result of a legacy OLE/CFB (compound file binary) scan.
 *
 * Captures structural facts (version, sector geometry, entry counts), macro /
 * embedded-object / suspicious-stream findings from the directory, a bounded
 * set of stream names, anomaly flags, and any URL/domain/email indicators that
 * were extracted from small high-value metadata streams.
 */
data class OleScanReport(
    val majorVersion: Int = 0,
    val minorVersion: Int = 0,
    val sectorSize: Int = 0,
    val miniSectorSize: Int = 0,
    val sectorCount: Int = 0,
    val storageCount: Int = 0,
    val streamCount: Int = 0,
    val macroStreamsPresent: Boolean = false,
    val macroStreamNames: List<String> = emptyList(),
    val embeddedOlePresent: Boolean = false,
    val embeddedOleNames: List<String> = emptyList(),
    val suspiciousStreamNames: List<String> = emptyList(),
    val streamNames: List<String> = emptyList(),
    val indicators: List<Indicator> = emptyList(),
    val abnormalities: List<String> = emptyList(),
    val parserErrors: List<ParserErrorRecord> = emptyList(),
    val scanTruncated: Boolean = false,
)
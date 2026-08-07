package com.pineandpackets.pocketlab.engine.pdf

import com.pineandpackets.pocketlab.core.model.Indicator
import com.pineandpackets.pocketlab.core.model.ParserErrorRecord

/**
 * Bounded result of a PDF scan. Only counts and bounded flags are retained;
 * raw script bodies, embedded files, and image bytes are never stored.
 */
data class PdfScanReport(
    val header: String?,
    val objectCount: Long,
    val streamCount: Long,
    val actions: PdfActions,
    val features: PdfFeatures,
    val abnormalities: List<String> = emptyList(),
    val indicators: List<Indicator> = emptyList(),
    val parserErrors: List<ParserErrorRecord> = emptyList(),
    val scanTruncated: Boolean = false,
    val encrypted: Boolean = false,
    val metadataPresent: Boolean = false,
    val signatureDetected: Boolean = false,
)

data class PdfActions(
    val hasJavaScript: Boolean = false,
    val hasOpenAction: Boolean = false,
    val hasLaunchAction: Boolean = false,
    val actionKeyCount: Int = 0,
    val launchTargetCount: Int = 0,
)

data class PdfFeatures(
    val hasAcroForm: Boolean = false,
    val hasXfa: Boolean = false,
    val hasEmbeddedFiles: Boolean = false,
    val hasAnnotations: Boolean = false,
    val hasRichMedia: Boolean = false,
    val hasRemoteResources: Boolean = false,
    val imageCount: Int = 0,
)
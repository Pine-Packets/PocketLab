package com.pineandpackets.pocketlab.engine.pdf

import com.pineandpackets.pocketlab.core.model.Confidence
import com.pineandpackets.pocketlab.core.model.Indicator
import com.pineandpackets.pocketlab.core.model.IndicatorSource
import com.pineandpackets.pocketlab.core.model.IndicatorType
import com.pineandpackets.pocketlab.core.model.ParserErrorRecord
import com.pineandpackets.pocketlab.engine.api.AnalysisContext
import com.pineandpackets.pocketlab.engine.api.ArtifactRef
import java.nio.charset.Charset

/**
 * Bounded, read-only PDF scanner.
 *
 * Reads at most [PdfAnalyzer.MAX_SCAN_BYTES] bytes and treats them as
 * byte-preserving Latin-1 text to hunt for high-value dictionaries and
 * structural markers. It never decodes script bodies, never extracts embedded
 * files, and never constructs PDF objects. Cooperative cancellation and the
 * shared budget are honoured on every scan step. Files larger than the limit
 * are scanned partially and reported as truncated (never false-clean).
 */
internal class PdfScanner(
    private val artifact: ArtifactRef,
    private val context: AnalysisContext,
) {

    fun scan(): PdfScanReport {
        context.checkCancelled()

        val size = artifact.sizeBytes
        if (size <= 0) {
            return PdfScanReport(
                header = null,
                objectCount = 0,
                streamCount = 0,
                actions = PdfActions(),
                features = PdfFeatures(),
                parserErrors = listOf(
                    ParserErrorRecord(
                        code = "PDF_EMPTY_OR_UNREADABLE",
                        message = "PDF reports a non-positive size",
                        analyzerId = PdfAnalyzer.ANALYZER_ID,
                    )
                ),
            )
        }

        val limit = PdfAnalyzer.MAX_SCAN_BYTES
        val toRead = minOf(size, limit)
        val bytes = artifact.readRange(offset = 0L, count = toRead.toInt())
        val truncated = size > limit
        val text = String(bytes, ISO_8859_1)

        context.checkCancelled()

        val header = HEADER_REGEX.find(text)?.value?.trim()
        val tail = text.takeLast(2048)

        val eofAt = tail.indexOf("%%EOF")
        val hasEof = eofAt >= 0
        val trailingBytes = if (hasEof && !truncated) {
            (tail.length - (eofAt + 5)).toLong().coerceAtLeast(0L)
        } else {
            0L
        }

        val extraEofMarkers = countOccurrences(tail, "%%EOF") > 1
        val multipleStartXref = countOccurrences(text.takeLast(8192), "startxref") > 1

        val actions = PdfActions(
            hasJavaScript = text.contains("/JavaScript") || text.contains("/JS") || text.contains("JS("),
            hasOpenAction = text.contains("/OpenAction"),
            hasLaunchAction = text.contains("/Launch"),
            actionKeyCount = countOccurrences(text, "/S ").coerceToInt(),
            launchTargetCount = countOccurrences(text, "/F ").coerceToInt(),
        )

        val features = PdfFeatures(
            hasAcroForm = text.contains("/AcroForm"),
            hasXfa = text.contains("/XFA"),
            hasEmbeddedFiles = text.contains("/EmbeddedFiles") || text.contains("/Filespec") || text.contains("/FileAttachment"),
            hasAnnotations = text.contains("/Annots") || text.contains("/Annotation"),
            hasRichMedia = text.contains("/RichMedia"),
            hasRemoteResources = text.contains("/URI") || text.contains("/GoToR"),
            imageCount = countOccurrences(text, "/Image").coerceToInt(),
        )

        val encrypted = text.contains("/Encrypt")
        val metadataPresent = text.contains("/Metadata")
        val signatureDetected = text.contains("/ByteRange") ||
            text.contains("/Adobe.PPKL") ||
            text.contains("/Type /Sig")

        val abnormalities = mutableListOf<String>()
        if (header == null) abnormalities += "MISSING_HEADER"
        else if (!header.startsWith("%PDF-")) abnormalities += "BAD_HEADER_PREFIX"
        if (!hasEof) abnormalities += "MISSING_EOF"
        if (extraEofMarkers) abnormalities += "EXTRA_EOF_MARKERS"
        if (multipleStartXref) abnormalities += "MULTIPLE_STARTXREF"
        if (trailingBytes > 0) abnormalities += "TRAILING_DATA:$trailingBytes"

        val parserErrors = mutableListOf<ParserErrorRecord>()
        if (text.isEmpty()) {
            parserErrors += ParserErrorRecord(
                code = "PDF_READ_FAILED",
                message = "No bytes returned from artifact",
                analyzerId = PdfAnalyzer.ANALYZER_ID,
            )
        }

        val indicators = extractIndicators(text)

        return PdfScanReport(
            header = header,
            objectCount = countOccurrences(text, " obj"),
            streamCount = countOccurrences(text, "stream"),
            actions = actions,
            features = features,
            abnormalities = abnormalities,
            indicators = indicators,
            parserErrors = parserErrors,
            scanTruncated = truncated || text.isEmpty(),
            encrypted = encrypted,
            metadataPresent = metadataPresent,
            signatureDetected = signatureDetected,
        )
    }

    private fun countOccurrences(text: String, needle: String): Long {
        var count = 0L
        var idx = 0
        while (idx < text.length) {
            val at = text.indexOf(needle, idx)
            if (at < 0) break
            count++
            if (count >= 2_000_000) break
            idx = at + needle.length
        }
        return count
    }

    private fun Long.coerceToInt(): Int = this.toInt().coerceAtMost(10_000)

    private fun extractIndicators(text: String): List<Indicator> {
        val source = IndicatorSource(container = artifact.name, entry = "pdf")
        val out = mutableListOf<Indicator>()
        val urlRegex = Regex("""https?://[^\s<>"'{}|\\^`\[\]]+""")
        for (m in urlRegex.findAll(text)) {
            if (out.size >= MAX_INDICATORS) break
            val url = m.value
            out += Indicator(
                type = IndicatorType.URL,
                displayValue = url,
                canonicalValue = url.lowercase(),
                defangedValue = defang(url),
                source = source,
                confidence = Confidence.HIGH,
                context = "URL found in PDF content",
                classification = listOf("NETWORK_DESTINATION"),
            )
        }
        return out.distinctBy { it.canonicalValue }
    }

    private fun defang(url: String): String =
        url.replace("https://", "hxxps://").replace("http://", "hxxp://").replace(".", "[.]")

    companion object {
        private val ISO_8859_1: Charset = Charsets.ISO_8859_1
        private val HEADER_REGEX = Regex("""%PDF-\d\.\d""")
        private const val MAX_INDICATORS = 10_000
    }
}
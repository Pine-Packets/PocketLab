package com.pineandpackets.pocketlab.engine.pdf

import com.pineandpackets.pocketlab.core.model.DetectedType
import com.pineandpackets.pocketlab.engine.api.AnalysisCancellation
import com.pineandpackets.pocketlab.engine.api.AnalysisContext
import com.pineandpackets.pocketlab.engine.api.ArtifactRef
import com.pineandpackets.pocketlab.engine.api.CaseBudget

/** Builds bounded in-memory PDF fixtures for analyzer tests. */
object PdfFixture {

    fun minimalPdf(): ByteArray =
        buildString {
            append("%PDF-1.7\n")
            append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
            append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
            append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\n")
            append("xref\n")
            append("0 4\n")
            append("0000000000 65535 f \n")
            append("0000000009 00000 n \n")
            append("0000000050 00000 n \n")
            append("0000000100 00000 n \n")
            append("trailer\n<< /Size 4 /Root 1 0 R >>\n")
            append("startxref\n152\n")
            append("%%EOF\n")
            append("%dummytrailingdata\n")
        }.toByteArray(Charsets.ISO_8859_1)

    fun javascriptPdf(): ByteArray =
        buildString {
            append("%PDF-1.7\n")
            append("1 0 obj\n<< /Type /Catalog /OpenAction 4 0 R >>\nendobj\n")
            append("4 0 obj\n<< /S /JavaScript /JS (app.alert\\(1\\); var u = 'https://evil.example.net/payload') >>\nendobj\n")
            append("trailer\n<< /Size 2 /Root 1 0 R >>\n")
            append("startxref\n0\n")
            append("%%EOF\n")
        }.toByteArray()

    fun launchPdf(): ByteArray =
        buildString {
            append("%PDF-1.4\n")
            append("1 0 obj\n<< /Type /Catalog /GoToR /URI (http://example.com/res) >>\nendobj\n")
            append("2 0 obj\n<< /S /Launch /F (cmd.exe) >>\nendobj\n")
            append("trailer\n<< /Size 2 >>\n")
            append("startxref\n0\n")
            append("%%EOF\n")
        }.toByteArray(Charsets.ISO_8859_1)

    fun xfaEmbeddedPdf(): ByteArray =
        buildString {
            append("%PDF-1.6\n")
            append("1 0 obj\n<< /AcroForm << /XFA (xfa) /Fields [] >> >>\nendobj\n")
            append("2 0 obj\n<< /EmbeddedFiles << /Names [(readme.txt) 3 0 R] >> >>\nendobj\n")
            append("3 0 obj\n<< /Type /Filespec /F (readme.txt) >>\nendobj\n")
            append("trailer\n<< /Size 4 >>\n")
            append("startxref\n0\n")
            append("%%EOF\n")
        }.toByteArray(Charsets.ISO_8859_1)

    fun truncatedPdf(): ByteArray =
        buildString {
            append("%PDF-1.7\n")
            append("1 0 obj\n<< /Type /Catalog >>\n")
            // no endobj/trailer/eof
        }.toByteArray(Charsets.ISO_8859_1)

    fun emptyPdf(): ByteArray = byteArrayOf()
}

/** Provides a bound ArtifactRef over in-memory bytes for analyzer tests. */
class ByteArtifactRef(
    private val bytes: ByteArray,
    name: String = "sample.pdf",
    override val detectedType: DetectedType = DetectedType.PDF,
) : ArtifactRef {
    override val artifactId: String = "test-artifact"
    override val parentId: String? = null
    override val name: String = name
    override val detectedSubtype: String? = null
    override val sizeBytes: Long = bytes.size.toLong()

    private val cancellation = AnalysisCancellation()

    override fun readNBytes(count: Int): ByteArray = bytes.copyOf(count.coerceAtMost(bytes.size))
    override fun readRange(offset: Long, count: Int): ByteArray {
        val start = offset.coerceIn(0, bytes.size.toLong()).toInt()
        return bytes.copyOfRange(start, (start + count).coerceAtMost(bytes.size))
    }

    fun context() = AnalysisContext(
        budget = CaseBudget(
            maxBytesRead = (bytes.size + 1024).toLong(),
            maxExpandedBytes = (bytes.size * 2L + 1024),
            maxArtifactCount = 10,
            maxArchiveEntries = 10,
            maxRecursionDepth = 4,
            maxFindings = 100,
            maxIndicators = 1000,
            maxFacts = 1000,
            maxOps = 100_000,
        ),
        cancellation = cancellation,
        deadlineEpochMs = null,
    )
}
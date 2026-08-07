package com.pineandpackets.pocketlab.engine.ooxml

import com.pineandpackets.pocketlab.core.model.DetectedType
import com.pineandpackets.pocketlab.engine.api.AnalysisCancellation
import com.pineandpackets.pocketlab.engine.api.AnalysisContext
import com.pineandpackets.pocketlab.engine.api.ArtifactRef
import com.pineandpackets.pocketlab.engine.api.CaseBudget
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds in-memory OOXML (ZIP) fixtures for analyzer tests. */
object OoxmlFixture {

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private const val DOCUMENT_XML = """<?xml version="1.0" encoding="UTF-8"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body><w:p><w:r><w:t>Hello</w:t></w:r></w:p></w:body>
</w:document>"""

    private const val DOC_RELS_WITH_HYPERLINK = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="https://phishing.example.net/login" TargetMode="External"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="http://legit.example.com/page" TargetMode="External"/>
</Relationships>"""

    /** Plain, benign .docx with no macros/hyperlinks/external content. */
    fun plainDocx(): ByteArray = zip(
        "[Content_Types].xml" to CONTENT_TYPES.toByteArray(),
        "_rels/.rels" to ROOT_RELS.toByteArray(),
        "word/document.xml" to DOCUMENT_XML.toByteArray(),
    )

    /** Macro-enabled .docm-style package with a vbaProject part. */
    fun macroDocm(): ByteArray = zip(
        "[Content_Types].xml" to CONTENT_TYPES.toByteArray(),
        "_rels/.rels" to ROOT_RELS.toByteArray(),
        "word/document.xml" to DOCUMENT_XML.toByteArray(),
        "word/vbaProject.bin" to byteArrayOf(0x43, 0x43, 0x0D, 0x0A, 0x00),
    )

    /** Spreadsheet with an external data link and remote hyperlink relationships. */
    fun xlsxExternalLinks(): ByteArray = zip(
        "[Content_Types].xml" to CONTENT_TYPES.toByteArray(),
        "_rels/.rels" to ROOT_RELS.toByteArray(),
        "xl/workbook.xml" to "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"/>".toByteArray(),
        "xl/externalLinks/externalLink1.xml" to
            "<externalLink xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"/>".toByteArray(),
        "word/_rels/document.xml.rels" to DOC_RELS_WITH_HYPERLINK.toByteArray(),
    )

    /** Word doc with embedded OLE object. */
    fun embeddedOleDocx(): ByteArray = zip(
        "[Content_Types].xml" to CONTENT_TYPES.toByteArray(),
        "_rels/.rels" to ROOT_RELS.toByteArray(),
        "word/document.xml" to DOCUMENT_XML.toByteArray(),
        "word/embeddings/oleObject1.bin" to byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte()),
    )

    /** Package with custom XML and digital signature parts. */
    fun customXmlSignedDocx(): ByteArray = zip(
        "[Content_Types].xml" to CONTENT_TYPES.toByteArray(),
        "_rels/.rels" to ROOT_RELS.toByteArray(),
        "word/document.xml" to DOCUMENT_XML.toByteArray(),
        "customXml/item1.xml" to "<data/>".toByteArray(),
        "_xmlsignatures/sig1.xml" to "<Signature/>".toByteArray(),
    )

    /** Not a ZIP at all. */
    fun notZip(): ByteArray = "%PDF-1.7\n".toByteArray()

    /** A ZIP with no OOXML parts (plain zip). */
    fun plainZip(): ByteArray = zip(
        "readme.txt" to "not an ooxml".toByteArray(),
        "data.bin" to byteArrayOf(1, 2, 3, 4),
    )

    fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }
}

/** Provides a bound ArtifactRef over in-memory bytes for analyzer tests. */
class ByteArtifactRef(
    private val bytes: ByteArray,
    name: String = "sample.docx",
    override val detectedType: DetectedType = DetectedType.OOXML,
) : ArtifactRef {
    override val artifactId: String = "test-ooxml-artifact"
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
            maxBytesRead = (bytes.size + 8192).toLong(),
            maxExpandedBytes = (bytes.size * 2L + 8192),
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

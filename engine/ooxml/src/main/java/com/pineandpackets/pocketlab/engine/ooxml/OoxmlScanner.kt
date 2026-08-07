package com.pineandpackets.pocketlab.engine.ooxml

import com.pineandpackets.pocketlab.core.model.Indicator
import com.pineandpackets.pocketlab.core.model.IndicatorSource
import com.pineandpackets.pocketlab.core.model.ParserErrorRecord
import com.pineandpackets.pocketlab.engine.api.ArtifactRef
import com.pineandpackets.pocketlab.engine.api.AnalysisContext
import com.pineandpackets.pocketlab.engine.ioc.IocExtractor
import org.apache.commons.compress.archivers.zip.ZipFile
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import kotlinx.coroutines.CancellationException

/**
 * Bounded, read-only OOXML scanner.
 *
 * OOXML packages are ZIP containers. This scanner opens the package through a
 * read-only, bounds-checked [SeekableByteChannel] backed by [ArtifactRef]
 * (so nothing is extracted to disk), enumerates parts, and reads only small
 * high-value XML relationship/metadata parts (`[Content_Types].xml` and the
 * `*.rels` relationship parts) with strict per-part and total byte caps. It detects
 * macro/VBA projects, ActiveX, embedded OLE, external links/connections,
 * custom XML, and digital-signature parts, and collects external relationship
 * targets (hyperlinks, remote templates) as indicators. It never parses part
 * bodies as active content, never extracts embedded binary parts to disk, and
 * never contacts any URL.
 */
internal class OoxmlScanner(
    private val artifact: ArtifactRef,
    private val context: AnalysisContext,
    private val iocExtractor: IocExtractor = IocExtractor(),
) {

    fun scan(): OoxmlScanReport {
        context.checkCancelled()

        if (artifact.sizeBytes <= 0) {
            return OoxmlScanReport(
                parserErrors = listOf(
                    ParserErrorRecord(
                        code = "OOXML_EMPTY_OR_UNREADABLE",
                        message = "OOXML reports a non-positive size",
                        analyzerId = OoxmlAnalyzer.ANALYZER_ID,
                    )
                ),
            )
        }

        val abnormalities = mutableListOf<String>()
        var partCount = 0
        var totalExpanded = 0L
        var contentTypesPresent = false
        var macroProjectPresent = false
        var activeXPresent = false
        var embeddedOlePresent = false
        var externalLinksPresent = false
        var customXmlPresent = false
        var signaturesPresent = false
        val hyperlinkTargets = mutableListOf<String>()
        val externalTargets = mutableListOf<String>()
        val parserErrors = mutableListOf<ParserErrorRecord>()
        var scanTruncated = false

        val channel = ArtifactChannel(artifact, context)
        try {
            val zip = ZipFile.builder()
                .setSeekableByteChannel(channel)
                .setIgnoreLocalFileHeader(true)
                .get()
            zip.use { zip ->
                val entries = zip.entries
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    context.checkCancelled()
                    if (partCount >= MAX_ENTRIES) {
                        abnormalities += "MAX_ENTRIES_EXCEEDED"
                        scanTruncated = true
                        break
                    }
                    partCount++

                    val name = entry.name
                    if (entry.isDirectory) continue

                    totalExpanded += entry.size
                    if (totalExpanded > MAX_TOTAL_EXPANDED) {
                        abnormalities += "TOTAL_EXPANDED_SIZE_EXCEEDED"
                        scanTruncated = true
                        break
                    }
                    if (entry.size > MAX_PART_SIZE) abnormalities += "OVERSIZED_PART:$name"

                    when {
                        name.equals(CONTENT_TYPES, ignoreCase = true) -> contentTypesPresent = true
                        name.endsWith("vbaProject.bin") || name.endsWith("vbaData.xml") -> macroProjectPresent = true
                        name.contains("activeX", ignoreCase = true) -> activeXPresent = true
                        name.contains("embeddings/", ignoreCase = true) ||
                            name.contains("oleObject", ignoreCase = true) -> embeddedOlePresent = true
                        name.startsWith("xl/externalLinks/", ignoreCase = true) -> externalLinksPresent = true
                        name.startsWith("customXml/", ignoreCase = true) -> customXmlPresent = true
                        name.contains("_xmlsignatures/", ignoreCase = true) ||
                            name.contains("_signatures", ignoreCase = true) -> signaturesPresent = true
                    }
                }

                if (!contentTypesPresent) abnormalities += "MISSING_CONTENT_TYPES"

                scanHyperlinks(zip, hyperlinkTargets, externalTargets, abnormalities)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            parserErrors += ParserErrorRecord(
                code = "OOXML_ZIP_INVALID",
                message = "Zip parse error: ${e.message}",
                analyzerId = OoxmlAnalyzer.ANALYZER_ID,
            )
        } catch (e: Exception) {
            context.checkCancelled()
            parserErrors += ParserErrorRecord(
                code = "OOXML_PARSE_ERROR",
                message = e.javaClass.simpleName,
                analyzerId = OoxmlAnalyzer.ANALYZER_ID,
            )
        }

        val indicators = extractIndicators(hyperlinkTargets, externalTargets)

        return OoxmlScanReport(
            partCount = partCount,
            contentTypesPresent = contentTypesPresent,
            macroProjectPresent = macroProjectPresent,
            activeXPresent = activeXPresent,
            embeddedOlePresent = embeddedOlePresent,
            externalLinksPresent = externalLinksPresent,
            customXmlPresent = customXmlPresent,
            signaturesPresent = signaturesPresent,
            hyperlinkTargets = hyperlinkTargets.distinct().take(MAX_INDICATORS),
            externalTargets = externalTargets.distinct().take(MAX_INDICATORS),
            indicators = indicators.take(MAX_INDICATORS),
            abnormalities = abnormalities.distinct(),
            parserErrors = parserErrors,
            scanTruncated = scanTruncated || parserErrors.isNotEmpty(),
        )
    }

    private fun scanHyperlinks(
        zip: ZipFile,
        hyperlinkTargets: MutableList<String>,
        externalTargets: MutableList<String>,
        abnormalities: MutableList<String>,
    ) {
        var remoteTemplateObserved = false
        val relEntries = mutableListOf<org.apache.commons.compress.archivers.zip.ZipArchiveEntry>()
        var relCount = 0
        val relIter = zip.entries
        while (relIter.hasMoreElements() && relCount < MAX_REL_PARTS) {
            context.checkCancelled()
            val e = relIter.nextElement()
            if (e.name.endsWith(".rels", ignoreCase = true)) {
                relEntries += e
                relCount++
            }
        }

        for (entry in relEntries) {
            context.checkCancelled()
            val name = entry.name
            val size = entry.size
            if (size <= 0 || size > MAX_REL_BYTES) {
                abnormalities += "SKIPPED_REL_TOO_LARGE:$name"
                continue
            }

            val text = try {
                zip.getInputStream(entry).use { input -> readBoundedUtf8(input, size) }
            } catch (e: Exception) {
                Timber.w(e, "Failed to read relationship part $name")
                abnormalities += "REL_READ_ERROR:$name"
                continue
            }
            context.checkCancelled()

            for (rel in RELATIONSHIP_REGEX.findAll(text)) {
                context.checkCancelled()
                val target = attr(rel.value, "Target") ?: continue
                val targetMode = attr(rel.value, "TargetMode")
                val type = attr(rel.value, "Type")
                if (targetMode.equals("External", ignoreCase = true)) {
                    externalTargets += target
                    if (type?.contains("hyperlink", ignoreCase = true) == true) {
                        hyperlinkTargets += target
                    }
                    if (type?.contains("remoteTemplate", ignoreCase = true) == true) remoteTemplateObserved = true
                }
            }
        }
        if (remoteTemplateObserved) abnormalities += "REMOTE_TEMPLATE_REFERENCED"
    }

    private fun attr(xml: String, name: String): String? {
        val m = Regex("""$name="([^"]*)"""").find(xml) ?: return null
        return m.groupValues[1]
    }

    private fun readBoundedUtf8(input: InputStream, declared: Long): String {
        val max = declared.coerceAtMost(MAX_REL_BYTES)
        val buffer = ByteArray(max.toInt())
        var offset = 0
        while (offset < buffer.size) {
            val n = input.read(buffer, offset, buffer.size - offset)
            if (n == -1) break
            offset += n
        }
        return String(buffer, 0, offset, Charsets.UTF_8)
    }

    private fun extractIndicators(hyperlinkTargets: List<String>, externalTargets: List<String>): List<Indicator> {
        val all = (hyperlinkTargets + externalTargets).distinct().take(MAX_INDICATORS)
        return all.flatMap { target ->
            iocExtractor.extractIndicators(
                target,
                source = IndicatorSource(container = artifact.name, entry = "relationship-target"),
            )
        }.distinctBy { it.canonicalValue }
    }

    companion object {
        private const val CONTENT_TYPES = "[Content_Types].xml"
        private const val MAX_ENTRIES = 500
        private const val MAX_PART_SIZE = 256L * 1024 * 1024
        private const val MAX_TOTAL_EXPANDED = 1024L * 1024 * 1024
        private const val MAX_REL_BYTES = 1024L * 1024
        private const val MAX_REL_PARTS = 64
        private const val MAX_INDICATORS = 100
        private val RELATIONSHIP_REGEX = Regex("""<Relationship[^>]*?>""")
    }
}

/**
 * A read-only [SeekableByteChannel] backed by the bounded [ArtifactRef] random
 * reads. All reads are bounds-checked against the artifact size and honor the
 * shared [AnalysisContext] cancellation.
 */
private class ArtifactChannel internal constructor(
    private val artifact: ArtifactRef,
    private val context: AnalysisContext,
) : SeekableByteChannel {
    private var position = 0L
    private var open = true
    private val size = artifact.sizeBytes

    override fun position(): Long = position
    override fun position(newPosition: Long): SeekableByteChannel {
        position = newPosition.coerceIn(0, size)
        return this
    }

    override fun read(dst: ByteBuffer): Int {
        context.checkCancelled()
        if (position >= size) return -1
        val want = minOf(dst.remaining().toLong(), size - position).toInt()
        if (want <= 0) return -1
        val bytes = artifact.readRange(position, want)
        if (bytes.isEmpty()) return -1
        dst.put(bytes)
        position += bytes.size
        return bytes.size
    }

    override fun close() { open = false }
    override fun isOpen(): Boolean = open
    override fun size(): Long = size
    override fun write(src: ByteBuffer): Int = throw IOException("read-only channel")
    override fun truncate(size: Long): SeekableByteChannel = throw IOException("read-only channel")
}
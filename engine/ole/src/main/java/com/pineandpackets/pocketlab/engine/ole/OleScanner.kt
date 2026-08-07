package com.pineandpackets.pocketlab.engine.ole

import com.pineandpackets.pocketlab.core.model.Indicator
import com.pineandpackets.pocketlab.core.model.IndicatorSource
import com.pineandpackets.pocketlab.core.model.ParserErrorRecord
import com.pineandpackets.pocketlab.engine.api.AnalysisContext
import com.pineandpackets.pocketlab.engine.api.ArtifactRef
import com.pineandpackets.pocketlab.engine.ioc.IocExtractor
import kotlinx.coroutines.CancellationException

/**
 * Bounded, read-only scanner for legacy OLE / Compound File Binary (CFB)
 * documents (.doc/.dot/.xls/.xlt/.xla/.ppt/.pps/.pot/.ppa/.rtf that use the
 * same CFB container).
 *
 * Safety model:
 *  - never executes, extracts, or opens embedded objects;
 *  - all reads are bounds-checked against the artifact size;
 *  - sector chains, directory depth, entry counts, FAT sectors, recursion and
 *    scanned-stream bytes are all capped;
 *  - checked (Long) arithmetic for offsets;
 *  - cooperative cancellation on every loop.
 *
 * Scope: validates the CFB header/geometry, enumerates the bounded directory
 * tree to detect macro (VBA), embedded-OLE and suspicious streams, and reads a
 * limited number of small regular streams to extract URL/domain/email
 * indicators. Mini-stream and deep property-set decoding are out of scope and
 * are reported as limitations, never as a false-clean result.
 */
internal class OleScanner(
    private val artifact: ArtifactRef,
    private val context: AnalysisContext,
    private val iocExtractor: IocExtractor = IocExtractor(),
) {

    private lateinit var bytes: ByteArray

    fun scan(): OleScanReport {
        context.checkCancelled()
        bytes = artifact.readRange(0, MAX_SCAN_BYTES)

        if (bytes.isEmpty()) {
            return OleScanReport(
                parserErrors = listOf(
                    ParserErrorRecord(
                        code = "OLE_UNREADABLE",
                        message = "OLE/CFB content could not be read",
                        analyzerId = OleAnalyzer.ANALYZER_ID,
                    )
                )
            )
        }

        if (bytes.size < HEADER_SIZE) {
            return OleScanReport(
                parserErrors = listOf(
                    ParserErrorRecord(
                        code = "OLE_HEADER_TRUNCATED",
                        message = "OLE/CFB header truncated",
                        analyzerId = OleAnalyzer.ANALYZER_ID,
                    )
                )
            )
        }

        if (!hasMagic()) {
            return OleScanReport(
                parserErrors = listOf(
                    ParserErrorRecord(
                        code = "OLE_BAD_MAGIC",
                        message = "Missing OLE/CFB compound-file signature",
                        analyzerId = OleAnalyzer.ANALYZER_ID,
                    )
                )
            )
        }

        val abnormalities = mutableListOf<String>()
        val parserErrors = mutableListOf<ParserErrorRecord>()

        val majorVersion = readU16(26)
        val minorVersion = readU16(24)
        val byteOrder = readU16(28)
        val sectorShift = readU16(30)
        val miniSectorShift = readU16(32)
        val fatSectorCount = readU32(44)
        val firstDirectorySector = readU32(48)
        val numDifat = readU32(72)
        val firstDifatSector = readU32(68)

        if (byteOrder != 0xFFFE) {
            abnormalities += "UNSUPPORTED_BYTE_ORDER"
            parserErrors += ParserErrorRecord("OLE_BAD_HEADER", "Byte order is not little-endian", OleAnalyzer.ANALYZER_ID)
            return OleScanReport(abnormalities = abnormalities, parserErrors = parserErrors, scanTruncated = true)
        }
        if (sectorShift > MAX_SECTOR_SHIFT) {
            abnormalities += "INVALID_SECTOR_SHIFT"
            parserErrors += ParserErrorRecord("OLE_BAD_HEADER", "Sector shift $sectorShift out of range", OleAnalyzer.ANALYZER_ID)
            return OleScanReport(abnormalities = abnormalities, parserErrors = parserErrors, scanTruncated = true)
        }
        val sectorSize = 1 shl sectorShift
        if (sectorSize > MAX_SECTOR_SIZE) {
            abnormalities += "SECTOR_GEOMETRY_OVERSIZED"
            parserErrors += ParserErrorRecord("OLE_BAD_HEADER", "Sector size $sectorSize too large", OleAnalyzer.ANALYZER_ID)
            return OleScanReport(abnormalities = abnormalities, parserErrors = parserErrors, scanTruncated = true)
        }
        if (majorVersion != 3 && majorVersion != 4) {
            abnormalities += "UNSUPPORTED_VERSION"
            parserErrors += ParserErrorRecord("OLE_UNSUPPORTED_VERSION", "Version $majorVersion.$minorVersion", OleAnalyzer.ANALYZER_ID)
            return OleScanReport(abnormalities = abnormalities, parserErrors = parserErrors, scanTruncated = true)
        }

        val dataSectors = (bytes.size.toLong() - HEADER_SIZE) / sectorSize
        if (dataSectors <= 0) {
            parserErrors += ParserErrorRecord("OLE_EMPTY", "No data sectors present", OleAnalyzer.ANALYZER_ID)
            return OleScanReport(abnormalities = abnormalities, parserErrors = parserErrors, scanTruncated = true)
        }

        val fatSectorIndexes = collectFatSectorIndexes(fatSectorCount, firstDifatSector, numDifat, dataSectors, abnormalities)
        val fat = buildFat(fatSectorIndexes, sectorSize, dataSectors, abnormalities)

        val entries = readDirectory(firstDirectorySector, fat, sectorSize, dataSectors, abnormalities)
        if (entries == null) {
            parserErrors += ParserErrorRecord("OLE_DIRECTORY_FAILED", "Unable to read directory", OleAnalyzer.ANALYZER_ID)
            return OleScanReport(
                majorVersion = majorVersion, minorVersion = minorVersion,
                sectorSize = sectorSize, sectorCount = dataSectors.toInt(),
                abnormalities = abnormalities, parserErrors = parserErrors, scanTruncated = true,
            )
        }

        val streams = streamList(entries)

        val macroStreamNames = mutableListOf<String>()
        val embeddedOleNames = mutableListOf<String>()
        val suspiciousNames = mutableListOf<String>()
        var storageCount = 0
        var streamCount = 0

        for (e in entries) {
            context.checkCancelled()
            when (e.type) {
                STORAGE_TYPE -> storageCount++
                STREAM_TYPE -> streamCount++
            }
        }
        for (st in streams) {
            val name = st.path
            if (isMacroName(name)) macroStreamNames.add(name)
            if (isEmbeddedName(name)) embeddedOleNames.add(name)
            if (isSuspiciousName(name)) suspiciousNames.add(name)
        }

        val indicators = extractIndicators(streams, fat, sectorSize, dataSectors)

        val truncated = abnormalities.any { it in TRUNCATION_MARKERS } || parserErrors.isNotEmpty()
        return OleScanReport(
            majorVersion = majorVersion,
            minorVersion = minorVersion,
            sectorSize = sectorSize,
            miniSectorSize = 64,
            sectorCount = dataSectors.toInt(),
            storageCount = storageCount,
            streamCount = streamCount,
            macroStreamsPresent = macroStreamNames.isNotEmpty(),
            macroStreamNames = macroStreamNames.distinct().take(MAX_NAMES),
            embeddedOlePresent = embeddedOleNames.isNotEmpty(),
            embeddedOleNames = embeddedOleNames.distinct().take(MAX_NAMES),
            suspiciousStreamNames = suspiciousNames.distinct().take(MAX_NAMES),
            streamNames = streams.map { it.path }.distinct().take(MAX_NAMES),
            indicators = indicators,
            abnormalities = abnormalities.distinct(),
            parserErrors = parserErrors,
            scanTruncated = truncated,
        )
    }

    private fun hasMagic(): Boolean {
        if (bytes.size < 8) return false
        val magic = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte(),
        )
        for (i in magic.indices) {
            if (bytes[i] != magic[i]) return false
        }
        return true
    }

    private fun readU16(offset: Int): Int {
        if (offset + 1 >= bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readU32(offset: Int): Long {
        if (offset + 3 >= bytes.size) return 0
        return u32At(bytes, offset)
    }

    private fun u32At(buf: ByteArray, a: Int): Long =
        (buf[a].toLong() and 0xFF) or
            ((buf[a + 1].toLong() and 0xFF) shl 8) or
            ((buf[a + 2].toLong() and 0xFF) shl 16) or
            ((buf[a + 3].toLong() and 0xFF) shl 24)

    private fun readSector(sector: Long, sectorSize: Int): ByteArray {
        if (sector < 0) return ByteArray(0)
        val start = HEADER_SIZE.toLong() + sector * sectorSize
        if (start < 0 || start >= bytes.size.toLong()) return ByteArray(0)
        val count = minOf(sectorSize.toLong(), bytes.size.toLong() - start).toInt()
        return bytes.copyOfRange(start.toInt(), start.toInt() + count)
    }

    private fun collectFatSectorIndexes(
        fatSectorCount: Long,
        firstDifat: Long,
        numDifat: Long,
        totalDataSectors: Long,
        abnormalities: MutableList<String>,
    ): List<Long> {
        val result = ArrayList<Long>()
        // First up to 109 FAT sector locations live directly in the header.
        val inlineCount = minOf(fatSectorCount, 109L).toInt()
        for (i in 0 until inlineCount) {
            val v = readU32(76 + i * 4)
            if (v in 0 until totalDataSectors) result.add(v)
            else abnormalities += "DIFAT_ENTRY_OUT_OF_RANGE"
        }
        // Iterate through DIFAT sectors for remaining FAT sector locations.
        var difatSector = firstDifat
        var difatSteps = 0
        val sectorSize = sectorSizeFor()
        while (difatSector != END_OF_CHAIN && difatSteps < MAX_DIFAT_SECTORS) {
            context.checkCancelled()
            difatSteps++
            if (difatSector < 0 || difatSector >= totalDataSectors) {
                abnormalities += "DIFAT_SECTOR_OUT_OF_RANGE"
                break
            }
            val buf = readSector(difatSector, sectorSize)
            val ppd = buf.size / 4 - 1
            for (i in 0 until ppd) {
                val v = u32At(buf, i * 4)
                if (v in 0 until totalDataSectors) result.add(v)
                else abnormalities += "DIFAT_ENTRY_OUT_OF_RANGE"
            }
            if (ppd > 0) {
                val next = u32At(buf, ppd * 4)
                difatSector = if (next == FREE_SECTOR) END_OF_CHAIN else next
            } else {
                difatSector = END_OF_CHAIN
            }
            if (result.size >= MAX_FAT_SECTORS) {
                abnormalities += "FAT_SECTORS_LIMIT"
                break
            }
        }
        return result
    }

    private fun sectorSizeFor(): Int {
        val shift = readU16(30).coerceIn(0, MAX_SECTOR_SHIFT)
        return 1 shl shift
    }

    private fun buildFat(
        fatSectorIndexes: List<Long>,
        sectorSize: Int,
        totalDataSectors: Long,
        abnormalities: MutableList<String>,
    ): LongArray {
        val ppf = sectorSize / 4
        val maxSectors = totalDataSectors.coerceAtMost(MAX_SECTORS).toInt()
        val fat = LongArray(maxSectors) { FREE_SECTOR }
        for (fatIndex in fatSectorIndexes) {
            context.checkCancelled()
            val buf = readSector(fatIndex, sectorSize)
            for (i in buf.indices step 4) {
                val global = (fatIndex.toInt() * ppf) + (i / 4)
                if (global >= maxSectors) break
                fat[global] = u32At(buf, i)
            }
        }
        return fat
    }

    private fun readDirectory(
        firstDir: Long,
        fat: LongArray,
        sectorSize: Int,
        dataSectors: Long,
        abnormalities: MutableList<String>,
    ): List<DirEntry>? {
        if (firstDir == END_OF_CHAIN || firstDir == FREE_SECTOR) return emptyList()
        if (firstDir < 0 || firstDir >= dataSectors) return emptyList()
        val entries = mutableListOf<DirEntry>()
        var sector = firstDir
        val visited = java.util.HashSet<Long>()
        var sectorsRead = 0
        var globalIndex = 0
        val entriesPerSector = sectorSize / DIR_ENTRY_SIZE
        while (sector != END_OF_CHAIN && sector != FREE_SECTOR) {
            context.checkCancelled()
            if (!visited.add(sector)) {
                abnormalities += "DIRECTORY_CHAIN_LOOP"
                break
            }
            if (sector < 0 || sector >= dataSectors) {
                abnormalities += "DIRECTORY_SECTOR_OUT_OF_RANGE"
                break
            }
            if (sectorsRead >= MAX_DIR_SECTORS) {
                abnormalities += "DIRECTORY_SECTORS_LIMIT"
                break
            }
            sectorsRead++
            val buf = readSector(sector, sectorSize)
            val entryCount = minOf(buf.size / DIR_ENTRY_SIZE, entriesPerSector)
            for (i in 0 until entryCount) {
                if (entries.size >= MAX_DIR_ENTRIES) {
                    abnormalities += "DIRECTORY_ENTRIES_LIMIT"
                    break
                }
                val e = parseDirEntry(buf, i * DIR_ENTRY_SIZE, globalIndex)
                globalIndex++
                // Keep the entry even when empty so that the tree indexes used
                // by child/left/right references stay aligned with the original
                // directory positions. Empty slots simply have an empty name.
                entries.add(e)
            }
            val idx = sector.toInt()
            sector = if (idx >= 0 && idx < fat.size) fat[idx] else END_OF_CHAIN
        }
        return entries
    }

    private fun parseDirEntry(buf: ByteArray, base: Int, index: Int): DirEntry {
        val nameLen = (buf[base + 64].toInt() and 0xFF) or ((buf[base + 65].toInt() and 0xFF) shl 8)
        val name = decodeDirName(buf, base, nameLen)
        val type = buf[base + 66].toInt() and 0xFF
        val left = u32At(buf, base + 68)
        val right = u32At(buf, base + 72)
        val child = u32At(buf, base + 76)
        val start = u32At(buf, base + 116)
        val size = u32At(buf, base + 120)
        return DirEntry(name, type, left, right, child, start, size, index)
    }

    private fun decodeDirName(buf: ByteArray, base: Int, nameLenBytes: Int): String {
        if (nameLenBytes <= 0) return ""
        val chars = (nameLenBytes - 2) / 2
        if (chars <= 0) return ""
        val sb = StringBuilder()
        for (i in 0 until chars) {
            val idx = base + i * 2
            if (idx + 1 >= base + 64 || idx + 1 >= buf.size) break
            val c = (buf[idx].toInt() and 0xFF) or ((buf[idx + 1].toInt() and 0xFF) shl 8)
            if (c == 0) break
            sb.append(c.toChar())
        }
        return sanitizeName(sb.toString())
    }

    private fun sanitizeName(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(if (c.isISOControl()) '?' else c)
        }
        return sb.toString().take(MAX_NAME_LEN)
    }

    private fun streamList(
        entries: List<DirEntry>,
    ): List<StreamInfo> {
        val out = mutableListOf<StreamInfo>()
        val seen = HashSet<Int>()
        // Emit every non-root entry as a top-level stream entry using its own
        // sanitized name. This is intentionally flat (not a full parent-path
        // reconstruction) to remain deterministic and bounded on hostile
        // directory structures, while still surfacing every stream name for
        // macro/embedded/suspicious name detection.
        for (e in entries) {
            context.checkCancelled()
            if (e.type == STREAM_TYPE && e.name.isNotEmpty() && seen.add(e.index)) {
                out.add(StreamInfo(e.name, e.startSector, e.size))
            }
        }
        return out
    }

    private fun extractIndicators(
        streams: List<StreamInfo>,
        fat: LongArray,
        sectorSize: Int,
        dataSectors: Long,
    ): List<Indicator> {
        val candidates = streams
            .filter { it.size in 1..MAX_STREAM_SCAN_BYTES && it.startSector >= 0 }
            .sortedBy { it.size }
            .take(MAX_CONTENT_STREAMS)
        val indicators = mutableListOf<Indicator>()
        val seen = HashSet<String>()
        for (st in candidates) {
            context.checkCancelled()
            val content = readRegularStream(st, fat, sectorSize, dataSectors) ?: continue
            if (content.isEmpty()) continue
            val text = decodeLatin1(content)
            val found = iocExtractor.extractIndicators(
                text,
                source = IndicatorSource(container = artifact.name, entry = st.path),
            )
            for (ind in found) {
                if (seen.add(ind.canonicalValue)) {
                    indicators.add(ind)
                }
            }
            if (indicators.size >= MAX_INDICATORS) break
        }
        return indicators.take(MAX_INDICATORS)
    }

    private fun decodeLatin1(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 0x20..0x7E) {
                sb.append(c.toChar())
            } else if (c == '\t'.code || c == '\n'.code || c == '\r'.code) {
                sb.append(c.toChar())
            } else {
                sb.append(' ')
            }
        }
        return sb.toString()
    }

    private fun readRegularStream(
        st: StreamInfo,
        fat: LongArray,
        sectorSize: Int,
        dataSectors: Long,
    ): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        var sector = st.startSector
        var remaining = minOf(st.size, MAX_STREAM_SCAN_BYTES.toLong())
        val visited = HashSet<Long>()
        while (remaining > 0) {
            context.checkCancelled()
            if (sector < 0 || sector == END_OF_CHAIN || sector == FREE_SECTOR) break
            if (sector >= dataSectors) break
            if (!visited.add(sector)) break
            val buf = readSector(sector, sectorSize)
            val take = minOf(remaining, buf.size.toLong()).toInt()
            if (take <= 0) break
            out.write(buf, 0, take)
            remaining -= take
            val idx = sector.toInt()
            sector = if (idx < fat.size) fat[idx] else END_OF_CHAIN
        }
        return out.toByteArray()
    }

    companion object {
        const val HEADER_SIZE = 512
        const val DIR_ENTRY_SIZE = 128
        const val MAX_SECTOR_SHIFT = 12
        const val MAX_SECTOR_SIZE = 4096
        const val MAX_SCAN_BYTES = 16 * 1024 * 1024
        const val MAX_SECTORS = 9000L
        const val MAX_FAT_SECTORS = 4096
        const val MAX_DIFAT_SECTORS = 256
        const val MAX_DIR_SECTORS = 256
        const val MAX_DIR_ENTRIES = 16_384
        const val MAX_STREAM_SCAN_BYTES = 96 * 1024
        const val MAX_CONTENT_STREAMS = 16
        const val MAX_INDICATORS = 100
        const val MAX_NAMES = 200
        const val MAX_NAME_LEN = 128

        const val END_OF_CHAIN = 0xFFFFFFFEL
        const val FREE_SECTOR = 0xFFFFFFFFL
        const val STREAM_TYPE = 2
        const val STORAGE_TYPE = 1
        const val ROOT_TYPE = 5

        val TRUNCATION_MARKERS = listOf("DIRECTORY_SECTORS_LIMIT", "DIRECTORY_ENTRIES_LIMIT")
    }
}

internal data class DirEntry(
    val name: String,
    val type: Int,
    val left: Long,
    val right: Long,
    val child: Long,
    val startSector: Long,
    val size: Long,
    val index: Int,
)

internal data class StreamInfo(
    val path: String,
    val startSector: Long,
    val size: Long,
)

private fun isMacroName(name: String): Boolean {
    val n = name.lowercase()
    return n == "vba" ||
        n == "_vba_project" ||
        n == "project" ||
        n.contains("vba") ||
        (n.contains("macros") && !n.contains("kmacro")) ||
        n == "dir" ||
        n == "_vba_project_cwd" ||
        n.contains("module1")
}

private fun isEmbeddedName(name: String): Boolean {
    val n = name.lowercase()
    return n.contains("objectpool") ||
        n.contains("embedding") ||
        n.contains("ole10native") ||
        n.contains("_1ole") ||
        n.contains("package") ||
        n.contains("contents") ||
        n.contains(".bin")
}

private fun isSuspiciousName(name: String): Boolean {
    val n = name.lowercase()
    return n.contains("xor") ||
        n.contains("decode") ||
        n.contains("obfus") ||
        n.contains("encrypted") ||
        n.contains("drop") ||
        n.contains("payload")
}
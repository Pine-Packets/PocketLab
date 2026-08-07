package com.pineandpackets.pocketlab.engine.ole

import com.pineandpackets.pocketlab.core.model.DetectedType
import com.pineandpackets.pocketlab.engine.api.AnalysisCancellation
import com.pineandpackets.pocketlab.engine.api.AnalysisContext
import com.pineandpackets.pocketlab.engine.api.ArtifactRef
import com.pineandpackets.pocketlab.engine.api.CaseBudget
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds minimal, structurally valid OLE/CFB byte fixtures for analyzer tests.
 * Uses 512-byte sectors, major version 3, little-endian.
 *
 * Layout (by sector index after the 512-byte header):
 *   0       FAT sector
 *   1       directory sector
 *   2..n-1  stream data sectors
 */
object OleFixture {

    const val HEADER_SIZE = 512
    const val SECTOR_SIZE = 512

    const val END_OF_CHAIN: Long = 0xFFFFFFFEL
    const val FREE_SECT: Long = 0xFFFFFFFFL
    const val FAT_SECT: Long = 0xFFFFFFFDL

    data class Stream(
        val name: String,
        val type: Int,                // 2 stream, 1 storage, 5 root
        val data: ByteArray = ByteArray(0),
    )

    private val MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte(),
    )

    /**
     * Build a CFB with the given stream/storage entries enumerated in the
     * directory after the root entry. Streams with data are laid out in data
     * sectors starting at sector 2 and are chained through FAT.
     */
    fun cfb(vararg streams: Stream): ByteArray {
        // Assign start sectors sequentially for streams with data.
        var nextSector = 2
        val startMap = HashMap<String, Long>()
        for (s in streams) {
            if (s.type == 2 && s.data.isNotEmpty()) {
                startMap[s.name] = nextSector.toLong()
                nextSector += (s.data.size + SECTOR_SIZE - 1) / SECTOR_SIZE
            }
        }

        // Directory entries: root (index 0) then one per stream. Multiple
        // directory sectors are chained when more than 4 entries are present.
        val entryCount = 1 + streams.size
        val dirSectorCount = (entryCount * 128 + SECTOR_SIZE - 1) / SECTOR_SIZE
        val dirStartSector = nextSector.toInt()  // sector after stream data
        nextSector += dirSectorCount

        val totalSectors = nextSector
        val fileSize = HEADER_SIZE + totalSectors * SECTOR_SIZE
        val raw = ByteArray(fileSize)
        val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)

        // Header.
        buf.put(MAGIC)
        putU16(buf, 24, 0x003E) // minor version
        putU16(buf, 26, 0x0003) // major version
        putU16(buf, 28, 0xFFFE) // byte order (little-endian)
        putU16(buf, 30, 9)      // sector shift => 512
        putU16(buf, 32, 6)      // mini sector shift => 64
        putU32(buf, 40, dirSectorCount.toLong()) // num directory sectors
        putU32(buf, 44, 1)      // num FAT sectors
        putU32(buf, 48, dirStartSector.toLong()) // first directory sector
        putU32(buf, 60, END_OF_CHAIN) // first mini FAT sector (none)
        putU32(buf, 68, END_OF_CHAIN) // first DIFAT sector (none)
        putU32(buf, 76, 0)     // DIFAT[0] = FAT sector 0

        // FAT sector (sector 0): chain directory sectors (0 => FAT).
        val fatBase = HEADER_SIZE
        for (i in 0 until SECTOR_SIZE / 4) {
            putU32(buf, fatBase + i * 4, when (i) {
                0 -> FAT_SECT
                in 1 until dirStartSector -> FREE_SECT // stream data chains set below
                in dirStartSector until dirStartSector + dirSectorCount ->
                    if (i == dirStartSector + dirSectorCount - 1) END_OF_CHAIN else (i + 1).toLong()
                else -> FREE_SECT
            })
        }

        // Register data-sector chains + copy stream bytes.
        for (s in streams) {
            if (s.type != 2 || s.data.isEmpty()) continue
            val start = startMap[s.name] ?: continue
            val sectorCount = (s.data.size + SECTOR_SIZE - 1) / SECTOR_SIZE
            val base = HEADER_SIZE + start.toInt() * SECTOR_SIZE
            System.arraycopy(s.data, 0, raw, base, s.data.size)
            for (i in 0 until sectorCount) {
                val idx = (start + i).toInt()
                putU32(buf, fatBase + idx * 4, if (i == sectorCount - 1) END_OF_CHAIN else start + i + 1)
            }
        }

        // Directory sector(s): root entry at index 0, then streams.
        for (sector in 0 until dirSectorCount) {
            val dirOffset = HEADER_SIZE + (dirStartSector + sector) * SECTOR_SIZE
            for (slot in 0 until SECTOR_SIZE / 128) {
                val entryGlobal = sector * (SECTOR_SIZE / 128) + slot
                val base = dirOffset + slot * 128
                if (entryGlobal == 0) {
                    writeDirEntry(buf, base, "Root Entry", 5, 0, 0)
                } else {
                    val streamIdx = entryGlobal - 1
                    if (streamIdx < streams.size) {
                        val s = streams[streamIdx]
                        val start = startMap[s.name] ?: 0
                        writeDirEntry(buf, base, s.name, s.type, s.data.size.toLong(), start)
                    }
                }
            }
        }

        return raw
    }

    /** A plain .doc with no macros, no embedded objects, no spammy names. */
    fun plainDoc(): ByteArray = cfb(
        Stream("WordDocument", 2, "plain word body content".toByteArray()),
        Stream("\u0001CompObj", 2, "comp".toByteArray()),
        Stream("SummaryInformation", 2, "Title: A normal document".toByteArray()),
    )

    /** A macro-enabled .doc-style CFB with VBA streams. */
    fun macroDoc(): ByteArray = cfb(
        Stream("WordDocument", 2, "word body".toByteArray()),
        Stream("Macros", 1),
        Stream("VBA", 1),
        Stream("Project", 2, "VBA project data".toByteArray()),
        Stream("\u000bVBA/_VBA_PROJECT", 2, "x".toByteArray()),
    )

    /** A legend .doc with embedded OLE object streams. */
    fun embeddedDoc(): ByteArray = cfb(
        Stream("WordDocument", 2, "word body".toByteArray()),
        Stream("ObjectPool", 2, "pool".toByteArray()),
        Stream("Package", 2, "pkg".toByteArray()),
        Stream("\u0001Ole10Native", 2, "native".toByteArray()),
    )

    /** A doc whose small stream content contains URL indicators. */
    fun docWithIndicators(url: String): ByteArray = cfb(
        Stream("WordDocument", 2, "word body".toByteArray()),
        Stream("SummaryInformation", 2, url.toByteArray(Charsets.UTF_8)),
    )

    /** Not an OLE file at all. */
    fun notCfb(): ByteArray = "%PDF-1.7\n".toByteArray()

    /** A truncated header. */
    fun truncatedHeader(): ByteArray = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(),
    )

    private fun writeDirEntry(
        buf: ByteBuffer,
        base: Int,
        name: String,
        type: Int,
        size: Long,
        startSector: Long,
    ) {
        val chars = name.toCharArray()
        val nameBytes = chars.size * 2 + 2
        for (i in chars.indices) {
            putU16(buf, base + i * 2, chars[i].code and 0xFFFF)
        }
        putU16(buf, base + 64, nameBytes)
        buf.put(base + 66, type.toByte())
        putU32(buf, base + 116, startSector)
        putU32(buf, base + 120, size)
    }

    private fun putU16(buf: ByteBuffer, offset: Int, value: Int) {
        val a = buf.array()
        a[offset] = (value and 0xFF).toByte()
        a[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun putU32(buf: ByteBuffer, offset: Int, value: Long) {
        val a = buf.array()
        a[offset] = (value and 0xFF).toByte()
        a[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        a[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        a[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}

/** Provides an [ArtifactRef] over in-memory bytes for analyzer tests. */
class OleArtifactRef(
    private val bytes: ByteArray,
    name: String = "sample.doc",
    override val detectedType: DetectedType = DetectedType.OLE,
) : ArtifactRef {
    override val artifactId: String = "test-ole-artifact"
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
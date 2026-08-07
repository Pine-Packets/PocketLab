package com.pineandpackets.pocketlab.engine.archive

import com.pineandpackets.pocketlab.core.common.AnalysisError
import com.pineandpackets.pocketlab.core.model.CaseTextEntry
import com.pineandpackets.pocketlab.core.model.IndicatorSource
import com.pineandpackets.pocketlab.engine.ioc.IocExtractor
import org.apache.commons.compress.archivers.zip.ZipFile
import timber.log.Timber
import java.io.File

/**
 * Bounded inventory and IOC extraction of text/notes entries inside a case
 * archive (WF-004). Text-like entries are streamed directly from the container
 * without full extraction, bounded by per-entry and total byte/entry quotas.
 * Content bytes are never retained; only path, scan metadata, and extracted
 * indicators are returned.
 *
 * Security: this never decompresses beyond quotas and never executes content.
 */
class CaseZipTextScanner(
    private val iocExtractor: IocExtractor = IocExtractor()
) {

    fun scan(archiveFile: File, containerName: String = archiveFile.name): Result<List<CaseTextEntry>> {
        return try {
            val zip = ZipFile.builder().setFile(archiveFile).get()
            zip.use { zip ->
                val results = mutableListOf<CaseTextEntry>()
                var totalScannedBytes = 0L

                for (entry in zip.entries) {
                    if (results.size >= MAX_SCAN_ENTRIES) break
                    if (entry.isDirectory || entry.size <= 0) continue
                    if (entry.generalPurposeBit?.usesEncryption() == true) continue

                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                    if (ext !in TEXT_EXTENSIONS) continue
                    if (entry.size > MAX_PER_ENTRY_BYTES) continue
                    if (totalScannedBytes + entry.size > MAX_TOTAL_SCAN_BYTES) break

                    val text = try {
                        zip.getInputStream(entry).use { input ->
                            input.readNBytes(entry.size.toInt()).toString(Charsets.UTF_8)
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to read text entry ${entry.name}")
                        continue
                    }

                    totalScannedBytes += entry.size

                    // Binary content guard: NUL bytes indicate non-text data
                    if (text.contains('\u0000')) continue

                    val indicators = iocExtractor.extractIndicators(
                        text,
                        source = IndicatorSource(container = containerName, entry = entry.name)
                    )

                    results.add(
                        CaseTextEntry(
                            path = entry.name,
                            expandedSize = entry.size,
                            scannedBytes = entry.size,
                            detectedCharset = "UTF-8",
                            indicators = indicators
                        )
                    )
                }

                Result.success(results)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to scan case archive text entries for ${archiveFile.name}")
            Result.failure(
                AnalysisError.ArchiveError("Failed to scan case archive text entries", e)
            )
        }
    }

    companion object {
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "log", "csv", "json", "eml", "rtf", "url",
            "conf", "ini", "properties", "yaml", "yml", "xml", "html", "htm"
        )
        private const val MAX_SCAN_ENTRIES = 50
        private const val MAX_PER_ENTRY_BYTES = 1024L * 1024L
        private const val MAX_TOTAL_SCAN_BYTES = 8L * 1024L * 1024L
    }
}

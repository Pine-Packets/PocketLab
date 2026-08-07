package com.pineandpackets.pocketlab.engine.archive

import com.pineandpackets.pocketlab.core.common.AnalysisError
import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import com.pineandpackets.pocketlab.core.common.addChecked
import org.apache.commons.compress.archivers.zip.ZipFile
import timber.log.Timber
import java.io.File
import java.util.UUID

data class ExtractionResult(
    val extractedFiles: Map<String, ExtractedFile>,
    val skippedEntries: List<SkippedEntry>,
    val totalBytesExtracted: Long,
    val extractionWarnings: List<String>
)

data class ExtractedFile(
    val originalPath: String,
    val extractedPath: String,
    val randomizedName: String,
    val size: Long,
    val detectedType: String?
)

data class SkippedEntry(
    val originalPath: String,
    val reason: String
)

class SelectiveExtractor {

    fun extractEntries(
        archiveFile: File,
        entriesToExtract: List<String>,
        destinationDir: File,
        analysisResult: ArchiveAnalysisResult
    ): Result<ExtractionResult> {
        return try {
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }

            val canonicalDestRoot = destinationDir.canonicalPath
            val extractedFiles = mutableMapOf<String, ExtractedFile>()
            val skippedEntries = mutableListOf<SkippedEntry>()
            val warnings = mutableListOf<String>()
            var totalBytesExtracted = 0L
            val usedNames = mutableSetOf<String>()

            val requestedSet = entriesToExtract.toSet()
            val validEntries = analysisResult.entries.filter { it.originalPath in requestedSet }
            val missingEntries = requestedSet - validEntries.map { it.originalPath }.toSet()

            for (missing in missingEntries) {
                skippedEntries.add(SkippedEntry(missing, "Entry not found in archive"))
            }

            val zipFile = ZipFile.builder().setFile(archiveFile).get()
            zipFile.use { zip ->
                for (entryInfo in validEntries) {
                    val entry = zip.getEntry(entryInfo.originalPath)
                    if (entry == null) {
                        skippedEntries.add(SkippedEntry(entryInfo.originalPath, "Entry not accessible in ZIP"))
                        continue
                    }

                    if (entry.isDirectory) {
                        skippedEntries.add(SkippedEntry(entryInfo.originalPath, "Directory entry"))
                        continue
                    }

                    if (entryInfo.isEncrypted) {
                        skippedEntries.add(SkippedEntry(entryInfo.originalPath, "Encrypted entry requires password"))
                        continue
                    }

                    val normalizedPath = entryInfo.normalizedPath
                    if (normalizedPath.isEmpty()) {
                        skippedEntries.add(SkippedEntry(entryInfo.originalPath, "Empty normalized path"))
                        continue
                    }

                    if (normalizedPath.contains("..") || normalizedPath.startsWith("/")) {
                        skippedEntries.add(SkippedEntry(entryInfo.originalPath, "Path traversal detected"))
                        continue
                    }

                    if (entryInfo.originalPath.contains("..") ||
                        entryInfo.originalPath.startsWith("/") ||
                        entryInfo.originalPath.startsWith("\\") ||
                        entryInfo.originalPath.matches(Regex("^[A-Za-z]:.*"))) {
                        skippedEntries.add(SkippedEntry(entryInfo.originalPath, "Original path contains traversal"))
                        continue
                    }

                    if (entryInfo.expandedSize > AnalysisLimits.MAX_SINGLE_ENTRY_BYTES) {
                        skippedEntries.add(SkippedEntry(entryInfo.originalPath, "Entry exceeds size limit"))
                        continue
                    }

                    val randomName = generateUniqueName(usedNames)
                    usedNames.add(randomName)
                    val outputFile = File(destinationDir, randomName)
                    val canonicalOutput = outputFile.canonicalPath

                    if (!canonicalOutput.startsWith(canonicalDestRoot + File.separator) &&
                        canonicalOutput != canonicalDestRoot) {
                        skippedEntries.add(SkippedEntry(entryInfo.originalPath, "Extraction path escapes workspace"))
                        warnings.add("Blocked path traversal for: ${entryInfo.originalPath}")
                        continue
                    }

                    var bytesWritten = 0L
                    try {
                        zip.getInputStream(entry).use { input ->
                            outputFile.outputStream().use { output ->
                                val buffer = ByteArray(AnalysisLimits.BUFFER_SIZE)
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    bytesWritten += bytesRead
                                    if (bytesWritten > AnalysisLimits.MAX_SINGLE_ENTRY_BYTES) {
                                        output.close()
                                        outputFile.delete()
                                        skippedEntries.add(SkippedEntry(entryInfo.originalPath, "Entry exceeds runtime size limit"))
                                        break
                                    }
                                    totalBytesExtracted = addChecked(
                                        totalBytesExtracted,
                                        bytesRead.toLong(),
                                        "Extraction byte counter overflow"
                                    )
                                    if (totalBytesExtracted > AnalysisLimits.MAX_ARCHIVE_EXPANDED_BYTES) {
                                        output.close()
                                        outputFile.delete()
                                        skippedEntries.add(SkippedEntry(entryInfo.originalPath, "Total extraction quota exceeded"))
                                        return@use
                                    }
                                    output.write(buffer, 0, bytesRead)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        outputFile.delete()
                        skippedEntries.add(SkippedEntry(entryInfo.originalPath, "Extraction failed: ${e.javaClass.simpleName}"))
                        continue
                    }

                    if (outputFile.exists() && outputFile.length() > 0) {
                        val detectedType = detectFileType(outputFile)
                        extractedFiles[entryInfo.originalPath] = ExtractedFile(
                            originalPath = entryInfo.originalPath,
                            extractedPath = canonicalOutput,
                            randomizedName = randomName,
                            size = bytesWritten,
                            detectedType = detectedType
                        )
                    }
                }
            }

            Timber.i("Extracted ${extractedFiles.size} entries, skipped ${skippedEntries.size}, " +
                "total bytes: $totalBytesExtracted")

            Result.success(
                ExtractionResult(
                    extractedFiles = extractedFiles,
                    skippedEntries = skippedEntries,
                    totalBytesExtracted = totalBytesExtracted,
                    extractionWarnings = warnings
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Selective extraction failed")
            Result.failure(AnalysisError.ArchiveError("Selective extraction failed", e))
        }
    }

    private fun generateUniqueName(usedNames: Set<String>): String {
        var name: String
        do {
            name = UUID.randomUUID().toString().replace("-", "") + ".bin"
        } while (name in usedNames)
        return name
    }

    private fun detectFileType(file: File): String? {
        return try {
            val header = ByteArray(8)
            val read = file.inputStream().use { input ->
                var offset = 0
                while (offset < header.size) {
                    val n = input.read(header, offset, header.size - offset)
                    if (n == -1) break
                    offset += n
                }
                offset
            }
            if (read < 4) return null
            when {
                header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() -> "ZIP"
                header[0] == 0x64.toByte() && header[1] == 0x65.toByte() &&
                    header[2] == 0x78.toByte() && header[3] == 0x0A.toByte() -> "DEX"
                header[0] == 0x7F.toByte() && header[1] == 0x45.toByte() &&
                    header[2] == 0x4C.toByte() && header[3] == 0x46.toByte() -> "ELF"
                header[0] == 0x4D.toByte() && header[1] == 0x5A.toByte() -> "PE"
                header[0] == 0x25.toByte() && header[1] == 0x50.toByte() &&
                    header[2] == 0x44.toByte() && header[3] == 0x46.toByte() -> "PDF"
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

package com.pineandpackets.pocketlab.engine.archive

import com.pineandpackets.pocketlab.core.common.AnalysisError
import com.pineandpackets.pocketlab.core.common.AnalysisLimits
import com.pineandpackets.pocketlab.core.common.addChecked
import timber.log.Timber
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a synthetic APKS container from multiple user-selected APK files so
 * the existing package-set analysis path can merge and compare split APKs
 * (FR-IN-003: multi-file split APK intake).
 *
 * The first input becomes "base.apk"; subsequent inputs become "split_N.apk".
 * A BundleConfig.pb marker is added so PackageSetAnalyzer detects the result
 * as an APKS package set.
 *
 * Security: input APKs are copied byte-for-byte into the container with no
 * re-compression, decompression, or inspection. They are never executed.
 * Entry counts and combined size are bounded by AnalysisLimits.
 */
class SplitApkSetBuilder {

    data class BuildResult(
        val containerFile: File,
        val apkCount: Int,
        val totalBytes: Long
    )

    /**
     * @param apkFiles staged APK files to bundle, in user-selected order.
     * @param outputDir directory (app-private) in which to create the container.
     * @return the created synthetic APKS container.
     */
    fun build(
        apkFiles: List<File>,
        outputDir: File,
        containerName: String = "split_set_${System.nanoTime()}.apks"
    ): Result<BuildResult> {
        if (apkFiles.isEmpty()) {
            return Result.failure(AnalysisError.ArchiveError("No APK files provided"))
        }
        if (apkFiles.size > AnalysisLimits.MAX_DEX_COUNT) {
            return Result.failure(
                AnalysisError.QuotaExceededError(
                    "Too many APK files (${apkFiles.size}), exceeds limit of ${AnalysisLimits.MAX_DEX_COUNT}"
                )
            )
        }

        var totalBytes = 0L
        for (file in apkFiles) {
            if (!file.exists() || !file.isFile) {
                return Result.failure(AnalysisError.ArchiveError("APK file does not exist: ${file.name}"))
            }
            totalBytes = try {
                addChecked(totalBytes, file.length(), "Total APK set size overflow")
            } catch (e: ArithmeticException) {
                return Result.failure(AnalysisError.QuotaExceededError("Total APK set size overflow"))
            }
            if (totalBytes > AnalysisLimits.MAX_INPUT_SIZE_BYTES) {
                return Result.failure(
                    AnalysisError.QuotaExceededError(
                        "Combined APK size exceeds the maximum input size of ${AnalysisLimits.MAX_INPUT_SIZE_BYTES} bytes"
                    )
                )
            }
        }

        val container = File(outputDir, containerName)
        return try {
            ZipOutputStream(container.outputStream()).use { zos ->
                apkFiles.forEachIndexed { index, file ->
                    val entryName = if (index == 0) "base.apk" else "split_${index}.apk"
                    val fileSize = file.length()
                    val crc = CRC32()
                    file.inputStream().use { input ->
                        val buffer = ByteArray(AnalysisLimits.BUFFER_SIZE)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            crc.update(buffer, 0, bytesRead)
                        }
                    }
                    val entry = ZipEntry(entryName).apply {
                        method = ZipEntry.STORED
                        size = fileSize
                        compressedSize = fileSize
                        setCrc(crc.value)
                    }
                    zos.putNextEntry(entry)
                    file.inputStream().use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()
                }

                // BundleConfig.pb marker so the container is detected as APKS
                val marker = ZipEntry("BundleConfig.pb")
                zos.putNextEntry(marker)
                zos.write(byteArrayOf(0x00))
                zos.closeEntry()
            }
            Timber.i("Built split APK set container ${container.name} with ${apkFiles.size} APKs ($totalBytes bytes)")
            Result.success(BuildResult(container, apkFiles.size, totalBytes))
        } catch (e: Exception) {
            container.delete()
            Timber.e(e, "Failed to build split APK set container")
            Result.failure(AnalysisError.ArchiveError("Failed to build split APK set", e))
        }
    }
}

package com.pineandpackets.pocketlab.engine.orchestrator

import com.pineandpackets.pocketlab.engine.api.AnalysisCancellation
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * [ArtifactSource] backed by a real [File], with budgeted reads and cooperative
 * cancellation. Reads never allocate beyond the requested count and always
 * honor the shared [AnalysisCancellation].
 */
class FileArtifactSource(
    private val file: File,
    private val cancellation: AnalysisCancellation,
) : ArtifactSource {

    override val name: String = file.name
    override val sizeBytes: Long = file.length()
    override val claimedMimeType: String? = null
    override val advisoryExtension: String? = file.extension.ifEmpty { null }

    override fun readNBytes(count: Int): ByteArray {
        cancellation.checkCancelled()
        return file.inputStream().use { input ->
            val buffer = ByteArray(count)
            var total = 0
            while (total < count) {
                cancellation.checkCancelled()
                val n = input.read(buffer, total, count - total)
                if (n < 0) break
                total += n
            }
            if (total == 0) ByteArray(0) else buffer.copyOf(total)
        }
    }

    override fun readRange(offset: Long, count: Int): ByteArray {
        cancellation.checkCancelled()
        if (offset < 0 || count < 0) return ByteArray(0)
        return RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            val buffer = ByteArray(count)
            val n = raf.read(buffer, 0, count)
            if (n <= 0) ByteArray(0) else buffer.copyOf(n)
        }
    }

    override fun computeSha256(): String? {
        cancellation.checkCancelled()
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                cancellation.checkCancelled()
                val n = input.read(buffer)
                if (n < 0) break
                if (n > 0) digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
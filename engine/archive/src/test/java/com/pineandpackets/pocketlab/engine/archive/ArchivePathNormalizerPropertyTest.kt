package com.pineandpackets.pocketlab.engine.archive

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.forAll
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Property-based tests over [ArchivePathNormalizer]. Entry names are treated as
 * hostile input, so these tests assert invariants over arbitrarily generated
 * path strings that include traversal, absolute, drive, backslash, NUL, Unicode,
 * and separator-confusion payloads.
 */
class ArchivePathNormalizerPropertyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File

    @Before
    fun setUp() {
        rootDir = tempFolder.newFolder("workspace")
    }

    private val hostileSegments = Arb.element(
        listOf(
            "a", "b", "file.txt", "dir", "name with spaces", "t\u00e9st", "\u65e5\u672c\u8a9e",
            "..", ".", "", "/", "\\", "\\\\", "C:", "c:", "a:", "z:", "\u0000", "x\u0000y",
            "\u202eevil", "%2e%2e", "a..b", "..hidden", "hidden..", "0x0", "\$", "#", "-", "..%2f"
        )
    )

    private val separators = Arb.element(listOf("/", "\\", "\\\\", "\u0000/"))

    private val hostilePath: Arb<String> = arbitrary { rs ->
        val segmentCount = (0..10).random(rs.random)
        val segments = List(segmentCount) { hostileSegments.bind() }
        val sep = separators.bind()
        segments.joinToString(sep)
    }

    private val config = PropTestConfig(seed = 20260806L, iterations = 300)

    @Test
    fun `accepted normalized paths never escape the workspace root`() {
        runBlocking {
            forAll(config, hostilePath) { path ->
                val normalized = ArchivePathNormalizer.normalize(path)
                if (normalized == null) {
                    true
                } else {
                    val rootCanonical = rootDir.canonicalPath
                    val target = File(rootDir, normalized).canonicalPath
                    target == rootCanonical ||
                        target.startsWith(rootCanonical + File.separator)
                }
            }
        }
    }

    @Test
    fun `accepted normalized paths are clean relative paths`() {
        runBlocking {
            forAll(config, hostilePath) { path ->
                val normalized = ArchivePathNormalizer.normalize(path)
                if (normalized == null) {
                    true
                } else {
                    normalized.isNotEmpty() &&
                        !normalized.contains("..") &&
                        !normalized.startsWith("/") &&
                        !normalized.contains('\u0000') &&
                        !normalized.matches(Regex("^[A-Za-z]:.*"))
                }
            }
        }
    }

    @Test
    fun `normalization is idempotent on accepted paths`() {
        runBlocking {
            forAll(config, hostilePath) { path ->
                val first = ArchivePathNormalizer.normalize(path)
                if (first == null) {
                    true
                } else {
                    ArchivePathNormalizer.normalize(first) == first
                }
            }
        }
    }

    @Test
    fun `rejected paths are suspicious or degenerate`() {
        runBlocking {
            forAll(config, hostilePath) { path ->
                val normalized = ArchivePathNormalizer.normalize(path)
                if (normalized == null) {
                    val degenerate = path
                        .replace("\\", "/")
                        .split("/")
                        .all { it.isEmpty() || it == "." }
                    ArchivePathNormalizer.isPathSuspicious(path) || degenerate
                } else {
                    true
                }
            }
        }
    }

    @Test
    fun `isPathSuspicious never disagrees on traversal and absolute forms`() {
        runBlocking {
            forAll(config, hostilePath) { path ->
                if (path.contains("..") ||
                    path.startsWith("/") ||
                    path.startsWith("\\") ||
                    path.matches(Regex("^[A-Za-z]:.*")) ||
                    path.contains('\u0000')
                ) {
                    ArchivePathNormalizer.isPathSuspicious(path)
                } else {
                    true
                }
            }
        }
    }

    @Test
    fun `accepted paths never physically resolve outside root on disk`() {
        runBlocking {
            forAll(config, hostilePath) { path ->
                val normalized = ArchivePathNormalizer.normalize(path)
                if (normalized == null) {
                    true
                } else {
                    val sampleDir = File(rootDir, "sample-${this.attempts()}")
                    sampleDir.mkdirs()
                    val outFile = File(sampleDir, normalized)
                    outFile.parentFile?.mkdirs()
                    outFile.createNewFile()
                    val rootCanonical = sampleDir.canonicalPath
                    val resolved = outFile.canonicalFile
                    resolved == File(rootCanonical) ||
                        resolved.path.startsWith(rootCanonical + File.separator)
                }
            }
        }
    }
}

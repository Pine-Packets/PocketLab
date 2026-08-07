package com.pineandpackets.pocketlab.engine.archive

/**
 * Centralized path-safety checks applied to archive entry names before any
 * entry is accepted for inventory, nested analysis, or physical extraction.
 *
 * All entry names are treated as hostile input. This object is deliberately
 * small and pure so it can be exhaustively exercised by property-based tests.
 */
object ArchivePathNormalizer {

    /** Returns true when the raw entry name shows traversal, absolute-path,
     * drive-path, or NUL indicators. */
    fun isPathSuspicious(path: String): Boolean {
        return path.contains("..") ||
            path.startsWith("/") ||
            path.startsWith("\\") ||
            path.matches(Regex("^[A-Za-z]:.*")) ||
            path.contains('\u0000')
    }

    /**
     * Normalizes an archive entry name into a safe relative path, or returns
     * null when the name must be rejected outright.
     *
     * Rejection rules:
     *  - NUL bytes are always rejected.
     *  - After replacing backslashes and removing empty / "." / ".." segments,
     *    the result must not begin with "/" and must not contain a ".." segment.
     *
     * The returned value is safe to use as a virtual relative path under a
     * workspace root; it never contains "..", never begins with "/", and never
     * contains a NUL byte.
     */
    fun normalize(path: String): String? {
        if (path.contains('\u0000')) return null

        val segments = path
            .replace("\\", "/")
            .split("/")

        val kept = mutableListOf<String>()
        for (segment in segments) {
            if (segment.isEmpty() || segment == "." || segment == "..") continue
            kept.add(segment)
        }

        val normalized = kept.joinToString("/")
        if (normalized.isEmpty() ||
            normalized.startsWith("/") ||
            normalized.contains("..") ||
            normalized.matches(Regex("^[A-Za-z]:.*"))
        ) {
            return null
        }
        return normalized
    }
}

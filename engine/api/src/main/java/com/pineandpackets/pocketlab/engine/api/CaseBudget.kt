package com.pineandpackets.pocketlab.engine.api

import com.pineandpackets.pocketlab.core.common.addChecked
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared, case-level accounting that is never reset by a nested container or
 * child artifact. Every analyzer and the dispatcher consume from the same
 * budget, so a deeply nested archive cannot gain fresh allocation headroom.
 *
 * All size summation uses checked arithmetic; integer overflow is a rejection
 * condition, never silent wraparound.
 */
class CaseBudget(
    val maxBytesRead: Long,
    val maxExpandedBytes: Long,
    val maxArtifactCount: Int,
    val maxArchiveEntries: Int,
    val maxRecursionDepth: Int,
    val maxFindings: Int,
    val maxIndicators: Int,
    val maxFacts: Int,
    val maxOps: Int,
) {
    private var bytesRead: Long = 0L
    private var expandedBytes: Long = 0L
    private val artifacts = CountHolder(maxArtifactCount)
    private val archiveEntries = CountHolder(maxArchiveEntries)
    private val recursionDepth = CountHolder(maxRecursionDepth)
    private val findings = CountHolder(maxFindings)
    private val indicators = CountHolder(maxIndicators)
    private val facts = CountHolder(maxFacts)
    private val ops = CountHolder(maxOps)
    private val events: ArrayDeque<String> = ArrayDeque()

    val quotaEvents: List<String> get() = events.toList()

    /** Returns false (recording the event) when consuming bytes would exceed the cap. */
    fun tryConsumeBytes(amount: Long): Boolean {
        val next = try { addChecked(bytesRead, amount) } catch (e: ArithmeticException) { null } ?: run {
            record("MAX_READ_BYTES_OVERFLOW"); return false
        }
        if (next > maxBytesRead) { record("MAX_READ_BYTES"); return false }
        bytesRead = next
        return true
    }

    /** Returns false (recording the event) when consuming expanded bytes would exceed the cap. */
    fun tryConsumeExpanded(amount: Long): Boolean {
        val next = try { addChecked(expandedBytes, amount) } catch (e: ArithmeticException) { null } ?: run {
            record("MAX_EXPANDED_BYTES_OVERFLOW"); return false
        }
        if (next > maxExpandedBytes) { record("MAX_EXPANDED_BYTES"); return false }
        expandedBytes = next
        return true
    }

    fun tryEnterArtifact(): Boolean = selected(artifacts, "MAX_ARTIFACT_COUNT")
    fun leaveArtifact() { artifacts.release() }
    fun tryAddEntry(): Boolean = selected(archiveEntries, "MAX_ARCHIVE_ENTRIES")

    fun tryEnterRecursion(): Boolean = selected(recursionDepth, "MAX_RECURSION_DEPTH")
    fun leaveRecursion() { recursionDepth.release() }

    fun tryAddFinding(): Boolean = selected(findings, "MAX_FINDINGS")
    fun tryAddIndicator(): Boolean = selected(indicators, "MAX_INDICATORS")
    fun tryAddFact(): Boolean = selected(facts, "MAX_FACTS")
    fun tryAddOp(): Boolean = selected(ops, "MAX_OPS")

    private fun selected(holder: CountHolder, event: String): Boolean {
        if (!holder.tryAcquire()) { record(event); return false }
        return true
    }

    private fun record(event: String) {
        if (events.size < 64) events.addLast(event)
    }
}

private class CountHolder(private val max: Int) {
    private val current = AtomicInteger(0)
    fun tryAcquire(): Boolean {
        while (true) {
            val c = current.get()
            if (c >= max) return false
            if (current.compareAndSet(c, c + 1)) return true
        }
    }
    fun release() {
        val c = current.decrementAndGet()
        check(c >= 0) { "budget counter underflow" }
    }
}
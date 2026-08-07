package com.pineandpackets.pocketlab.engine.orchestrator

import com.pineandpackets.pocketlab.engine.api.CaseBudget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaseBudgetTest {

    private fun budget(
        maxBytesRead: Long = 100,
        maxExpandedBytes: Long = 1000,
        maxArtifactCount: Int = 10,
        maxArchiveEntries: Int = 10,
        maxRecursionDepth: Int = 4,
        maxFindings: Int = 10,
        maxIndicators: Int = 10,
        maxFacts: Int = 10,
        maxOps: Int = 100,
    ) = CaseBudget(
        maxBytesRead = maxBytesRead,
        maxExpandedBytes = maxExpandedBytes,
        maxArtifactCount = maxArtifactCount,
        maxArchiveEntries = maxArchiveEntries,
        maxRecursionDepth = maxRecursionDepth,
        maxFindings = maxFindings,
        maxIndicators = maxIndicators,
        maxFacts = maxFacts,
        maxOps = maxOps,
    )

    @Test
    fun `rejects byte reads beyond cap and records quota event`() {
        val b = budget()
        assertTrue(b.tryConsumeBytes(60))
        assertTrue(b.tryConsumeBytes(40))
        assertFalse(b.tryConsumeBytes(1))
        assertTrue(b.quotaEvents.contains("MAX_READ_BYTES"))
    }

    @Test
    fun `overflow in summation is a rejection not wraparound`() {
        val b = budget(maxBytesRead = Long.MAX_VALUE)
        assertTrue(b.tryConsumeBytes(Long.MAX_VALUE - 1))
        assertFalse(b.tryConsumeBytes(Long.MAX_VALUE))
        assertTrue(b.quotaEvents.contains("MAX_READ_BYTES_OVERFLOW"))
    }

    @Test
    fun `shared budget is consumed across nested containers without reset`() {
        val b = budget()
        assertTrue(b.tryConsumeBytes(70))
        assertTrue(b.tryConsumeBytes(30))
        assertFalse(b.tryConsumeBytes(1))
    }

    @Test
    fun `artifact and recursion counters are acquired and released`() {
        val b = budget(maxArtifactCount = 2, maxRecursionDepth = 1)
        assertTrue(b.tryEnterArtifact())
        assertTrue(b.tryEnterArtifact())
        assertFalse(b.tryEnterArtifact())
        b.leaveArtifact()
        b.leaveArtifact()
        assertTrue(b.tryEnterArtifact())

        assertTrue(b.tryEnterRecursion())
        assertFalse(b.tryEnterRecursion())
        b.leaveRecursion()
        assertTrue(b.tryEnterRecursion())
    }

    @Test
    fun `counter underflow throws as programmer error`() {
        val b = budget()
        assertTrue(b.tryEnterRecursion())
        b.leaveRecursion()
        try {
            b.leaveRecursion()
            throw AssertionError("expected underflow to throw")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("underflow"))
        }
    }
}

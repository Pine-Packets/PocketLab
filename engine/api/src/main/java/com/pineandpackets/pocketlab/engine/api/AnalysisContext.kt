package com.pineandpackets.pocketlab.engine.api

import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Cooperative cancellation + wall-clock deadline shared by the main and
 * isolated analysis paths. [cancel] may be called from any thread (e.g. the
 * Binder binder thread); analyzers observe it via [checkCancelled], which
 * throws [CancellationException] when set.
 */
class AnalysisCancellation {
    private val cancelled = AtomicBoolean(false)
    private val reason = AtomicReference<String?>(null)

    val isCancelled: Boolean get() = cancelled.get()

    fun cancel(reason: String = "user_cancelled") {
        cancelled.set(true)
        this.reason.set(reason)
    }

    fun checkCancelled() {
        if (cancelled.get()) throw CancellationException(reason.get() ?: "analysis_cancelled")
    }
}

/**
 * Runtime context handed to each analyzer. Carries the shared case-level
 * [CaseBudget], cooperative [AnalysisCancellation], a wall-clock deadline, and
 * budget accounting for parser operations.
 */
class AnalysisContext(
    val budget: CaseBudget,
    val cancellation: AnalysisCancellation,
    val deadlineEpochMs: Long?,
) {
    val maxOps: Int = budget.maxOps

    /**
     * Throws [CancellationException] if cancelled or past the wall-clock
     * deadline. Analyzers must call this in every parsing loop iteration.
     */
    fun checkCancelled() {
        cancellation.checkCancelled()
        val deadline = deadlineEpochMs
        if (deadline != null && System.currentTimeMillis() > deadline) {
            throw CancellationException("analysis_timeout")
        }
    }

    /** Records one engine operation against the budget; false when quota hit. */
    fun tryOp(): Boolean = budget.tryAddOp()
}

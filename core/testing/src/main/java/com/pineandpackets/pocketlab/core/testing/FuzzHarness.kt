package com.pineandpackets.pocketlab.core.testing

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Deterministic, seed-fixed multi-strategy fuzzing harness used to exercise the
 * parser boundary with hostile input.
 *
 * Every parser in the engine treats imported bytes as hostile. This harness
 * generates a large corpus of byte arrays under a fixed seed (so failures are
 * reproducible), then runs a caller-provided probe against each input and
 * reports inputs that cause:
 *
 *  1. A fatal `Error` (e.g. [OutOfMemoryError], [StackOverflowError],
 *     [NegativeArraySizeError], [AssertionError]) escaping the parser, which must
 *     never happen regardless of input shape;
 *  2. Non-termination (a probe that does not return within [terminationBudgetMs]);
 *  3. Non-determinism for a pure probe (RNG-free classification should be stable
 *     across repeated runs).
 *
 * The harness never attempts to detect logical misparses - that is the job of the
 * per-parser corpus tests. It is concerned only with crash-safety, resource
 * bounds, and termination, matching the "safe parser failure" definition.
 */
object FuzzHarness {

    class FuzzFailure(
        val kind: String,
        val seedIndex: Int,
        val inputSize: Int,
        val sampleHex: String,
        val message: String
    ) {
        override fun toString(): String =
            "[$kind] index=$seedIndex size=$inputSize sample=\"$sampleHex\" $message"
    }

    private fun sampleHex(bytes: ByteArray, max: Int = 24): String =
        bytes.take(minOf(max, bytes.size)).joinToString("") { "%02x".format(it) }

    private fun Throwable.isFatalError(): Boolean =
        when (this) {
            is OutOfMemoryError,
            is StackOverflowError,
            is NegativeArraySizeException,
            is AssertionError,
            is ExceptionInInitializerError -> true
            else -> false
        }

    /**
     * Deterministic corpus generator. Produces [perSize] fresh buffers per input
     * size in [sizes], for every magic prefix in [prefixes] (plus a `null` prefix
     * meaning "raw random, no magic"). Contents are derived from [seed] plus the
     * input index, so a fixed [seed] always yields the identical corpus.
     */
    fun corpus(
        prefixes: List<ByteArray?>,
        sizes: IntArray,
        perSize: Int,
        seed: Long
    ): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var counter = 0L
        for (prefix in prefixes) {
            for (size in sizes) {
                for (i in 0 until perSize) {
                    val body = ByteArray(size)
                    val rng = java.util.Random(seed + counter * 31 + 17)
                    rng.nextBytes(body)
                    out.add((prefix ?: ByteArray(0)) + body)
                    counter++
                }
            }
        }
        return out
    }

    /**
     * Runs [probe] against every element of [corpus] and returns the list of
     * failures. The probe must return normally (or throw a domain exception such
     * as a wrapped [com.pineandpackets.pocketlab.core.common.AnalysisError] which
     * callers catch internally) for both valid and malformed input. The harness
     * asserts only that no fatal, non-terminating, or non-deterministic probe
     * exists.
     */
    fun fuzz(
        corpus: List<ByteArray>,
        terminationBudgetMs: Long = 5_000,
        determinismRuns: Boolean = true,
        probe: (ByteArray) -> Any?
    ): List<FuzzFailure> {
        val failures = mutableListOf<FuzzFailure>()
        val pool: ExecutorService = Executors.newSingleThreadExecutor()
        try {
            for (index in corpus.indices) {
                val input = corpus[index]
                val future: Future<Any?> = pool.submit(Callable { probe(input) })
                val firstResult: Any?
                try {
                    firstResult = future.get(terminationBudgetMs, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    failures.add(
                        FuzzFailure("interrupted", index, input.size, sampleHex(input), e.javaClass.simpleName)
                    )
                    break
                } catch (e: ExecutionException) {
                    val cause = e.cause
                    if (cause != null && cause.isFatalError()) {
                        failures.add(
                            FuzzFailure(
                                "fatal:${cause.javaClass.simpleName}",
                                index, input.size, sampleHex(input), cause.message ?: "no message"
                            )
                        )
                    }
                    // Non-fatal exception is expected; ignore.
                    continue
                } catch (e: TimeoutException) {
                    future.cancel(true)
                    failures.add(
                        FuzzFailure(
                            "timeout", index, input.size, sampleHex(input),
                            "still running after ${terminationBudgetMs}ms"
                        )
                    )
                    continue
                }

                if (determinismRuns && input.isNotEmpty()) {
                    try {
                        val future2 = pool.submit(Callable { probe(input) })
                        val second = future2.get(terminationBudgetMs, TimeUnit.MILLISECONDS)
                        if (firstResult != null && second != null && firstResult != second) {
                            failures.add(
                                FuzzFailure(
                                    "non-deterministic",
                                    index, input.size, sampleHex(input),
                                    "two runs yielded different results"
                                )
                            )
                        }
                    } catch (_: Exception) {
                        // First run already governed classification.
                    }
                }
            }
        } finally {
            try {
                pool.shutdownNow()
            } catch (_: Exception) {
            }
        }
        return failures
    }
}
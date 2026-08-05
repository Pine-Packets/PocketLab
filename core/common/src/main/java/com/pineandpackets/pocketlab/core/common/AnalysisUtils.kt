package com.pineandpackets.pocketlab.core.common

import kotlinx.coroutines.CancellationException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun requireNonNull(value: Any?, lazyMessage: () -> Any) {
    contract {
        returns() implies (value != null)
    }
    if (value == null) {
        throw IllegalArgumentException(lazyMessage().toString())
    }
}

fun checkOverflow(result: Long, message: String = "Integer overflow detected") {
    if (result < 0) {
        throw ArithmeticException(message)
    }
}

fun addChecked(a: Long, b: Long, message: String = "Addition overflow"): Long {
    val result = a + b
    checkOverflow(result, message)
    return result
}

fun multiplyChecked(a: Long, b: Long, message: String = "Multiplication overflow"): Long {
    val result = Math.multiplyExact(a, b)
    return result
}

sealed class AnalysisError(override val message: String, override val cause: Throwable? = null) : Exception(message, cause) {
    class IntakeError(message: String, cause: Throwable? = null) : AnalysisError(message, cause)
    class ArchiveError(message: String, cause: Throwable? = null) : AnalysisError(message, cause)
    class ParserError(message: String, cause: Throwable? = null) : AnalysisError(message, cause)
    class QuotaExceededError(message: String) : AnalysisError(message)
    class CancellationError(message: String = "Analysis cancelled") : AnalysisError(message)
    class SecurityError(message: String, cause: Throwable? = null) : AnalysisError(message, cause)
}

inline fun <T> safeAnalysis(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: AnalysisError) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(AnalysisError.ParserError("Unexpected error during analysis", e))
    }
}

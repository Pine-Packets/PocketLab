package com.pineandpackets.pocketlab.engine.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class ProcessCrashDetector {

    sealed class ProcessState {
        data object Healthy : ProcessState()
        data class Crashed(val jobId: String?, val reason: String, val timestamp: Long = System.currentTimeMillis()) : ProcessState()
        data class Recovering(val attempt: Int, val timestamp: Long = System.currentTimeMillis()) : ProcessState()
        data object Unavailable : ProcessState()
    }

    private val _state = MutableStateFlow<ProcessState>(ProcessState.Healthy)
    val state: StateFlow<ProcessState> = _state.asStateFlow()

    private val crashHistory = mutableListOf<ProcessState.Crashed>()
    private var recoveryAttempts = 0
    private val maxRecoveryAttempts = 3

    fun reportCrash(jobId: String?, reason: String) {
        val crash = ProcessState.Crashed(jobId, reason)
        synchronized(crashHistory) {
            crashHistory.add(crash)
        }
        recoveryAttempts++

        if (recoveryAttempts > maxRecoveryAttempts) {
            Timber.e("Max recovery attempts ($maxRecoveryAttempts) exceeded")
            _state.value = ProcessState.Unavailable
        } else {
            Timber.w("Isolated process crash detected (attempt $recoveryAttempts/$maxRecoveryAttempts): $reason")
            _state.value = ProcessState.Recovering(recoveryAttempts)
        }
    }

    fun reportRecovered() {
        recoveryAttempts = 0
        _state.value = ProcessState.Healthy
        Timber.i("Isolated process recovered successfully")
    }

    fun reportUnavailable() {
        _state.value = ProcessState.Unavailable
    }

    fun canAttemptRecovery(): Boolean = recoveryAttempts <= maxRecoveryAttempts

    fun getCrashHistory(): List<ProcessState.Crashed> = synchronized(crashHistory) { crashHistory.toList() }

    fun clearHistory() {
        synchronized(crashHistory) {
            crashHistory.clear()
        }
        recoveryAttempts = 0
        _state.value = ProcessState.Healthy
    }
}

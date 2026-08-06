package com.pineandpackets.pocketlab.engine.service

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProcessCrashDetectorTest {

    private lateinit var detector: ProcessCrashDetector

    @Before
    fun setUp() {
        detector = ProcessCrashDetector()
    }

    @Test
    fun `initial state is healthy`() {
        assertEquals(ProcessCrashDetector.ProcessState.Healthy, detector.state.value)
    }

    @Test
    fun `report crash transitions to recovering`() {
        detector.reportCrash("job-123", "SIGSEGV in parser")

        val state = detector.state.value
        assertTrue(state is ProcessCrashDetector.ProcessState.Recovering)
        assertEquals(1, (state as ProcessCrashDetector.ProcessState.Recovering).attempt)
    }

    @Test
    fun `multiple crashes increment recovery attempts`() {
        detector.reportCrash("job-1", "crash 1")
        detector.reportCrash("job-2", "crash 2")
        detector.reportCrash("job-3", "crash 3")

        val state = detector.state.value
        assertTrue(state is ProcessCrashDetector.ProcessState.Recovering)
        assertEquals(3, (state as ProcessCrashDetector.ProcessState.Recovering).attempt)
    }

    @Test
    fun `exceeding max recovery attempts transitions to unavailable`() {
        detector.reportCrash("job-1", "crash 1")
        detector.reportCrash("job-2", "crash 2")
        detector.reportCrash("job-3", "crash 3")
        detector.reportCrash("job-4", "crash 4")

        assertEquals(ProcessCrashDetector.ProcessState.Unavailable, detector.state.value)
    }

    @Test
    fun `cannot attempt recovery after max exceeded`() {
        detector.reportCrash("job-1", "crash 1")
        detector.reportCrash("job-2", "crash 2")
        detector.reportCrash("job-3", "crash 3")
        detector.reportCrash("job-4", "crash 4")

        assertFalse(detector.canAttemptRecovery())
    }

    @Test
    fun `report recovered resets state to healthy`() {
        detector.reportCrash("job-1", "crash")
        assertTrue(detector.state.value is ProcessCrashDetector.ProcessState.Recovering)

        detector.reportRecovered()
        assertEquals(ProcessCrashDetector.ProcessState.Healthy, detector.state.value)
        assertTrue(detector.canAttemptRecovery())
    }

    @Test
    fun `crash history is recorded`() {
        detector.reportCrash("job-1", "crash 1")
        detector.reportCrash("job-2", "crash 2")

        val history = detector.getCrashHistory()
        assertEquals(2, history.size)
        assertTrue(history.any { it.jobId == "job-1" })
        assertTrue(history.any { it.jobId == "job-2" })
    }

    @Test
    fun `clear history resets everything`() {
        detector.reportCrash("job-1", "crash")
        detector.reportCrash("job-2", "crash")

        detector.clearHistory()

        assertEquals(ProcessCrashDetector.ProcessState.Healthy, detector.state.value)
        assertEquals(0, detector.getCrashHistory().size)
        assertTrue(detector.canAttemptRecovery())
    }

    @Test
    fun `report unavailable sets unavailable state`() {
        detector.reportUnavailable()
        assertEquals(ProcessCrashDetector.ProcessState.Unavailable, detector.state.value)
    }
}

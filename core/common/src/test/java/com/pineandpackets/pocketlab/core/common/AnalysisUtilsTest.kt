package com.pineandpackets.pocketlab.core.common

import org.junit.Assert.*
import org.junit.Test

class AnalysisUtilsTest {
    
    @Test
    fun `checkOverflow throws on negative result`() {
        try {
            checkOverflow(-1, "Test overflow")
            fail("Expected ArithmeticException")
        } catch (e: ArithmeticException) {
            assertEquals("Test overflow", e.message)
        }
    }
    
    @Test
    fun `checkOverflow passes on positive result`() {
        checkOverflow(100, "Should not throw")
    }
    
    @Test
    fun `addChecked detects overflow`() {
        try {
            addChecked(Long.MAX_VALUE, 1)
            fail("Expected ArithmeticException")
        } catch (e: ArithmeticException) {
            // Expected
        }
    }
    
    @Test
    fun `addChecked returns correct sum`() {
        assertEquals(15, addChecked(10, 5))
    }
    
    @Test
    fun `multiplyChecked detects overflow`() {
        try {
            multiplyChecked(Long.MAX_VALUE, 2)
            fail("Expected ArithmeticException")
        } catch (e: ArithmeticException) {
            // Expected
        }
    }
    
    @Test
    fun `multiplyChecked returns correct product`() {
        assertEquals(50, multiplyChecked(10, 5))
    }
    
    @Test
    fun `safeAnalysis returns success on normal execution`() {
        val result = safeAnalysis { 42 }
        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }
    
    @Test
    fun `safeAnalysis returns failure on AnalysisError`() {
        val result = safeAnalysis<Int> {
            throw AnalysisError.ParserError("Test error")
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AnalysisError.ParserError)
    }
}

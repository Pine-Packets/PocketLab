package com.pineandpackets.pocketlab.core.common

import org.junit.Assert.*
import org.junit.Test

class HashUtilsTest {
    
    @Test
    fun `sha256 produces correct hash for empty input`() {
        val hash = HashUtils.sha256(ByteArray(0))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash)
    }
    
    @Test
    fun `sha256 produces correct hash for test input`() {
        val hash = HashUtils.sha256("test".toByteArray())
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", hash)
    }
    
    @Test
    fun `sha1 produces correct hash for empty input`() {
        val hash = HashUtils.sha1(ByteArray(0))
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", hash)
    }
    
    @Test
    fun `md5 produces correct hash for empty input`() {
        val hash = HashUtils.md5(ByteArray(0))
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", hash)
    }
}

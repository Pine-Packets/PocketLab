package com.pineandpackets.pocketlab.core.common

import java.security.MessageDigest

object HashUtils {
    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.toHex()
    }
    
    fun sha1(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val hashBytes = digest.digest(bytes)
        return hashBytes.toHex()
    }
    
    fun md5(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5")
        val hashBytes = digest.digest(bytes)
        return hashBytes.toHex()
    }
    
    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }
}

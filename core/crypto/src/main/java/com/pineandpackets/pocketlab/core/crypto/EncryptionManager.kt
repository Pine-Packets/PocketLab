package com.pineandpackets.pocketlab.core.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptionManager(context: Context) {
    
    private val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }
    
    private val secretKey: SecretKey by lazy {
        getOrCreateKey()
    }
    
    private fun getOrCreateKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) {
            return existingKey.secretKey
        }
        
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
        
        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }
    
    fun encrypt(plaintext: ByteArray): EncryptedData {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        
        return EncryptedData(
            ciphertext = ciphertext,
            iv = iv,
            version = ENCRYPTION_VERSION
        )
    }
    
    fun decrypt(encryptedData: EncryptedData): ByteArray {
        if (encryptedData.version != ENCRYPTION_VERSION) {
            throw IllegalArgumentException("Unsupported encryption version: ${encryptedData.version}")
        }
        
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, encryptedData.iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        return cipher.doFinal(encryptedData.ciphertext)
    }
    
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "pocketlab_encryption_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ENCRYPTION_VERSION = 1
    }
}

data class EncryptedData(
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val version: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedData) return false
        
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (version != other.version) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + version
        return result
    }
}

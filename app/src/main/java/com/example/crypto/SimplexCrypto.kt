package com.example.crypto

import com.example.rust.MarcianoRustCore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * SimplexCrypto delegates all symmetric AES-256-GCM encryption/decryption and PBKDF2 derivation
 * to the native Rust core (`marcianox_core`), using JVM Cipher only if native .so is not loaded in host lint/preview.
 */
object SimplexCrypto {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_SIZE = 12
    private const val DEFAULT_KDF_SALT_B64 = "TWFyY2lhbm9YLUtERi1TYWx0LXYx" // "MarcianoX-KDF-Salt-v1"

    fun deriveKey(pin: String, saltB64: String = DEFAULT_KDF_SALT_B64): SecretKeySpec {
        if (MarcianoRustCore.isLoaded) {
            val derived = MarcianoRustCore.deriveKeyPbkdf2(pin, saltB64, 100_000)
            return try {
                SecretKeySpec(derived, "AES")
            } finally {
                MemoryZeroizationManager.zero(derived, MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY)
            }
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val pinBytes = pin.toByteArray(Charsets.UTF_8)
        val hash = digest.digest(pinBytes)
        return try {
            SecretKeySpec(hash, "AES")
        } finally {
            MemoryZeroizationManager.zeroAll(
                pinBytes,
                hash,
                level = MemoryZeroizationManager.SanitizationLevel.STANDARD
            )
        }
    }

    fun generateSessionKey(): ByteArray {
        val random = SecureRandom()
        val keyBytes = ByteArray(32)
        random.nextBytes(keyBytes)
        return MemoryZeroizationManager.register(keyBytes)
    }

    fun encrypt(plaintext: String, key: ByteArray): EncryptedData {
        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        if (MarcianoRustCore.isLoaded && key.size == 32) {
            return try {
                val res = MarcianoRustCore.encryptAesGcm(plaintextBytes, key)
                EncryptedData(ciphertext = res.ciphertextB64, iv = res.ivB64)
            } finally {
                MemoryZeroizationManager.zero(
                    plaintextBytes,
                    level = MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY
                )
            }
        }
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        var ciphertext: ByteArray? = null
        return try {
            val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)
            ciphertext = cipher.doFinal(plaintextBytes)
            EncryptedData(
                ciphertext = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP),
                iv = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP)
            )
        } finally {
            MemoryZeroizationManager.zeroAll(
                plaintextBytes,
                iv,
                ciphertext,
                level = MemoryZeroizationManager.SanitizationLevel.STANDARD
            )
        }
    }

    fun decrypt(ciphertextBase64: String, ivBase64: String, key: ByteArray): String {
        if (MarcianoRustCore.isLoaded && key.size == 32) {
            val pt = MarcianoRustCore.decryptAesGcm(ciphertextBase64, ivBase64, key)
                ?: return "[Decryption Error: Session key rotated or purged]"
            return try {
                String(pt, Charsets.UTF_8)
            } finally {
                MemoryZeroizationManager.zero(
                    pt,
                    level = MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY
                )
            }
        }
        var ciphertext: ByteArray? = null
        var iv: ByteArray? = null
        var decryptedBytes: ByteArray? = null
        return try {
            ciphertext = android.util.Base64.decode(ciphertextBase64, android.util.Base64.NO_WRAP)
            iv = android.util.Base64.decode(ivBase64, android.util.Base64.NO_WRAP)
            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
            decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            "[Decryption Error: Session key rotated or purged]"
        } finally {
            MemoryZeroizationManager.zeroAll(
                ciphertext,
                iv,
                decryptedBytes,
                level = MemoryZeroizationManager.SanitizationLevel.STANDARD
            )
        }
    }
}

data class EncryptedData(val ciphertext: String, val iv: String)

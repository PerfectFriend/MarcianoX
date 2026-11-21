package com.example.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * SecurityManager handles AES-GCM (256-bit) encryption and decryption operations
 * leveraging the hardware-backed Android Keystore system for secure cryptographic
 * key generation, storage, and lifecycle management.
 */
open class SecurityManager(
    val defaultKeyAlias: String = DEFAULT_KEY_ALIAS
) {

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH = 128
        const val GCM_IV_LENGTH = 12
        const val KEY_SIZE_BITS = 256
        const val DEFAULT_KEY_ALIAS = "simplex_master_keystore_key"

        /**
         * Singleton instance configured with the default master alias.
         */
        val Default: SecurityManager by lazy { SecurityManager(DEFAULT_KEY_ALIAS) }

        // Convenience delegates to Default singleton instance
        fun getOrCreateSecretKey(alias: String = DEFAULT_KEY_ALIAS): SecretKey =
            Default.getOrCreateSecretKey(alias)

        fun hasKey(alias: String = DEFAULT_KEY_ALIAS): Boolean =
            Default.hasKey(alias)

        fun deleteKey(alias: String = DEFAULT_KEY_ALIAS): Boolean =
            Default.deleteKey(alias)

        fun deleteAllKeys(): Int =
            Default.deleteAllKeys()

        fun encrypt(plaintext: ByteArray, alias: String = DEFAULT_KEY_ALIAS): EncryptedPayload =
            Default.encrypt(plaintext, alias)

        fun encrypt(plaintext: String, alias: String = DEFAULT_KEY_ALIAS): EncryptedPayload =
            Default.encrypt(plaintext, alias)

        fun encryptToString(plaintext: String, alias: String = DEFAULT_KEY_ALIAS): String =
            Default.encryptToString(plaintext, alias)

        fun decrypt(ciphertext: ByteArray, iv: ByteArray, alias: String = DEFAULT_KEY_ALIAS): ByteArray =
            Default.decrypt(ciphertext, iv, alias)

        fun decrypt(payload: EncryptedPayload, alias: String = DEFAULT_KEY_ALIAS): String =
            Default.decrypt(payload, alias)

        fun decryptFromString(combinedBase64: String, alias: String = DEFAULT_KEY_ALIAS): String =
            Default.decryptFromString(combinedBase64, alias)
    }

    private val lock = Any()
    private val secureRandom = SecureRandom()
    private val memoryKeyStore = mutableMapOf<String, SecretKey>()

    val isAndroidKeyStore: Boolean by lazy {
        try {
            java.security.Security.getProvider(ANDROID_KEYSTORE) != null
        } catch (e: Exception) {
            false
        }
    }

    private val keyStore: KeyStore? by lazy {
        if (isAndroidKeyStore) {
            try {
                KeyStore.getInstance(ANDROID_KEYSTORE).apply {
                    load(null)
                }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    /**
     * Retrieves an existing AES-256 SecretKey from Android Keystore,
     * or generates and securely stores a new one if it does not already exist.
     */
    fun getOrCreateSecretKey(alias: String = defaultKeyAlias): SecretKey {
        synchronized(lock) {
            val ks = keyStore
            if (ks != null) {
                if (ks.containsAlias(alias)) {
                    val entry = ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry
                    if (entry != null) {
                        return entry.secretKey
                    }
                    val directKey = ks.getKey(alias, null) as? SecretKey
                    if (directKey != null) {
                        return directKey
                    }
                }

                // Generate a hardware-backed AES-256 GCM key in the Android Keystore
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )

                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .setRandomizedEncryptionRequired(true)
                    .build()

                keyGenerator.init(keyGenParameterSpec)
                return keyGenerator.generateKey()
            } else {
                // Fallback for host test environments: standard AES-256 KeyGenerator with memory store
                return memoryKeyStore.getOrPut(alias) {
                    val keyGenerator = KeyGenerator.getInstance("AES")
                    keyGenerator.init(KEY_SIZE_BITS, secureRandom)
                    keyGenerator.generateKey()
                }
            }
        }
    }

    /**
     * Checks if a cryptographic key exists in the Android Keystore under the given alias.
     */
    fun hasKey(alias: String = defaultKeyAlias): Boolean {
        return synchronized(lock) {
            val ks = keyStore
            if (ks != null) ks.containsAlias(alias) else memoryKeyStore.containsKey(alias)
        }
    }

    /**
     * Deletes a cryptographic key entry from the Android Keystore.
     * Returns true if the key existed and was deleted, false otherwise.
     */
    fun deleteKey(alias: String = defaultKeyAlias): Boolean {
        return synchronized(lock) {
            val ks = keyStore
            if (ks != null) {
                if (ks.containsAlias(alias)) {
                    ks.deleteEntry(alias)
                    true
                } else {
                    false
                }
            } else {
                memoryKeyStore.remove(alias) != null
            }
        }
    }

    /**
     * Returns a list of all cryptographic aliases currently stored in the Android Keystore.
     */
    fun listKeyAliases(): List<String> {
        return synchronized(lock) {
            val ks = keyStore
            if (ks != null) ks.aliases().toList() else memoryKeyStore.keys.toList()
        }
    }

    /**
     * Purges all keys from the Android Keystore managed by this app.
     * Useful for panic wipes and factory resets.
     * Returns the number of keys deleted.
     */
    fun deleteAllKeys(): Int {
        return synchronized(lock) {
            val ks = keyStore
            if (ks != null) {
                val aliases = ks.aliases().toList()
                var count = 0
                for (alias in aliases) {
                    try {
                        ks.deleteEntry(alias)
                        count++
                    } catch (e: Exception) {
                        // Continue purging remaining keys
                    }
                }
                count
            } else {
                val count = memoryKeyStore.size
                memoryKeyStore.clear()
                count
            }
        }
    }

    /**
     * Encrypts a raw ByteArray using AES-GCM (256-bit) with a key from the Android Keystore.
     * Produces an EncryptedPayload containing ciphertext and the unique 12-byte IV.
     */
    fun encrypt(plaintext: ByteArray, alias: String = defaultKeyAlias): EncryptedPayload {
        val secretKey = getOrCreateSecretKey(alias)
        val cipher = Cipher.getInstance(TRANSFORMATION)

        val iv: ByteArray = try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            cipher.iv ?: run {
                val explicitIv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, explicitIv))
                explicitIv
            }
        } catch (e: Exception) {
            val explicitIv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, explicitIv))
            explicitIv
        }

        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedPayload(ciphertext = ciphertext, iv = iv)
    }

    /**
     * Encrypts a plaintext String using UTF-8 encoding.
     */
    fun encrypt(plaintext: String, alias: String = defaultKeyAlias): EncryptedPayload {
        return encrypt(plaintext.toByteArray(Charsets.UTF_8), alias)
    }

    /**
     * Encrypts plaintext String and returns a single Base64-encoded string combining IV and Ciphertext.
     */
    fun encryptToString(plaintext: String, alias: String = defaultKeyAlias): String {
        return encrypt(plaintext, alias).toCombinedBase64()
    }

    /**
     * Encrypts raw bytes and returns combined byte array: [12-byte IV] + [Ciphertext + Tag].
     */
    fun encryptToBytes(plaintext: ByteArray, alias: String = defaultKeyAlias): ByteArray {
        return encrypt(plaintext, alias).toCombinedBytes()
    }

    /**
     * Decrypts AES-GCM ciphertext using the specified IV and Keystore key.
     */
    fun decrypt(ciphertext: ByteArray, iv: ByteArray, alias: String = defaultKeyAlias): ByteArray {
        require(iv.isNotEmpty()) { "IV must not be empty" }
        require(iv.size == GCM_IV_LENGTH) { "Invalid GCM IV length: expected $GCM_IV_LENGTH bytes, got ${iv.size}" }

        val secretKey = getOrCreateSecretKey(alias)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Decrypts an EncryptedPayload and returns the plaintext as a UTF-8 String.
     */
    fun decrypt(payload: EncryptedPayload, alias: String = defaultKeyAlias): String {
        val decryptedBytes = decrypt(payload.ciphertext, payload.iv, alias)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Decrypts Base64-encoded ciphertext and IV into a UTF-8 String.
     */
    fun decrypt(ciphertextBase64: String, ivBase64: String, alias: String = defaultKeyAlias): String {
        val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val decryptedBytes = decrypt(ciphertext, iv, alias)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Decrypts a combined Base64 string containing [12-byte IV] + [Ciphertext + Tag].
     */
    fun decryptFromString(combinedBase64: String, alias: String = defaultKeyAlias): String {
        val payload = EncryptedPayload.fromCombinedBase64(combinedBase64)
        return decrypt(payload, alias)
    }

    /**
     * Decrypts combined bytes containing [12-byte IV] + [Ciphertext + Tag].
     */
    fun decryptFromBytes(combinedBytes: ByteArray, alias: String = defaultKeyAlias): ByteArray {
        val payload = EncryptedPayload.fromCombinedBytes(combinedBytes)
        return decrypt(payload.ciphertext, payload.iv, alias)
    }
}

/**
 * Encapsulates encrypted data payload containing the ciphertext (with GCM auth tag)
 * and the 12-byte initialization vector (IV).
 */
data class EncryptedPayload(
    val ciphertext: ByteArray,
    val iv: ByteArray
) {
    /**
     * Formats the payload as a unified byte array: [12-byte IV] + [Ciphertext].
     */
    fun toCombinedBytes(): ByteArray {
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return combined
    }

    /**
     * Returns a single Base64 encoded string combining IV and Ciphertext.
     */
    fun toCombinedBase64(): String {
        return Base64.encodeToString(toCombinedBytes(), Base64.NO_WRAP)
    }

    fun getCiphertextBase64(): String = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    fun getIvBase64(): String = Base64.encodeToString(iv, Base64.NO_WRAP)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedPayload
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!iv.contentEquals(other.iv)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }

    companion object {
        fun fromCombinedBytes(combined: ByteArray): EncryptedPayload {
            require(combined.size > SecurityManager.GCM_IV_LENGTH) {
                "Invalid payload size: expected at least ${SecurityManager.GCM_IV_LENGTH + 1} bytes, got ${combined.size}"
            }
            val iv = ByteArray(SecurityManager.GCM_IV_LENGTH)
            val ciphertext = ByteArray(combined.size - SecurityManager.GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, SecurityManager.GCM_IV_LENGTH)
            System.arraycopy(combined, SecurityManager.GCM_IV_LENGTH, ciphertext, 0, ciphertext.size)
            return EncryptedPayload(ciphertext = ciphertext, iv = iv)
        }

        fun fromCombinedBase64(combinedBase64: String): EncryptedPayload {
            val combinedBytes = Base64.decode(combinedBase64, Base64.NO_WRAP)
            return fromCombinedBytes(combinedBytes)
        }
    }
}

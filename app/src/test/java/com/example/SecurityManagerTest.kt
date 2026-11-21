package com.example

import com.example.crypto.EncryptedPayload
import com.example.crypto.SecurityManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityManagerTest {

    private lateinit var securityManager: SecurityManager

    @Before
    fun setUp() {
        securityManager = SecurityManager("test_sec_manager_key")
        securityManager.deleteAllKeys()
    }

    @Test
    fun testKeyGenerationAndLifecycle() {
        val testAlias = "unit_test_key_alias"
        assertFalse("Key should not exist initially", securityManager.hasKey(testAlias))

        val key = securityManager.getOrCreateSecretKey(testAlias)
        assertNotNull("SecretKey should be generated", key)
        assertTrue("Key should exist after generation", securityManager.hasKey(testAlias))

        val sameKey = securityManager.getOrCreateSecretKey(testAlias)
        assertNotNull("Subsequent call should return existing key", sameKey)

        val deleted = securityManager.deleteKey(testAlias)
        assertTrue("deleteKey should return true for existing key", deleted)
        assertFalse("Key should not exist after deletion", securityManager.hasKey(testAlias))
    }

    @Test
    fun testStringEncryptionAndDecryption() {
        val message = "TOP_SECRET_COVERT_COMMUNICATION_2026"
        val payload = securityManager.encrypt(message)

        assertNotNull(payload)
        assertEquals(SecurityManager.GCM_IV_LENGTH, payload.iv.size)
        assertTrue(payload.ciphertext.isNotEmpty())

        val decrypted = securityManager.decrypt(payload)
        assertEquals(message, decrypted)
    }

    @Test
    fun testCombinedStringEncryptionAndDecryption() {
        val message = "Quantum-Resistant Tactical Handshake Packet"
        val combinedBase64 = securityManager.encryptToString(message)

        assertNotNull(combinedBase64)
        assertTrue(combinedBase64.isNotEmpty())

        val decrypted = securityManager.decryptFromString(combinedBase64)
        assertEquals(message, decrypted)
    }

    @Test
    fun testByteArrayEncryptionAndDecryption() {
        val rawData = byteArrayOf(0x01, 0x02, 0x03, 0xFA.toByte(), 0xFF.toByte())
        val payload = securityManager.encrypt(rawData)

        val decryptedBytes = securityManager.decrypt(payload.ciphertext, payload.iv)
        assertArrayEquals(rawData, decryptedBytes)
    }

    @Test
    fun testAuthenticationTagFailureOnTamperedCiphertext() {
        val message = "Tamper Proof Channel"
        val payload = securityManager.encrypt(message)

        // Corrupt the ciphertext
        val tamperedCiphertext = payload.ciphertext.clone()
        tamperedCiphertext[tamperedCiphertext.size - 1] = (tamperedCiphertext[tamperedCiphertext.size - 1] + 1).toByte()

        try {
            securityManager.decrypt(tamperedCiphertext, payload.iv)
            fail("Decryption should fail when ciphertext/tag is tampered with")
        } catch (e: Exception) {
            // Expected AEADBadTagException or GeneralSecurityException
            assertTrue(true)
        }
    }

    @Test
    fun testEncryptedPayloadSerialization() {
        val rawCiphertext = "testCiphertext".toByteArray()
        val rawIv = ByteArray(12) { it.toByte() }
        val payload = EncryptedPayload(rawCiphertext, rawIv)

        val combinedBytes = payload.toCombinedBytes()
        assertEquals(rawIv.size + rawCiphertext.size, combinedBytes.size)

        val restoredFromBytes = EncryptedPayload.fromCombinedBytes(combinedBytes)
        assertEquals(payload, restoredFromBytes)

        val combinedBase64 = payload.toCombinedBase64()
        val restoredFromBase64 = EncryptedPayload.fromCombinedBase64(combinedBase64)
        assertEquals(payload, restoredFromBase64)
    }

    @Test
    fun testMultipleKeyAliasesIsolation() {
        val aliasA = "operator_alpha_key"
        val aliasB = "operator_bravo_key"

        val message = "Isolated Message Channel"
        val payloadA = securityManager.encrypt(message, alias = aliasA)

        // Decrypting with wrong key alias should fail due to tag mismatch
        try {
            securityManager.decrypt(payloadA.ciphertext, payloadA.iv, alias = aliasB)
            fail("Decryption with a different key alias should fail AEAD authentication")
        } catch (e: Exception) {
            assertTrue(true)
        }

        // Decrypting with the correct alias should succeed
        val decrypted = securityManager.decrypt(payloadA, alias = aliasA)
        assertEquals(message, decrypted)
    }
}

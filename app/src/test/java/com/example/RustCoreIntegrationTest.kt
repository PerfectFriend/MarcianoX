package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.ProfileVaultManager
import com.example.net.ProxyChainManager
import com.example.net.ProxyConfig
import com.example.net.ProxyType
import com.example.rust.MarcianoRustCore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket
import java.security.SecureRandom

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RustCoreIntegrationTest {

    @Test
    fun `native Rust library loads cleanly on JVM host`() {
        assertTrue("Expected libmarcianox_core.so to load: ${MarcianoRustCore.loadError}", MarcianoRustCore.isLoaded)
        assertTrue(MarcianoRustCore.version().contains("marcianox-core"))
    }

    @Test
    fun `Rust Argon2id hash and verification reject wrong PIN`() {
        val rec = MarcianoRustCore.hashPinArgon2("847291")
        assertTrue(rec.phcHash.startsWith("\$argon2id\$"))
        assertTrue(rec.kdfSaltB64.isNotBlank())
        assertTrue(MarcianoRustCore.verifyPinArgon2("847291", rec.phcHash))
        assertFalse(MarcianoRustCore.verifyPinArgon2("847292", rec.phcHash))
    }

    @Test
    fun `Rust AES-256-GCM and PBKDF2 roundtrip works`() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val payload = "MarcianoX Rust AES-GCM Payload 2026".toByteArray(Charsets.UTF_8)
        val enc = MarcianoRustCore.encryptAesGcm(payload, key)
        val dec = MarcianoRustCore.decryptAesGcm(enc.ciphertextB64, enc.ivB64, key)
        assertNotNull(dec)
        assertArrayEquals(payload, dec)

        val saltB64 = android.util.Base64.encodeToString(ByteArray(16) { 0x42 }, android.util.Base64.NO_WRAP)
        val k1 = MarcianoRustCore.deriveKeyPbkdf2("secret-pass", saltB64, 10_000)
        val k2 = MarcianoRustCore.deriveKeyPbkdf2("secret-pass", saltB64, 10_000)
        assertEquals(32, k1.size)
        assertArrayEquals(k1, k2)
    }

    @Test
    fun `Rust BIP-39 24-word generation validation and seed derivation`() {
        val words = MarcianoRustCore.generateBip39()
        assertEquals(24, words.size)
        assertTrue(MarcianoRustCore.validateBip39(words))

        val corrupted = words.toMutableList().apply { this[0] = "notabipwordxyz" }
        assertFalse(MarcianoRustCore.validateBip39(corrupted))

        val seedHex = MarcianoRustCore.bip39ToSeedHex(words, "mars-vault")
        assertEquals(128, seedHex.length)
    }

    @Test
    fun `Rust X25519 Double Ratchet multi-message bidirectional exchange`() {
        val alice = MarcianoRustCore.generateX25519Keypair()
        val bob = MarcianoRustCore.generateX25519Keypair()

        MarcianoRustCore.ratchetInit(101, alice.privateB64, alice.publicB64, bob.publicB64, isInitiator = true)
        MarcianoRustCore.ratchetInit(102, bob.privateB64, bob.publicB64, alice.publicB64, isInitiator = false)

        val m1 = MarcianoRustCore.ratchetEncrypt(101, "Hello Bob via Rust Double Ratchet".toByteArray())
        val d1 = MarcianoRustCore.ratchetDecrypt(102, m1.envelope)
        assertEquals("Hello Bob via Rust Double Ratchet", String(d1.plaintext, Charsets.UTF_8))

        val m2 = MarcianoRustCore.ratchetEncrypt(102, "Hello Alice reply".toByteArray())
        val d2 = MarcianoRustCore.ratchetDecrypt(101, m2.envelope)
        assertEquals("Hello Alice reply", String(d2.plaintext, Charsets.UTF_8))
    }

    @Test
    fun `Rust Shamir GF256 3-of-5 splits and recovers secret while failing with 2 shares`() {
        val secret = "SUPER_SECRET_VAULT_MNEMONIC_AND_KEYS".toByteArray(Charsets.UTF_8)
        val shares = MarcianoRustCore.shamirSplit(secret, "strong-passphrase", threshold = 3, total = 5)
        assertEquals(5, shares.size)

        val recovered = MarcianoRustCore.shamirRecover(
            listOf(shares[0].shareB64, shares[2].shareB64, shares[4].shareB64),
            "strong-passphrase"
        )
        assertArrayEquals(secret, recovered)

        var failedWithTwo = false
        try {
            MarcianoRustCore.shamirRecover(listOf(shares[0].shareB64, shares[1].shareB64), "strong-passphrase")
        } catch (_: Exception) {
            failedWithTwo = true
        }
        assertTrue(failedWithTwo)
    }

    @Test
    fun `ProfileVaultManager stores Argon2id hashes without plaintext mnemonic`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("vault_unit_test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val mnemonic = MarcianoRustCore.generateBip39()
        val masterKey = ProfileVaultManager.storeProfileCredentials(
            prefs = prefs,
            alias = "MARS_ALPHA",
            mnemonic = mnemonic,
            pin = "749261",
            distressPin = "831045",
            panicPin = "592837"
        )
        assertEquals(32, masterKey.size)
        assertNull(prefs.getString("profile_MARS_ALPHA_mnemonic", null))
        assertTrue(prefs.getString("profile_MARS_ALPHA_pin_phc", "")!!.startsWith("\$argon2id\$"))

        assertEquals(ProfileVaultManager.PinVerdict.PRIMARY_MATCH, ProfileVaultManager.verifyPin(prefs, "MARS_ALPHA", "749261"))
        assertEquals(ProfileVaultManager.PinVerdict.DISTRESS_MATCH, ProfileVaultManager.verifyPin(prefs, "MARS_ALPHA", "831045"))
        assertEquals(ProfileVaultManager.PinVerdict.PANIC_MATCH, ProfileVaultManager.verifyPin(prefs, "MARS_ALPHA", "592837"))
        assertEquals(ProfileVaultManager.PinVerdict.INVALID, ProfileVaultManager.verifyPin(prefs, "MARS_ALPHA", "000000"))

        val unwrapped = ProfileVaultManager.unwrapMasterKeyWithPin(prefs, "MARS_ALPHA", "749261")
        assertArrayEquals(masterKey, unwrapped)
        val decryptedMnemonic = ProfileVaultManager.decryptMnemonic(prefs, "MARS_ALPHA", unwrapped!!)
        assertEquals(mnemonic, decryptedMnemonic)

        ProfileVaultManager.purgeProfileSecrets(prefs, "MARS_ALPHA")
        assertFalse(ProfileVaultManager.hasPinConfigured(prefs, "MARS_ALPHA"))
    }

    @Test
    fun `Rust probeRoute verifies live local TCP socket and rejects closed port`() = runBlocking {
        val ss = ServerSocket(0)
        val openPort = ss.localPort
        try {
            val okRes = MarcianoRustCore.probeRoute("127.0.0.1", openPort, "127.0.0.1", 9050, false)
            assertTrue("Expected probeRoute to succeed on open port $openPort: ${okRes.detail}", okRes.ok)

            val parsed = ProxyChainManager.extractHostPort(ProxyType.WIREGUARD, "[Peer]\nEndpoint = 127.0.0.1:$openPort")
            assertEquals("127.0.0.1" to openPort, parsed)
        } finally {
            ss.close()
        }
    }
}

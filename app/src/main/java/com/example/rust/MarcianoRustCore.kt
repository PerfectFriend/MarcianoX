package com.example.rust

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

data class RustPinAuthRecord(
    val phcHash: String,
    val kdfSaltB64: String
)

data class RustAesGcmPayload(
    val ciphertextB64: String,
    val ivB64: String
)

data class RustX25519Keypair(
    val privateB64: String,
    val publicB64: String
)

data class RustRatchetEnvelope(
    val dhPubB64: String,
    val msgNum: Int,
    val ciphertextB64: String,
    val ivB64: String
)

data class RustRatchetEncryptionResult(
    val envelope: RustRatchetEnvelope,
    val updatedStateJson: String
)

data class RustRatchetDecryptionResult(
    val plaintext: ByteArray,
    val updatedStateJson: String
)

data class RustShamirShare(
    val index: Int,
    val threshold: Int,
    val total: Int,
    val shareB64: String,
    val checksumHex: String
)

data class RustRouteProbeResult(
    val ok: Boolean,
    val rttMs: Long,
    val mode: String,
    val detail: String
)

data class RustRelayStatus(
    val connected: Boolean,
    val relayUrl: String,
    val socksProxy: String,
    val bytesSent: Long,
    val bytesReceived: Long,
    val lastError: String
)

data class RustRelayEvent(
    val eventType: String,
    val payload: String
)

object MarcianoRustCore {
    @Volatile
    var isLoaded: Boolean = false
        private set

    @Volatile
    var loadError: String? = null
        private set

    init {
        try {
            System.loadLibrary("marcianox_core")
            isLoaded = true
        } catch (t: Throwable) {
            isLoaded = false
            loadError = t.message ?: t.javaClass.simpleName
        }
    }

    @JvmStatic external fun nativeVersion(): String
    @JvmStatic external fun nativeHashPinArgon2(pin: String): String
    @JvmStatic external fun nativeVerifyPinArgon2(pin: String, phcHash: String): Boolean
    @JvmStatic external fun nativeDeriveKeyPbkdf2(secret: String, saltB64: String, iterations: Int): ByteArray?
    @JvmStatic external fun nativeEncryptAesGcm(plaintext: ByteArray, key: ByteArray): String
    @JvmStatic external fun nativeDecryptAesGcm(ciphertextB64: String, ivB64: String, key: ByteArray): ByteArray?
    @JvmStatic external fun nativeGenerateBip39(): String
    @JvmStatic external fun nativeGenerateDeterministicBip39(dateStr: String, customWordsJson: String): String
    @JvmStatic external fun nativeValidateBip39(wordsJson: String): Boolean
    @JvmStatic external fun nativeBip39ToSeedHex(wordsJson: String, passphrase: String): String
    @JvmStatic external fun nativeGenerateX25519Keypair(): String
    @JvmStatic external fun nativeRatchetInit(
        contactId: Int,
        selfPrivB64: String,
        selfPubB64: String,
        remotePubB64: String,
        isInitiator: Boolean
    ): String
    @JvmStatic external fun nativeRatchetLoadState(contactId: Int, stateJson: String): Boolean
    @JvmStatic external fun nativeRatchetEncrypt(contactId: Int, plaintext: ByteArray): String
    @JvmStatic external fun nativeRatchetDecrypt(contactId: Int, envelopeJson: String): String
    @JvmStatic external fun nativeShamirSplit(payload: ByteArray, passphrase: String, threshold: Int, total: Int): String
    @JvmStatic external fun nativeShamirRecover(sharesJson: String, passphrase: String): ByteArray?
    @JvmStatic external fun nativeVaultSetSession(alias: String, masterKey: ByteArray)
    @JvmStatic external fun nativeVaultLockAndZeroize()
    @JvmStatic external fun nativePanicZeroizeAll(): Int
    @JvmStatic external fun nativeProbeRoute(
        targetHost: String,
        targetPort: Int,
        socksHost: String,
        socksPort: Int,
        useSocks5: Boolean
    ): String
    @JvmStatic external fun nativeProbeSocks5Daemon(socksHost: String, socksPort: Int): String
    @JvmStatic external fun nativeRelayConnect(
        wsUrl: String,
        socksHost: String,
        socksPort: Int,
        useSocks5: Boolean
    ): String
    @JvmStatic external fun nativeRelaySendFrame(frameJson: String): String
    @JvmStatic external fun nativeRelayDisconnect()
    @JvmStatic external fun nativeRelayStatus(): String
    @JvmStatic external fun nativeRelayDrainEvents(): String

    fun version(): String = if (isLoaded) nativeVersion() else "UNLOADED ($loadError)"

    fun hashPinArgon2(pin: String): RustPinAuthRecord {
        val root = JSONObject(nativeHashPinArgon2(pin))
        if (!root.optBoolean("ok")) error(root.optString("error", "Argon2 hash failed"))
        val data = root.getJSONObject("data")
        return RustPinAuthRecord(
            phcHash = data.getString("phc_hash"),
            kdfSaltB64 = data.getString("kdf_salt_b64")
        )
    }

    fun verifyPinArgon2(pin: String, phcHash: String): Boolean {
        if (!isLoaded || phcHash.isBlank()) return false
        return nativeVerifyPinArgon2(pin, phcHash)
    }

    fun deriveKeyPbkdf2(secret: String, saltB64: String, iterations: Int = 100_000): ByteArray {
        return nativeDeriveKeyPbkdf2(secret, saltB64, iterations)
            ?: error("Rust PBKDF2 derivation failed")
    }

    fun encryptAesGcm(plaintext: ByteArray, key: ByteArray): RustAesGcmPayload {
        val root = JSONObject(nativeEncryptAesGcm(plaintext, key))
        if (!root.optBoolean("ok")) error(root.optString("error", "AES-GCM encrypt failed"))
        val data = root.getJSONObject("data")
        return RustAesGcmPayload(
            ciphertextB64 = data.getString("ciphertext_b64"),
            ivB64 = data.getString("iv_b64")
        )
    }

    fun decryptAesGcm(ciphertextB64: String, ivB64: String, key: ByteArray): ByteArray? {
        if (!isLoaded) return null
        return nativeDecryptAesGcm(ciphertextB64, ivB64, key)
    }

    fun generateBip39(): List<String> {
        val root = JSONObject(nativeGenerateBip39())
        if (!root.optBoolean("ok")) error(root.optString("error", "BIP39 generation failed"))
        val arr = root.getJSONArray("data")
        return List(arr.length()) { arr.getString(it) }
    }

    fun generateDeterministicBip39(dateStr: String, customWords: List<String>): List<String> {
        val arrJson = JSONArray(customWords).toString()
        val root = JSONObject(nativeGenerateDeterministicBip39(dateStr, arrJson))
        if (!root.optBoolean("ok")) error(root.optString("error", "Deterministic BIP39 failed"))
        val arr = root.getJSONArray("data")
        return List(arr.length()) { arr.getString(it) }
    }

    fun validateBip39(words: List<String>): Boolean {
        if (!isLoaded) return false
        return nativeValidateBip39(JSONArray(words).toString())
    }

    fun bip39ToSeedHex(words: List<String>, passphrase: String = ""): String {
        val root = JSONObject(nativeBip39ToSeedHex(JSONArray(words).toString(), passphrase))
        if (!root.optBoolean("ok")) error(root.optString("error", "BIP39 seed derivation failed"))
        return root.getString("data")
    }

    fun generateX25519Keypair(): RustX25519Keypair {
        val root = JSONObject(nativeGenerateX25519Keypair())
        if (!root.optBoolean("ok")) error(root.optString("error", "X25519 keygen failed"))
        val data = root.getJSONObject("data")
        return RustX25519Keypair(
            privateB64 = data.getString("private_b64"),
            publicB64 = data.getString("public_b64")
        )
    }

    fun ratchetInit(
        contactId: Int,
        selfPrivB64: String,
        selfPubB64: String,
        remotePubB64: String,
        isInitiator: Boolean
    ): String {
        val root = JSONObject(nativeRatchetInit(contactId, selfPrivB64, selfPubB64, remotePubB64, isInitiator))
        if (!root.optBoolean("ok")) error(root.optString("error", "Ratchet init failed"))
        return root.getJSONObject("data").toString()
    }

    fun ratchetLoadState(contactId: Int, stateJson: String): Boolean {
        if (!isLoaded || stateJson.isBlank()) return false
        return nativeRatchetLoadState(contactId, stateJson)
    }

    fun ratchetEncrypt(contactId: Int, plaintext: ByteArray): RustRatchetEncryptionResult {
        val root = JSONObject(nativeRatchetEncrypt(contactId, plaintext))
        if (!root.optBoolean("ok")) error(root.optString("error", "Ratchet encrypt failed"))
        val data = root.getJSONObject("data")
        val env = data.getJSONObject("envelope")
        val state = data.getJSONObject("state")
        return RustRatchetEncryptionResult(
            envelope = RustRatchetEnvelope(
                dhPubB64 = env.getString("dh_pub_b64"),
                msgNum = env.getInt("msg_num"),
                ciphertextB64 = env.getString("ciphertext_b64"),
                ivB64 = env.getString("iv_b64")
            ),
            updatedStateJson = state.toString()
        )
    }

    fun ratchetDecrypt(contactId: Int, envelope: RustRatchetEnvelope): RustRatchetDecryptionResult {
        val envJson = JSONObject().apply {
            put("dh_pub_b64", envelope.dhPubB64)
            put("msg_num", envelope.msgNum)
            put("ciphertext_b64", envelope.ciphertextB64)
            put("iv_b64", envelope.ivB64)
        }.toString()
        val root = JSONObject(nativeRatchetDecrypt(contactId, envJson))
        if (!root.optBoolean("ok")) error(root.optString("error", "Ratchet decrypt failed"))
        val data = root.getJSONObject("data")
        val pt = Base64.decode(data.getString("plaintext_b64"), Base64.DEFAULT)
        return RustRatchetDecryptionResult(
            plaintext = pt,
            updatedStateJson = data.getJSONObject("state").toString()
        )
    }

    fun shamirSplit(
        payload: ByteArray,
        passphrase: String,
        threshold: Int = 3,
        total: Int = 5
    ): List<RustShamirShare> {
        val root = JSONObject(nativeShamirSplit(payload, passphrase, threshold, total))
        if (!root.optBoolean("ok")) error(root.optString("error", "Shamir split failed"))
        val arr = root.getJSONArray("data")
        return List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            RustShamirShare(
                index = obj.getInt("index"),
                threshold = obj.getInt("threshold"),
                total = obj.getInt("total"),
                shareB64 = obj.getString("share_b64"),
                checksumHex = obj.getString("checksum_hex")
            )
        }
    }

    fun shamirRecover(sharesB64: List<String>, passphrase: String): ByteArray {
        return nativeShamirRecover(JSONArray(sharesB64).toString(), passphrase)
            ?: error("Shamir recovery failed (check shares or passphrase)")
    }

    fun probeRoute(
        targetHost: String,
        targetPort: Int,
        socksHost: String = "127.0.0.1",
        socksPort: Int = 9050,
        useSocks5: Boolean = true
    ): RustRouteProbeResult {
        val root = JSONObject(nativeProbeRoute(targetHost, targetPort, socksHost, socksPort, useSocks5))
        val d = root.getJSONObject("data")
        return RustRouteProbeResult(
            ok = d.getBoolean("ok"),
            rttMs = d.getLong("rtt_ms"),
            mode = d.getString("mode"),
            detail = d.getString("detail")
        )
    }

    fun probeSocks5Daemon(socksHost: String = "127.0.0.1", socksPort: Int = 9050): RustRouteProbeResult {
        val root = JSONObject(nativeProbeSocks5Daemon(socksHost, socksPort))
        val d = root.getJSONObject("data")
        return RustRouteProbeResult(
            ok = d.getBoolean("ok"),
            rttMs = d.getLong("rtt_ms"),
            mode = d.getString("mode"),
            detail = d.getString("detail")
        )
    }

    fun relayConnect(
        wsUrl: String,
        socksHost: String = "127.0.0.1",
        socksPort: Int = 9050,
        useSocks5: Boolean = false
    ): Result<Unit> {
        val root = JSONObject(nativeRelayConnect(wsUrl, socksHost, socksPort, useSocks5))
        return if (root.optBoolean("ok")) Result.success(Unit)
        else Result.failure(IllegalStateException(root.optString("error", "Relay connect failed")))
    }

    fun relaySendFrame(frameJson: String): Result<Unit> {
        val root = JSONObject(nativeRelaySendFrame(frameJson))
        return if (root.optBoolean("ok")) Result.success(Unit)
        else Result.failure(IllegalStateException(root.optString("error", "Relay send failed")))
    }

    fun relayStatus(): RustRelayStatus {
        val root = JSONObject(nativeRelayStatus())
        val d = root.getJSONObject("data")
        return RustRelayStatus(
            connected = d.getBoolean("connected"),
            relayUrl = d.getString("relay_url"),
            socksProxy = d.getString("socks_proxy"),
            bytesSent = d.getLong("bytes_sent"),
            bytesReceived = d.getLong("bytes_received"),
            lastError = d.getString("last_error")
        )
    }

    fun relayDrainEvents(): List<RustRelayEvent> {
        val root = JSONObject(nativeRelayDrainEvents())
        val arr = root.getJSONArray("data")
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            RustRelayEvent(
                eventType = o.getString("event_type"),
                payload = o.getString("payload")
            )
        }
    }
}

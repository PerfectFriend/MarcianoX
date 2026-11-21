package com.example.crypto

import android.content.SharedPreferences
import android.util.Base64
import com.example.rust.MarcianoRustCore
import java.security.SecureRandom

/**
 * ProfileVaultManager replaces legacy plaintext SharedPreferences mnemonic & unsalted SHA-256 PIN storage
 * with Rust Argon2id PHC hashing, PBKDF2 per-profile key derivation, and Android Keystore hardware wrapping.
 */
object ProfileVaultManager {
    private const val KEY_ALIAS_PREFIX = "marcianox_vault_"

    fun storeProfileCredentials(
        prefs: SharedPreferences,
        alias: String,
        mnemonic: List<String>,
        pin: String,
        distressPin: String,
        panicPin: String
    ): ByteArray {
        val pinRec = MarcianoRustCore.hashPinArgon2(pin)
        val distressRec = MarcianoRustCore.hashPinArgon2(distressPin)
        val panicRec = MarcianoRustCore.hashPinArgon2(panicPin)

        // Generate random 32-byte master vault key (used for SQLCipher & message storage)
        val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val pinDerivedWrapKey = MarcianoRustCore.deriveKeyPbkdf2(pin, pinRec.kdfSaltB64, 100_000)

        // Seal masterKey with PIN-derived PBKDF2 key, then additionally wrap with Android Keystore
        val pinSealedMaster = MarcianoRustCore.encryptAesGcm(masterKey, pinDerivedWrapKey)
        val ksAlias = "$KEY_ALIAS_PREFIX$alias"
        val keystoreWrappedMaster = SecurityManager.encryptToString(
            Base64.encodeToString(masterKey, Base64.NO_WRAP),
            ksAlias
        )

        // Encrypt 24-word BIP-39 mnemonic inside Rust AES-256-GCM using masterKey (never plaintext!)
        val mnemonicBytes = mnemonic.joinToString(" ").toByteArray(Charsets.UTF_8)
        val sealedMnemonic = MarcianoRustCore.encryptAesGcm(mnemonicBytes, masterKey)

        MemoryZeroizationManager.zeroAll(
            pinDerivedWrapKey,
            mnemonicBytes,
            level = MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY
        )

        prefs.edit()
            .remove("profile_${alias}_mnemonic")
            .putString("profile_${alias}_mnemonic_ct", sealedMnemonic.ciphertextB64)
            .putString("profile_${alias}_mnemonic_iv", sealedMnemonic.ivB64)
            .putString("profile_${alias}_pin_phc", pinRec.phcHash)
            .putString("profile_${alias}_pin_salt", pinRec.kdfSaltB64)
            .putString("profile_${alias}_distress_phc", distressRec.phcHash)
            .putString("profile_${alias}_panic_phc", panicRec.phcHash)
            .putString("profile_${alias}_master_pin_ct", pinSealedMaster.ciphertextB64)
            .putString("profile_${alias}_master_pin_iv", pinSealedMaster.ivB64)
            .putString("profile_${alias}_master_ks_blob", keystoreWrappedMaster)
            .putString("profile_${alias}_skin", prefs.getString("profile_${alias}_skin", "CYBERPUNK") ?: "CYBERPUNK")
            .apply()

        return masterKey
    }

    fun hasPinConfigured(prefs: SharedPreferences, alias: String?): Boolean {
        if (alias.isNullOrBlank()) return false
        return prefs.contains("profile_${alias}_pin_phc") || prefs.contains("profile_${alias}_pin_hash")
    }

    enum class PinVerdict {
        PRIMARY_MATCH,
        DISTRESS_MATCH,
        PANIC_MATCH,
        INVALID
    }

    fun verifyPin(prefs: SharedPreferences, alias: String?, pin: String): PinVerdict {
        if (alias.isNullOrBlank()) return PinVerdict.INVALID
        val panicPhc = prefs.getString("profile_${alias}_panic_phc", null)
        if (!panicPhc.isNullOrBlank() && MarcianoRustCore.verifyPinArgon2(pin, panicPhc)) {
            return PinVerdict.PANIC_MATCH
        }
        val distressPhc = prefs.getString("profile_${alias}_distress_phc", null)
        if (!distressPhc.isNullOrBlank() && MarcianoRustCore.verifyPinArgon2(pin, distressPhc)) {
            return PinVerdict.DISTRESS_MATCH
        }
        val pinPhc = prefs.getString("profile_${alias}_pin_phc", null)
        if (!pinPhc.isNullOrBlank() && MarcianoRustCore.verifyPinArgon2(pin, pinPhc)) {
            return PinVerdict.PRIMARY_MATCH
        }
        return PinVerdict.INVALID
    }

    fun unwrapMasterKeyWithPin(prefs: SharedPreferences, alias: String, pin: String): ByteArray? {
        val salt = prefs.getString("profile_${alias}_pin_salt", null) ?: return null
        val ct = prefs.getString("profile_${alias}_master_pin_ct", null) ?: return null
        val iv = prefs.getString("profile_${alias}_master_pin_iv", null) ?: return null
        val wrapKey = MarcianoRustCore.deriveKeyPbkdf2(pin, salt, 100_000)
        return try {
            MarcianoRustCore.decryptAesGcm(ct, iv, wrapKey)
        } finally {
            MemoryZeroizationManager.zero(wrapKey, MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY)
        }
    }

    fun unwrapMasterKeyWithKeystore(prefs: SharedPreferences, alias: String): ByteArray? {
        val blob = prefs.getString("profile_${alias}_master_ks_blob", null) ?: return null
        return try {
            val b64 = SecurityManager.decryptFromString(blob, "$KEY_ALIAS_PREFIX$alias")
            Base64.decode(b64, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun decryptMnemonic(prefs: SharedPreferences, alias: String, masterKey: ByteArray): List<String> {
        val ct = prefs.getString("profile_${alias}_mnemonic_ct", null)
        val iv = prefs.getString("profile_${alias}_mnemonic_iv", null)
        if (!ct.isNullOrBlank() && !iv.isNullOrBlank()) {
            val bytes = MarcianoRustCore.decryptAesGcm(ct, iv, masterKey) ?: return emptyList()
            return try {
                String(bytes, Charsets.UTF_8).split(" ").filter { it.isNotBlank() }
            } finally {
                MemoryZeroizationManager.zero(bytes, MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY)
            }
        }
        return emptyList()
    }

    fun purgeProfileSecrets(prefs: SharedPreferences, alias: String) {
        SecurityManager.deleteKey("$KEY_ALIAS_PREFIX$alias")
        prefs.edit()
            .remove("profile_${alias}_mnemonic")
            .remove("profile_${alias}_mnemonic_ct")
            .remove("profile_${alias}_mnemonic_iv")
            .remove("profile_${alias}_pin_hash")
            .remove("profile_${alias}_distress_pin_hash")
            .remove("profile_${alias}_panic_pin_hash")
            .remove("profile_${alias}_pin_phc")
            .remove("profile_${alias}_pin_salt")
            .remove("profile_${alias}_distress_phc")
            .remove("profile_${alias}_panic_phc")
            .remove("profile_${alias}_master_pin_ct")
            .remove("profile_${alias}_master_pin_iv")
            .remove("profile_${alias}_master_ks_blob")
            .remove("profile_${alias}_skin")
            .apply()
    }
}

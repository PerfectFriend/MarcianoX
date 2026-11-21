package com.example.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Bip39Utility - A standard-compliant deterministic mnemonic tool for Simplex Secure Chat.
 * Generates, validates, and derives cryptographically strong master seeds from 12-word phrases.
 * Strictly implements the BIP-39 standard and key derivation hierarchy.
 */
object Bip39Utility {

    data class DerivedKey(
        val path: String,
        val purpose: String,
        val keyHex: String
    )

    /**
     * Converts a 16-byte (128-bit) entropy into a standard BIP-39 12-word mnemonic phrase.
     */
    fun bytesToMnemonic(entropy: ByteArray): List<String> {
        if (entropy.size != 16) throw IllegalArgumentException("Entropy must be 16 bytes for 12 words")

        // 1. Compute SHA-256 checksum
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(entropy)
        val checksumByte = hash[0] // We only need the first 4 bits for 128-bit entropy

        // 2. Convert entropy + checksum into a sequence of bits
        val bits = BooleanArray(132)
        var bitIndex = 0

        // Entropy bits (128 bits)
        for (i in 0 until 16) {
            val b = entropy[i].toInt()
            for (j in 7 downTo 0) {
                bits[bitIndex++] = ((b ushr j) and 1) == 1
            }
        }

        // Checksum bits (4 bits)
        val cb = checksumByte.toInt()
        for (j in 7 downTo 4) {
            bits[bitIndex++] = ((cb ushr j) and 1) == 1
        }

        // 3. Split into 12 groups of 11 bits and map to the official English wordlist
        val words = ArrayList<String>(12)
        for (i in 0 until 12) {
            var index = 0
            for (j in 0 until 11) {
                index = index shl 1
                if (bits[i * 11 + j]) {
                    index = index or 1
                }
            }
            words.add(Bip39EnglishWordList.WORDS[index])
        }
        return words
    }

    /**
     * Converts a 32-byte (256-bit) entropy into a standard BIP-39 24-word mnemonic phrase.
     */
    fun bytesToMnemonic24(entropy: ByteArray): List<String> {
        if (entropy.size != 32) throw IllegalArgumentException("Entropy must be 32 bytes for 24 words")

        // 1. Compute SHA-256 checksum (8 bits = first byte of SHA-256 hash)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(entropy)
        val checksumByte = hash[0]

        // 2. Convert entropy + checksum into a sequence of bits (264 bits total)
        val bits = BooleanArray(264)
        var bitIndex = 0

        // Entropy bits (256 bits)
        for (i in 0 until 32) {
            val b = entropy[i].toInt()
            for (j in 7 downTo 0) {
                bits[bitIndex++] = ((b ushr j) and 1) == 1
            }
        }

        // Checksum bits (8 bits)
        val cb = checksumByte.toInt()
        for (j in 7 downTo 0) {
            bits[bitIndex++] = ((cb ushr j) and 1) == 1
        }

        // 3. Split into 24 groups of 11 bits and map to the official English wordlist
        val words = ArrayList<String>(24)
        for (i in 0 until 24) {
            var index = 0
            for (j in 0 until 11) {
                index = index shl 1
                if (bits[i * 11 + j]) {
                    index = index or 1
                }
            }
            words.add(Bip39EnglishWordList.WORDS[index])
        }
        return words
    }

    /**
     * Generates a 24-word mnemonic phrase deterministically from date and 5 custom words.
     * dateString format: "yyyy-MM-dd"
     */
    fun generateDeterministicMnemonic24(dateString: String, customWordsList: List<String>): List<String> {
        if (com.example.rust.MarcianoRustCore.isLoaded) {
            return com.example.rust.MarcianoRustCore.generateDeterministicBip39(dateString, customWordsList)
        }
        val cleanWords = customWordsList.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val combinedString = "$dateString:" + cleanWords.joinToString(" ")
        val digest = MessageDigest.getInstance("SHA-256")
        val entropyHash = digest.digest(combinedString.toByteArray(Charsets.UTF_8))
        return bytesToMnemonic24(entropyHash)
    }

    /**
     * Validates if a space-separated 24-word mnemonic is correct and checksum matches BIP-39.
     */
    fun validateMnemonic24(mnemonic: String): Boolean {
        val trimmed = mnemonic.trim().lowercase()
        if (trimmed.isEmpty()) return false
        val words = trimmed.split(Regex("\\s+"))
        if (words.size != 24) return false
        if (com.example.rust.MarcianoRustCore.isLoaded) {
            return com.example.rust.MarcianoRustCore.validateBip39(words)
        }

        // Check if all words exist in the standard word list
        val wordToIndex = Bip39EnglishWordList.WORDS.withIndex().associate { it.value to it.index }
        val indices = IntArray(24)
        for (i in 0 until 24) {
            val word = words[i]
            val index = wordToIndex[word] ?: return false
            indices[i] = index
        }

        // Reconstruct 264 bits
        val bits = BooleanArray(264)
        for (i in 0 until 24) {
            val index = indices[i]
            for (j in 0 until 11) {
                bits[i * 11 + j] = ((index ushr (10 - j)) and 1) == 1
            }
        }

        // Extract entropy (first 256 bits = 32 bytes)
        val entropy = ByteArray(32)
        for (i in 0 until 32) {
            var b = 0
            for (j in 0 until 8) {
                b = b shl 1
                if (bits[i * 8 + j]) {
                    b = b or 1
                }
            }
            entropy[i] = b.toByte()
        }

        // Extract checksum (last 8 bits)
        var checksum = 0
        for (j in 256 until 264) {
            checksum = checksum shl 1
            if (bits[j]) {
                checksum = checksum or 1
            }
        }

        // Compute SHA-256 checksum of entropy
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(entropy)
        val computedChecksum = hash[0].toInt() and 0xFF

        return checksum == computedChecksum
    }

    /**
     * Generates a 12-word mnemonic phrase deterministically from date and custom words.
     * dateString format: "yyyy-MM-dd"
     */
    fun generateDeterministicMnemonic(dateString: String, customWords: String): List<String> {
        val cleanWords = customWords.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val combinedString = "$dateString:" + cleanWords.joinToString(" ")

        // Hash the combined date and words to generate high-quality entropy
        val digest = MessageDigest.getInstance("SHA-256")
        val entropyHash = digest.digest(combinedString.toByteArray(Charsets.UTF_8))

        // Take the first 16 bytes (128 bits) of the hash as our entropy
        val entropy = entropyHash.copyOf(16)
        return bytesToMnemonic(entropy)
    }

    /**
     * Generates a random 12-word mnemonic phrase.
     * Captures 128 bits of high-entropy secure random data.
     */
    fun generateMnemonic(): List<String> {
        val random = SecureRandom()
        val entropy = ByteArray(16) // 16 bytes = 128 bits of raw entropy
        random.nextBytes(entropy)
        return bytesToMnemonic(entropy)
    }

    /**
     * Validates if a space-separated 12-word mnemonic is syntactically correct and checksum matches BIP-39.
     */
    fun validateMnemonic(mnemonic: String): Boolean {
        val trimmed = mnemonic.trim().lowercase()
        if (trimmed.isEmpty()) return false
        val words = trimmed.split(Regex("\\s+"))
        if (words.size != 12) return false
        if (com.example.rust.MarcianoRustCore.isLoaded) {
            return com.example.rust.MarcianoRustCore.validateBip39(words)
        }

        // Check if all words exist in the standard word list
        val wordToIndex = Bip39EnglishWordList.WORDS.withIndex().associate { it.value to it.index }
        val indices = IntArray(12)
        for (i in 0 until 12) {
            val word = words[i]
            val index = wordToIndex[word] ?: return false
            indices[i] = index
        }

        // Reconstruct 132 bits
        val bits = BooleanArray(132)
        for (i in 0 until 12) {
            val index = indices[i]
            for (j in 0 until 11) {
                bits[i * 11 + j] = ((index ushr (10 - j)) and 1) == 1
            }
        }

        // Extract entropy (first 128 bits = 16 bytes)
        val entropy = ByteArray(16)
        for (i in 0 until 16) {
            var b = 0
            for (j in 0 until 8) {
                b = b shl 1
                if (bits[i * 8 + j]) {
                    b = b or 1
                }
            }
            entropy[i] = b.toByte()
        }

        // Extract checksum (last 4 bits)
        var checksum = 0
        for (j in 128 until 132) {
            checksum = checksum shl 1
            if (bits[j]) {
                checksum = checksum or 1
            }
        }

        // Compute SHA-256 checksum of entropy
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(entropy)
        val computedChecksum = (hash[0].toInt() ushr 4) and 0x0F

        return checksum == computedChecksum
    }

    /**
     * Cryptographically derives a 512-bit (64-byte) master key seed from the mnemonic via Rust BIP-39.
     */
    fun deriveSeed(mnemonic: String, passphrase: String = ""): ByteArray {
        val words = mnemonic.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (com.example.rust.MarcianoRustCore.isLoaded && (words.size == 12 || words.size == 24)) {
            val hexStr = com.example.rust.MarcianoRustCore.bip39ToSeedHex(words, passphrase)
            val seed = hexStr.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            SimplexLogger.log("BIP39_RUST", "Standard BIP-39 512-bit seed derived via Rust PBKDF2/SHA512.")
            return seed
        }
        val cleanPhrase = words.joinToString(" ")
        val salt = "mnemonic$passphrase"
        val spec = PBEKeySpec(cleanPhrase.toCharArray(), salt.toByteArray(Charsets.UTF_8), 2048, 512)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val seed = f.generateSecret(spec).encoded
        SimplexLogger.log("BIP39_CRYPTO", "Standard BIP-39 512-bit seed derived successfully via PBKDF2/SHA512.")
        return seed
    }

    /**
     * HKDF-SHA256 Derivation (RFC 5869) to derive subkeys from the 512-bit master seed.
     */
    fun hkdfDerive(masterSeed: ByteArray, info: String, length: Int = 32): ByteArray {
        // HKDF-Extract
        val mac = Mac.getInstance("HmacSHA256")
        val salt = "SimplexSecureSalt".toByteArray(Charsets.UTF_8)
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(masterSeed)

        // HKDF-Expand (we only need 1 block of 32 bytes)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info.toByteArray(Charsets.UTF_8))
        mac.update(1.toByte())
        val okm = mac.doFinal()

        return okm.copyOf(length)
    }

    /**
     * Derives the full application key hierarchy based on the BIP-32-like paths from master seed.
     */
    fun deriveKeyHierarchy(masterSeed: ByteArray): List<DerivedKey> {
        val paths = listOf(
            Triple("m/44'/9050'/0'/0", "SQLCipher SQLite DB Key", "Database Master Encryption"),
            Triple("m/44'/9050'/0'/1", "Message E2EE AES-GCM Key", "Interactive Chat Session Payload"),
            Triple("m/44'/9050'/0'/2", "Ed25519 Message Signature Key", "Cryptographic Identity Attestation"),
            Triple("m/44'/9050'/0'/3", "Tor Onion v3 Identity Key", "Decentralized Onion Service Hosting")
        )

        return paths.map { (path, purpose, desc) ->
            val derivedBytes = hkdfDerive(masterSeed, path, 32)
            val hexString = derivedBytes.joinToString("") { "%02x".format(it) }
            DerivedKey(path = path, purpose = "$purpose ($desc)", keyHex = hexString)
        }
    }
}

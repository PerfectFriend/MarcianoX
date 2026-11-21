package com.example.crypto

import android.util.Base64
import com.example.rust.MarcianoRustCore
import java.security.MessageDigest

/**
 * DistributedStorageProtocol powered by Rust Shamir GF(256) secret sharing + PBKDF2 + AES-256-GCM.
 */
object DistributedStorageProtocol {

    data class ExchangeCapacity(
        val localAllocatedGb: Float,
        val earnedCloudGb: Float,
        val redundancyFactor: Int = 5,
        val bufferMultiplier: Int = 8,
        val maxPeerChunksAllowed: Long,
        val storageRationFormula: String = "Earned = Local / 8"
    )

    data class StorageShard(
        val shardId: String,
        val shardIndex: Int,
        val totalRequired: Int,
        val thresholdRequired: Int,
        val shareB64: String,
        val encryptedPayload: ByteArray,
        val checksumSha256: ByteArray,
        val targetPeerNodeId: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as StorageShard
            if (shardId != other.shardId) return false
            if (shardIndex != other.shardIndex) return false
            if (totalRequired != other.totalRequired) return false
            if (thresholdRequired != other.thresholdRequired) return false
            if (shareB64 != other.shareB64) return false
            if (!encryptedPayload.contentEquals(other.encryptedPayload)) return false
            if (!checksumSha256.contentEquals(other.checksumSha256)) return false
            if (targetPeerNodeId != other.targetPeerNodeId) return false
            return true
        }

        override fun hashCode(): Int {
            var result = shardId.hashCode()
            result = 31 * result + shardIndex
            result = 31 * result + totalRequired
            result = 31 * result + thresholdRequired
            result = 31 * result + shareB64.hashCode()
            result = 31 * result + encryptedPayload.contentHashCode()
            result = 31 * result + checksumSha256.contentHashCode()
            result = 31 * result + targetPeerNodeId.hashCode()
            return result
        }
    }

    data class ProofOfSpacetime(
        val challengeSeed: ByteArray,
        val blockIndex: Long,
        val expectedResponseHash: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ProofOfSpacetime
            if (!challengeSeed.contentEquals(other.challengeSeed)) return false
            if (blockIndex != other.blockIndex) return false
            if (!expectedResponseHash.contentEquals(other.expectedResponseHash)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = challengeSeed.contentHashCode()
            result = 31 * result + blockIndex.hashCode()
            result = 31 * result + expectedResponseHash.contentHashCode()
            return result
        }
    }

    fun calculateExchangeCapacity(allocatedDiskGb: Float): ExchangeCapacity {
        val earnedCloud = allocatedDiskGb / 8.0f
        val maxChunks = ((allocatedDiskGb * 1024).toLong()) / 16L
        return ExchangeCapacity(
            localAllocatedGb = allocatedDiskGb,
            earnedCloudGb = earnedCloud,
            maxPeerChunksAllowed = maxChunks
        )
    }

    /**
     * Splits payload using Rust Shamir GF(256) 3-of-5 threshold scheme + AES-256-GCM + PBKDF2.
     */
    fun encryptAndShard(
        payload: ByteArray,
        passphraseOrKey: ByteArray,
        backupId: String,
        threshold: Int = 3,
        total: Int = 5
    ): List<StorageShard> {
        val pass = Base64.encodeToString(passphraseOrKey, Base64.NO_WRAP)
        val rustShares = MarcianoRustCore.shamirSplit(payload, pass, threshold, total)
        val md = MessageDigest.getInstance("SHA-256")
        val shards = rustShares.map { share ->
            val rawBytes = Base64.decode(share.shareB64, Base64.NO_WRAP)
            val checksum = md.digest(rawBytes)
            StorageShard(
                shardId = "$backupId-shamir-${share.index}of${share.total}",
                shardIndex = share.index,
                totalRequired = share.total,
                thresholdRequired = share.threshold,
                shareB64 = share.shareB64,
                encryptedPayload = rawBytes,
                checksumSha256 = checksum,
                targetPeerNodeId = "shamir_node_${share.checksumHex.take(10)}"
            )
        }
        SimplexLogger.log("RUST_SHAMIR", "Backup [$backupId] split into $threshold-of-$total Shamir GF(256) shares.")
        return shards
    }

    /**
     * Reconstructs original payload from any >= threshold (default 3) Shamir shards.
     */
    fun reconstructAndDecrypt(
        shards: List<StorageShard>,
        passphraseOrKey: ByteArray
    ): ByteArray {
        val pass = Base64.encodeToString(passphraseOrKey, Base64.NO_WRAP)
        val shareStrings = shards.map { it.shareB64 }
        return MarcianoRustCore.shamirRecover(shareStrings, pass)
    }

    fun generateStorageChallenge(
        shard: StorageShard,
        challengeSeed: ByteArray
    ): ProofOfSpacetime {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(shard.encryptedPayload)
        md.update(challengeSeed)
        val expectedHash = md.digest()
        return ProofOfSpacetime(
            challengeSeed = challengeSeed,
            blockIndex = shard.shardIndex.toLong(),
            expectedResponseHash = expectedHash
        )
    }

    fun verifyStorageProof(
        proof: ProofOfSpacetime,
        peerResponseHash: ByteArray
    ): Boolean {
        val isValid = proof.expectedResponseHash.contentEquals(peerResponseHash)
        SimplexLogger.log("RUST_SHAMIR", "Share integrity challenge: ${if (isValid) "PASSED" else "FAILED"}")
        return isValid
    }
}

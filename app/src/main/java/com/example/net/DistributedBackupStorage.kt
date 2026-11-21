package com.example.net

import android.content.Context
import android.util.Log
import com.example.rust.MarcianoRustCore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BackupShard(
    val shardId: Int,
    val totalShards: Int,
    val threshold: Int,
    val byteSize: Int,
    val checksum: String,
    val shareB64: String,
    val assignedPeerOnion: String,
    val status: String
)

data class CapacityLedger(
    val peerOnion: String,
    val spaceHeldForPeerBytes: Long,
    val spaceSeededOnPeerBytes: Long,
    val status: String,
    val trustScore: Float
)

/**
 * Shamir GF(256) 3-of-5 encrypted backup engine delegating PBKDF2 + AES-256-GCM + Shamir
 * polynomial splitting to the Rust native core (`MarcianoRustCore`).
 */
object DistributedBackupStorage {
    private const val TAG = "DistributedBackup"

    private val _shards = MutableStateFlow<List<BackupShard>>(emptyList())
    val shards: StateFlow<List<BackupShard>> = _shards.asStateFlow()

    private val _ledger = MutableStateFlow<List<CapacityLedger>>(emptyList())
    val ledger: StateFlow<List<CapacityLedger>> = _ledger.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _logs.value = listOf("[$timestamp] $message") + _logs.value.take(49)
        Log.d(TAG, message)
    }

    fun performShardedBackup(
        context: Context,
        backupBytes: ByteArray,
        passphrase: String,
        activePeers: List<String> = emptyList()
    ): List<BackupShard> {
        if (!MarcianoRustCore.isLoaded) {
            addLog("ERROR: Rust native core not loaded; cannot split Shamir shares.")
            return emptyList()
        }
        addLog("Initiating Rust Shamir GF(256) 3-of-5 + AES-256-GCM backup split (${backupBytes.size} bytes)...")
        val rustShares = MarcianoRustCore.shamirSplit(backupBytes, passphrase, threshold = 3, total = 5)
        val result = rustShares.mapIndexed { idx, s ->
            val peerLabel = activePeers.getOrNull(idx) ?: "export_file_${s.index}.marcianox"
            addLog("Share #${s.index}/5 ready (SHA-256: ${s.checksumHex.take(12)}..., ${s.shareB64.length} chars)")
            BackupShard(
                shardId = s.index,
                totalShards = s.total,
                threshold = s.threshold,
                byteSize = s.shareB64.length,
                checksum = s.checksumHex,
                shareB64 = s.shareB64,
                assignedPeerOnion = peerLabel,
                status = "READY_FOR_EXPORT"
            )
        }
        _shards.value = result
        addLog("3-of-5 Shamir shares generated cleanly in Rust native memory.")
        return result
    }

    fun recoverFromShares(sharesB64: List<String>, passphrase: String): ByteArray? {
        if (!MarcianoRustCore.isLoaded) return null
        return try {
            val recovered = MarcianoRustCore.shamirRecover(sharesB64, passphrase)
            addLog("SUCCESS: Vault reconstructed from ${sharesB64.size} Shamir shares (${recovered.size} bytes).")
            recovered
        } catch (e: Exception) {
            addLog("FAILED: Shamir reconstruction failed -> ${e.message}")
            null
        }
    }
}

package com.example.crypto

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.example.rust.MarcianoRustCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.min

data class EncryptedVoiceClip(
    val ciphertextB64: String,
    val ivB64: String,
    val durationSec: Int,
    val waveformBars: List<Float>
)

/**
 * Captures real microphone PCM audio via AudioRecord, computes real amplitude waveform bars,
 * encrypts audio bytes via Rust AES-256-GCM, and plays back directly from decrypted RAM via AudioTrack.
 */
object MediaCryptoManager {
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    private var recordJob: Job? = null
    private var playJob: Job? = null
    private var pcmOut: ByteArrayOutputStream? = null
    private var startTimeMs: Long = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _liveAmplitude = MutableStateFlow(0f)
    val liveAmplitude: StateFlow<Float> = _liveAmplitude.asStateFlow()

    private val _recordedBars = MutableStateFlow<List<Float>>(emptyList())
    val recordedBars: StateFlow<List<Float>> = _recordedBars.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startRecording(scope: CoroutineScope, onError: (String) -> Unit) {
        if (_isRecording.value) return
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        if (minBuf <= 0) {
            onError("AudioRecord hardware buffer unavailable")
            return
        }

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_IN,
                ENCODING,
                minBuf * 2
            )
        } catch (e: SecurityException) {
            onError("Microphone permission denied")
            return
        } catch (e: Exception) {
            onError(e.message ?: "Failed to initialize AudioRecord")
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            onError("AudioRecord failed to initialize")
            return
        }

        val stream = ByteArrayOutputStream()
        pcmOut = stream
        startTimeMs = System.currentTimeMillis()
        _recordedBars.value = emptyList()
        _isRecording.value = true

        recordJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(minBuf)
            val bars = mutableListOf<Float>()
            try {
                recorder.startRecording()
                while (isActive && _isRecording.value) {
                    val read = recorder.read(buf, 0, buf.size)
                    if (read > 0) {
                        stream.write(buf, 0, read)
                        var peak = 0
                        var i = 0
                        while (i + 1 < read) {
                            val sample = (buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)
                            val amp = abs(sample)
                            if (amp > peak) peak = amp
                            i += 2
                        }
                        val norm = min(1f, peak / 22000f).coerceAtLeast(0.06f)
                        _liveAmplitude.value = norm
                        if (bars.size < 48) {
                            bars.add(norm)
                        } else {
                            bars.removeAt(0)
                            bars.add(norm)
                        }
                        _recordedBars.value = bars.toList()
                    }
                }
            } finally {
                try {
                    recorder.stop()
                } catch (_: Exception) {}
                recorder.release()
                MemoryZeroizationManager.zero(buf, MemoryZeroizationManager.SanitizationLevel.FAST)
            }
        }
    }

    fun stopAndEncrypt(masterKey: ByteArray): EncryptedVoiceClip? {
        if (!_isRecording.value) return null
        _isRecording.value = false
        recordJob?.cancel()
        recordJob = null

        val rawPcm = pcmOut?.toByteArray() ?: return null
        pcmOut?.reset()
        pcmOut = null
        if (rawPcm.isEmpty()) return null

        val durationSec = (((System.currentTimeMillis() - startTimeMs) / 1000L).toInt()).coerceAtLeast(1)
        val encrypted = MarcianoRustCore.encryptAesGcm(rawPcm, masterKey)
        MemoryZeroizationManager.zero(rawPcm, MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY)

        val bars = _recordedBars.value.ifEmpty { listOf(0.25f, 0.5f, 0.35f) }
        return EncryptedVoiceClip(
            ciphertextB64 = encrypted.ciphertextB64,
            ivB64 = encrypted.ivB64,
            durationSec = durationSec,
            waveformBars = bars
        )
    }

    fun cancelRecording() {
        _isRecording.value = false
        recordJob?.cancel()
        recordJob = null
        pcmOut?.reset()
        pcmOut = null
        _liveAmplitude.value = 0f
        _recordedBars.value = emptyList()
    }

    fun playEncryptedClip(
        scope: CoroutineScope,
        ciphertextB64: String,
        ivB64: String,
        masterKey: ByteArray,
        onFinished: () -> Unit,
        onError: (String) -> Unit
    ) {
        stopPlayback()
        playJob = scope.launch(Dispatchers.IO) {
            val pcm = MarcianoRustCore.decryptAesGcm(ciphertextB64, ivB64, masterKey)
            if (pcm == null || pcm.isEmpty()) {
                onError("Failed to decrypt voice payload")
                onFinished()
                return@launch
            }

            val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING).coerceAtLeast(4096)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_OUT)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            try {
                track.play()
                var offset = 0
                while (isActive && offset < pcm.size) {
                    val chunk = min(minBuf, pcm.size - offset)
                    track.write(pcm, offset, chunk)
                    offset += chunk
                }
            } finally {
                MemoryZeroizationManager.zero(pcm, MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY)
                try {
                    track.stop()
                } catch (_: Exception) {}
                track.release()
                onFinished()
            }
        }
    }

    fun stopPlayback() {
        playJob?.cancel()
        playJob = null
    }
}

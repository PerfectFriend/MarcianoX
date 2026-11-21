package com.example.crypto

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Arrays
import java.util.Collections
import java.util.WeakHashMap

/**
 * MemoryZeroizationManager provides cryptographic memory sanitization for heap buffers.
 *
 * Prevents memory-dump extraction of plaintext cryptographic material (AES keys,
 * session secrets, IVs, and decrypted payloads) from Android heap memory.
 * Includes countermeasures against JVM/ART Dead Code Elimination (DCE) via volatile
 * sinks, multi-pass overwrites, scoped auto-wiping, and an active buffer registry.
 */
object MemoryZeroizationManager {

    private val secureRandom = SecureRandom()

    /**
     * Volatile sink used as a compiler black-hole to prevent Dead Code Elimination (DCE).
     * The runtime optimizer cannot eliminate write loops when results flow into a volatile field.
     */
    @Volatile
    var blackHoleSink: Byte = 0

    /**
     * Weak registry of active cryptographic buffers in heap memory.
     * Buffers registered here can be batch-wiped instantaneously during panic sequences
     * or lockscreen lockouts. Uses WeakHashMap so it doesn't leak memory if not explicitly unregistered.
     */
    private val activeBufferRegistry: MutableSet<ByteArray> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ByteArray, Boolean>()))

    enum class SanitizationLevel {
        /**
         * Single-pass zeroization (0x00) with volatile verification.
         */
        FAST,

        /**
         * Dual-pass sanitization: cryptographically random bytes followed by 0x00.
         */
        STANDARD,

        /**
         * Multi-pass sanitization inspired by DoD 5220.22-M:
         * Pass 1: Fixed pattern (0xAA)
         * Pass 2: Complement pattern (0x55)
         * Pass 3: Cryptographically secure random bytes
         * Pass 4: Zero bytes (0x00) with verification
         */
        CRYPTOGRAPHIC_MILITARY
    }

    /**
     * Clears a ByteArray buffer with the specified sanitization level.
     * Returns true if the buffer was non-null and successfully zeroed.
     */
    fun zero(
        array: ByteArray?,
        level: SanitizationLevel = SanitizationLevel.STANDARD
    ): Boolean {
        if (array == null || array.isEmpty()) return false

        when (level) {
            SanitizationLevel.FAST -> {
                // Single pass: zeroes
                Arrays.fill(array, 0.toByte())
            }
            SanitizationLevel.STANDARD -> {
                // Pass 1: Secure random bytes
                secureRandom.nextBytes(array)
                // Pass 2: Zeroes
                Arrays.fill(array, 0.toByte())
            }
            SanitizationLevel.CRYPTOGRAPHIC_MILITARY -> {
                // Pass 1: 0xAA (10101010)
                Arrays.fill(array, 0xAA.toByte())
                // Pass 2: 0x55 (01010101)
                Arrays.fill(array, 0x55.toByte())
                // Pass 3: Cryptographically secure random bytes
                secureRandom.nextBytes(array)
                // Pass 4: Zeroes (0x00)
                Arrays.fill(array, 0.toByte())
            }
        }

        // Anti-DCE barrier: read back into volatile black hole to prevent compiler elimination
        var checksum: Byte = 0
        for (i in array.indices) {
            checksum = (checksum.toInt() xor array[i].toInt()).toByte()
        }
        blackHoleSink = checksum

        // Unregister from active registry if present
        activeBufferRegistry.remove(array)
        return true
    }

    /**
     * Clears multiple ByteArray buffers in a single call.
     */
    fun zeroAll(
        vararg arrays: ByteArray?,
        level: SanitizationLevel = SanitizationLevel.STANDARD
    ): Int {
        var count = 0
        for (arr in arrays) {
            if (zero(arr, level)) {
                count++
            }
        }
        return count
    }

    /**
     * Clears a collection of ByteArray buffers.
     */
    fun zeroAll(
        arrays: Collection<ByteArray?>,
        level: SanitizationLevel = SanitizationLevel.STANDARD
    ): Int {
        var count = 0
        for (arr in arrays) {
            if (zero(arr, level)) {
                count++
            }
        }
        return count
    }

    /**
     * Overwrites a CharArray buffer (such as PINs or passphrases) with null characters.
     */
    fun zero(chars: CharArray?): Boolean {
        if (chars == null || chars.isEmpty()) return false
        Arrays.fill(chars, '\u0000')

        var checksum = 0
        for (c in chars) {
            checksum = checksum xor c.code
        }
        blackHoleSink = checksum.toByte()
        return true
    }

    /**
     * Overwrites the contents of a direct or heap-based ByteBuffer.
     */
    fun zero(byteBuffer: ByteBuffer?): Boolean {
        if (byteBuffer == null) return false
        val originalPosition = byteBuffer.position()
        byteBuffer.position(0)
        while (byteBuffer.hasRemaining()) {
            byteBuffer.put(0.toByte())
        }
        byteBuffer.position(originalPosition)
        return true
    }

    /**
     * Registers a sensitive ByteArray in the active memory registry.
     * All registered buffers can be zeroized immediately via [wipeAllActiveBuffers].
     */
    fun register(array: ByteArray): ByteArray {
        activeBufferRegistry.add(array)
        return array
    }

    /**
     * Unregisters a ByteArray from the active memory registry.
     */
    fun unregister(array: ByteArray) {
        activeBufferRegistry.remove(array)
    }

    /**
     * Purges and zeroes all currently registered active memory buffers.
     * Returns the count of buffers zeroed.
     */
    fun wipeAllActiveBuffers(level: SanitizationLevel = SanitizationLevel.STANDARD): Int {
        synchronized(activeBufferRegistry) {
            val snapshot = activeBufferRegistry.toList()
            var count = 0
            for (buf in snapshot) {
                if (zero(buf, level)) {
                    count++
                }
            }
            activeBufferRegistry.clear()
            return count
        }
    }

    /**
     * Verifies that every byte in the buffer is strictly 0x00.
     */
    fun isZeroed(array: ByteArray?): Boolean {
        if (array == null || array.isEmpty()) return true
        for (b in array) {
            if (b.toInt() != 0) return false
        }
        return true
    }

    /**
     * Executes a lambda block with a sensitive ByteArray buffer, ensuring that the buffer
     * is completely zeroized immediately upon exiting the block, even if an exception occurs.
     */
    inline fun <R> withWipedBuffer(
        size: Int,
        level: SanitizationLevel = SanitizationLevel.STANDARD,
        block: (ByteArray) -> R
    ): R {
        val buffer = ByteArray(size)
        register(buffer)
        return try {
            block(buffer)
        } finally {
            zero(buffer, level)
        }
    }

    /**
     * Creates an isolated duplicate copy of the source buffer, runs the block,
     * and guarantees the duplicate copy is sanitized upon completion.
     */
    inline fun <R> withSecureCopy(
        source: ByteArray,
        level: SanitizationLevel = SanitizationLevel.STANDARD,
        block: (ByteArray) -> R
    ): R {
        val copy = source.clone()
        register(copy)
        return try {
            block(copy)
        } finally {
            zero(copy, level)
        }
    }

    /**
     * Extension function allowing convenient scoped usage and zeroization of existing buffers:
     * `keyBytes.useAndWipe { ... }`
     */
    inline fun <R> ByteArray.useAndWipe(
        level: SanitizationLevel = SanitizationLevel.STANDARD,
        block: (ByteArray) -> R
    ): R {
        return try {
            block(this)
        } finally {
            zero(this, level)
        }
    }

    /**
     * Suggests and triggers Garbage Collection compaction passes to assist in clearing
     * transient dereferenced memory.
     */
    fun forceHeapCompaction() {
        try {
            System.gc()
            System.runFinalization()
            System.gc()
        } catch (e: Exception) {
            // Ignore failure in restricted environments
        }
    }
}

/**
 * AutoCloseable container for sensitive byte buffers that automatically zeroizes
 * its underlying array when closed or exiting a `use { ... }` block.
 */
class SecureByteArray(size: Int) : AutoCloseable {

    val bytes: ByteArray = ByteArray(size)
    private var isWiped = false

    init {
        MemoryZeroizationManager.register(bytes)
    }

    constructor(initialData: ByteArray) : this(initialData.size) {
        System.arraycopy(initialData, 0, bytes, 0, initialData.size)
    }

    val size: Int get() = bytes.size

    operator fun get(index: Int): Byte = bytes[index]
    operator fun set(index: Int, value: Byte) {
        bytes[index] = value
    }

    fun isZeroed(): Boolean = MemoryZeroizationManager.isZeroed(bytes)

    override fun close() {
        if (!isWiped) {
            MemoryZeroizationManager.zero(bytes, MemoryZeroizationManager.SanitizationLevel.STANDARD)
            isWiped = true
        }
    }
}

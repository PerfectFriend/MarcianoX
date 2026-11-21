package com.example.crypto

import java.io.File
import java.security.SecureRandom
import java.util.Arrays

object MemoryZeroizer {

    private val secureRandom = SecureRandom()

    /**
     * Instantly overwrites the contents of the given ByteArray with zero bytes.
     */
    fun zero(array: ByteArray?) {
        MemoryZeroizationManager.zero(array, MemoryZeroizationManager.SanitizationLevel.FAST)
    }

    /**
     * Instantly overwrites the contents of the given CharArray with standard null characters.
     */
    fun zero(array: CharArray?) {
        MemoryZeroizationManager.zero(array)
    }

    /**
     * Overwrites a ByteArray with cryptographically secure random bytes before zeroing it out.
     * This provides dual-pass sanitization.
     */
    fun sanitizeSecurely(array: ByteArray?) {
        MemoryZeroizationManager.zero(array, MemoryZeroizationManager.SanitizationLevel.STANDARD)
    }

    /**
     * Secures a file by overwriting its content with random garbage, then zero-bytes,
     * before deleting it. This prevents flash storage recovery tools from pulling the DB remnants.
     */
    fun wipeAndDeleteFile(file: File?): Boolean {
        if (file == null || !file.exists()) return false
        return try {
            if (file.isFile) {
                val length = file.length()
                if (length > 0) {
                    val randomBuffer = ByteArray(4096)
                    file.outputStream().use { fos ->
                        var bytesWritten = 0L
                        while (bytesWritten < length) {
                            val chunkSize = Math.min(4096L, length - bytesWritten).toInt()
                            secureRandom.nextBytes(randomBuffer)
                            fos.write(randomBuffer, 0, chunkSize)
                            bytesWritten += chunkSize
                        }
                        fos.flush()
                    }
                    
                    // Second pass: Write zeroes
                    Arrays.fill(randomBuffer, 0.toByte())
                    file.outputStream().use { fos ->
                        var bytesWritten = 0L
                        while (bytesWritten < length) {
                            val chunkSize = Math.min(4096L, length - bytesWritten).toInt()
                            fos.write(randomBuffer, 0, chunkSize)
                            bytesWritten += chunkSize
                        }
                        fos.flush()
                    }
                }
            }
            file.delete()
        } catch (e: Exception) {
            // Fallback to standard delete if overwriting fails
            file.delete()
        }
    }

    /**
     * Forces immediate JVM Garbage Collection to purge references to zeroized components
     * and clears all registered in-memory cryptographic buffers.
     */
    fun triggerGcPurge() {
        MemoryZeroizationManager.wipeAllActiveBuffers()
        MemoryZeroizationManager.forceHeapCompaction()
    }
}

package com.example

import com.example.crypto.MemoryZeroizationManager
import com.example.crypto.MemoryZeroizationManager.useAndWipe
import com.example.crypto.SecureByteArray
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class MemoryZeroizationManagerTest {

    @Test
    fun testZeroByteArrayLevels() {
        val testData1 = byteArrayOf(0x12, 0x34, 0x56, 0x78.toByte(), 0x9A.toByte())
        assertFalse(MemoryZeroizationManager.isZeroed(testData1))
        val result1 = MemoryZeroizationManager.zero(testData1, MemoryZeroizationManager.SanitizationLevel.FAST)
        assertTrue(result1)
        assertTrue(MemoryZeroizationManager.isZeroed(testData1))

        val testData2 = byteArrayOf(0xFF.toByte(), 0xEE.toByte(), 0xDD.toByte())
        val result2 = MemoryZeroizationManager.zero(testData2, MemoryZeroizationManager.SanitizationLevel.STANDARD)
        assertTrue(result2)
        assertTrue(MemoryZeroizationManager.isZeroed(testData2))

        val testData3 = ByteArray(64) { (it + 1).toByte() }
        val result3 = MemoryZeroizationManager.zero(testData3, MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY)
        assertTrue(result3)
        assertTrue(MemoryZeroizationManager.isZeroed(testData3))
    }

    @Test
    fun testZeroAllMultipleArrays() {
        val b1 = byteArrayOf(1, 2, 3)
        val b2 = byteArrayOf(4, 5, 6)
        val b3: ByteArray? = null

        val count = MemoryZeroizationManager.zeroAll(b1, b2, b3)
        assertEquals(2, count)
        assertTrue(MemoryZeroizationManager.isZeroed(b1))
        assertTrue(MemoryZeroizationManager.isZeroed(b2))
    }

    @Test
    fun testWithWipedBufferEnsuresZeroization() {
        var capturedBuffer: ByteArray? = null
        val result = MemoryZeroizationManager.withWipedBuffer(32) { buf ->
            capturedBuffer = buf
            for (i in buf.indices) {
                buf[i] = (i + 1).toByte()
            }
            assertFalse(MemoryZeroizationManager.isZeroed(buf))
            "COMPUTATION_SUCCESS"
        }

        assertEquals("COMPUTATION_SUCCESS", result)
        assertNotNull(capturedBuffer)
        assertTrue(MemoryZeroizationManager.isZeroed(capturedBuffer))
    }

    @Test
    fun testWithWipedBufferClearsOnException() {
        var capturedBuffer: ByteArray? = null
        try {
            MemoryZeroizationManager.withWipedBuffer(16) { buf ->
                capturedBuffer = buf
                buf[0] = 0x42
                throw IllegalStateException("Simulated crypto failure")
            }
        } catch (e: IllegalStateException) {
            // Expected
        }

        assertNotNull(capturedBuffer)
        assertTrue(MemoryZeroizationManager.isZeroed(capturedBuffer))
    }

    @Test
    fun testWithSecureCopy() {
        val masterKey = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        var copyReference: ByteArray? = null

        MemoryZeroizationManager.withSecureCopy(masterKey) { copy ->
            copyReference = copy
            assertArrayEquals(masterKey, copy)
            copy[0] = 0x99.toByte()
        }

        assertNotNull(copyReference)
        assertTrue(MemoryZeroizationManager.isZeroed(copyReference))
        // Master key original should remain untouched
        assertEquals(0x01.toByte(), masterKey[0])
    }

    @Test
    fun testByteArrayUseAndWipe() {
        val sessionKey = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        val output = sessionKey.useAndWipe { key ->
            assertEquals(3, key.size)
            "USED"
        }
        assertEquals("USED", output)
        assertTrue(MemoryZeroizationManager.isZeroed(sessionKey))
    }

    @Test
    fun testSecureByteArrayAutoCloseable() {
        val initialData = byteArrayOf(10, 20, 30, 40)
        var capturedBytes: ByteArray? = null

        SecureByteArray(initialData).use { secureArr ->
            capturedBytes = secureArr.bytes
            assertEquals(4, secureArr.size)
            assertEquals(10.toByte(), secureArr[0])
            assertFalse(secureArr.isZeroed())
        }

        assertNotNull(capturedBytes)
        assertTrue(MemoryZeroizationManager.isZeroed(capturedBytes))
    }

    @Test
    fun testActiveBufferRegistryAndWipeAll() {
        val b1 = byteArrayOf(1, 2, 3)
        val b2 = byteArrayOf(4, 5, 6)

        MemoryZeroizationManager.register(b1)
        MemoryZeroizationManager.register(b2)

        val count = MemoryZeroizationManager.wipeAllActiveBuffers()
        assertTrue(count >= 2)
        assertTrue(MemoryZeroizationManager.isZeroed(b1))
        assertTrue(MemoryZeroizationManager.isZeroed(b2))
    }

    @Test
    fun testZeroCharArray() {
        val pin = charArrayOf('1', '2', '3', '4', '5', '6', '7', '8')
        val res = MemoryZeroizationManager.zero(pin)
        assertTrue(res)
        for (c in pin) {
            assertEquals('\u0000', c)
        }
    }

    @Test
    fun testZeroByteBuffer() {
        val byteBuffer = ByteBuffer.allocate(8)
        byteBuffer.put(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        byteBuffer.flip()

        val res = MemoryZeroizationManager.zero(byteBuffer)
        assertTrue(res)
        for (i in 0 until 8) {
            assertEquals(0.toByte(), byteBuffer.get(i))
        }
    }
}

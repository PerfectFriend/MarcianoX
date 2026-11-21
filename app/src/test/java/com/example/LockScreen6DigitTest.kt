package com.example

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.ScreenCapturePreventionManager
import com.example.ui.get6DigitPinStrength
import com.example.viewmodel.SimplexViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LockScreen6DigitTest {

    private fun provisionTestProfile(viewModel: SimplexViewModel) {
        viewModel.createAndActivateProfile(
            alias = "TEST_VAULT",
            mnemonic = List(24) { "legal" },
            pin = "123456",
            distressPin = "112233",
            panicPin = "999999"
        )
    }

    @Test
    fun `6-digit master PIN unlocks sealed Argon2id profile successfully`() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = SimplexViewModel(application)
        provisionTestProfile(viewModel)

        viewModel.lock()
        assertTrue(viewModel.isLocked.value)

        // Enter configured 6-digit master PIN "123456"
        viewModel.handleUnlockPin("123456")

        assertFalse(viewModel.isLocked.value)
        assertFalse(viewModel.isDecoyMode.value)
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun `6-digit decoy distress PIN activates decoy mode`() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = SimplexViewModel(application)
        provisionTestProfile(viewModel)

        viewModel.lock()
        assertTrue(viewModel.isLocked.value)

        // Enter configured 6-digit distress PIN "112233"
        viewModel.handleUnlockPin("112233")

        assertFalse(viewModel.isLocked.value)
        assertTrue(viewModel.isDecoyMode.value)
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun `6-digit decoy panic PIN triggers data clearing sequence`() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = SimplexViewModel(application)
        provisionTestProfile(viewModel)

        viewModel.lock()
        assertTrue(viewModel.isLocked.value)
        assertFalse(viewModel.isPanicWiped.value)

        // Enter configured 6-digit panic PIN "999999"
        viewModel.handleUnlockPin("999999")

        assertTrue(viewModel.isPanicWiped.value)
    }

    @Test
    fun `custom 6-digit profile PINs operate correctly for primary, distress, and panic`() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = SimplexViewModel(application)

        viewModel.createAndActivateProfile(
            alias = "OPERATOR_6D",
            mnemonic = List(24) { "legal" },
            pin = "482915",
            distressPin = "739104",
            panicPin = "665544"
        )

        // 1. Primary 6-digit PIN
        viewModel.lock()
        assertTrue(viewModel.isLocked.value)
        viewModel.handleUnlockPin("482915")
        assertFalse(viewModel.isLocked.value)
        assertFalse(viewModel.isDecoyMode.value)

        // 2. Decoy Distress 6-digit PIN
        viewModel.lock()
        assertTrue(viewModel.isLocked.value)
        viewModel.handleUnlockPin("739104")
        assertFalse(viewModel.isLocked.value)
        assertTrue(viewModel.isDecoyMode.value)

        // 3. Decoy Panic 6-digit PIN
        viewModel.lock()
        assertTrue(viewModel.isLocked.value)
        viewModel.handleUnlockPin("665544")
        assertTrue(viewModel.isPanicWiped.value)
    }

    @Test
    fun `rate limiting locks keypad after 5 consecutive failed attempts`() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = SimplexViewModel(application)
        provisionTestProfile(viewModel)

        viewModel.lock()
        assertTrue(viewModel.isLocked.value)
        assertEquals(0, viewModel.lockoutSecondsRemaining.value)

        // 4 failed attempts: keypad should not be locked yet
        for (i in 1..4) {
            viewModel.handleUnlockPin("00000$i")
            assertEquals(0, viewModel.lockoutSecondsRemaining.value)
            assertEquals("Access Denied. Passcode Invalid.", viewModel.errorMessage.value)
        }

        // 5th failed attempt: triggers 30s lockout
        viewModel.handleUnlockPin("000005")
        assertTrue(viewModel.isKeypadLocked())
        assertTrue(viewModel.lockoutSecondsRemaining.value > 0)
        assertTrue(viewModel.errorMessage.value?.contains("Too many failed") == true)

        // Attempting to enter PIN while locked out is rejected
        viewModel.handleUnlockPin("123456")
        assertTrue(viewModel.isLocked.value)
    }

    @Test
    fun `6-digit PIN strength evaluator correctly grades entropy`() {
        // Weak repetitive
        val (weakRep, _) = get6DigitPinStrength("111111")
        assertTrue(weakRep.startsWith("WEAK"))

        // Weak sequential
        val (weakSeq, _) = get6DigitPinStrength("123456")
        assertTrue(weakSeq.startsWith("WEAK"))

        // Weak common pattern
        val (weakPat, _) = get6DigitPinStrength("121212")
        assertTrue(weakPat.startsWith("WEAK"))

        // Strong high entropy
        val (strong, _) = get6DigitPinStrength("941827")
        assertTrue(strong.startsWith("STRONG"))
    }

    @Test
    fun `ScreenCapturePreventionManager enforces and clears FLAG_SECURE on Window`() {
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = activityController.get()

        // Explicitly set protection enabled
        ScreenCapturePreventionManager.setScreenCaptureProtection(activity, true)
        assertTrue(ScreenCapturePreventionManager.isWindowSecure(activity))
        assertTrue(ScreenCapturePreventionManager.isCaptureBlocked.value)

        // Explicitly set protection disabled
        ScreenCapturePreventionManager.setScreenCaptureProtection(activity, false)
        assertFalse(ScreenCapturePreventionManager.isWindowSecure(activity))
        assertFalse(ScreenCapturePreventionManager.isCaptureBlocked.value)

        // applyProtection must respect disabled enforcement flag even with enforceOnEmulator = true
        ScreenCapturePreventionManager.applyProtection(activity, enforceOnEmulator = true)
        assertFalse(ScreenCapturePreventionManager.isWindowSecure(activity))
        assertFalse(ScreenCapturePreventionManager.isCaptureBlocked.value)

        // Re-enable enforcement and verify applyProtection applies FLAG_SECURE
        ScreenCapturePreventionManager.setScreenCaptureProtection(activity, true)
        ScreenCapturePreventionManager.applyProtection(activity, enforceOnEmulator = true)
        assertTrue(ScreenCapturePreventionManager.isWindowSecure(activity))
        assertTrue(ScreenCapturePreventionManager.isCaptureBlocked.value)
    }

    @Test
    fun `SimplexCrypto encrypts decrypts and wipes registered session key from heap`() {
        val key = com.example.crypto.SimplexCrypto.generateSessionKey()
        assertFalse(com.example.crypto.MemoryZeroizationManager.isZeroed(key))

        val encrypted = com.example.crypto.SimplexCrypto.encrypt("CLASSIFIED_PAYLOAD", key)
        val decrypted = com.example.crypto.SimplexCrypto.decrypt(encrypted.ciphertext, encrypted.iv, key)
        assertEquals("CLASSIFIED_PAYLOAD", decrypted)

        // Wipe all registered active buffers via MemoryZeroizationManager
        com.example.crypto.MemoryZeroizationManager.wipeAllActiveBuffers(
            com.example.crypto.MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY
        )
        assertTrue(com.example.crypto.MemoryZeroizationManager.isZeroed(key))

        // Decryption with wiped zeroized key fails safely
        val postWipe = com.example.crypto.SimplexCrypto.decrypt(encrypted.ciphertext, encrypted.iv, key)
        assertNotEquals("CLASSIFIED_PAYLOAD", postWipe)
        assertTrue(postWipe.contains("Decryption Error"))
    }
}

package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.AUTO_LOCK_PRESETS
import com.example.ui.formatAutoLockDuration
import com.example.viewmodel.SimplexViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutoLockTimerTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        // Clear secure preferences between runs
        application.getSharedPreferences("simplex_secure_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun createUnlockedSession(viewModel: SimplexViewModel, pin: String = "123456") {
        viewModel.createAndActivateProfile(
            alias = "OPERATOR",
            mnemonic = List(24) { "legal" },
            pin = pin,
            distressPin = "112233",
            panicPin = "999999"
        )
    }

    @Test
    fun `default auto-lock timeout is 60 seconds`() = runTest {
        val viewModel = SimplexViewModel(application)
        assertEquals(60, viewModel.autoLockTimeoutSeconds.value)
    }

    @Test
    fun `setting auto-lock timeout updates StateFlow and persists to SharedPreferences`() = runTest {
        val viewModel = SimplexViewModel(application)

        // Set to 120 seconds (2 minutes)
        viewModel.setAutoLockTimeout(120)
        assertEquals(120, viewModel.autoLockTimeoutSeconds.value)

        // Verify persistence in SharedPreferences
        val prefs = application.getSharedPreferences("simplex_secure_prefs", Context.MODE_PRIVATE)
        assertEquals(120, prefs.getInt(SimplexViewModel.PREF_KEY_AUTO_LOCK_TIMEOUT, -1))

        // Set to Never (-1)
        viewModel.setAutoLockTimeout(SimplexViewModel.AUTO_LOCK_NEVER)
        assertEquals(SimplexViewModel.AUTO_LOCK_NEVER, viewModel.autoLockTimeoutSeconds.value)
        assertEquals(SimplexViewModel.AUTO_LOCK_NEVER, prefs.getInt(SimplexViewModel.PREF_KEY_AUTO_LOCK_TIMEOUT, 99))
    }

    @Test
    fun `auto-lock triggers after timeout and requires 6-digit PIN to unlock again`() = runTest {
        val viewModel = SimplexViewModel(application)
        createUnlockedSession(viewModel)

        // Set a 30-second auto-lock timeout
        viewModel.setAutoLockTimeout(30)
        assertFalse(viewModel.isLocked.value)
        assertEquals(30, viewModel.autoLockSecondsRemaining.value)

        // Inactivity before timeout: app remains unlocked
        val startTime = System.currentTimeMillis()
        viewModel.recordUserActivity(startTime)
        viewModel.checkAutoLock(startTime + 15_000L) // 15s elapsed
        assertFalse(viewModel.isLocked.value)
        assertEquals(15, viewModel.autoLockSecondsRemaining.value)

        // Inactivity exceeding timeout: app automatically locks
        viewModel.checkAutoLock(startTime + 31_000L) // 31s elapsed
        assertTrue(viewModel.isLocked.value)
        assertNull(viewModel.autoLockSecondsRemaining.value)
        assertTrue(viewModel.errorMessage.value?.contains("auto-locked") == true)

        // Unlocking with 6-digit PIN restores unlocked state and re-arms timer
        viewModel.handleUnlockPin("123456")
        assertFalse(viewModel.isLocked.value)
        assertEquals(30, viewModel.autoLockSecondsRemaining.value)
    }

    @Test
    fun `user activity resets auto-lock countdown timer`() = runTest {
        val viewModel = SimplexViewModel(application)
        createUnlockedSession(viewModel)
        viewModel.setAutoLockTimeout(60)
        assertFalse(viewModel.isLocked.value)

        val startTime = System.currentTimeMillis()
        viewModel.recordUserActivity(startTime)
        viewModel.checkAutoLock(startTime + 50_000L) // 50s elapsed, 10s remaining
        assertFalse(viewModel.isLocked.value)
        assertEquals(10, viewModel.autoLockSecondsRemaining.value)

        // Fresh interaction resets timer baseline:
        val newActivityTime = startTime + 52_000L
        viewModel.recordUserActivity(newActivityTime)
        viewModel.checkAutoLock(newActivityTime + 30_000L) // 30s elapsed from fresh interaction
        assertFalse(viewModel.isLocked.value)
        assertEquals(30, viewModel.autoLockSecondsRemaining.value)
    }

    @Test
    fun `setting timeout to Never prevents automatic locking`() = runTest {
        val viewModel = SimplexViewModel(application)
        createUnlockedSession(viewModel)
        viewModel.setAutoLockTimeout(SimplexViewModel.AUTO_LOCK_NEVER)

        assertFalse(viewModel.isLocked.value)
        assertNull(viewModel.autoLockSecondsRemaining.value)

        // Even after 1 hour (3600 seconds), session remains unlocked
        val startTime = System.currentTimeMillis()
        viewModel.checkAutoLock(startTime + 3_600_000L)
        assertFalse(viewModel.isLocked.value)
    }

    @Test
    fun `immediate background lock locks app instantly when backgrounded`() = runTest {
        val viewModel = SimplexViewModel(application)
        createUnlockedSession(viewModel)
        viewModel.setAutoLockTimeout(SimplexViewModel.AUTO_LOCK_IMMEDIATE)

        assertFalse(viewModel.isLocked.value)

        // App goes to background (user switches apps or locks phone)
        viewModel.onAppBackgrounded()

        assertTrue(viewModel.isLocked.value)
        assertTrue(viewModel.errorMessage.value?.contains("locked") == true)
    }

    @Test
    fun `foregrounding app after background duration exceeding timeout triggers lock`() = runTest {
        val viewModel = SimplexViewModel(application)
        createUnlockedSession(viewModel)
        viewModel.setAutoLockTimeout(60)
        assertFalse(viewModel.isLocked.value)

        val t0 = System.currentTimeMillis()
        viewModel.recordUserActivity(t0)
        viewModel.onAppBackgrounded(t0)

        // Foregrounded after 30s (< 60s timeout) -> remains unlocked
        viewModel.onAppForegrounded(t0 + 30_000L)
        assertFalse(viewModel.isLocked.value)

        // Background again, then foregrounded after 65s (> 60s timeout) -> locks automatically
        viewModel.onAppBackgrounded(t0 + 31_000L)
        viewModel.onAppForegrounded(t0 + 96_000L)
        assertTrue(viewModel.isLocked.value)
        assertNull(viewModel.autoLockSecondsRemaining.value)
        assertTrue(viewModel.errorMessage.value?.contains("auto-locked") == true)
    }

    @Test
    fun `formatAutoLockDuration formats all presets appropriately`() {
        assertEquals("Disabled (Never)", formatAutoLockDuration(-1))
        assertEquals("Immediate on background", formatAutoLockDuration(0))
        assertEquals("15 Seconds", formatAutoLockDuration(15))
        assertEquals("30 Seconds", formatAutoLockDuration(30))
        assertEquals("1 Minute", formatAutoLockDuration(60))
        assertEquals("2 Minutes", formatAutoLockDuration(120))
        assertEquals("5 Minutes", formatAutoLockDuration(300))
        assertEquals("10 Minutes", formatAutoLockDuration(600))
        assertEquals("1m 30s", formatAutoLockDuration(90))

        assertTrue(AUTO_LOCK_PRESETS.any { it.seconds == 60 && it.tag == "auto_lock_chip_60s" })
        assertTrue(AUTO_LOCK_PRESETS.any { it.seconds == 30 && it.tag == "auto_lock_chip_30s" })
        assertTrue(AUTO_LOCK_PRESETS.any { it.seconds == 0 && it.tag == "auto_lock_chip_immediate" })
        assertTrue(AUTO_LOCK_PRESETS.any { it.seconds == -1 && it.tag == "auto_lock_chip_never" })
    }

    @Test
    fun `formatAutoLockDuration localizes into Russian when AppLanguage RUSSIAN is active`() {
        try {
            com.example.ui.LocalizationHelper.setLanguage(com.example.ui.AppLanguage.RUSSIAN)
            assertEquals("Отключено (Никогда)", formatAutoLockDuration(-1))
            assertEquals("Сразу при сворачивании", formatAutoLockDuration(0))
            assertEquals("15 сек", formatAutoLockDuration(15))
            assertEquals("1 мин", formatAutoLockDuration(60))
            assertEquals("1 мин 30 сек", formatAutoLockDuration(90))
        } finally {
            com.example.ui.LocalizationHelper.setLanguage(com.example.ui.AppLanguage.ENGLISH)
        }
    }
}

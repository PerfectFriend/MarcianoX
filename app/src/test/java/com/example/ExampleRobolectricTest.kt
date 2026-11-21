package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.SimplexViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MarcianoX", appName)
  }

  @Test
  fun `lockscreen distinguishes master PIN and decoy panic PIN`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = SimplexViewModel(application)

    // Set up test profile with known PINs
    viewModel.createAndActivateProfile(
        alias = "TEST_OPERATOR",
        mnemonic = List(24) { "abandon" },
        pin = "12345678",
        distressPin = "11223344",
        panicPin = "99887766"
    )

    // Lock the session
    viewModel.lock()
    assertTrue(viewModel.isLocked.value)
    assertFalse(viewModel.isPanicWiped.value)

    // 1. Test entering Master PIN unlocks full session
    viewModel.handleUnlockPin("12345678")
    assertFalse(viewModel.isLocked.value)
    assertFalse(viewModel.isDecoyMode.value)
    assertNull(viewModel.errorMessage.value)

    // 2. Lock and test Decoy Distress PIN opens decoy mode
    viewModel.lock()
    assertTrue(viewModel.isLocked.value)
    viewModel.handleUnlockPin("11223344")
    assertFalse(viewModel.isLocked.value)
    assertTrue(viewModel.isDecoyMode.value)

    // 3. Lock and test Invalid PIN is rejected
    viewModel.lock()
    assertTrue(viewModel.isLocked.value)
    viewModel.handleUnlockPin("00000000")
    assertTrue(viewModel.isLocked.value)
    assertEquals("Access Denied. Passcode Invalid.", viewModel.errorMessage.value)

    // 4. Test entering Decoy Panic PIN engages data clearing sequence
    viewModel.handleUnlockPin("99887766")
    assertTrue(viewModel.isPanicWiped.value)
  }
}


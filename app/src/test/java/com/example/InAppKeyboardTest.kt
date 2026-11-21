package com.example

import android.app.Activity
import android.app.Application
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.example.ui.SkinStyle
import com.example.ui.keyboard.InAppKeyboardHost
import com.example.ui.keyboard.InAppKeyboardManager
import com.example.ui.keyboard.KeyboardLanguage
import com.example.ui.keyboard.KeyboardLayoutMode
import com.example.ui.keyboard.SecureInAppDialogHost
import com.example.ui.keyboard.hardenWindowAgainstInputLeaks
import com.example.viewmodel.SimplexViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class InAppKeyboardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        InAppKeyboardManager.wipeAndHide()
        InAppKeyboardManager.setLanguage(KeyboardLanguage.EN)
        InAppKeyboardManager.setLayoutMode(KeyboardLayoutMode.TEXT)
    }

    @After
    fun tearDown() {
        InAppKeyboardManager.wipeAndHide()
    }

    @Test
    fun `external OS IME and Gboard session is completely blocked and intercepted by InAppKeyboard`() {
        var externalImeRequested = false
        var textValue by mutableStateOf("")
        val initialBlockedCount = InAppKeyboardManager.blockedExternalImeCount.value

        val spyInterceptor = PlatformTextInputInterceptor { request, nextHandler ->
            // If DisableSoftKeyboard ever leaked to nextHandler, this flag would flip true!
            externalImeRequested = true
            nextHandler.startInputMethod(request)
        }

        composeTestRule.setContent {
            InterceptPlatformTextInput(interceptor = spyInterceptor) {
                InAppKeyboardHost(
                    hostId = InAppKeyboardManager.MAIN_HOST_ID,
                    skin = SkinStyle.CYBERPUNK
                ) {
                    val keyboardController = LocalSoftwareKeyboardController.current
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_text_input")
                    )
                    androidx.compose.material3.Button(
                        onClick = { keyboardController?.show() },
                        modifier = Modifier.testTag("force_show_os_ime_btn")
                    ) {
                        androidx.compose.material3.Text("Show")
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("test_text_input").performClick()
        composeTestRule.waitForIdle()

        assertFalse("External OS IME must never receive startInputMethod!", externalImeRequested)
        assertTrue(InAppKeyboardManager.blockedExternalImeCount.value > initialBlockedCount)
        composeTestRule.onNodeWithTag("in_app_keyboard_root").assertIsDisplayed()

        // Calling LocalSoftwareKeyboardController.show() must increment blocked count & keep using in-app keyboard
        val countBeforeBtn = InAppKeyboardManager.blockedExternalImeCount.value
        composeTestRule.onNodeWithTag("force_show_os_ime_btn").performClick()
        composeTestRule.waitForIdle()

        assertFalse(externalImeRequested)
        assertEquals(countBeforeBtn + 1, InAppKeyboardManager.blockedExternalImeCount.value)
    }

    @Test
    fun `multi-language EN AZ RU typing symbols and backspace mutate field in-process`() {
        var textValue by mutableStateOf("")

        composeTestRule.setContent {
            InAppKeyboardHost(
                hostId = InAppKeyboardManager.MAIN_HOST_ID,
                skin = SkinStyle.CYBERPUNK
            ) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("multi_lang_field")
                )
            }
        }

        composeTestRule.onNodeWithTag("multi_lang_field").performClick()
        composeTestRule.waitForIdle()

        // English QWERTY input
        composeTestRule.onNodeWithTag("kbd_char_h").performClick()
        composeTestRule.onNodeWithTag("kbd_char_i").performClick()
        composeTestRule.waitForIdle()
        assertEquals("hi", textValue)

        // Switch EN -> AZ and press Azerbaijani 'ə'
        composeTestRule.onNodeWithTag("kbd_key_language").performClick()
        composeTestRule.waitForIdle()
        assertEquals(KeyboardLanguage.AZ, InAppKeyboardManager.activeLanguage.value)
        composeTestRule.onNodeWithTag("kbd_char_ə").performClick()
        composeTestRule.waitForIdle()
        assertEquals("hiə", textValue)

        // Switch AZ -> RU and press Russian 'й'
        composeTestRule.onNodeWithTag("kbd_key_language").performClick()
        composeTestRule.waitForIdle()
        assertEquals(KeyboardLanguage.RU, InAppKeyboardManager.activeLanguage.value)
        composeTestRule.onNodeWithTag("kbd_char_й").performClick()
        composeTestRule.waitForIdle()
        assertEquals("hiəй", textValue)

        // Repeatable Backspace deletes 1 character
        composeTestRule.onNodeWithTag("kbd_key_backspace").performTouchInput { click() }
        composeTestRule.waitForIdle()
        assertEquals("hiə", textValue)

        // Symbols mode types '@'
        composeTestRule.onNodeWithTag("kbd_key_toggle_symbols").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("kbd_char_@").performClick()
        composeTestRule.waitForIdle()
        assertEquals("hiə@", textValue)
    }

    @Test
    fun `NumberKeyboard auto-switches between NUMERIC DECIMAL and PHONE modes and supports scramble`() {
        var numValue by mutableStateOf("")
        var decValue by mutableStateOf("")
        var phoneValue by mutableStateOf("")

        composeTestRule.setContent {
            InAppKeyboardHost(
                hostId = InAppKeyboardManager.MAIN_HOST_ID,
                skin = SkinStyle.CYBERPUNK
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = numValue,
                        onValueChange = { numValue = it },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.testTag("num_field")
                    )
                    OutlinedTextField(
                        value = decValue,
                        onValueChange = { decValue = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.testTag("dec_field")
                    )
                    OutlinedTextField(
                        value = phoneValue,
                        onValueChange = { phoneValue = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.testTag("phone_field")
                    )
                }
            }
        }

        // 1. Numeric field
        composeTestRule.onNodeWithTag("num_field").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(KeyboardLayoutMode.NUMERIC, InAppKeyboardManager.layoutMode.value)
        composeTestRule.onNodeWithTag("kbd_scramble_digits_toggle").performClick()
        composeTestRule.waitForIdle()
        assertTrue(InAppKeyboardManager.isScrambleDigits.value)
        composeTestRule.onNodeWithTag("kbd_num_7").performClick()
        composeTestRule.waitForIdle()
        assertEquals("7", numValue)

        // 2. Decimal field
        composeTestRule.onNodeWithTag("dec_field").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(KeyboardLayoutMode.DECIMAL, InAppKeyboardManager.layoutMode.value)
        composeTestRule.onNodeWithTag("kbd_num_4").performClick()
        composeTestRule.onNodeWithTag("kbd_num_.").performClick()
        composeTestRule.onNodeWithTag("kbd_num_2").performClick()
        composeTestRule.waitForIdle()
        assertEquals("4.2", decValue)

        // 3. Phone field
        composeTestRule.onNodeWithTag("phone_field").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(KeyboardLayoutMode.PHONE, InAppKeyboardManager.layoutMode.value)
        composeTestRule.onNodeWithTag("kbd_num_+").performClick()
        composeTestRule.onNodeWithTag("kbd_num_1").performClick()
        composeTestRule.waitForIdle()
        assertEquals("+1", phoneValue)
    }

    @Test
    fun `hardenWindowAgainstInputLeaks disables OS soft input and Autofill on window`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        hardenWindowAgainstInputLeaks(activity.window, applyScreenCaptureGuard = true)

        val softInputMode = activity.window.attributes.softInputMode
        assertTrue(
            "SOFT_INPUT_STATE_ALWAYS_HIDDEN must be set",
            (softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE) ==
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        assertTrue(
            "SOFT_INPUT_ADJUST_NOTHING must be set",
            (softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST) ==
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertEquals(
                View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,
                activity.window.decorView.importantForAutofill
            )
        }
    }

    @Test
    fun `SimplexViewModel lock and triggerPanicWipe clear active keyboard target and hide keyboard`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = SimplexViewModel(app)

        var typedValue by mutableStateOf("")
        composeTestRule.setContent {
            InAppKeyboardHost(
                hostId = InAppKeyboardManager.MAIN_HOST_ID,
                skin = SkinStyle.CYBERPUNK
            ) {
                OutlinedTextField(
                    value = typedValue,
                    onValueChange = { typedValue = it },
                    modifier = Modifier.testTag("lock_clear_field")
                )
            }
        }

        composeTestRule.onNodeWithTag("lock_clear_field").performClick()
        composeTestRule.waitForIdle()
        assertTrue(InAppKeyboardManager.isKeyboardVisible.value)

        viewModel.lock()
        composeTestRule.waitForIdle()
        assertFalse(InAppKeyboardManager.isKeyboardVisible.value)
        assertNull(InAppKeyboardManager.activeTarget.value)

        // Re-focus and trigger panic wipe
        composeTestRule.onNodeWithTag("lock_clear_field").performClick()
        composeTestRule.waitForIdle()
        assertTrue(InAppKeyboardManager.isKeyboardVisible.value)

        viewModel.triggerPanicWipe()
        composeTestRule.waitForIdle()
        assertFalse(InAppKeyboardManager.isKeyboardVisible.value)
        assertNull(InAppKeyboardManager.activeTarget.value)
    }

    @Test
    fun `InAppKeyboard and bottom Done key remain strictly inside viewport bounds under 1_20x density`() {
        var textValue by mutableStateOf("")

        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = base.density * 1.20f,
                    fontScale = base.fontScale * 1.25f
                )
            ) {
                InAppKeyboardHost(
                    hostId = InAppKeyboardManager.MAIN_HOST_ID,
                    skin = SkinStyle.CYBERPUNK
                ) {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scaled_viewport_field")
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("scaled_viewport_field").performClick()
        composeTestRule.waitForIdle()

        val rootBounds = composeTestRule.onRoot().getUnclippedBoundsInRoot()
        val kbdBounds = composeTestRule.onNodeWithTag("in_app_keyboard_root").getUnclippedBoundsInRoot()
        val doneBounds = composeTestRule.onNodeWithTag("kbd_key_done").getUnclippedBoundsInRoot()
        val spaceBounds = composeTestRule.onNodeWithTag("kbd_key_space").getUnclippedBoundsInRoot()

        assertTrue("Keyboard top (${kbdBounds.top}) must be >= 0", kbdBounds.top >= rootBounds.top)
        assertTrue("Keyboard bottom (${kbdBounds.bottom}) must stay <= root bottom (${rootBounds.bottom})", kbdBounds.bottom <= rootBounds.bottom)
        assertTrue("Done key bottom (${doneBounds.bottom}) must stay <= root bottom (${rootBounds.bottom})", doneBounds.bottom <= rootBounds.bottom)
        assertTrue("Space key bottom (${spaceBounds.bottom}) must stay <= root bottom (${rootBounds.bottom})", spaceBounds.bottom <= rootBounds.bottom)

        // Verify 12-key Russian layout also stays inside horizontal bounds
        composeTestRule.onNodeWithTag("kbd_key_language").performClick() // AZ
        composeTestRule.onNodeWithTag("kbd_key_language").performClick() // RU
        composeTestRule.waitForIdle()
        val rightEdgeKeyBounds = composeTestRule.onNodeWithTag("kbd_char_ъ").getUnclippedBoundsInRoot()
        assertTrue("Rightmost RU key (${rightEdgeKeyBounds.right}) must fit within root width (${rootBounds.right})", rightEdgeKeyBounds.right <= rootBounds.right)

        // Verify 5-row PHONE mode stays inside vertical bounds
        composeTestRule.onNodeWithTag("kbd_mode_phone").performClick()
        composeTestRule.waitForIdle()
        val phoneDoneBounds = composeTestRule.onNodeWithTag("kbd_num_done").getUnclippedBoundsInRoot()
        assertTrue("Phone Done key bottom (${phoneDoneBounds.bottom}) must stay <= root bottom (${rootBounds.bottom})", phoneDoneBounds.bottom <= rootBounds.bottom)

        // Confirm Hide button shares the compact single top bar alongside mode toggles (no separate banner header)
        val hideBounds = composeTestRule.onNodeWithTag("kbd_hide_button").getUnclippedBoundsInRoot()
        val modeBounds = composeTestRule.onNodeWithTag("kbd_mode_text").getUnclippedBoundsInRoot()
        assertTrue(
            "Mode toggles and Hide button must occupy the same compact header row",
            kotlin.math.abs((hideBounds.top + hideBounds.bottom).value / 2f - (modeBounds.top + modeBounds.bottom).value / 2f) < 16f
        )
    }

    @Test
    fun `SecureInAppDialogHost keeps InAppKeyboard visible and within dialog bounds`() {
        var alias by mutableStateOf("")

        composeTestRule.setContent {
            SecureInAppDialogHost(
                hostId = "test_secure_dialog",
                skin = SkinStyle.CYBERPUNK,
                onDismissRequest = {}
            ) {
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_alias_field")
                )
            }
        }

        composeTestRule.onNodeWithTag("dialog_alias_field").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("in_app_keyboard_root").assertIsDisplayed()
        composeTestRule.onNodeWithTag("kbd_key_done").assertIsDisplayed()
        composeTestRule.onNodeWithTag("kbd_char_a").performClick()
        composeTestRule.waitForIdle()
        assertEquals("a", alias)
    }
}

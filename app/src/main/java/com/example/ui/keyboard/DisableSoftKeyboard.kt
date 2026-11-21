package com.example.ui.keyboard

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.window.DialogWindowProvider
import com.example.crypto.ScreenCapturePreventionManager
import kotlinx.coroutines.awaitCancellation

/**
 * Forces Android's InputMethodManager to dismiss any external/system IME (e.g., Gboard)
 * attached to the given View's window token.
 */
fun forceHideSystemIme(view: View?) {
    if (view == null) return
    try {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    } catch (_: Exception) {
        // Safe no-op in headless/test environments
    }
}

/**
 * Hardens an Activity or Dialog Window against:
 * 1) External system keyboard (Gboard) popping up via WindowManager softInputMode
 * 2) Android Autofill / Password Manager services sniffing text fields
 * 3) Screen capture leaks via FLAG_SECURE when enabled
 */
fun hardenWindowAgainstInputLeaks(
    window: Window?,
    applyScreenCaptureGuard: Boolean = true,
    isDialogWindow: Boolean = false
) {
    if (window == null) return
    try {
        if (isDialogWindow) {
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            window.setGravity(Gravity.FILL)
            window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        if (applyScreenCaptureGuard) {
            ScreenCapturePreventionManager.applyProtectionToWindow(window)
        }
    } catch (_: Exception) {
        // Safe no-op on restricted test windows
    }
}

private fun Context.findHostActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun findNearestWindowWithDialogFlag(view: View): Pair<Window?, Boolean> {
    var parent = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) {
            return parent.window to true
        }
        parent = parent.parent
    }
    return view.context.findHostActivity()?.window to false
}

/**
 * DisableSoftKeyboard (Zero-Trust OS IME Firewall)
 *
 * Intercepts all Jetpack Compose text-input sessions using PlatformTextInputInterceptor
 * (InterceptPlatformTextInput) and deliberately NEVER delegates to `nextHandler`.
 * As a result:
 * - External keyboards (Gboard, third-party IMEs) NEVER receive InputConnection, EditorInfo, or keystrokes.
 * - LocalSoftwareKeyboardController.show() cannot raise the system keyboard.
 * - Autofill and window soft input mode are locked down at the Android Window layer.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DisableSoftKeyboard(
    hostId: String = InAppKeyboardManager.MAIN_HOST_ID,
    blockExternalIme: Boolean = true,
    content: @Composable () -> Unit
) {
    val view = LocalView.current

    // Harden the current Window (Activity or Dialog) against OS IME, Autofill, and inset drift
    DisposableEffect(view, blockExternalIme) {
        if (blockExternalIme) {
            val (window, isDialog) = findNearestWindowWithDialogFlag(view)
            hardenWindowAgainstInputLeaks(
                window = window,
                applyScreenCaptureGuard = true,
                isDialogWindow = isDialog
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
            forceHideSystemIme(view)
        }
        onDispose { }
    }

    val currentSessionTarget = remember(hostId) { androidx.compose.runtime.mutableStateOf<InAppInputTarget?>(null) }

    val hardenedKeyboardController = remember(view, hostId, blockExternalIme) {
        object : SoftwareKeyboardController {
            override fun show() {
                if (blockExternalIme) {
                    forceHideSystemIme(view)
                    InAppKeyboardManager.recordBlockedExternalImeAttempt()
                    if (InAppKeyboardManager.activeTarget.value == null) {
                        currentSessionTarget.value?.let { InAppKeyboardManager.registerTarget(it) }
                    }
                    InAppKeyboardManager.showKeyboard()
                }
            }

            override fun hide() {
                forceHideSystemIme(view)
                InAppKeyboardManager.hideKeyboard()
            }
        }
    }

    val platformInterceptor = remember(view, hostId, blockExternalIme) {
        PlatformTextInputInterceptor { request, nextHandler ->
            if (!blockExternalIme) {
                nextHandler.startInputMethod(request)
            } else {
                // CRITICAL SECURITY BOUNDARY:
                // Never forward to `nextHandler.startInputMethod(request)`.
                // Gboard / Android InputMethodManager is completely severed from this session.
                InAppKeyboardManager.recordBlockedExternalImeAttempt()
                forceHideSystemIme(view)

                val editorInfo = EditorInfo()
                val inputConnection = request.createInputConnection(editorInfo)
                val target = InputConnectionTarget(
                    hostId = hostId,
                    inputConnection = inputConnection,
                    editorInfo = editorInfo
                )
                currentSessionTarget.value = target
                InAppKeyboardManager.registerTarget(target)
                try {
                    awaitCancellation()
                } finally {
                    if (currentSessionTarget.value === target) {
                        currentSessionTarget.value = null
                    }
                    InAppKeyboardManager.unregisterTarget(target)
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalSoftwareKeyboardController provides hardenedKeyboardController
    ) {
        InterceptPlatformTextInput(interceptor = platformInterceptor) {
            content()
        }
    }
}

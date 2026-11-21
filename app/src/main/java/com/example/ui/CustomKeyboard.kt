package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.keyboard.InAppKeyboard
import com.example.ui.keyboard.InAppKeyboardManager

/**
 * Backward-compatible alias delegating to the hardened Zero-Trust InAppKeyboardManager.
 */
object CustomKeyboardManager {
    val isKeyboardVisible = InAppKeyboardManager.isKeyboardVisible

    fun hideKeyboard() = InAppKeyboardManager.hideKeyboard()
    fun wipeAndHide() = InAppKeyboardManager.wipeAndHide()
    fun sendText(text: String) = InAppKeyboardManager.sendText(text)
    fun sendBackspace() = InAppKeyboardManager.sendBackspace()
    fun sendDone() = InAppKeyboardManager.sendDone()
    fun clearText() = InAppKeyboardManager.clearText()
}

/**
 * Backward-compatible composable delegating to InAppKeyboard.
 */
@Composable
fun CustomKeyboardView(
    skin: SkinStyle,
    hostId: String = InAppKeyboardManager.MAIN_HOST_ID,
    modifier: Modifier = Modifier
) {
    InAppKeyboard(
        skin = skin,
        hostId = hostId,
        modifier = modifier
    )
}

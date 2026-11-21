package com.example.ui.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteSurroundingTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Modular In-App Keyboard layout types inspired by PerfectFriend/in-app-keyboard.
 */
enum class KeyboardLayoutMode(val shortLabel: String) {
    TEXT("ABC"),
    NUMERIC("123"),
    DECIMAL("0.0"),
    PHONE("TEL"),
    SYMBOLS("#+=")
}

/**
 * Supported multi-language QWERTY layouts (EN / AZ / RU) matching PerfectFriend/in-app-keyboard.
 */
enum class KeyboardLanguage(val code: String, val displayName: String) {
    EN("EN", "English"),
    AZ("AZ", "Azərbaycan"),
    RU("RU", "Русский");

    fun next(): KeyboardLanguage {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }

    companion object {
        fun fromSystemLocale(): KeyboardLanguage {
            return when (Locale.getDefault().language.lowercase()) {
                "ru" -> RU
                "az" -> AZ
                else -> EN
            }
        }
    }
}

/**
 * Internal zero-trust bridge between the in-app keyboard UI and the active Compose text field.
 * All text edits stay strictly inside the application process memory and never traverse Android IME IPC.
 */
interface InAppInputTarget {
    val hostId: String
    val layoutModeHint: KeyboardLayoutMode
    val isSensitive: Boolean

    fun sendText(text: String)
    fun sendBackspace()
    fun clearText()
    fun sendDone()
}

/**
 * Direct InputConnection target captured via Compose PlatformTextInputInterceptor.
 */
class InputConnectionTarget(
    override val hostId: String,
    private val inputConnection: InputConnection,
    private val editorInfo: EditorInfo
) : InAppInputTarget {

    override val layoutModeHint: KeyboardLayoutMode = detectLayoutMode(editorInfo.inputType)
    override val isSensitive: Boolean = detectSensitiveField(editorInfo.inputType)

    override fun sendText(text: String) {
        inputConnection.commitText(text, 1)
    }

    override fun sendBackspace() {
        val selected = try {
            inputConnection.getSelectedText(0)
        } catch (_: Exception) {
            null
        }
        if (!selected.isNullOrEmpty()) {
            inputConnection.commitText("", 1)
        } else {
            inputConnection.deleteSurroundingText(1, 0)
        }
    }

    override fun clearText() {
        val beforeLen = try {
            inputConnection.getTextBeforeCursor(2048, 0)?.length ?: 0
        } catch (_: Exception) {
            0
        }
        val afterLen = try {
            inputConnection.getTextAfterCursor(2048, 0)?.length ?: 0
        } catch (_: Exception) {
            0
        }
        if (beforeLen > 0 || afterLen > 0) {
            inputConnection.deleteSurroundingText(beforeLen, afterLen)
        }
    }

    override fun sendDone() {
        val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        val resolvedAction = if (action == EditorInfo.IME_ACTION_UNSPECIFIED || action == EditorInfo.IME_ACTION_NONE) {
            EditorInfo.IME_ACTION_DONE
        } else {
            action
        }
        inputConnection.performEditorAction(resolvedAction)
    }

    companion object {
        fun detectLayoutMode(inputType: Int): KeyboardLayoutMode {
            val clazz = inputType and InputType.TYPE_MASK_CLASS
            return when (clazz) {
                InputType.TYPE_CLASS_PHONE -> KeyboardLayoutMode.PHONE
                InputType.TYPE_CLASS_NUMBER -> {
                    if ((inputType and InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0) {
                        KeyboardLayoutMode.DECIMAL
                    } else {
                        KeyboardLayoutMode.NUMERIC
                    }
                }
                else -> KeyboardLayoutMode.TEXT
            }
        }

        fun detectSensitiveField(inputType: Int): Boolean {
            val clazz = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            return when (clazz) {
                InputType.TYPE_CLASS_TEXT -> {
                    variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                }
                InputType.TYPE_CLASS_NUMBER -> {
                    variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                }
                else -> false
            }
        }
    }
}

/**
 * Legacy EditCommand-based session adapter retained for direct tests or custom input hosts.
 */
class LegacyInputTarget(
    override val hostId: String,
    val initialValue: TextFieldValue,
    val imeOptions: ImeOptions,
    val onEditCommand: (List<EditCommand>) -> Unit,
    val onImeActionPerformed: (ImeAction) -> Unit
) : InAppInputTarget {

    var currentValue: TextFieldValue = initialValue
        private set

    override val layoutModeHint: KeyboardLayoutMode = when (imeOptions.keyboardType) {
        KeyboardType.Number, KeyboardType.NumberPassword -> KeyboardLayoutMode.NUMERIC
        KeyboardType.Decimal -> KeyboardLayoutMode.DECIMAL
        KeyboardType.Phone -> KeyboardLayoutMode.PHONE
        else -> KeyboardLayoutMode.TEXT
    }

    override val isSensitive: Boolean =
        imeOptions.keyboardType == KeyboardType.Password ||
            imeOptions.keyboardType == KeyboardType.NumberPassword

    fun updateValue(value: TextFieldValue) {
        currentValue = value
    }

    override fun sendText(text: String) {
        onEditCommand(listOf(CommitTextCommand(text, 1)))
    }

    override fun sendBackspace() {
        val selection = currentValue.selection
        if (!selection.collapsed) {
            onEditCommand(listOf(CommitTextCommand("", 1)))
        } else {
            onEditCommand(listOf(DeleteSurroundingTextCommand(1, 0)))
        }
    }

    override fun clearText() {
        onEditCommand(
            listOf(
                DeleteSurroundingTextCommand(currentValue.text.length, currentValue.text.length),
                CommitTextCommand("", 1)
            )
        )
    }

    override fun sendDone() {
        onImeActionPerformed(imeOptions.imeAction)
    }
}

/**
 * Central Zero-Trust Keyboard Controller.
 * Guarantees that zero typed characters, suggestions, or input connections ever leave the app boundary.
 */
object InAppKeyboardManager {
    const val MAIN_HOST_ID = "main_window"

    private val _activeTarget = MutableStateFlow<InAppInputTarget?>(null)
    val activeTarget: StateFlow<InAppInputTarget?> = _activeTarget.asStateFlow()

    private val _activeHostId = MutableStateFlow<String?>(null)
    val activeHostId: StateFlow<String?> = _activeHostId.asStateFlow()

    private val _isKeyboardVisible = MutableStateFlow(false)
    val isKeyboardVisible: StateFlow<Boolean> = _isKeyboardVisible.asStateFlow()

    private val _layoutMode = MutableStateFlow(KeyboardLayoutMode.TEXT)
    val layoutMode: StateFlow<KeyboardLayoutMode> = _layoutMode.asStateFlow()

    private val _activeLanguage = MutableStateFlow(KeyboardLanguage.fromSystemLocale())
    val activeLanguage: StateFlow<KeyboardLanguage> = _activeLanguage.asStateFlow()

    private val _isSensitiveField = MutableStateFlow(false)
    val isSensitiveField: StateFlow<Boolean> = _isSensitiveField.asStateFlow()

    private val _isScrambleDigits = MutableStateFlow(false)
    val isScrambleDigits: StateFlow<Boolean> = _isScrambleDigits.asStateFlow()

    private val _blockedExternalImeCount = MutableStateFlow(0)
    val blockedExternalImeCount: StateFlow<Int> = _blockedExternalImeCount.asStateFlow()

    fun registerTarget(target: InAppInputTarget) {
        _activeTarget.value = target
        _activeHostId.value = target.hostId
        _layoutMode.value = target.layoutModeHint
        _isSensitiveField.value = target.isSensitive
        _isKeyboardVisible.value = true
    }

    fun unregisterTarget(target: InAppInputTarget) {
        if (_activeTarget.value === target) {
            _activeTarget.value = null
            _activeHostId.value = null
            _isSensitiveField.value = false
            _isKeyboardVisible.value = false
        }
    }

    fun recordBlockedExternalImeAttempt() {
        _blockedExternalImeCount.value = _blockedExternalImeCount.value + 1
    }

    fun showKeyboard() {
        if (_activeTarget.value != null) {
            _isKeyboardVisible.value = true
        }
    }

    fun hideKeyboard() {
        _isKeyboardVisible.value = false
    }

    /**
     * Zero-trust wipe: disconnects active input targets immediately (e.g., on app lock or panic wipe).
     */
    fun wipeAndHide() {
        _isKeyboardVisible.value = false
        _activeTarget.value = null
        _activeHostId.value = null
        _isSensitiveField.value = false
    }

    fun setLayoutMode(mode: KeyboardLayoutMode) {
        _layoutMode.value = mode
    }

    fun setLanguage(language: KeyboardLanguage) {
        _activeLanguage.value = language
    }

    fun cycleLanguage() {
        _activeLanguage.value = _activeLanguage.value.next()
    }

    fun toggleScrambleDigits() {
        _isScrambleDigits.value = !_isScrambleDigits.value
    }

    fun sendText(text: String) {
        _activeTarget.value?.sendText(text)
    }

    fun sendBackspace() {
        _activeTarget.value?.sendBackspace()
    }

    fun clearText() {
        _activeTarget.value?.clearText()
    }

    fun sendDone() {
        _activeTarget.value?.sendDone()
        hideKeyboard()
    }
}

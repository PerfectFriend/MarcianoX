package com.example.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.LocalizationHelper
import com.example.ui.SkinStyle

/**
 * TextKeyboard (Modular Multi-Language QWERTY & Symbols Layout)
 *
 * Implements the EN / AZ / RU keyboard layouts from PerfectFriend/in-app-keyboard
 * without external dictionaries, predictive logging, or system IME forwarding.
 */
@Composable
fun TextKeyboard(
    language: KeyboardLanguage,
    isSymbolsMode: Boolean,
    skin: SkinStyle,
    isSensitive: Boolean,
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onCycleLanguage: () -> Unit,
    onToggleSymbols: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: androidx.compose.ui.unit.Dp = 38.dp
) {
    var isShiftActive by remember { mutableStateOf(false) }
    var isCapsLock by remember { mutableStateOf(false) }
    var symbolPage by remember { mutableStateOf(0) }

    val enRows = remember {
        listOf(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("z", "x", "c", "v", "b", "n", "m")
        )
    }

    // Standard Azerbaijani layout with ə, ğ, ı, ö, ş, ü, ç
    val azRows = remember {
        listOf(
            listOf("q", "ü", "e", "r", "t", "y", "u", "i", "o", "p", "ö", "ğ"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ı", "ə"),
            listOf("z", "x", "c", "v", "b", "n", "m", "ç", "ş")
        )
    }

    // Standard Russian ЙЦУКЕН layout
    val ruRows = remember {
        listOf(
            listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ"),
            listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
            listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю")
        )
    }

    val symbolPage0 = remember {
        listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("@", "#", "$", "%", "&", "-", "+", "(", ")"),
            listOf("*", "\"", "'", ":", ";", "!", "?")
        )
    }

    val symbolPage1 = remember {
        listOf(
            listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶"),
            listOf("{", "}", "[", "]", "<", ">", "^", "\\", "/"),
            listOf("_", "=", "€", "£", "¥", "¢", "§")
        )
    }

    val rows = if (isSymbolsMode) {
        if (symbolPage == 0) symbolPage0 else symbolPage1
    } else {
        when (language) {
            KeyboardLanguage.EN -> enRows
            KeyboardLanguage.AZ -> azRows
            KeyboardLanguage.RU -> ruRows
        }
    }

    val compactFont = if (!isSymbolsMode && (language == KeyboardLanguage.AZ || language == KeyboardLanguage.RU)) {
        12.sp
    } else {
        14.sp
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEachIndexed { rowIndex, rowChars ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (rowIndex == 2) {
                    if (isSymbolsMode) {
                        KeyButton(
                            label = if (symbolPage == 0) "=\\<" else "123",
                            skin = skin,
                            height = keyHeight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            backgroundColor = skin.cardColor.copy(alpha = 0.72f),
                            contentColor = skin.accentColor,
                            isSensitive = isSensitive,
                            onClick = { symbolPage = if (symbolPage == 0) 1 else 0 },
                            modifier = Modifier.weight(1.4f),
                            testTagName = "kbd_key_symbol_page"
                        )
                    } else {
                        val shiftActive = isShiftActive || isCapsLock
                        KeyButton(
                            label = if (isCapsLock) "⇪" else "⇧",
                            skin = skin,
                            height = keyHeight,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            backgroundColor = when {
                                isCapsLock -> skin.accentColor
                                isShiftActive -> skin.primaryColor
                                else -> skin.cardColor.copy(alpha = 0.72f)
                            },
                            contentColor = if (shiftActive) skin.backgroundColor else skin.textColor,
                            isSensitive = isSensitive,
                            onClick = {
                                if (isShiftActive) {
                                    isCapsLock = true
                                    isShiftActive = false
                                } else if (isCapsLock) {
                                    isCapsLock = false
                                    isShiftActive = false
                                } else {
                                    isShiftActive = true
                                }
                            },
                            modifier = Modifier.weight(1.35f),
                            testTagName = "kbd_key_shift"
                        )
                    }
                }

                rowChars.forEach { rawChar ->
                    val displayChar = if (!isSymbolsMode && (isShiftActive || isCapsLock)) {
                        rawChar.uppercase()
                    } else {
                        rawChar
                    }
                    KeyButton(
                        label = displayChar,
                        skin = skin,
                        height = keyHeight,
                        fontSize = compactFont,
                        isSensitive = isSensitive,
                        onClick = {
                            onKeyPress(displayChar)
                            if (isShiftActive && !isCapsLock) {
                                isShiftActive = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        testTagName = "kbd_char_$rawChar"
                    )
                }

                if (rowIndex == 2) {
                    RepeatableBackspaceKeyButton(
                        skin = skin,
                        height = keyHeight,
                        onBackspace = onBackspace,
                        modifier = Modifier.weight(1.35f)
                    )
                }
            }
        }

        // Bottom Control Row: ?123/ABC | Language (EN/AZ/RU) | Spacebar | . | DONE
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 1.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            KeyButton(
                label = if (isSymbolsMode) "ABC" else "?123",
                skin = skin,
                height = keyHeight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                backgroundColor = skin.cardColor.copy(alpha = 0.72f),
                isSensitive = isSensitive,
                onClick = {
                    symbolPage = 0
                    onToggleSymbols()
                },
                modifier = Modifier.weight(1.45f),
                testTagName = "kbd_key_toggle_symbols"
            )

            KeyButton(
                label = language.code,
                skin = skin,
                height = keyHeight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                backgroundColor = skin.cardColor.copy(alpha = 0.72f),
                contentColor = skin.primaryColor,
                isSensitive = isSensitive,
                onClick = onCycleLanguage,
                modifier = Modifier.weight(1.25f),
                testTagName = "kbd_key_language"
            )

            KeyButton(
                label = language.displayName,
                skin = skin,
                height = keyHeight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                backgroundColor = skin.cardColor.copy(alpha = 0.92f),
                contentColor = skin.textColor.copy(alpha = 0.55f),
                isSensitive = isSensitive,
                onClick = { onKeyPress(" ") },
                modifier = Modifier.weight(4.2f),
                testTagName = "kbd_key_space"
            )

            KeyButton(
                label = ".",
                skin = skin,
                height = keyHeight,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                isSensitive = isSensitive,
                onClick = { onKeyPress(".") },
                modifier = Modifier.weight(1f),
                testTagName = "kbd_key_dot"
            )

            KeyButton(
                label = LocalizationHelper.get("kbd_done"),
                skin = skin,
                height = keyHeight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                backgroundColor = skin.primaryColor,
                contentColor = skin.backgroundColor,
                borderColor = skin.primaryColor,
                isSensitive = isSensitive,
                onClick = onDone,
                modifier = Modifier.weight(1.85f),
                testTagName = "kbd_key_done"
            )
        }
    }
}

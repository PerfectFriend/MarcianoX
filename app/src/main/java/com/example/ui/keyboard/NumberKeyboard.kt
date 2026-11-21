package com.example.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.LocalizationHelper
import com.example.ui.SkinStyle

/**
 * NumberKeyboard (Modular Numeric / Decimal / Phone Pad Layout)
 *
 * Implements the dedicated numeric, decimal, and phone input layouts from
 * PerfectFriend/in-app-keyboard, plus optional local digit scrambling.
 */
@Composable
fun NumberKeyboard(
    mode: KeyboardLayoutMode,
    isScrambled: Boolean,
    skin: SkinStyle,
    isSensitive: Boolean,
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSwitchToText: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: androidx.compose.ui.unit.Dp = 38.dp
) {
    val digits = remember(isScrambled) {
        if (isScrambled) {
            (0..9).map { it.toString() }.shuffled()
        } else {
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        }
    }

    val rows = remember(mode, digits) {
        when (mode) {
            KeyboardLayoutMode.DECIMAL -> listOf(
                listOf(digits[0], digits[1], digits[2]),
                listOf(digits[3], digits[4], digits[5]),
                listOf(digits[6], digits[7], digits[8]),
                listOf(".", digits[9], "BACK")
            )
            KeyboardLayoutMode.PHONE -> listOf(
                listOf(digits[0], digits[1], digits[2]),
                listOf(digits[3], digits[4], digits[5]),
                listOf(digits[6], digits[7], digits[8]),
                listOf("+", digits[9], "*"),
                listOf("#", "-", "BACK")
            )
            else -> listOf(
                listOf(digits[0], digits[1], digits[2]),
                listOf(digits[3], digits[4], digits[5]),
                listOf(digits[6], digits[7], digits[8]),
                listOf("CLR", digits[9], "BACK")
            )
        }
    }

    val effectiveRowHeight = if (mode == KeyboardLayoutMode.PHONE) {
        (keyHeight - 5.dp).coerceAtLeast(30.dp)
    } else {
        keyHeight
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                rowKeys.forEach { key ->
                    when (key) {
                        "BACK" -> {
                            RepeatableBackspaceKeyButton(
                                skin = skin,
                                onBackspace = onBackspace,
                                height = effectiveRowHeight,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        "CLR" -> {
                            KeyButton(
                                label = "CLR",
                                skin = skin,
                                height = effectiveRowHeight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                backgroundColor = Color(0xFFFF3333).copy(alpha = 0.16f),
                                contentColor = Color(0xFFFF6666),
                                borderColor = Color(0xFFFF3333).copy(alpha = 0.45f),
                                isSensitive = isSensitive,
                                onClick = onClear,
                                modifier = Modifier.weight(1f),
                                testTagName = "kbd_num_clear"
                            )
                        }
                        else -> {
                            KeyButton(
                                label = key,
                                skin = skin,
                                height = effectiveRowHeight,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                isSensitive = isSensitive,
                                onClick = { onKeyPress(key) },
                                modifier = Modifier.weight(1f),
                                testTagName = "kbd_num_$key"
                            )
                        }
                    }
                }
            }
        }

        // Bottom Action Row: ABC | DONE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyButton(
                label = "ABC",
                skin = skin,
                height = effectiveRowHeight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                backgroundColor = skin.cardColor.copy(alpha = 0.72f),
                isSensitive = isSensitive,
                onClick = onSwitchToText,
                modifier = Modifier.weight(1f),
                testTagName = "kbd_num_to_abc"
            )
            KeyButton(
                label = LocalizationHelper.get("kbd_done"),
                skin = skin,
                height = effectiveRowHeight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                backgroundColor = skin.primaryColor,
                contentColor = skin.backgroundColor,
                borderColor = skin.primaryColor,
                isSensitive = isSensitive,
                onClick = onDone,
                modifier = Modifier.weight(2f),
                testTagName = "kbd_num_done"
            )
        }
    }
}

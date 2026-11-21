package com.example.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.SkinStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * KeyButton (Zero-Trust Secure Key Component)
 *
 * Designed to eliminate side-channel keystroke leakage:
 * - No SoundEffectConstants.CLICK acoustic leakage.
 * - No floating press-preview popup overlay (prevents camera / shoulder-surfing capture).
 * - Optional ripple suppression on sensitive password/PIN fields.
 */
@Composable
fun KeyButton(
    label: String,
    skin: SkinStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 38.dp,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Medium,
    backgroundColor: Color = skin.cardColor.copy(alpha = 0.88f),
    contentColor: Color = skin.textColor,
    borderColor: Color = skin.primaryColor.copy(alpha = 0.22f),
    isSensitive: Boolean = false,
    testTagName: String? = null
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    val effectiveBg = if (isPressed && !isSensitive) {
        skin.primaryColor.copy(alpha = 0.22f)
    } else {
        backgroundColor
    }

    val interactionSource = remember { MutableInteractionSource() }

    val glassBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(
            effectiveBg.copy(alpha = (effectiveBg.alpha + 0.08f).coerceAtMost(1f)),
            effectiveBg.copy(alpha = (effectiveBg.alpha * 0.78f).coerceAtLeast(0.4f))
        )
    )

    Box(
        modifier = modifier
            .height(height)
            .defaultMinSize(minWidth = 0.dp, minHeight = 32.dp)
            .padding(horizontal = 1.5.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(glassBrush)
            .border(0.9.dp, borderColor, RoundedCornerShape(6.dp))
            .then(
                if (isSensitive) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                    )
                } else {
                    Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                }
            )
            .then(if (testTagName != null) Modifier.testTag(testTagName) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

/**
 * Repeatable Backspace KeyButton supporting single tap and continuous long-press deletion
 * without audio feedback leaks.
 */
@Composable
fun RepeatableBackspaceKeyButton(
    skin: SkinStyle,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 38.dp,
    testTagName: String = "kbd_key_backspace"
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .height(height)
            .defaultMinSize(minWidth = 0.dp, minHeight = 32.dp)
            .padding(horizontal = 1.5.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(skin.cardColor.copy(alpha = 0.85f), skin.cardColor.copy(alpha = 0.58f))
                )
            )
            .border(1.dp, skin.accentColor.copy(alpha = 0.42f), RoundedCornerShape(6.dp))
            .pointerInput(onBackspace) {
                detectTapGestures(
                    onPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBackspace()
                        var held = true
                        val job = scope.launch {
                            delay(380)
                            while (held) {
                                onBackspace()
                                delay(90)
                            }
                        }
                        tryAwaitRelease()
                        held = false
                        job.cancel()
                    }
                )
            }
            .testTag(testTagName),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "⌫",
            color = skin.accentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

package com.example.ui.keyboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.ui.LocalizationHelper
import com.example.ui.SkinStyle

/**
 * KeyboardLayout switches between TextKeyboard, Symbols, and NumberKeyboard (Numeric/Decimal/Phone).
 */
@Composable
fun KeyboardLayout(
    layoutMode: KeyboardLayoutMode,
    language: KeyboardLanguage,
    isScrambledDigits: Boolean,
    isSensitive: Boolean,
    skin: SkinStyle,
    keyHeight: Dp = 38.dp,
    modifier: Modifier = Modifier
) {
    when (layoutMode) {
        KeyboardLayoutMode.TEXT, KeyboardLayoutMode.SYMBOLS -> {
            TextKeyboard(
                language = language,
                isSymbolsMode = layoutMode == KeyboardLayoutMode.SYMBOLS,
                skin = skin,
                isSensitive = isSensitive,
                keyHeight = keyHeight,
                onKeyPress = { InAppKeyboardManager.sendText(it) },
                onBackspace = { InAppKeyboardManager.sendBackspace() },
                onCycleLanguage = { InAppKeyboardManager.cycleLanguage() },
                onToggleSymbols = {
                    InAppKeyboardManager.setLayoutMode(
                        if (layoutMode == KeyboardLayoutMode.SYMBOLS) KeyboardLayoutMode.TEXT
                        else KeyboardLayoutMode.SYMBOLS
                    )
                },
                onDone = { InAppKeyboardManager.sendDone() },
                modifier = modifier
            )
        }
        KeyboardLayoutMode.NUMERIC, KeyboardLayoutMode.DECIMAL, KeyboardLayoutMode.PHONE -> {
            NumberKeyboard(
                mode = layoutMode,
                isScrambled = isScrambledDigits,
                skin = skin,
                isSensitive = isSensitive,
                keyHeight = keyHeight,
                onKeyPress = { InAppKeyboardManager.sendText(it) },
                onBackspace = { InAppKeyboardManager.sendBackspace() },
                onClear = { InAppKeyboardManager.clearText() },
                onSwitchToText = { InAppKeyboardManager.setLayoutMode(KeyboardLayoutMode.TEXT) },
                onDone = { InAppKeyboardManager.sendDone() },
                modifier = modifier
            )
        }
    }
}

/**
 * InAppKeyboard (Main Zero-Trust Keyboard Composable)
 *
 * Maximizes screen utility with zero wasted static header text:
 * - Single compact functional strip (mode switchers, conditional digit scrambler,
 *   compact blocked-IME counter badge, and accessible Hide button).
 * - Layered Cyberpunk-Matrix HUD ornament texture backing.
 */
@Composable
fun InAppKeyboard(
    skin: SkinStyle,
    hostId: String = InAppKeyboardManager.MAIN_HOST_ID,
    keyHeight: Dp = 38.dp,
    modifier: Modifier = Modifier
) {
    val isVisible by InAppKeyboardManager.isKeyboardVisible.collectAsState()
    val activeTarget by InAppKeyboardManager.activeTarget.collectAsState()
    val activeHostId by InAppKeyboardManager.activeHostId.collectAsState()
    val layoutMode by InAppKeyboardManager.layoutMode.collectAsState()
    val language by InAppKeyboardManager.activeLanguage.collectAsState()
    val isSensitive by InAppKeyboardManager.isSensitiveField.collectAsState()
    val isScrambledDigits by InAppKeyboardManager.isScrambleDigits.collectAsState()
    val blockedCount by InAppKeyboardManager.blockedExternalImeCount.collectAsState()

    val shouldRenderHere = isVisible && activeTarget != null && activeHostId == hostId

    AnimatedVisibility(
        visible = shouldRenderHere,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier.zIndex(9999f)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            skin.primaryColor.copy(alpha = 0.55f),
                            skin.accentColor.copy(alpha = 0.45f),
                            skin.primaryColor.copy(alpha = 0.55f)
                        )
                    ),
                    shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                )
                .testTag("in_app_keyboard_root"),
            colors = CardDefaults.cardColors(containerColor = skin.backgroundColor.copy(alpha = 0.96f)),
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(id = R.drawable.img_cyber_ornament_frame),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.20f)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 6.dp, end = 6.dp, top = 3.dp, bottom = 5.dp)
                ) {
                    // Single Ultra-Compact Functional Utility Strip (no static filler text)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val modes = listOf(
                            KeyboardLayoutMode.TEXT,
                            KeyboardLayoutMode.NUMERIC,
                            KeyboardLayoutMode.DECIMAL,
                            KeyboardLayoutMode.PHONE
                        )
                        modes.forEach { mode ->
                            val selected = layoutMode == mode ||
                                (mode == KeyboardLayoutMode.TEXT && layoutMode == KeyboardLayoutMode.SYMBOLS)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (selected) skin.primaryColor.copy(alpha = 0.22f)
                                        else skin.cardColor.copy(alpha = 0.72f)
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) skin.primaryColor else skin.textColor.copy(alpha = 0.16f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { InAppKeyboardManager.setLayoutMode(mode) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("kbd_mode_${mode.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.shortLabel,
                                    color = if (selected) skin.primaryColor else skin.textColor.copy(alpha = 0.72f),
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (layoutMode in listOf(
                                KeyboardLayoutMode.NUMERIC,
                                KeyboardLayoutMode.DECIMAL,
                                KeyboardLayoutMode.PHONE
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isScrambledDigits) skin.accentColor.copy(alpha = 0.22f)
                                        else skin.cardColor.copy(alpha = 0.72f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isScrambledDigits) skin.accentColor else skin.textColor.copy(alpha = 0.2f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { InAppKeyboardManager.toggleScrambleDigits() }
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                                    .testTag("kbd_scramble_digits_toggle"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = LocalizationHelper.get("kbd_scramble"),
                                    color = if (isScrambledDigits) skin.accentColor else skin.textColor.copy(alpha = 0.72f),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        if (blockedCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(skin.primaryColor.copy(alpha = 0.14f))
                                    .border(
                                        0.8.dp,
                                        skin.primaryColor.copy(alpha = 0.45f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 3.dp)
                                    .testTag("blocked_ime_counter_badge")
                            ) {
                                Text(
                                    text = "×$blockedCount",
                                    color = skin.primaryColor,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = { InAppKeyboardManager.hideKeyboard() },
                            modifier = Modifier
                                .size(width = 48.dp, height = 32.dp)
                                .testTag("kbd_hide_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Hide In-App Keyboard",
                                tint = skin.primaryColor.copy(alpha = 0.9f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    KeyboardLayout(
                        layoutMode = layoutMode,
                        language = language,
                        isScrambledDigits = isScrambledDigits,
                        isSensitive = isSensitive,
                        skin = skin,
                        keyHeight = keyHeight
                    )
                }
            }
        }
    }
}

/**
 * Host wrapper for Activities and full-screen views that:
 * 1) Installs the DisableSoftKeyboard OS IME firewall
 * 2) Adds dynamic bottom padding matching the active InAppKeyboard height
 * 3) Renders the InAppKeyboard overlay strictly inside the safe container bounds.
 */
@Composable
fun InAppKeyboardHost(
    hostId: String,
    skin: SkinStyle,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isVisible by InAppKeyboardManager.isKeyboardVisible.collectAsState()
    val activeHostId by InAppKeyboardManager.activeHostId.collectAsState()
    val showLocalKeyboard = isVisible && activeHostId == hostId
    var measuredKeyboardHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val bottomPad = if (showLocalKeyboard) {
        if (measuredKeyboardHeightPx > 0) with(density) { measuredKeyboardHeightPx.toDp() } else 230.dp
    } else {
        0.dp
    }

    DisableSoftKeyboard(hostId = hostId) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val adaptiveKeyHeight = if (maxHeight < 440.dp) 30.dp else 38.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomPad)
            ) {
                content()
            }

            InAppKeyboard(
                skin = skin,
                hostId = hostId,
                keyHeight = adaptiveKeyHeight,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { measuredKeyboardHeightPx = it.height }
            )
        }
    }
}

/**
 * Full-screen Dialog overlay container for modals/AlertDialogs containing text fields.
 * Ensures:
 * 1) The Dialog Window fills MATCH_PARENT edge-to-edge and blocks OS IME (Gboard), Autofill, and Screen Capture.
 * 2) safeDrawingPadding keeps both the dialog content and the InAppKeyboard strictly inside visible screen bounds
 *    (never obscured behind system navigation bars).
 * 3) The dialog card shifts smoothly above the keyboard when active.
 */
@Composable
fun SecureInAppDialogHost(
    hostId: String,
    skin: SkinStyle,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isVisible by InAppKeyboardManager.isKeyboardVisible.collectAsState()
    val activeHostId by InAppKeyboardManager.activeHostId.collectAsState()
    val showLocalKeyboard = isVisible && activeHostId == hostId
    var measuredKeyboardHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val bottomPad = if (showLocalKeyboard) {
        if (measuredKeyboardHeightPx > 0) with(density) { measuredKeyboardHeightPx.toDp() } else 230.dp
    } else {
        16.dp
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = {
            InAppKeyboardManager.hideKeyboard()
            onDismissRequest()
        },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        DisableSoftKeyboard(hostId = hostId) {
            BoxWithConstraints(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .safeDrawingPadding()
            ) {
                val adaptiveKeyHeight = if (maxHeight < 440.dp) 30.dp else 38.dp

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = bottomPad
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    content()
                }

                InAppKeyboard(
                    skin = skin,
                    hostId = hostId,
                    keyHeight = adaptiveKeyHeight,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { measuredKeyboardHeightPx = it.height }
                )
            }
        }
    }
}

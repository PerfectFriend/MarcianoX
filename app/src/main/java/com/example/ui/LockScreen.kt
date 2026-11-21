package com.example.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.crypto.BiometricAuthHelper
import com.example.crypto.ScreenCapturePreventionManager
import com.example.crypto.findActivity
import com.example.viewmodel.SetupStep
import com.example.viewmodel.SimplexViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Calculates cryptographic entropy and safety ratings tailored specifically for 6-digit PIN codes.
 */
fun get6DigitPinStrength(pin: String): Pair<String, Color> {
    if (pin.isEmpty()) return "" to Color.Gray
    val isRepetitive = pin.all { it == pin[0] }
    val isSequential = "01234567890".contains(pin) || "9876543210".contains(pin)
    val commonWeakPins = setOf("123456", "654321", "111111", "000000", "121212", "123321", "112233")
    val isCommon = pin in commonWeakPins

    return when {
        isRepetitive -> "WEAK (All identical digits)" to Color(0xFFFF3333)
        isSequential -> "WEAK (Sequential numbers)" to Color(0xFFFF3333)
        isCommon -> "WEAK (Common pattern)" to Color(0xFFFF3333)
        pin.length < 6 -> "INCOMPLETE" to Color(0xFFFFCC00)
        else -> {
            val uniqueDigits = pin.toSet().size
            if (uniqueDigits <= 3) "MEDIUM (Low variety)" to Color(0xFFFFCC00)
            else if (uniqueDigits <= 4) "MEDIUM (Balanced)" to Color(0xFF66FF66)
            else "STRONG (High entropy 6-digit key)" to Color(0xFF00FF66)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LockScreen(viewModel: SimplexViewModel) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val pinBuffer by viewModel.pinBuffer.collectAsState()
    val isSetupState by viewModel.isSetupState.collectAsState()
    val setupStep by viewModel.setupStep.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isPanicWiped by viewModel.isPanicWiped.collectAsState()
    val lockoutSeconds by viewModel.lockoutSecondsRemaining.collectAsState()
    val currentSkin by viewModel.currentSkin.collectAsState()

    // Keypad settings
    var isKeyScrambled by remember { mutableStateOf(false) }
    var isBiometricScanning by remember { mutableStateOf(false) }
    var showCaptureDetailsDialog by remember { mutableStateOf(false) }
    var showPanicConfirmDialog by remember { mutableStateOf(false) }

    // Screen Capture Prevention enforcement state
    val isScreenCaptureEnforced by ScreenCapturePreventionManager.isEnforced.collectAsState()
    val isCaptureBlocked by ScreenCapturePreventionManager.isCaptureBlocked.collectAsState()

    // Ensure FLAG_SECURE is applied to the window while on Lockscreen
    DisposableEffect(activity, isScreenCaptureEnforced) {
        ScreenCapturePreventionManager.applyProtection(activity)
        onDispose {
            // Screen capture protection remains enforced according to global setting
        }
    }

    // Shake animation for error state
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null && !errorMessage!!.contains("Too many failed") && !errorMessage!!.contains("auto-locked") && !errorMessage!!.contains("locked")) {
            shakeOffset.snapTo(0f)
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 350
                    -14f at 50
                    14f at 100
                    -10f at 150
                    10f at 200
                    -6f at 250
                    6f at 300
                    0f at 350
                }
            )
        }
    }

    // Theme palette
    val cyberGreen = currentSkin.primaryColor
    val cyberRed = Color(0xFFFF3333)
    val cyberCyan = currentSkin.accentColor
    val cardOnyx = currentSkin.cardColor

    ThemeBackground(skin = currentSkin) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag("lockscreen_container"),
            contentAlignment = Alignment.Center
        ) {
            if (isPanicWiped) {
                // Emergency Panic Data Clearing Terminal UI
                PanicWipeTerminalView(viewModel = viewModel, cyberRed = cyberRed, cardOnyx = cardOnyx)
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // ==========================================
                    // 1. TOP HEADER & OS CAPTURE SHIELD BADGE
                    // ==========================================
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // OS Screen Capture Prevention status badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isCaptureBlocked) cyberGreen.copy(alpha = 0.14f) else cyberRed.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isCaptureBlocked) cyberGreen.copy(alpha = 0.65f) else cyberRed.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { showCaptureDetailsDialog = true }
                                    .testTag("screen_capture_shield_badge")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Screen Capture Shield",
                                        tint = if (isCaptureBlocked) cyberGreen else cyberRed,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (isCaptureBlocked) "FLAG_SECURE SHIELD" else "CAPTURE UNRESTRICTED",
                                        color = if (isCaptureBlocked) cyberGreen else cyberRed,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Protection Details",
                                        tint = (if (isCaptureBlocked) cyberGreen else cyberRed).copy(alpha = 0.75f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        // Biometric Martian Seal Emblem
                        Box(
                            modifier = Modifier
                                .size(66.dp)
                                .shadow(
                                    elevation = if (isBiometricScanning) 16.dp else 8.dp,
                                    shape = CircleShape,
                                    spotColor = if (isBiometricScanning) cyberGreen else cyberCyan
                                )
                                .clip(CircleShape)
                                .border(
                                    width = 2.dp,
                                    brush = Brush.sweepGradient(
                                        listOf(
                                            if (isBiometricScanning) cyberGreen else cyberCyan,
                                            cyberGreen,
                                            if (isBiometricScanning) cyberGreen else cyberCyan
                                        )
                                    ),
                                    shape = CircleShape
                                )
                                .padding(2.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !isSetupState && !isBiometricScanning) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isBiometricScanning = true
                                    BiometricAuthHelper.authenticate(
                                        context = context,
                                        title = "MarcianoX Biometric Enclave",
                                        subtitle = "Unlock Android Keystore Master Vault Key",
                                        negativeButtonText = "Use PIN",
                                        onSuccess = {
                                            isBiometricScanning = false
                                            val unlocked = viewModel.unlockWithKeystoreBiometrics()
                                            if (!unlocked) {
                                                Toast.makeText(
                                                    context,
                                                    "Hardware Keystore key unavailable. Enter 6-digit PIN.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        onError = { err ->
                                            isBiometricScanning = false
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                .testTag("biometric_emblem_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_cyber_martian_emblem),
                                contentDescription = "Marciano Biometric Emblem",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Setup Phase Breadcrumbs
                        if (isSetupState) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                val currentPhase = when (setupStep) {
                                    SetupStep.SET_PIN, SetupStep.CONFIRM_PIN -> 1
                                    SetupStep.SET_DISTRESS_PIN, SetupStep.CONFIRM_DISTRESS_PIN -> 2
                                    SetupStep.SET_PANIC_PIN, SetupStep.CONFIRM_PANIC_PIN -> 3
                                }

                                listOf("1. PRIMARY 6-DIGIT", "2. DISTRESS DECOY", "3. PANIC PURGE").forEachIndexed { index, label ->
                                    val stepNum = index + 1
                                    val isActive = currentPhase == stepNum
                                    val isCompleted = currentPhase > stepNum
                                    val color = if (isActive) cyberGreen else if (isCompleted) cyberGreen.copy(alpha = 0.6f) else Color.DarkGray

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isActive) color.copy(alpha = 0.15f) else Color.Transparent)
                                            .border(1.dp, color.copy(alpha = if (isActive) 0.8f else 0.3f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = color
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = if (isSetupState) {
                                when (setupStep) {
                                    SetupStep.SET_PIN -> "ENTER PRIMARY 6-DIGIT PIN"
                                    SetupStep.CONFIRM_PIN -> "CONFIRM MASTER 6-DIGIT PIN"
                                    SetupStep.SET_DISTRESS_PIN -> "SET DECOY DISTRESS 6-DIGIT PIN"
                                    SetupStep.CONFIRM_DISTRESS_PIN -> "CONFIRM DECOY DISTRESS PIN"
                                    SetupStep.SET_PANIC_PIN -> "SET DECOY PANIC 6-DIGIT PIN"
                                    SetupStep.CONFIRM_PANIC_PIN -> "CONFIRM DECOY PANIC PIN"
                                }
                            } else {
                                "SECURE 6-DIGIT PASSCODE"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = if (isSetupState) {
                                when (setupStep) {
                                    SetupStep.SET_PIN -> "Create a 6-digit master passcode to encrypt local keys."
                                    SetupStep.CONFIRM_PIN -> "Re-enter your 6-digit master passcode to verify."
                                    SetupStep.SET_DISTRESS_PIN -> "Create a decoy distress PIN. When entered, safely opens innocuous chats."
                                    SetupStep.CONFIRM_DISTRESS_PIN -> "Re-enter your decoy distress PIN to verify."
                                    SetupStep.SET_PANIC_PIN -> "Create a decoy panic PIN. When entered, immediately zeroizes memory and databases."
                                    SetupStep.CONFIRM_PANIC_PIN -> "Re-enter your decoy panic PIN to verify."
                                }
                            } else {
                                "Enter your 6-digit Master, Distress, or Panic PIN (Rust Argon2id)"
                            },
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }

                    // ==========================================
                    // 2. 6-DIGIT LED INDICATOR DOTS & FEEDBACK
                    // ==========================================
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset(x = shakeOffset.value.dp)
                            .testTag("pin_indicator_section")
                    ) {
                        // Exactly 6-digit LED Indicator Dots
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .testTag("pin_dots_row")
                        ) {
                            for (i in 1..SimplexViewModel.PIN_LENGTH) {
                                val isActive = pinBuffer.length >= i
                                val isError = errorMessage != null && !errorMessage!!.contains("auto-locked") && !errorMessage!!.contains("locked")
                                val dotColor = if (isError) cyberRed else if (isActive) cyberGreen else Color.DarkGray

                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .shadow(if (isActive) 10.dp else 0.dp, CircleShape, spotColor = dotColor)
                                        .border(1.5.dp, dotColor.copy(alpha = if (isActive || isError) 1f else 0.5f), CircleShape)
                                        .background(
                                            if (isActive) dotColor else Color.Transparent,
                                            CircleShape
                                        )
                                        .testTag("pin_dot_$i")
                                )
                            }

                            // Manual submit button when at least 4 digits are typed
                            if (pinBuffer.length >= 4 && lockoutSeconds == 0) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.submitPin()
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(cyberGreen.copy(alpha = 0.2f))
                                        .border(1.2.dp, cyberGreen, CircleShape)
                                        .testTag("submit_pin_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Submit PIN",
                                        tint = cyberGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // 6-digit Strength Meter (during Setup)
                        if (isSetupState && pinBuffer.isNotEmpty()) {
                            val (label, color) = get6DigitPinStrength(pinBuffer)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = "ENTROPY: $label",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    val segments = when {
                                        label.startsWith("WEAK") -> 1
                                        label.startsWith("MEDIUM") -> 2
                                        label.startsWith("STRONG") -> 3
                                        else -> 0
                                    }
                                    for (s in 1..3) {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 14.dp, height = 3.dp)
                                                .clip(RoundedCornerShape(1.dp))
                                                .background(if (s <= segments) color else Color.DarkGray)
                                        )
                                    }
                                }
                            }
                        }

                        // Lockout & Error Messages Display
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (lockoutSeconds > 0) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(cyberRed.copy(alpha = 0.2f))
                                        .border(1.dp, cyberRed, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .testTag("lockout_warning")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Lockout Warning",
                                        tint = cyberRed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "KEYPAD LOCKED: ${lockoutSeconds}s (ANTI-BRUTE-FORCE)",
                                        color = cyberRed,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else if (errorMessage != null) {
                                val isAutoLockNotice = errorMessage!!.contains("auto-locked") || errorMessage!!.contains("locked")
                                val noticeColor = if (isAutoLockNotice) cyberCyan else cyberRed
                                val noticeIcon = if (isAutoLockNotice) Icons.Default.Lock else Icons.Default.Warning
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.testTag("error_message_row")
                                ) {
                                    Icon(
                                        imageVector = noticeIcon,
                                        contentDescription = "Alert Indicator",
                                        tint = noticeColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = errorMessage ?: "",
                                        color = noticeColor,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // ==========================================
                    // 3. CUSTOM 6-DIGIT TACTICAL KEYPAD
                    // ==========================================
                    CyberHudPanel(
                        skin = currentSkin,
                        shape = RoundedCornerShape(16.dp),
                        borderColor = cyberGreen.copy(alpha = 0.42f),
                        backdropAlpha = 0.72f,
                        ornamentAlpha = 0.25f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .testTag("custom_keypad_container")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            // PIN Shield (Scramble Mode) Toggle Bar
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Shield",
                                        tint = if (isKeyScrambled) cyberGreen else Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "SCRAMBLE PAD",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isKeyScrambled) cyberGreen else Color.Gray,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Switch(
                                    checked = isKeyScrambled,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isKeyScrambled = it
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = cyberGreen,
                                        checkedTrackColor = cyberGreen.copy(alpha = 0.3f),
                                        uncheckedThumbColor = Color.Gray,
                                        uncheckedTrackColor = Color.DarkGray
                                    ),
                                    modifier = Modifier
                                        .scale(0.75f)
                                        .testTag("scramble_keypad_toggle")
                                )
                            }

                            // Determine keypad arrangement: Standard or Scrambled
                            val keyRows = remember(isKeyScrambled, pinBuffer.isEmpty()) {
                                if (isKeyScrambled) {
                                    val digits = (0..9).shuffled()
                                    listOf(
                                        listOf(digits[0].toString(), digits[1].toString(), digits[2].toString()),
                                        listOf(digits[3].toString(), digits[4].toString(), digits[5].toString()),
                                        listOf(digits[6].toString(), digits[7].toString(), digits[8].toString()),
                                        listOf("PANIC", digits[9].toString(), "BACK")
                                    )
                                } else {
                                    listOf(
                                        listOf("1", "2", "3"),
                                        listOf("4", "5", "6"),
                                        listOf("7", "8", "9"),
                                        listOf("PANIC", "0", "BACK")
                                    )
                                }
                            }

                            val isLockedOut = lockoutSeconds > 0

                            for (row in keyRows) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    for (key in row) {
                                        val isPanicKey = key == "PANIC"
                                        val isBackKey = key == "BACK"

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 52.dp)
                                                .aspectRatio(1.85f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    Brush.verticalGradient(
                                                        when {
                                                            isPanicKey -> listOf(
                                                                cyberRed.copy(alpha = 0.24f),
                                                                cyberRed.copy(alpha = 0.10f)
                                                            )
                                                            isBackKey -> listOf(
                                                                cardOnyx.copy(alpha = 0.90f),
                                                                Color.Black.copy(alpha = 0.72f)
                                                            )
                                                            else -> listOf(
                                                                cardOnyx.copy(alpha = 0.92f),
                                                                currentSkin.backgroundColor.copy(alpha = 0.80f)
                                                            )
                                                        }
                                                    )
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = when {
                                                        isPanicKey -> cyberRed.copy(alpha = 0.65f)
                                                        isBackKey -> cyberCyan.copy(alpha = 0.35f)
                                                        else -> cyberGreen.copy(alpha = 0.28f)
                                                    },
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .combinedClickable(
                                                    enabled = !isLockedOut,
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        when (key) {
                                                            "BACK" -> viewModel.deleteLastDigit()
                                                            "PANIC" -> showPanicConfirmDialog = true
                                                            else -> viewModel.enterDigit(key)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (isBackKey) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            viewModel.clearPinBuffer()
                                                        } else if (isPanicKey) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            viewModel.triggerPanicWipe()
                                                        }
                                                    }
                                                )
                                                .testTag("pin_${key.lowercase()}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isBackKey) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Digit (Long Press to Clear)",
                                                    tint = if (isLockedOut) Color.Gray else cyberCyan,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            } else if (isPanicKey) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = "PANIC",
                                                        color = if (isLockedOut) Color.Gray else cyberRed,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Black,
                                                        fontFamily = FontFamily.Monospace,
                                                        letterSpacing = 1.sp
                                                    )
                                                    Text(
                                                        text = "PURGE",
                                                        color = (if (isLockedOut) Color.Gray else cyberRed).copy(alpha = 0.7f),
                                                        fontSize = 7.5.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    text = key,
                                                    color = if (isLockedOut) Color.Gray else Color.White,
                                                    fontSize = 22.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 4. OS CAPTURE SHIELD DETAILS DIALOG
    // ==========================================
    if (showCaptureDetailsDialog) {
        Dialog(onDismissRequest = { showCaptureDetailsDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = cardOnyx,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, cyberGreen.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("screen_capture_dialog")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(cyberGreen.copy(alpha = 0.15f))
                            .border(1.2.dp, cyberGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Screen Capture Guard",
                            tint = cyberGreen,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "OS SCREEN CAPTURE SHIELD",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Prevents screen capture through Android OS WindowManager.LayoutParams.FLAG_SECURE:\n\n" +
                                "• Hardware Screenshots: Hardware button combinations (Power + VolDown) and system gesture captures are blocked by the OS.\n" +
                                "• Screen Recording: Third-party screen recorders, MediaProjection, and malware capture a blank/black canvas.\n" +
                                "• Recents / Overview Task Switcher: Window thumbnails are redacted, preventing thumbnail leakage when switching apps.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live toggle to test enforcement
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "ENFORCE FLAG_SECURE",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (ScreenCapturePreventionManager.isEmulator()) "Running in Emulator environment" else "Running on Physical Device",
                                color = Color.Gray,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Switch(
                            checked = isScreenCaptureEnforced,
                            onCheckedChange = { enabled ->
                                ScreenCapturePreventionManager.setScreenCaptureProtection(activity, enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = cyberGreen,
                                checkedTrackColor = cyberGreen.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.testTag("toggle_flag_secure")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showCaptureDetailsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = cyberGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "CLOSE SECURITY BRIEF",
                            color = Color.Black,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    // ==========================================
    // 5. PANIC PURGE CONFIRMATION MODAL
    // ==========================================
    if (showPanicConfirmDialog) {
        Dialog(onDismissRequest = { showPanicConfirmDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = cardOnyx,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, cyberRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("panic_confirm_dialog")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(cyberRed.copy(alpha = 0.15f))
                            .border(1.2.dp, cyberRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Panic Purge",
                            tint = cyberRed,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "EMERGENCY DATA CLEARING",
                        color = cyberRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Triggering emergency panic purge will instantly zeroize all in-memory cryptographic keys and overwrite local database tables. This cannot be undone.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { showPanicConfirmDialog = false },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CANCEL", color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                showPanicConfirmDialog = false
                                viewModel.triggerPanicWipe()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = cyberRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("confirm_panic_purge_button")
                        ) {
                            Text("EXECUTE", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 6. BIOMETRIC SCANNER HUD OVERLAY
    // ==========================================
    if (isBiometricScanning) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .border(2.dp, cyberGreen.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = cyberGreen,
                        modifier = Modifier.size(80.dp),
                        strokeWidth = 2.5.dp
                    )
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Scanning",
                        tint = cyberGreen,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Text(
                    text = "HARDWARE KEYSTORE HANDSHAKE...",
                    color = cyberGreen,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Awaiting Android BiometricPrompt verification...",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = { isBiometricScanning = false }) {
                    Text(
                        text = "USE PIN INSTEAD",
                        color = cyberCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Emergency Panic Terminal component rendering stage logs, progress bar, and fresh identity restart.
 */
@Composable
private fun PanicWipeTerminalView(
    viewModel: SimplexViewModel,
    cyberRed: Color,
    cardOnyx: Color
) {
    val panicWipeStage by viewModel.panicWipeStage.collectAsState()
    val panicWipeProgress by viewModel.panicWipeProgress.collectAsState()
    val panicWipeLogs by viewModel.panicWipeLogs.collectAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardOnyx)
            .border(1.5.dp, cyberRed.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(20.dp)
            .testTag("panic_terminal_view")
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(cyberRed.copy(alpha = 0.15f))
                .border(1.5.dp, cyberRed, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Data Clearing Sequence",
                tint = cyberRed,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "DATA CLEARING SEQUENCE",
            color = cyberRed,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )

        Text(
            text = "DECOY PANIC PIN TRIGGERED",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Progress Bar and Percentage
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = panicWipeStage,
                    color = cyberRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(panicWipeProgress * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { panicWipeProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = cyberRed,
                trackColor = Color.DarkGray
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Terminal readout box showing data clearing logs
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp, max = 140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                reverseLayout = false,
                modifier = Modifier.fillMaxSize()
            ) {
                items(panicWipeLogs.size) { index ->
                    Text(
                        text = panicWipeLogs[index],
                        color = if (index == panicWipeLogs.size - 1) cyberRed else Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Action button once wipe finishes
        if (panicWipeProgress >= 1f) {
            Button(
                onClick = { viewModel.resetToFreshState() },
                colors = ButtonDefaults.buttonColors(containerColor = cyberRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_clean_state_button")
            ) {
                Text(
                    text = "RESTART WITH FRESH IDENTITY",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

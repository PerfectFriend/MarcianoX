package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.SimplexViewModel

data class AutoLockPreset(
    val seconds: Int,
    val label: String,
    val description: String,
    val tag: String,
    val labelKey: String,
    val descriptionKey: String
)

val AUTO_LOCK_PRESETS = listOf(
    AutoLockPreset(15, "15 SEC", "High Security - Instant lock", "auto_lock_chip_15s", "auto_lock_preset_15s_label", "auto_lock_preset_15s_desc"),
    AutoLockPreset(30, "30 SEC", "Paranoid Mode", "auto_lock_chip_30s", "auto_lock_preset_30s_label", "auto_lock_preset_30s_desc"),
    AutoLockPreset(60, "1 MIN", "Recommended Default", "auto_lock_chip_60s", "auto_lock_preset_60s_label", "auto_lock_preset_60s_desc"),
    AutoLockPreset(120, "2 MIN", "Standard Session", "auto_lock_chip_120s", "auto_lock_preset_120s_label", "auto_lock_preset_120s_desc"),
    AutoLockPreset(300, "5 MIN", "Extended Session", "auto_lock_chip_300s", "auto_lock_preset_300s_label", "auto_lock_preset_300s_desc"),
    AutoLockPreset(600, "10 MIN", "Maximum Inactivity", "auto_lock_chip_600s", "auto_lock_preset_600s_label", "auto_lock_preset_600s_desc"),
    AutoLockPreset(0, "IMMEDIATE", "Lock on app background", "auto_lock_chip_immediate", "auto_lock_preset_immediate_label", "auto_lock_preset_immediate_desc"),
    AutoLockPreset(-1, "NEVER", "Manual lock only", "auto_lock_chip_never", "auto_lock_preset_never_label", "auto_lock_preset_never_desc")
)

fun formatAutoLockDuration(seconds: Int): String {
    val lang = LocalizationHelper.currentLanguage.value
    if (lang == AppLanguage.ENGLISH) {
        return when {
            seconds == -1 -> "Disabled (Never)"
            seconds == 0 -> "Immediate on background"
            seconds < 60 -> "$seconds Seconds"
            seconds % 60 == 0 -> "${seconds / 60} Minute${if (seconds / 60 > 1) "s" else ""}"
            else -> "${seconds / 60}m ${seconds % 60}s"
        }
    }
    return when {
        seconds == -1 -> LocalizationHelper.get("auto_lock_never")
        seconds == 0 -> LocalizationHelper.get("auto_lock_immediate")
        seconds < 60 -> if (lang == AppLanguage.RUSSIAN) "$seconds сек" else "${seconds}s"
        seconds % 60 == 0 -> {
            val mins = seconds / 60
            if (lang == AppLanguage.RUSSIAN) "$mins мин" else "${mins}m"
        }
        else -> {
            val mins = seconds / 60
            val rem = seconds % 60
            if (lang == AppLanguage.RUSSIAN) "$mins мин $rem сек" else "${mins}m ${rem}s"
        }
    }
}

@Composable
fun AutoLockTimerBadge(
    viewModel: SimplexViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeoutSeconds by viewModel.autoLockTimeoutSeconds.collectAsState()
    val secondsRemaining by viewModel.autoLockSecondsRemaining.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val currentLanguage by LocalizationHelper.currentLanguage.collectAsState()

    if (isLocked) return

    val cyberCyan = Color(0xFF00F0FF)
    val cyberAmber = Color(0xFFFFB300)

    val unitSuffix = if (currentLanguage == AppLanguage.RUSSIAN) "с" else "s"
    val (badgeText, badgeColor) = remember(timeoutSeconds, secondsRemaining, currentLanguage) {
        when {
            timeoutSeconds == -1 -> LocalizationHelper.get("auto_lock_manual_badge") to Color.Gray
            timeoutSeconds == 0 -> LocalizationHelper.get("auto_lock_bg_badge") to cyberAmber
            secondsRemaining != null -> {
                val rem = secondsRemaining ?: timeoutSeconds
                val color = if (rem <= 10) cyberAmber else cyberCyan
                "${rem}$unitSuffix" to color
            }
            else -> "${timeoutSeconds}$unitSuffix" to cyberCyan
        }
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF0F151B))
            .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("auto_lock_timer_badge"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = LocalizationHelper.get("auto_lock"),
                tint = badgeColor,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = badgeText,
                color = badgeColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AutoLockSettingsContent(
    viewModel: SimplexViewModel,
    modifier: Modifier = Modifier
) {
    val cyberGreen = Color(0xFF00FF66)
    val cyberCyan = Color(0xFF00F0FF)
    val cyberRed = Color(0xFFFF3333)
    val cyberAmber = Color(0xFFFFB300)

    val currentTimeout by viewModel.autoLockTimeoutSeconds.collectAsState()
    val secondsRemaining by viewModel.autoLockSecondsRemaining.collectAsState()
    val currentLanguage by LocalizationHelper.currentLanguage.collectAsState()

    var sliderValue by remember(currentTimeout) {
        mutableFloatStateOf(if (currentTimeout in 10..600) currentTimeout.toFloat() else 60f)
    }

    val remainingUnit = if (currentLanguage == AppLanguage.RUSSIAN) "с" else "s"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status & Overview Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1017)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, cyberCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .testTag("auto_lock_setting_card")
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (currentTimeout == -1) Color.Gray
                                    else if (currentTimeout == 0) cyberAmber
                                    else cyberGreen,
                                    CircleShape
                                )
                        )
                        Text(
                            text = LocalizationHelper.get("auto_lock_title"),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(cyberCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = formatAutoLockDuration(currentTimeout).uppercase(),
                            color = cyberCyan,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = LocalizationHelper.get("auto_lock_desc"),
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    lineHeight = 15.sp
                )

                if (currentTimeout > 0 && secondsRemaining != null) {
                    val progress = (secondsRemaining!!.toFloat() / currentTimeout.toFloat()).coerceIn(0f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = LocalizationHelper.get("auto_lock_countdown_label"),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray
                            )
                            Text(
                                text = "${secondsRemaining}$remainingUnit ${LocalizationHelper.get("auto_lock_remaining_suffix")}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (secondsRemaining!! <= 10) cyberAmber else cyberGreen
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (secondsRemaining!! <= 10) cyberAmber else cyberCyan,
                            trackColor = Color.DarkGray
                        )
                    }
                }
            }
        }

        // Preset Chips
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = LocalizationHelper.get("auto_lock_presets_header"),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = cyberCyan
            )

            AUTO_LOCK_PRESETS.chunked(2).forEach { rowPresets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowPresets.forEach { preset ->
                        val isSelected = currentTimeout == preset.seconds
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) cyberCyan.copy(alpha = 0.15f) else Color(0xFF10141C))
                                .border(
                                    1.dp,
                                    if (isSelected) cyberCyan else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    viewModel.setAutoLockTimeout(preset.seconds)
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag(preset.tag),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = LocalizationHelper.get(preset.labelKey),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isSelected) cyberCyan else Color.White
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = cyberCyan,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = LocalizationHelper.get(preset.descriptionKey),
                                    fontSize = 9.sp,
                                    color = Color.Gray,
                                    lineHeight = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Custom Slider Selector
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10131B)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = LocalizationHelper.get("auto_lock_custom_slider"),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.LightGray
                    )
                    Text(
                        text = "${sliderValue.toInt()}$remainingUnit (${formatAutoLockDuration(sliderValue.toInt())})",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = cyberCyan
                    )
                }

                Slider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                        viewModel.setAutoLockTimeout(it.toInt())
                    },
                    valueRange = 10f..600f,
                    steps = 58,
                    colors = SliderDefaults.colors(
                        thumbColor = cyberCyan,
                        activeTrackColor = cyberCyan.copy(alpha = 0.7f),
                        inactiveTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auto_lock_slider")
                )
            }
        }

        // Security Telemetry & Manual Lock Trigger
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF140D0D)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, cyberRed.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = cyberRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = LocalizationHelper.get("auto_lock_telemetry_title"),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = cyberRed
                    )
                }

                Text(
                    text = LocalizationHelper.get("auto_lock_telemetry_body"),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.LightGray,
                    lineHeight = 14.sp
                )

                Button(
                    onClick = { viewModel.lock(reasonMessage = "Session locked manually.") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cyberRed.copy(alpha = 0.2f),
                        contentColor = cyberRed
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .border(1.dp, cyberRed, RoundedCornerShape(4.dp))
                        .testTag("manual_lock_button"),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = LocalizationHelper.get("auto_lock_now_button"),
                            tint = cyberRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = LocalizationHelper.get("auto_lock_now_button"),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

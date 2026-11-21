package com.example.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.crypto.MemoryZeroizationManager
import com.example.viewmodel.DecryptedMessage
import com.example.viewmodel.SimplexViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Hardware-recorded PCM voice message bubble backed by MediaCryptoManager (AudioTrack + Rust AES-256-GCM).
 */
@Composable
fun WalkieTalkieMessageBubble(
    msg: DecryptedMessage,
    viewModel: SimplexViewModel,
    isMe: Boolean,
    cyberGreen: Color,
    cyberCyan: Color
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    var isBurned by remember { mutableStateOf(false) }
    var burnAfterPlay by remember { mutableStateOf(true) }

    val durationSec = remember(msg.text) {
        Regex("""(\d+)""").find(msg.text)?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 6
    }

    val bars = remember(msg.waveformBars) {
        if (msg.waveformBars.isNotEmpty()) {
            msg.waveformBars.take(16).map { (8f + it.coerceIn(0f, 1f) * 28f).toInt() }
        } else {
            List(12) { idx -> 12 + ((idx * 7) % 20) }
        }
    }

    LaunchedEffect(isPlaying, durationSec) {
        if (isPlaying) {
            playbackProgress = 0f
            val steps = (durationSec * 10).coerceAtLeast(10)
            for (step in 1..steps) {
                if (!isPlaying) break
                delay(100)
                playbackProgress = (step.toFloat() / steps).coerceIn(0f, 1f)
            }
        }
    }

    val outlineColor = if (isMe) cyberGreen.copy(alpha = 0.45f) else cyberCyan.copy(alpha = 0.45f)
    val textHighlightColor = if (isMe) cyberGreen else cyberCyan

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        if (isBurned) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1012))
                    .border(1.dp, Color(0xFFFF3333).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Burned",
                        tint = Color(0xFFFF3333),
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            text = "VOICE ENVELOPE PURGED",
                            color = Color(0xFFFF3333),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Encrypted PCM payload shredded from RAM & SQLCipher.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 10.dp,
                            topEnd = 10.dp,
                            bottomStart = if (isMe) 10.dp else 2.dp,
                            bottomEnd = if (isMe) 2.dp else 10.dp
                        )
                    )
                    .background(Color(0xFF10121A))
                    .border(
                        width = 1.dp,
                        color = outlineColor,
                        shape = RoundedCornerShape(
                            topStart = 10.dp,
                            topEnd = 10.dp,
                            bottomStart = if (isMe) 10.dp else 2.dp,
                            bottomEnd = if (isMe) 2.dp else 10.dp
                        )
                    )
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isMe) "VOICE PCM [OUT]" else "VOICE PCM [IN]",
                            color = textHighlightColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        val infiniteTransition = rememberInfiniteTransition(label = "burn_glow")
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.5f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse"
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (burnAfterPlay) Color(0xFF330D0D).copy(alpha = pulseAlpha)
                                    else Color(0xFF14222A)
                                )
                                .border(
                                    1.dp,
                                    if (burnAfterPlay) Color(0xFFFF3333).copy(alpha = pulseAlpha) else cyberCyan.copy(alpha = 0.4f),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { burnAfterPlay = !burnAfterPlay }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (burnAfterPlay) "BURN-ON-PLAY" else "KEEP",
                                color = if (burnAfterPlay) Color(0xFFFF3333) else cyberCyan,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isPlaying) Color.Gray else textHighlightColor)
                                .clickable(enabled = !isPlaying) {
                                    isPlaying = true
                                    playbackProgress = 0f
                                    viewModel.playEncryptedVoiceMessage(
                                        msg = msg,
                                        onFinished = {
                                            isPlaying = false
                                            playbackProgress = 1f
                                            if (burnAfterPlay) {
                                                isBurned = true
                                                viewModel.deleteMessageById(msg.id)
                                            }
                                        },
                                        onError = { err ->
                                            isPlaying = false
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                .testTag("voice_play_button_${msg.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Refresh else Icons.Default.PlayArrow,
                                contentDescription = "Play encrypted voice note",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            bars.forEachIndexed { idx, height ->
                                val active = isPlaying && (playbackProgress * bars.size > idx)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(height.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(if (active) textHighlightColor else Color.DarkGray)
                                )
                            }
                        }

                        Text(
                            text = if (isPlaying) {
                                val left = (durationSec - playbackProgress * durationSec).coerceAtLeast(0f)
                                String.format("%.1fs", left)
                            } else {
                                "${durationSec}s"
                            },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            modifier = Modifier.width(34.dp),
                            textAlign = TextAlign.End
                        )
                    }

                    if (isPlaying) {
                        LinearProgressIndicator(
                            progress = { playbackProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp)),
                            color = textHighlightColor,
                            trackColor = Color.DarkGray
                        )
                    }
                }
            }
        }

        Text(
            text = getFormattedTime(msg.timestamp),
            color = Color.DarkGray,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
        )
    }
}

/**
 * Decodes an EXIF-stripped JPEG encrypted in Rust AES-256-GCM and immediately wipes the decrypted buffer.
 */
@Composable
fun EncryptedPhotoMessageBubble(
    msg: DecryptedMessage,
    viewModel: SimplexViewModel,
    isMe: Boolean,
    cyberGreen: Color,
    cyberCyan: Color
) {
    var decryptedBitmap by remember(msg.id, msg.mediaCiphertextB64) { mutableStateOf<ImageBitmap?>(null) }
    var decryptFailed by remember(msg.id) { mutableStateOf(false) }
    var showFullImage by remember { mutableStateOf(false) }

    LaunchedEffect(msg.id, msg.mediaCiphertextB64) {
        withContext(Dispatchers.IO) {
            val bytes = viewModel.decryptMediaBytes(msg)
            if (bytes == null) {
                decryptFailed = true
                return@withContext
            }
            try {
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) {
                    decryptedBitmap = bmp.asImageBitmap()
                    decryptFailed = false
                } else {
                    decryptFailed = true
                }
            } finally {
                MemoryZeroizationManager.zero(
                    bytes,
                    MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY
                )
            }
        }
    }

    val outlineColor = if (isMe) cyberGreen.copy(alpha = 0.45f) else cyberCyan.copy(alpha = 0.45f)
    val accent = if (isMe) cyberGreen else cyberCyan

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF10131C))
                .border(1.dp, outlineColor, RoundedCornerShape(12.dp))
                .clickable(enabled = decryptedBitmap != null) { showFullImage = true }
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Encrypted Photo",
                        tint = accent,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "EXIF-STRIPPED AES-GCM",
                        color = accent,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = msg.deliveryStatus,
                    color = Color.Gray,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            val bmp = decryptedBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "Decrypted Photo Attachment",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 130.dp, max = 210.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (decryptFailed) "UNABLE TO DECRYPT MEDIA" else "DECRYPTING RUST AES-GCM...",
                        color = if (decryptFailed) Color(0xFFFF3333) else Color.Gray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Text(
            text = getFormattedTime(msg.timestamp),
            color = Color.DarkGray,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
        )
    }

    if (showFullImage && decryptedBitmap != null) {
        Dialog(onDismissRequest = { showFullImage = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0B0D14))
                    .border(1.dp, accent, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DECRYPTED PHOTO (EXIF PURGED)",
                        color = accent,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showFullImage = false }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Image(
                    bitmap = decryptedBitmap!!,
                    contentDescription = "Full encrypted photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Button(
                    onClick = {
                        showFullImage = false
                        viewModel.deleteMessageById(msg.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3333)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SHRED PHOTO FROM VAULT", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
    }
}

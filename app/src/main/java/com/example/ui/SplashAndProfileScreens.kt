package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.crypto.Bip39Utility
import com.example.viewmodel.SimplexViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

@Composable
fun CyberpunkSplash(viewModel: SimplexViewModel) {
    val splashText by viewModel.splashText.collectAsState()
    val targetProgress by viewModel.splashProgress.collectAsState()
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "splash_progress"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "splash")

    val scannerLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanner"
    )

    val neonPulse by infiniteTransition.animateColor(
        initialValue = Color(0xFF00FF66),
        targetValue = Color(0xFF00F0FF),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neon_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070B))
            .testTag("cyberpunk_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_wallpaper_matrix_cyber),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.75f)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF06080D).copy(alpha = 0.25f),
                            Color(0xFF05060A).copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val scanY = scannerLineY * h
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xFF00FF66).copy(alpha = 0.20f), Color.Transparent)
                ),
                topLeft = Offset(0f, scanY - 60f),
                size = Size(w, 120f)
            )
            drawLine(
                color = neonPulse.copy(alpha = 0.65f),
                start = Offset(0f, scanY),
                end = Offset(w, scanY),
                strokeWidth = 2.5f
            )

            drawCircle(
                color = Color(0xFF00F0FF).copy(alpha = 0.14f),
                radius = 290f,
                center = Offset(w / 2f, h / 2f),
                style = Stroke(width = 1.5f)
            )
            drawCircle(
                color = Color(0xFF00FF66).copy(alpha = 0.22f),
                radius = 185f,
                center = Offset(w / 2f, h / 2f),
                style = Stroke(width = 2f)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(152.dp)
                    .shadow(22.dp, CircleShape, spotColor = neonPulse)
                    .clip(CircleShape)
                    .border(
                        width = 2.5.dp,
                        brush = Brush.sweepGradient(
                            listOf(Color(0xFF00FF66), Color(0xFF00F0FF), Color(0xFF00FF66))
                        ),
                        shape = CircleShape
                    )
                    .padding(4.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_martianx_icon),
                    contentDescription = "MarcianoX Martian Emblem",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "MARCIANOX",
                color = Color.White,
                fontSize = 30.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 7.sp,
                modifier = Modifier.testTag("splash_title")
            )

            Text(
                text = "MARTIAN MATRIX ENCLAVE // v3.0",
                color = Color(0xFF00FF66),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(30.dp))

            CyberHudPanel(
                skin = SkinStyle.CYBERPUNK,
                shape = RoundedCornerShape(10.dp),
                borderColor = Color(0xFF00FF66).copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp)
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BOOT SEQUENCE",
                            color = Color(0xFF00F0FF),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            color = Color(0xFF00FF66),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF00FF66),
                        trackColor = Color(0xFF0D1822)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = splashText.uppercase(),
                        color = Color(0xFF00FF66),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileSelectionScreen(viewModel: SimplexViewModel) {
    val profileList by viewModel.profileList.collectAsState()
    val primaryProfile by viewModel.primaryProfileAlias.collectAsState()
    var showCreateProfile by remember { mutableStateOf(false) }
    var deleteConfirmAlias by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    ThemeBackground(skin = SkinStyle.CYBERPUNK) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("profile_selection_screen")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, bottom = 72.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Martian Cyberpunk Header Banner with emblem
                CyberHudPanel(
                    skin = SkinStyle.CYBERPUNK,
                    shape = RoundedCornerShape(14.dp),
                    borderColor = Color(0xFF00F0FF).copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_cyber_martian_emblem),
                            contentDescription = "MarcianoX Identity Node",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, Color(0xFF00FF66), CircleShape)
                        )
                        Column {
                            Text(
                                text = "MARCIANOX",
                                color = Color(0xFF00F0FF),
                                fontSize = 20.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 4.sp
                            )
                            Text(
                                text = LocalizationHelper.get("select_working_account"),
                                color = Color(0xFF00FF66).copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (profileList.isEmpty()) {
                    CyberHudPanel(
                        skin = SkinStyle.CYBERPUNK,
                        shape = RoundedCornerShape(12.dp),
                        borderColor = Color(0xFFFF3333).copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_cyber_martian_emblem),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .alpha(0.65f)
                                    .border(1.dp, Color(0xFFFF3333).copy(alpha = 0.5f), CircleShape)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "NO CODES DETECTED ON CORE STORAGE",
                                color = Color(0xFFFF3333),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Initialize a BIP-39 cryptographic node using 24-word entropy to unlock encrypted Martian relays.",
                                color = Color.LightGray.copy(alpha = 0.78f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(profileList) { alias ->
                            val isPrimary = primaryProfile == alias
                            CyberHudPanel(
                                skin = SkinStyle.CYBERPUNK,
                                shape = RoundedCornerShape(10.dp),
                                borderColor = if (isPrimary) Color(0xFF00FF66).copy(alpha = 0.8f) else Color(0xFF00F0FF).copy(alpha = 0.28f),
                                backdropAlpha = 0.82f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectProfile(alias) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = if (isPrimary) Color(0xFF00FF66) else Color(0xFF00F0FF),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column {
                                            Text(
                                                text = alias.uppercase(),
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (isPrimary) "PRIMARY WORKSPACE ACCOUNT" else "SECONDARY IDENTITY NODE",
                                                color = if (isPrimary) Color(0xFF00FF66).copy(alpha = 0.85f) else Color.LightGray.copy(alpha = 0.7f),
                                                fontSize = 8.5.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isPrimary) Color(0xFF00FF66).copy(alpha = 0.2f) else Color(0xFF1F2430))
                                                .border(
                                                    0.8.dp,
                                                    if (isPrimary) Color(0xFF00FF66) else Color.White.copy(alpha = 0.15f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .clickable {
                                                    viewModel.togglePrimaryProfile(alias, !isPrimary)
                                                }
                                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = if (isPrimary) "DEFAULT" else "SET DEFAULT",
                                                color = if (isPrimary) Color(0xFF00FF66) else Color.LightGray,
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        IconButton(
                                            onClick = { deleteConfirmAlias = alias },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Profile",
                                                tint = Color(0xFFFF5555).copy(alpha = 0.8f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Create Profile Action at Bottom
            Button(
                onClick = { showCreateProfile = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00FF66).copy(alpha = 0.18f),
                    contentColor = Color(0xFF00FF66)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .align(Alignment.BottomCenter)
                    .border(
                        1.2.dp,
                        Brush.horizontalGradient(listOf(Color(0xFF00FF66), Color(0xFF00F0FF))),
                        RoundedCornerShape(10.dp)
                    )
                    .testTag("create_profile_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "[ CREATE SECURE PROFILE ]",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }

    // Dialog: Delete confirmation
    deleteConfirmAlias?.let { alias ->
        AlertDialog(
            onDismissRequest = { deleteConfirmAlias = null },
            title = {
                Text("DESTROY PROFILE CONTEXT?", fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Text(
                    "Are you absolutely sure you want to permanently delete profile \"$alias\"? This will destroy all localized keys, backups, and messages linked with this identity forever.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProfile(alias)
                        deleteConfirmAlias = null
                    }
                ) {
                    Text("YES, DESTROY", color = Color(0xFFFF3333), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmAlias = null }) {
                    Text("CANCEL", color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            },
            containerColor = Color(0xFF0E1017)
        )
    }

    // Dialog: Step-by-Step Profile Creator
    if (showCreateProfile) {
        Bip39CreateProfileDialog(
            viewModel = viewModel,
            onDismiss = { showCreateProfile = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bip39CreateProfileDialog(viewModel: SimplexViewModel, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(1) } // Steps 1 to 4
    
    // Inputs step 1
    var word1 by remember { mutableStateOf("") }
    var word2 by remember { mutableStateOf("") }
    var word3 by remember { mutableStateOf("") }
    var word4 by remember { mutableStateOf("") }
    var word5 by remember { mutableStateOf("") }
    
    val currentDate = remember {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.format(Date())
    }

    // Derived mnemonics
    var generatedWords by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Checkbox step 2
    var warningAccepted by remember { mutableStateOf(false) }

    // Inputs step 3 (Verification)
    var testIndices by remember { mutableStateOf<List<Int>>(emptyList()) }
    var inputCheck1 by remember { mutableStateOf("") }
    var inputCheck2 by remember { mutableStateOf("") }
    var inputCheck3 by remember { mutableStateOf("") }
    var verificationError by remember { mutableStateOf(false) }

    // Inputs step 4 (PIN Configuration)
    var profileAliasInput by remember { mutableStateOf("") }
    var pinSetupStep by remember { mutableStateOf(1) } // 1=Primary, 2=Confirm Primary, 3=Distress, 4=Confirm Distress, 5=Panic, 6=Confirm Panic
    var inputPinPrimary by remember { mutableStateOf("") }
    var inputPinPrimaryConfirm by remember { mutableStateOf("") }
    var inputPinDistress by remember { mutableStateOf("") }
    var inputPinDistressConfirm by remember { mutableStateOf("") }
    var inputPinPanic by remember { mutableStateOf("") }
    var inputPinPanicConfirm by remember { mutableStateOf("") }

    // Custom Shuffled keypad state
    var shuffledKeys by remember { mutableStateOf(emptyList<String>()) }
    
    // Shuffle numpad keys
    LaunchedEffect(pinSetupStep, step) {
        shuffledKeys = (0..9).map { it.toString() }.shuffled()
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    BackHandler {
        com.example.ui.keyboard.InAppKeyboardManager.hideKeyboard()
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .background(Color(0xFF07080D))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        ThemeBackground(skin = SkinStyle.CYBERPUNK) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                        // Header progress indicators
                        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "BIP-39 INITIALIZER",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00F0FF)
                                )
                                IconButton(onClick = {
                                    com.example.ui.keyboard.InAppKeyboardManager.hideKeyboard()
                                    onDismiss()
                                }) {
                                    Text("[X]", color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }
                            }

                            // Progress bar steps
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (i in 1..4) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(3.dp)
                                            .background(if (i <= step) Color(0xFF00FF66) else Color.DarkGray)
                                    )
                                }
                            }
                        }

                        // STEP Content (scrollable so fields stay accessible when in-app keyboard opens)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.Center)
                                .padding(top = 64.dp, bottom = 64.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            when (step) {
                            1 -> {
                                // Step 1: Gathering Entropy Source
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "01. CONTEXT ENTROPY INJECTOR",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Generate 24 seed words. We merge the current space-date stamp with 5 customized words of your choice.",
                                        color = Color.Gray,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    // Automatic date label
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Black)
                                            .border(1.dp, Color.White.copy(alpha = 0.05f))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("AUTOMATIC DATE ENTROPY:", color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                        Text(currentDate, color = Color(0xFF00FF66), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }

                                    Text("ENTER 5 UNIQUE CUSTOM WORDS:", color = Color.LightGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedTextField(
                                            value = word1,
                                            onValueChange = { if (it.length <= 16) word1 = it },
                                            placeholder = { Text("Word 1 (max 16 chars)", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                            modifier = Modifier.fillMaxWidth().testTag("entropy_word_1"),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray, focusedBorderColor = Color(0xFF00F0FF))
                                        )
                                        OutlinedTextField(
                                            value = word2,
                                            onValueChange = { if (it.length <= 16) word2 = it },
                                            placeholder = { Text("Word 2 (max 16 chars)", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                            modifier = Modifier.fillMaxWidth().testTag("entropy_word_2"),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray, focusedBorderColor = Color(0xFF00F0FF))
                                        )
                                        OutlinedTextField(
                                            value = word3,
                                            onValueChange = { if (it.length <= 16) word3 = it },
                                            placeholder = { Text("Word 3 (max 16 chars)", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray, focusedBorderColor = Color(0xFF00F0FF))
                                        )
                                        OutlinedTextField(
                                            value = word4,
                                            onValueChange = { if (it.length <= 16) word4 = it },
                                            placeholder = { Text("Word 4 (max 16 chars)", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray, focusedBorderColor = Color(0xFF00F0FF))
                                        )
                                        OutlinedTextField(
                                            value = word5,
                                            onValueChange = { if (it.length <= 16) word5 = it },
                                            placeholder = { Text("Word 5 (max 16 chars)", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray, focusedBorderColor = Color(0xFF00F0FF))
                                        )
                                    }
                                }
                            }
                            
                            2 -> {
                                // Step 2: Display 24 words
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "02. GENERATED RECOVERY SEED",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Here is your 24-word master cryptographic recovery seed. Write it down. If lost, your account and credentials cannot be recovered.",
                                        color = Color.Gray,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    // Display 24 words in 4x6 grid
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                    ) {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(3),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            items(24) { idx ->
                                                val word = generatedWords.getOrNull(idx) ?: ""
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(3.dp))
                                                        .background(Color(0xFF0F1118))
                                                        .padding(5.dp)
                                                ) {
                                                    Text(
                                                        text = "${idx + 1}. $word",
                                                        color = Color(0xFF00F0FF),
                                                        fontSize = 9.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Clipboard Copy button
                                    Button(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(generatedWords.joinToString(" ")))
                                            Toast.makeText(context, "Seed copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("copy_seed_button")
                                    ) {
                                        Text("COPY SEED CODES", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }

                                    // Red warning card
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF320E0E))
                                            .border(1.dp, Color(0xFFFF3333).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = "⚠️ SAFETY WARNING",
                                                color = Color(0xFFFF3333),
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "You are strictly and solely responsible for storing this mnemonic code offline. There is absolutely no server backup, no recovery email, and no system restore point.",
                                                color = Color.LightGray,
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    // Accept checkbox
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = warningAccepted,
                                            onCheckedChange = { warningAccepted = it },
                                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00FF66))
                                        )
                                        Text(
                                            text = "I have written down the 24 words in a secure place.",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            3 -> {
                                // Step 3: Verify 3 random words
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "03. VERIFY SECURITY MNEMONIC",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "For security purposes, please re-type the random 3 seed words based on their corresponding indexes:",
                                        color = Color.Gray,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    if (verificationError) {
                                        Text(
                                            text = "X WRONG WORDS! Check spelling/ordering and try again.",
                                            color = Color(0xFFFF3333),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    val t1 = testIndices.getOrNull(0) ?: 1
                                    val t2 = testIndices.getOrNull(1) ?: 2
                                    val t3 = testIndices.getOrNull(2) ?: 3

                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = inputCheck1,
                                            onValueChange = { inputCheck1 = it },
                                            label = { Text("Word #$t1", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                            modifier = Modifier.fillMaxWidth().testTag("verify_word_1"),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray, focusedBorderColor = Color(0xFF00F0FF))
                                        )
                                        OutlinedTextField(
                                            value = inputCheck2,
                                            onValueChange = { inputCheck2 = it },
                                            label = { Text("Word #$t2", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                            modifier = Modifier.fillMaxWidth().testTag("verify_word_2"),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray, focusedBorderColor = Color(0xFF00F0FF))
                                        )
                                        OutlinedTextField(
                                            value = inputCheck3,
                                            onValueChange = { inputCheck3 = it },
                                            label = { Text("Word #$t3", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                            modifier = Modifier.fillMaxWidth().testTag("verify_word_3"),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray, focusedBorderColor = Color(0xFF00F0FF))
                                        )
                                    }
                                }
                            }

                            4 -> {
                                // Step 4: PIN + Alias setup with Shuffled custom Matrix keypad!
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "04. PROFILE IDENTITY PROTOCOLS",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )

                                    if (pinSetupStep == 1 || pinSetupStep == 2) {
                                        Text("SET 6-DIGIT PRIMARY LOCK PIN", color = Color(0xFF00FF66), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    } else if (pinSetupStep == 3 || pinSetupStep == 4) {
                                        Text("SET 6-DIGIT DECOY DISTRESS PIN (Opens fake mode)", color = Color(0xFF00F0FF), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("SET 6-DIGIT PANIC PIN (Instantly wipes database)", color = Color(0xFFFF3333), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }

                                    // Display profile alias text box at very top of Step 4
                                    OutlinedTextField(
                                        value = profileAliasInput,
                                        onValueChange = { if (it.length <= 16) profileAliasInput = it.trim().replace("\\s".toRegex(), "") },
                                        placeholder = { Text("Enter Profile Alias / Name", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                        modifier = Modifier.fillMaxWidth().testTag("profile_alias_input"),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray, focusedBorderColor = Color(0xFF00F0FF))
                                    )

                                    // PIN buffers visual dots
                                    val currentEnteringPin = when (pinSetupStep) {
                                        1 -> inputPinPrimary
                                        2 -> inputPinPrimaryConfirm
                                        3 -> inputPinDistress
                                        4 -> inputPinDistressConfirm
                                        5 -> inputPinPanic
                                        else -> inputPinPanicConfirm
                                    }

                                    // Label showing step status
                                    val pinStatusLabel = when (pinSetupStep) {
                                        1 -> "Enter Primary PIN"
                                        2 -> "Confirm Primary PIN"
                                        3 -> "Enter Distress PIN"
                                        4 -> "Confirm Distress PIN"
                                        5 -> "Enter Panic PIN"
                                        else -> "Confirm Panic PIN"
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(pinStatusLabel.uppercase(), color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                        // 6-digit PIN Dots
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            for (dot in 1..SimplexViewModel.PIN_LENGTH) {
                                                val fill = dot <= currentEnteringPin.length
                                                Box(
                                                    modifier = Modifier
                                                        .size(9.dp)
                                                        .clip(RoundedCornerShape(4.5.dp))
                                                        .background(if (fill) Color(0xFF00FF66) else Color.DarkGray)
                                                )
                                            }
                                        }
                                    }

                                    // Shuffled Matrix Keypad
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            val rows = listOf(
                                                shuffledKeys.take(3),
                                                shuffledKeys.drop(3).take(3),
                                                shuffledKeys.drop(6).take(3),
                                                listOf("", shuffledKeys.getOrNull(9) ?: "0", "DEL")
                                            )

                                            rows.forEach { rowKeys ->
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    rowKeys.forEach { key ->
                                                        if (key.isEmpty()) {
                                                            Spacer(modifier = Modifier.weight(1f))
                                                        } else {
                                                            Box(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .heightIn(min = 48.dp)
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(Color(0xFF10121A))
                                                                    .border(1.dp, Color(0xFF00FF66).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                                    .clickable {
                                                                        val targetLen = SimplexViewModel.PIN_LENGTH
                                                                        if (key == "DEL") {
                                                                            when (pinSetupStep) {
                                                                                1 -> if (inputPinPrimary.isNotEmpty()) inputPinPrimary = inputPinPrimary.dropLast(1)
                                                                                2 -> if (inputPinPrimaryConfirm.isNotEmpty()) inputPinPrimaryConfirm = inputPinPrimaryConfirm.dropLast(1)
                                                                                3 -> if (inputPinDistress.isNotEmpty()) inputPinDistress = inputPinDistress.dropLast(1)
                                                                                4 -> if (inputPinDistressConfirm.isNotEmpty()) inputPinDistressConfirm = inputPinDistressConfirm.dropLast(1)
                                                                                5 -> if (inputPinPanic.isNotEmpty()) inputPinPanic = inputPinPanic.dropLast(1)
                                                                                6 -> if (inputPinPanicConfirm.isNotEmpty()) inputPinPanicConfirm = inputPinPanicConfirm.dropLast(1)
                                                                            }
                                                                        } else {
                                                                            // Append digit
                                                                            when (pinSetupStep) {
                                                                                1 -> if (inputPinPrimary.length < targetLen) {
                                                                                    inputPinPrimary += key
                                                                                    if (inputPinPrimary.length == targetLen) pinSetupStep = 2
                                                                                }
                                                                                2 -> if (inputPinPrimaryConfirm.length < targetLen) {
                                                                                    inputPinPrimaryConfirm += key
                                                                                    if (inputPinPrimaryConfirm.length == targetLen) {
                                                                                        if (inputPinPrimaryConfirm == inputPinPrimary) {
                                                                                            pinSetupStep = 3
                                                                                        } else {
                                                                                            inputPinPrimary = ""
                                                                                            inputPinPrimaryConfirm = ""
                                                                                            pinSetupStep = 1
                                                                                            Toast.makeText(context, "Primary PIN mismatch. Restarting.", Toast.LENGTH_SHORT).show()
                                                                                        }
                                                                                    }
                                                                                }
                                                                                3 -> if (inputPinDistress.length < targetLen) {
                                                                                    inputPinDistress += key
                                                                                    if (inputPinDistress.length == targetLen) {
                                                                                        if (inputPinDistress == inputPinPrimary) {
                                                                                            inputPinDistress = ""
                                                                                            Toast.makeText(context, "Distress PIN cannot match primary PIN.", Toast.LENGTH_SHORT).show()
                                                                                        } else {
                                                                                            pinSetupStep = 4
                                                                                        }
                                                                                    }
                                                                                }
                                                                                4 -> if (inputPinDistressConfirm.length < targetLen) {
                                                                                    inputPinDistressConfirm += key
                                                                                    if (inputPinDistressConfirm.length == targetLen) {
                                                                                        if (inputPinDistressConfirm == inputPinDistress) {
                                                                                            pinSetupStep = 5
                                                                                        } else {
                                                                                            inputPinDistress = ""
                                                                                            inputPinDistressConfirm = ""
                                                                                            pinSetupStep = 3
                                                                                            Toast.makeText(context, "Distress PIN mismatch. Restarting.", Toast.LENGTH_SHORT).show()
                                                                                        }
                                                                                    }
                                                                                }
                                                                                5 -> if (inputPinPanic.length < targetLen) {
                                                                                    inputPinPanic += key
                                                                                    if (inputPinPanic.length == targetLen) {
                                                                                        if (inputPinPanic == inputPinPrimary || inputPinPanic == inputPinDistress) {
                                                                                            inputPinPanic = ""
                                                                                            Toast.makeText(context, "Panic PIN cannot match primary or distress PIN.", Toast.LENGTH_SHORT).show()
                                                                                        } else {
                                                                                            pinSetupStep = 6
                                                                                        }
                                                                                    }
                                                                                }
                                                                                6 -> if (inputPinPanicConfirm.length < targetLen) {
                                                                                    inputPinPanicConfirm += key
                                                                                    if (inputPinPanicConfirm.length == targetLen) {
                                                                                        if (inputPinPanicConfirm != inputPinPanic) {
                                                                                            inputPinPanic = ""
                                                                                            inputPinPanicConfirm = ""
                                                                                            pinSetupStep = 5
                                                                                            Toast.makeText(context, "Panic PIN mismatch. Restarting.", Toast.LENGTH_SHORT).show()
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                    .padding(vertical = 12.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = key,
                                                                    color = if (key == "DEL") Color(0xFFFF3333) else Color(0xFF00FF66),
                                                                    fontFamily = FontFamily.Monospace,
                                                                    fontSize = 15.sp,
                                                                    fontWeight = FontWeight.Bold
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

                    // BOTTOM BUTTONS BAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (step > 1) {
                            Button(
                                onClick = { step -= 1 },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("< BACK", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        val canProceed = when (step) {
                            1 -> word1.isNotEmpty() && word2.isNotEmpty() && word3.isNotEmpty() && word4.isNotEmpty() && word5.isNotEmpty()
                            2 -> warningAccepted
                            3 -> inputCheck1.isNotEmpty() && inputCheck2.isNotEmpty() && inputCheck3.isNotEmpty()
                            else -> profileAliasInput.isNotEmpty() &&
                                inputPinPrimaryConfirm.length == SimplexViewModel.PIN_LENGTH &&
                                inputPinDistressConfirm.length == SimplexViewModel.PIN_LENGTH &&
                                inputPinPanicConfirm.length == SimplexViewModel.PIN_LENGTH
                        }

                        Button(
                            onClick = {
                                when (step) {
                                    1 -> {
                                        // Deterministic derivation of 24 words
                                        com.example.ui.keyboard.InAppKeyboardManager.hideKeyboard()
                                        val customList = listOf(word1, word2, word3, word4, word5)
                                        generatedWords = Bip39Utility.generateDeterministicMnemonic24(currentDate, customList)
                                        step = 2
                                    }
                                    2 -> {
                                        // Pick 3 random test indices from 1 to 24
                                        val rand = Random(System.currentTimeMillis())
                                        val indices = mutableListOf<Int>()
                                        while (indices.size < 3) {
                                            val nextVal = rand.nextInt(24) + 1
                                            if (nextVal !in indices) indices.add(nextVal)
                                        }
                                        indices.sort()
                                        testIndices = indices
                                        step = 3
                                    }
                                    3 -> {
                                        // Verify
                                        val w1 = generatedWords[testIndices[0] - 1].trim().lowercase()
                                        val w2 = generatedWords[testIndices[1] - 1].trim().lowercase()
                                        val w3 = generatedWords[testIndices[2] - 1].trim().lowercase()

                                        if (inputCheck1.trim().lowercase() == w1 &&
                                            inputCheck2.trim().lowercase() == w2 &&
                                            inputCheck3.trim().lowercase() == w3) {
                                            verificationError = false
                                            com.example.ui.keyboard.InAppKeyboardManager.hideKeyboard()
                                            step = 4
                                        } else {
                                            verificationError = true
                                        }
                                    }
                                    4 -> {
                                        // Finalize creation
                                        com.example.ui.keyboard.InAppKeyboardManager.hideKeyboard()
                                        viewModel.createAndActivateProfile(
                                            alias = profileAliasInput,
                                            mnemonic = generatedWords,
                                            pin = inputPinPrimaryConfirm,
                                            distressPin = inputPinDistressConfirm,
                                            panicPin = inputPinPanicConfirm
                                        )
                                        onDismiss()
                                    }
                                }
                            },
                            enabled = canProceed,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canProceed) Color(0xFF00FF66) else Color.DarkGray,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("step_proceed_button")
                        ) {
                            val nextText = when (step) {
                                1 -> "GENERATE SEED >"
                                2 -> "VERIFY SEED >"
                                3 -> "SETUP PIN >"
                                else -> "ACTIVATE IDENTITY"
                            }
                            Text(nextText, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

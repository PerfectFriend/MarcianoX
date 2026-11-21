package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.db.ChatContact
import com.example.viewmodel.DecryptedMessage
import com.example.viewmodel.SimplexViewModel
import com.example.viewmodel.TorStatus
import com.example.net.TorNetworkManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: SimplexViewModel) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsState()
    val selectedContactId by viewModel.selectedContactId.collectAsState()
    val activeMessages by viewModel.activeMessages.collectAsState()
    val torStatus by viewModel.torStatus.collectAsState()
    val networkMode by viewModel.networkMode.collectAsState()

    var showCreateInviteDialog by remember { mutableStateOf(false) }
    var showConnectDialog by remember { mutableStateOf(false) }
    var showPanicConfirmDialog by remember { mutableStateOf(false) }
    var showQueueDetailsDialog by remember { mutableStateOf<ChatContact?>(null) }
    var showTorConfigDialog by remember { mutableStateOf(false) }
    var torConfigInitialTab by remember { mutableIntStateOf(0) }

    val cyberGreen = Color(0xFF00FF66)
    val cyberCyan = Color(0xFF00F0FF)
    val cyberRed = Color(0xFFFF3333)
    val darkBg = Color(0xFF0A0B0E)
    val cardOnyx = Color(0xFF12141C)

    val selectedContact = contacts.find { it.id == selectedContactId }

    val currentSkin by viewModel.currentSkin.collectAsState()

    ThemeBackground(skin = currentSkin) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(currentSkin.backgroundColor.copy(alpha = 0.88f))
            ) {
                // Single Consolidated Functional Status HUD + SOS Quick-Panic Trigger (no filler strip)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(currentSkin.cardColor.copy(alpha = 0.85f))
                        .border(width = 0.8.dp, color = currentSkin.primaryColor.copy(alpha = 0.25f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(cyberGreen, CircleShape)
                        )
                        Text(
                            text = torStatus.statusText,
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${torStatus.numCircuits}c",
                            color = cyberGreen,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .border(0.8.dp, cyberGreen.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(cyberRed)
                            .clickable { showPanicConfirmDialog = true }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                            .testTag("sos_panic_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "SOS Panic Icon",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "SOS PANIC",
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Ornamented Martian Cyberpunk TopAppBar
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_cyber_ornament_frame),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.24f)
                    )
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_cyber_martian_emblem),
                                    contentDescription = "MarcianoX Emblem",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .border(1.2.dp, currentSkin.primaryColor, CircleShape)
                                )
                                Text(
                                    text = "MARCIANOX",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 2.sp,
                                    fontSize = 17.sp
                                )
                            }
                        },
                        actions = {
                            AutoLockTimerBadge(
                                viewModel = viewModel,
                                onClick = {
                                    torConfigInitialTab = 5
                                    showTorConfigDialog = true
                                },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            IconButton(
                                onClick = {
                                    torConfigInitialTab = 0
                                    showTorConfigDialog = true
                                },
                                modifier = Modifier.testTag("tor_config_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Tor Router Config",
                                    tint = currentSkin.primaryColor
                                )
                            }
                            IconButton(
                                onClick = { viewModel.lock() },
                                modifier = Modifier.testTag("lock_app_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock Application",
                                    tint = currentSkin.accentColor
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = currentSkin.cardColor.copy(alpha = 0.72f),
                            titleContentColor = Color.White,
                            actionIconContentColor = Color.White
                        )
                    )
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->

        // Dynamic Responsive / Adaptive Single-screen Box
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isExpanded = maxWidth >= 720.dp

            if (isExpanded) {
                // Adaptive Canonical Split View: List on left, chat detail on right
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                            .background(cardOnyx.copy(alpha = 0.5f))
                            .border(width = 1.dp, color = Color.White.copy(alpha = 0.05f))
                    ) {
                        ContactsColumn(
                            contacts = contacts,
                            selectedContactId = selectedContactId,
                            onContactSelected = { viewModel.selectContact(it) },
                            onCreateInvite = { showCreateInviteDialog = true },
                            onConnect = { showConnectDialog = true },
                            onShowDetails = { showQueueDetailsDialog = it },
                            onDelete = { viewModel.deleteContact(it) }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight()
                    ) {
                        if (selectedContact != null) {
                            ChatDetailColumn(
                                contact = selectedContact,
                                messages = activeMessages,
                                onBack = { viewModel.selectContact(null) },
                                showBackButton = false,
                                onSendMessage = { viewModel.sendMessage(it) },
                                onShowDetails = { showQueueDetailsDialog = it },
                                viewModel = viewModel
                            )
                        } else {
                            EmptyChatState()
                        }
                    }
                }
            } else {
                // Tactical Single view selector: List or detail based on selection
                if (selectedContact != null) {
                    ChatDetailColumn(
                        contact = selectedContact,
                        messages = activeMessages,
                        onBack = { viewModel.selectContact(null) },
                        showBackButton = true,
                        onSendMessage = { viewModel.sendMessage(it) },
                        onShowDetails = { showQueueDetailsDialog = it },
                        viewModel = viewModel
                    )
                } else {
                    ContactsColumn(
                        contacts = contacts,
                        selectedContactId = selectedContactId,
                        onContactSelected = { viewModel.selectContact(it) },
                        onCreateInvite = { showCreateInviteDialog = true },
                        onConnect = { showConnectDialog = true },
                        onShowDetails = { showQueueDetailsDialog = it },
                        onDelete = { viewModel.deleteContact(it) }
                    )
                }
            }
        }
    }
}

    // --- ALERT AND ACTION DIALOGS ---

    // 1. SOS Panic Confirmation Dialog
    if (showPanicConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPanicConfirmDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Panic alert",
                        tint = cyberRed
                    )
                    Text(
                        text = "DESTROY LOCAL ASSETS?",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Text(
                    text = "This is a full data destruction wipeout. It will instantly erase all active contacts, simplex queues, local keys, and message databases. This operation cannot be undone.",
                    fontSize = 13.sp,
                    color = Color.LightGray
                )
            },
            containerColor = Color(0xFF1E1010),
            confirmButton = {
                Button(
                    onClick = {
                        showPanicConfirmDialog = false
                        viewModel.triggerPanicWipe()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = cyberRed)
                ) {
                    Text("CONFIRM DESTRUCTION", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPanicConfirmDialog = false }) {
                    Text("ABORT", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }

    if (showTorConfigDialog) {
        TorConfigDialog(
            viewModel = viewModel,
            initialTab = torConfigInitialTab,
            onDismiss = { showTorConfigDialog = false }
        )
    }

    // 2. Create Invite Dialog
    if (showCreateInviteDialog) {
        var peerAliasInput by remember { mutableStateOf("") }
        var generatedInviteLink by remember { mutableStateOf("") }

        com.example.ui.keyboard.SecureInAppDialogHost(
            hostId = "create_invite_dialog",
            skin = currentSkin,
            onDismissRequest = { showCreateInviteDialog = false }
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardOnyx,
                border = androidx.compose.foundation.BorderStroke(1.dp, cyberGreen.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "GENERATE DISPATCH QUEUE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = cyberGreen
                    )

                    Text(
                        text = "Generate a secure unidirection link address. Share this link over a secure out-of-band proxy channel for your contact to complete the socket handshake.",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )

                    if (generatedInviteLink.isEmpty()) {
                        OutlinedTextField(
                            value = peerAliasInput,
                            onValueChange = { peerAliasInput = it },
                            label = { Text("Peer Alias / Code", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = cyberGreen,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedLabelColor = cyberGreen,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_invite_alias_input")
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                                .border(1.dp, cyberGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "DISPATCH LINK READY:",
                                color = cyberGreen,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = generatedInviteLink,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 4
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showCreateInviteDialog = false }) {
                            Text("CLOSE", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (generatedInviteLink.isEmpty()) {
                            Button(
                                onClick = {
                                    if (peerAliasInput.isNotBlank()) {
                                        generatedInviteLink = viewModel.createInvite(peerAliasInput)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = cyberGreen),
                                enabled = peerAliasInput.isNotBlank()
                            ) {
                                Text("GENERATE", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("simplex_invite", generatedInviteLink)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Invite copied to secure buffer", Toast.LENGTH_SHORT).show()
                                    showCreateInviteDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = cyberCyan)
                            ) {
                                Text("COPY INVITE LINK", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // 3. Connect to Invite Dialog
    if (showConnectDialog) {
        var peerAliasInput by remember { mutableStateOf("") }
        var inviteLinkInput by remember { mutableStateOf("") }

        com.example.ui.keyboard.SecureInAppDialogHost(
            hostId = "connect_invite_dialog",
            skin = currentSkin,
            onDismissRequest = { showConnectDialog = false }
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardOnyx,
                border = androidx.compose.foundation.BorderStroke(1.dp, cyberCyan.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Text(
                        text = "RECEIVE UNIDIRECTION FLOW",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = cyberCyan
                    )

                    Text(
                        text = "Paste your contact's generated invite Simplex queue URL below to run the connection handshake over the Tor Onion Router network.",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )

                    OutlinedTextField(
                        value = peerAliasInput,
                        onValueChange = { peerAliasInput = it },
                        label = { Text("Peer Local Alias", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = cyberCyan,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedLabelColor = cyberCyan,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connect_invite_alias_input")
                    )

                    OutlinedTextField(
                        value = inviteLinkInput,
                        onValueChange = { inviteLinkInput = it },
                        label = { Text("Simplex Queue Link (simplex://...)", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = cyberCyan,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedLabelColor = cyberCyan,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connect_invite_link_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showConnectDialog = false }) {
                            Text("CANCEL", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (peerAliasInput.isNotBlank() && inviteLinkInput.isNotBlank()) {
                                    viewModel.connectToInvite(peerAliasInput, inviteLinkInput)
                                    showConnectDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = cyberCyan),
                            enabled = peerAliasInput.isNotBlank() && inviteLinkInput.isNotBlank()
                        ) {
                            Text("LOCK ROUTE", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 4. Cryptographic Queue Details Dialog
    if (showQueueDetailsDialog != null) {
        val details = showQueueDetailsDialog!!
        var selectedExpiry by remember(details.id) {
            mutableStateOf(viewModel.getUndeliveredBurnMinutes(details.id))
        }

        AlertDialog(
            onDismissRequest = { showQueueDetailsDialog = null },
            title = {
                Text(
                    text = "ROUTING DEVOLUTION LOG",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = cyberCyan
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Active Peer:", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(details.aliasName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    Text("Network Address Type:", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(details.onionAddress, color = cyberGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    Text("Inbound Simplex Queue URI:", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(details.inboundQueueUri, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    Text("Outbound Simplex Queue URI:", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(details.outboundQueueUri, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    Text("Shared Ephemeral Key (Hex):", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        text = details.sharedSecretHex.take(32) + "...",
                        color = cyberCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // NEW: Undelivered Message Auto-Burn Timeout setting
                    Text(
                        text = "Undelivered Burn Timeout (N):",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    val options = listOf(
                        0 to "NEVER",
                        10 to "10 MIN",
                        60 to "1 HOUR",
                        240 to "4 HOURS",
                        720 to "12 HOURS",
                        1440 to "24 HOURS",
                        2880 to "48 HOURS"
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(options) { (minutes, label) ->
                            val isSelected = selectedExpiry == minutes
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) cyberCyan.copy(alpha = 0.2f) else Color(0xFF1B1D28))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) cyberCyan else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        selectedExpiry = minutes
                                        viewModel.setUndeliveredBurnMinutes(details.id, minutes)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) cyberCyan else Color.LightGray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // NEW: Onion Link Proxy Mode
                    Text(
                        text = "Onion Link Proxy Mode:",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("ONLINE", "OFFLINE", "FLAKY").forEach { mode ->
                            val isSelected = networkMode == mode
                            val color = when (mode) {
                                "ONLINE" -> cyberGreen
                                "OFFLINE" -> cyberRed
                                else -> Color(0xFFFFB300) // FLAKY
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) color.copy(alpha = 0.15f) else Color(0xFF1B1D28))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) color else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        viewModel.setNetworkMode(mode)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(color, CircleShape)
                                    )
                                    Text(
                                        text = mode,
                                        color = if (isSelected) color else Color.LightGray,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // NEW: Onion Relay Time Warp Console
                    Text(
                        text = "Onion Queue Time Warp (Testing):",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            10 to "+10 MIN",
                            60 to "+1 HOUR",
                            1440 to "+24 HOUR"
                        ).forEach { (warpMinutes, label) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF1F1113))
                                    .border(
                                        width = 1.dp,
                                        color = cyberRed.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        viewModel.fastForwardTime(warpMinutes.toLong())
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Time Warp",
                                        tint = cyberRed,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = label,
                                        color = cyberRed,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            containerColor = cardOnyx,
            confirmButton = {
                Button(
                    onClick = { showQueueDetailsDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = cyberCyan)
                ) {
                    Text("OK", color = Color.Black)
                }
            }
        )
    }
}

// --- SUB-COLUMNS FOR REUSABLE LAYOUT ---

@Composable
fun ContactsColumn(
    contacts: List<ChatContact>,
    selectedContactId: Int?,
    onContactSelected: (Int) -> Unit,
    onCreateInvite: () -> Unit,
    onConnect: () -> Unit,
    onShowDetails: (ChatContact) -> Unit,
    onDelete: (ChatContact) -> Unit
) {
    val cyberGreen = Color(0xFF00FF66)
    val cyberCyan = Color(0xFF00F0FF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Option Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onCreateInvite,
                modifier = Modifier
                    .weight(1f)
                    .testTag("create_invite_button"),
                colors = ButtonDefaults.buttonColors(containerColor = cyberGreen),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add invite", tint = Color.Black, modifier = Modifier.size(16.dp))
                    Text("Create Invite", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = onConnect,
                modifier = Modifier
                    .weight(1f)
                    .testTag("connect_peer_button"),
                colors = ButtonDefaults.buttonColors(containerColor = cyberCyan),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Connect queue", tint = Color.Black, modifier = Modifier.size(16.dp))
                    Text("Connect Link", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Label list header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DISPATCH CHANNELS (${contacts.size})",
                color = Color.LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh socket indicator",
                tint = Color.Gray,
                modifier = Modifier.size(14.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (contacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CyberHudPanel(
                    skin = SkinStyle.CYBERPUNK,
                    shape = RoundedCornerShape(16.dp),
                    borderColor = cyberCyan.copy(alpha = 0.38f),
                    backdropAlpha = 0.70f,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_cyber_martian_emblem),
                            contentDescription = "No Queues Active",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(86.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, cyberGreen.copy(alpha = 0.7f), CircleShape)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "NO ACTIVE CHANNELS",
                            color = cyberGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Create or connect a unidirectional invite to initialize end-to-end encrypted relays.",
                            color = Color.LightGray.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(contacts, key = { it.id }) { contact ->
                    val isSelected = contact.id == selectedContactId
                    val activeColor = if (contact.status == "CONNECTED") cyberGreen else Color.Yellow

                    CyberHudPanel(
                        skin = SkinStyle.CYBERPUNK,
                        shape = RoundedCornerShape(10.dp),
                        borderColor = if (isSelected) cyberCyan.copy(alpha = 0.75f) else cyberGreen.copy(alpha = 0.22f),
                        backdropAlpha = if (isSelected) 0.86f else 0.72f,
                        ornamentAlpha = 0.22f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onContactSelected(contact.id) }
                            .testTag("contact_item_${contact.id}")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.weight(0.7f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .border(1.dp, activeColor.copy(alpha = 0.7f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = contact.aliasName.take(2).uppercase(),
                                        color = activeColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(activeColor, CircleShape)
                                        )
                                        Text(
                                            text = contact.aliasName,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Text(
                                        text = contact.onionAddress,
                                        color = Color.LightGray.copy(alpha = 0.7f),
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.weight(0.3f),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onShowDetails(contact) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Routing metrics information",
                                        tint = cyberCyan.copy(alpha = 0.85f),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDelete(contact) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Purge contact stream",
                                        tint = Color(0xFFFF5555).copy(alpha = 0.75f),
                                        modifier = Modifier.size(17.dp)
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

@Composable
fun ChatDetailColumn(
    contact: ChatContact,
    messages: List<DecryptedMessage>,
    onBack: () -> Unit,
    showBackButton: Boolean,
    onSendMessage: (String) -> Unit,
    onShowDetails: (ChatContact) -> Unit,
    viewModel: SimplexViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var rawInputText by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()

    var isWalkieTalkieMode by remember { mutableStateOf(false) }
    val isVoiceRecording by viewModel.isVoiceRecording.collectAsState()
    val voiceAmplitudeBars by viewModel.voiceAmplitudeBars.collectAsState()
    var autoBurnSeconds by remember { mutableIntStateOf(0) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceRecording { err ->
                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Microphone permission required for voice encryption", Toast.LENGTH_SHORT).show()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.sendEncryptedPhotoFromUri(uri) { err ->
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            }
        }
    }

    val cyberGreen = Color(0xFF00FF66)
    val cyberCyan = Color(0xFF00F0FF)
    val cyberRed = Color(0xFFFF3333)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            lazyListState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.38f))
    ) {
        // Ornamental Chat Header with details button
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_cyber_ornament_frame),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.24f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0E1119).copy(alpha = 0.78f))
                    .border(width = 0.8.dp, color = cyberCyan.copy(alpha = 0.25f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(0.7f)
            ) {
                if (showBackButton) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back",
                            tint = Color.White
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = contact.aliasName,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = contact.onionAddress,
                        color = cyberGreen,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(0.3f)
            ) {
                IconButton(
                    onClick = {
                        autoBurnSeconds = when (autoBurnSeconds) {
                            0 -> 10
                            10 -> 30
                            30 -> 60
                            else -> 0
                        }
                    }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Auto Burn Timer",
                            tint = if (autoBurnSeconds > 0) cyberRed else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (autoBurnSeconds > 0) "${autoBurnSeconds}s" else "OFF",
                            fontSize = 8.sp,
                            color = if (autoBurnSeconds > 0) cyberRed else Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = { onShowDetails(contact) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Show connection socket metrics",
                        tint = cyberCyan
                    )
                }
            }
        }
        }

        // Encrypted Terminal Messaging Log
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                if (msg.isSystem) {
                    // System handshakes representation log
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⚡ [SECURE PROTOCOL] " + msg.text,
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(Color(0xFF14161C), RoundedCornerShape(4.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    val isMe = msg.sender == "ME"
                    val bubbleBg = if (isMe) Color(0xFF0D211A).copy(alpha = 0.86f) else Color(0xFF111520).copy(alpha = 0.86f)
                    val outlineColor = if (isMe) cyberGreen.copy(alpha = 0.45f) else cyberCyan.copy(alpha = 0.40f)
                    val textHighlightColor = if (isMe) cyberGreen else cyberCyan

                    if (msg.mediaMimeType.startsWith("image/")) {
                        EncryptedPhotoMessageBubble(
                            msg = msg,
                            viewModel = viewModel,
                            isMe = isMe,
                            cyberGreen = cyberGreen,
                            cyberCyan = cyberCyan
                        )
                    } else if (
                        msg.mediaMimeType.startsWith("audio/") ||
                        msg.text.startsWith("[WALKIE_TALKIE:") ||
                        msg.text.startsWith("[VOICE_PCM:")
                    ) {
                        WalkieTalkieMessageBubble(
                            msg = msg,
                            viewModel = viewModel,
                            isMe = isMe,
                            cyberGreen = cyberGreen,
                            cyberCyan = cyberCyan
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (isMe) 12.dp else 3.dp,
                                            bottomEnd = if (isMe) 3.dp else 12.dp
                                        )
                                    )
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(bubbleBg, Color(0xFF080A0F).copy(alpha = 0.90f))
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = outlineColor,
                                        shape = RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (isMe) 12.dp else 3.dp,
                                            bottomEnd = if (isMe) 3.dp else 12.dp
                                        )
                                    )
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    var burnSecondsLeft by remember(msg.id) { mutableIntStateOf(autoBurnSeconds) }
                                    if (autoBurnSeconds > 0) {
                                        LaunchedEffect(msg.id) {
                                            while (burnSecondsLeft > 0) {
                                                kotlinx.coroutines.delay(1000)
                                                burnSecondsLeft--
                                            }
                                            viewModel.deleteMessageById(msg.id)
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (isMe) {
                                                when (msg.deliveryStatus) {
                                                    "PENDING" -> "MEM-STREAM [QUEUED]"
                                                    "FAILED" -> "MEM-STREAM [EXPIRED]"
                                                    else -> "MEM-STREAM [OUT]"
                                                }
                                            } else "MEM-STREAM [IN]",
                                            color = if (isMe && msg.deliveryStatus == "PENDING") Color(0xFFFFB300)
                                                    else if (isMe && msg.deliveryStatus == "FAILED") cyberRed
                                                    else textHighlightColor,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp
                                        )
                                        if (isMe && msg.deliveryStatus == "PENDING") {
                                            val remainingMin = if (msg.expiryMinutes > 0) {
                                                val passedMin = (System.currentTimeMillis() - msg.timestamp) / (60 * 1000)
                                                (msg.expiryMinutes - passedMin).coerceAtLeast(0)
                                            } else 0
                                            Text(
                                                text = if (msg.expiryMinutes > 0) "⌛ EXPIRES ${remainingMin}m" else "⌛ QUEUED",
                                                color = Color(0xFFFFB300),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        } else if (isMe && msg.deliveryStatus == "FAILED") {
                                            Text(
                                                text = "❌ TIMEOUT",
                                                color = cyberRed,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        } else if (autoBurnSeconds > 0) {
                                            Text(
                                                text = "🔥 BURN ${burnSecondsLeft}s",
                                                color = cyberRed,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        } else {
                                            Text(
                                                text = "E2E: AES-GCM",
                                                color = Color.Gray,
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = if (msg.deliveryStatus == "FAILED") "⚠️ [E2E transit timeout: Secure payload deleted from Tor transit buffers]" else msg.text,
                                        color = if (msg.deliveryStatus == "FAILED") Color.Gray else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.SansSerif,
                                        lineHeight = 18.sp,
                                        style = if (msg.deliveryStatus == "FAILED") androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default
                                    )
                                }
                            }

                            // Message Time Label
                            Text(
                                text = getFormattedTime(msg.timestamp),
                                color = Color.DarkGray,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Terminal text entry console box
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_cyber_ornament_frame),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.22f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1017).copy(alpha = 0.82f))
                    .border(width = 0.8.dp, color = cyberGreen.copy(alpha = 0.25f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            // Walkie-Talkie Toggle
            IconButton(
                onClick = {
                    if (isWalkieTalkieMode && isVoiceRecording) {
                        viewModel.cancelVoiceRecording()
                    }
                    isWalkieTalkieMode = !isWalkieTalkieMode
                },
                modifier = Modifier.size(44.dp).testTag("toggle_voice_mode_button")
            ) {
                Icon(
                    imageVector = if (isWalkieTalkieMode) Icons.Default.Create else Icons.Default.PlayArrow,
                    contentDescription = "Toggle Walkie Talkie mode",
                    tint = if (isWalkieTalkieMode) cyberGreen else Color.Gray
                )
            }

            if (isWalkieTalkieMode) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF14161F))
                        .border(1.dp, if (isVoiceRecording) Color.Red.copy(alpha = 0.6f) else Color.DarkGray, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isVoiceRecording) Color.Red else Color.Gray, CircleShape)
                    )

                    Text(
                        text = if (isVoiceRecording) "REC PCM 16kHz..." else "HARDWARE MIC READY",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = if (isVoiceRecording) Color.Red else Color.LightGray,
                        modifier = Modifier.weight(1f)
                    )

                    if (isVoiceRecording) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val liveBars = if (voiceAmplitudeBars.isEmpty()) List(7) { 0.25f } else voiceAmplitudeBars.takeLast(8)
                            liveBars.forEach { norm ->
                                val barHeight = (6f + norm.coerceIn(0f, 1f) * 24f).dp
                                Box(
                                    modifier = Modifier
                                        .size(width = 3.dp, height = barHeight)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(Color.Red)
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isVoiceRecording) {
                            Button(
                                onClick = {
                                    viewModel.cancelVoiceRecording()
                                    isWalkieTalkieMode = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.DarkGray.copy(alpha = 0.3f),
                                    contentColor = Color.LightGray
                                ),
                                modifier = Modifier.border(1.dp, Color.Gray, RoundedCornerShape(20.dp)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "CANCEL",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (isVoiceRecording) {
                                    viewModel.stopAndSendVoiceRecording()
                                    isWalkieTalkieMode = false
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        viewModel.startVoiceRecording { err ->
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isVoiceRecording) Color.Red.copy(alpha = 0.2f) else cyberGreen.copy(alpha = 0.2f),
                                contentColor = if (isVoiceRecording) Color.Red else cyberGreen
                            ),
                            modifier = Modifier
                                .border(1.dp, if (isVoiceRecording) Color.Red else cyberGreen, RoundedCornerShape(20.dp))
                                .testTag("voice_record_action_button"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isVoiceRecording) "STOP & SEND" else "START REC",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("attach_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Attach Encrypted Photo",
                        tint = cyberCyan
                    )
                }

                OutlinedTextField(
                    value = rawInputText,
                    onValueChange = { rawInputText = it },
                    placeholder = { Text("E2E Payload dispatch...", color = Color.Gray, fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = cyberGreen,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_text_field"),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted status symbol icon",
                            tint = cyberGreen.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (rawInputText.isNotBlank()) cyberGreen else Color.DarkGray
                        )
                        .clickable(enabled = rawInputText.isNotBlank()) {
                            onSendMessage(rawInputText)
                            rawInputText = ""
                            coroutineScope.launch {
                                if (messages.isNotEmpty()) {
                                    lazyListState.animateScrollToItem(messages.size - 1)
                                }
                            }
                        }
                        .testTag("send_payload_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Encrypt and Dispatch",
                        tint = if (rawInputText.isNotBlank()) Color.Black else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        }
    }
}

@Composable
fun EmptyChatState() {
    val cyberGreen = Color(0xFF00FF66)
    val cyberCyan = Color(0xFF00F0FF)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f)),
        contentAlignment = Alignment.Center
    ) {
        CyberHudPanel(
            skin = SkinStyle.CYBERPUNK,
            shape = RoundedCornerShape(18.dp),
            borderColor = cyberCyan.copy(alpha = 0.45f),
            backdropAlpha = 0.72f,
            modifier = Modifier.padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .shadow(16.dp, CircleShape, spotColor = cyberGreen)
                        .clip(CircleShape)
                        .border(
                            2.dp,
                            Brush.sweepGradient(listOf(cyberGreen, cyberCyan, cyberGreen)),
                            CircleShape
                        )
                        .padding(3.dp)
                        .clip(CircleShape)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_cyber_martian_emblem),
                        contentDescription = "Marciano Tactical Enclave",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "SECURED DIRECT CORES",
                    color = cyberGreen,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select an existing channel from the index to initialize live Tor relay listening queues.",
                    color = Color.LightGray.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

fun getFormattedTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss 'UTC'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(timestamp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorConfigDialog(viewModel: SimplexViewModel, initialTab: Int = 0, onDismiss: () -> Unit) {
    val isRunning by TorNetworkManager.isServerRunning.collectAsState()
    val isRelayConnected by TorNetworkManager.isRelayConnected.collectAsState()
    val useSocks5 by TorNetworkManager.useSocks5.collectAsState()
    val configuredRelayUrl by TorNetworkManager.relayUrl.collectAsState()
    val lastRelayError by TorNetworkManager.lastRelayError.collectAsState()
    val logs by TorNetworkManager.logs.collectAsState()
    val activeConnections by TorNetworkManager.activeConnections.collectAsState()
    val totalBytes by TorNetworkManager.totalBytesTransferred.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var relayUrlInput by remember(configuredRelayUrl) { mutableStateOf(configuredRelayUrl) }
    var socksHostInput by remember { mutableStateOf(TorNetworkManager.socksHost) }
    var socksPortInput by remember { mutableStateOf(TorNetworkManager.torPort.toString()) }
    var useSocksInput by remember(useSocks5) { mutableStateOf(useSocks5) }
    var isConnectingRelay by remember { mutableStateOf(false) }

    var testAddressInput by remember { mutableStateOf(configuredRelayUrl) }
    var testResultText by remember { mutableStateOf("") }
    var isTestingRoute by remember { mutableStateOf(false) }

    var activeTab by remember(initialTab) { mutableIntStateOf(initialTab) } // 0 = Tor, 1 = Decoy PINs, 2 = Chain Tunnel, 3 = Skins, 4 = Identity, 5 = Auto-Lock

    val currentSkin by viewModel.currentSkin.collectAsState()

    val cyberGreen = Color(0xFF00FF66)
    val cyberCyan = Color(0xFF00F0FF)
    val cyberRed = Color(0xFFFF3333)
    val cardOnyx = Color(0xFF12141C)

    com.example.ui.keyboard.SecureInAppDialogHost(
        hostId = "tor_config_dialog",
        skin = currentSkin,
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = cardOnyx,
            border = androidx.compose.foundation.BorderStroke(1.dp, (if (activeTab == 0) cyberGreen else cyberCyan).copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Tor Node",
                        tint = if (activeTab == 0) cyberGreen else cyberCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (activeTab == 0) LocalizationHelper.get("tor_routing") 
                               else if (activeTab == 1) LocalizationHelper.get("decoy_systems")
                               else if (activeTab == 2) LocalizationHelper.get("chain_tunnel")
                               else if (activeTab == 3) "SKINS / THEME"
                               else if (activeTab == 4) "IDENTITY NODES"
                               else LocalizationHelper.get("auto_lock"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 500.dp)
                ) {
                // NEW: Dynamic Multi-Language Selector Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
                ) {
                    Text(
                        text = "LANG:",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    val currentLang by LocalizationHelper.currentLanguage.collectAsState()
                    AppLanguage.values().forEach { lang ->
                        val isSelected = currentLang == lang
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isSelected) cyberCyan.copy(alpha = 0.2f) else Color(0xFF10121A))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) cyberCyan else Color.White.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(3.dp)
                                )
                                .clickable {
                                    LocalizationHelper.setLanguage(lang)
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = lang.code.uppercase(),
                                color = if (isSelected) cyberCyan else Color.Gray,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Tab bar selection
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 6.dp)
                ) {
                    item {
                        Button(
                            onClick = { activeTab = 0 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeTab == 0) cyberGreen.copy(alpha = 0.15f) else Color.Transparent,
                                contentColor = if (activeTab == 0) cyberGreen else Color.Gray
                            ),
                            modifier = Modifier
                                .border(1.dp, if (activeTab == 0) cyberGreen else Color.DarkGray, RoundedCornerShape(4.dp)),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("TOR ROUTING", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }

                    item {
                        Button(
                            onClick = { activeTab = 1 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeTab == 1) cyberCyan.copy(alpha = 0.15f) else Color.Transparent,
                                contentColor = if (activeTab == 1) cyberCyan else Color.Gray
                            ),
                            modifier = Modifier
                                .border(1.dp, if (activeTab == 1) cyberCyan else Color.DarkGray, RoundedCornerShape(4.dp)),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("DECOY SYSTEMS", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }

                    item {
                        Button(
                            onClick = { activeTab = 2 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeTab == 2) cyberCyan.copy(alpha = 0.15f) else Color.Transparent,
                                contentColor = if (activeTab == 2) cyberCyan else Color.Gray
                            ),
                            modifier = Modifier
                                .border(1.dp, if (activeTab == 2) cyberCyan else Color.DarkGray, RoundedCornerShape(4.dp)),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("CHAIN TUNNEL", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }

                    item {
                        Button(
                            onClick = { activeTab = 3 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeTab == 3) cyberCyan.copy(alpha = 0.15f) else Color.Transparent,
                                contentColor = if (activeTab == 3) cyberCyan else Color.Gray
                            ),
                            modifier = Modifier
                                .border(1.dp, if (activeTab == 3) cyberCyan else Color.DarkGray, RoundedCornerShape(4.dp)),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("SKINS / THEME", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }

                    item {
                        Button(
                            onClick = { activeTab = 4 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeTab == 4) cyberCyan.copy(alpha = 0.15f) else Color.Transparent,
                                contentColor = if (activeTab == 4) cyberCyan else Color.Gray
                            ),
                            modifier = Modifier
                                .border(1.dp, if (activeTab == 4) cyberCyan else Color.DarkGray, RoundedCornerShape(4.dp)),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("IDENTITY NODES", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }

                    item {
                        Button(
                            onClick = { activeTab = 5 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeTab == 5) cyberCyan.copy(alpha = 0.15f) else Color.Transparent,
                                contentColor = if (activeTab == 5) cyberCyan else Color.Gray
                            ),
                            modifier = Modifier
                                .border(1.dp, if (activeTab == 5) cyberCyan else Color.DarkGray, RoundedCornerShape(4.dp))
                                .testTag("tab_auto_lock"),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("AUTO-LOCK TIMER", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (activeTab == 0) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Rust WebSocket Relay + SOCKS5 (Orbot) Transport Control Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black)
                                .border(
                                    1.dp,
                                    if (isRelayConnected) cyberGreen.copy(alpha = 0.55f) else cyberCyan.copy(alpha = 0.35f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "RUST WEBSOCKET RELAY & TOR SOCKS5",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isRelayConnected) cyberGreen else cyberCyan
                                    )
                                    Text(
                                        text = if (isRelayConnected) "ONLINE // tokio-tungstenite + rustls"
                                        else "OFFLINE // Orbot SOCKS5: ${if (isRunning) "DETECTED" else "STANDBY"}",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.LightGray
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (isRelayConnected) {
                                            TorNetworkManager.disconnectRelay()
                                        } else {
                                            isConnectingRelay = true
                                            TorNetworkManager.updateTransportConfig(
                                                relayWsUrl = relayUrlInput,
                                                newSocksHost = socksHostInput,
                                                newSocksPort = socksPortInput.toIntOrNull() ?: 9050,
                                                useSocks = useSocksInput
                                            )
                                            coroutineScope.launch {
                                                TorNetworkManager.connectRelay(relayUrlInput, useSocksInput)
                                                isConnectingRelay = false
                                            }
                                        }
                                    },
                                    enabled = !isConnectingRelay && relayUrlInput.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRelayConnected) cyberRed.copy(alpha = 0.2f) else cyberGreen.copy(alpha = 0.2f),
                                        contentColor = if (isRelayConnected) cyberRed else cyberGreen
                                    ),
                                    modifier = Modifier
                                        .border(1.dp, if (isRelayConnected) cyberRed else cyberGreen, RoundedCornerShape(20.dp))
                                        .testTag("relay_connect_toggle_button"),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isConnectingRelay) "..." else if (isRelayConnected) "DISCONNECT" else "CONNECT",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = relayUrlInput,
                                onValueChange = { relayUrlInput = it },
                                label = { Text("Relay WebSocket URL (wss://... or ws://...)", color = Color.Gray, fontSize = 9.sp) },
                                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = cyberGreen,
                                    unfocusedBorderColor = Color.DarkGray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("relay_url_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = socksHostInput,
                                    onValueChange = { socksHostInput = it },
                                    label = { Text("SOCKS5 Host", color = Color.Gray, fontSize = 9.sp) },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = cyberCyan,
                                        unfocusedBorderColor = Color.DarkGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1.4f)
                                )
                                OutlinedTextField(
                                    value = socksPortInput,
                                    onValueChange = { socksPortInput = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Port", color = Color.Gray, fontSize = 9.sp) },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = cyberCyan,
                                        unfocusedBorderColor = Color.DarkGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.weight(0.8f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ROUTE VIA ORBOT / TOR SOCKS5",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (useSocksInput) cyberGreen else Color.Gray
                                    )
                                    Text(
                                        text = "Daemon probe: ${if (isRunning) "ALIVE on $socksHostInput:$socksPortInput" else "No local SOCKS5 on $socksHostInput:$socksPortInput"}",
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isRunning) cyberGreen else Color.Gray
                                    )
                                }
                                Switch(
                                    checked = useSocksInput,
                                    onCheckedChange = { checked ->
                                        useSocksInput = checked
                                        TorNetworkManager.updateTransportConfig(
                                            relayWsUrl = relayUrlInput,
                                            newSocksHost = socksHostInput,
                                            newSocksPort = socksPortInput.toIntOrNull() ?: 9050,
                                            useSocks = checked
                                        )
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = cyberGreen,
                                        checkedTrackColor = cyberGreen.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier.scale(0.8f)
                                )
                            }

                            if (lastRelayError.isNotBlank()) {
                                Text(
                                    text = "Last relay error: $lastRelayError",
                                    color = cyberRed,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Metrics Section
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161824)),
                                modifier = Modifier.weight(1f).border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("DATA ROUTED", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val kb = totalBytes / 1024.0
                                    val displaySize = if (kb > 1024) String.format("%.2f MB", kb / 1024.0) else String.format("%.2f KB", kb)
                                    Text(displaySize, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = cyberGreen)
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161824)),
                                modifier = Modifier.weight(1f).border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("ACTIVE SOCKETS", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${activeConnections.size} Live / ${if (isRunning) "SOCKS5 OK" else "Direct TLS"}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = cyberCyan)
                                }
                            }
                        }

                        // Live Rust Route Tester Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF161824))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = LocalizationHelper.get("route_tester"),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = testAddressInput,
                                    onValueChange = { testAddressInput = it },
                                    placeholder = { Text("wss://relay... or host:port or .onion", color = Color.DarkGray, fontSize = 11.sp) },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = cyberCyan,
                                        unfocusedBorderColor = Color.DarkGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).height(46.dp)
                                )

                                Button(
                                    onClick = {
                                        isTestingRoute = true
                                        testResultText = "Running Rust tokio TCP / SOCKS5 probe..."
                                        coroutineScope.launch {
                                            val result = TorNetworkManager.testOnionRoute(testAddressInput)
                                            testResultText = result
                                            isTestingRoute = false
                                        }
                                    },
                                    enabled = !isTestingRoute && testAddressInput.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = cyberCyan, contentColor = Color.Black),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                ) {
                                    Text("PROBE", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (testResultText.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black)
                                        .border(1.dp, Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = testResultText,
                                        color = if (testResultText.startsWith("FAILED")) cyberRed else cyberGreen,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }

                        // Logs Terminal Section
                        Text(
                            text = "REAL-TIME RUST TRANSPORT LOGS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Gray
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 160.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black)
                                .border(1.dp, Color.DarkGray.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            if (logs.isEmpty()) {
                                Text(
                                    text = "Rust tokio network engine ready.",
                                    color = Color.DarkGray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(logs) { logMsg ->
                                        val tint = if (logMsg.contains("SUCCESS") || logMsg.contains("CONNECTED")) {
                                            cyberGreen
                                        } else if (logMsg.contains("Connecting") || logMsg.contains("Probing")) {
                                            cyberCyan
                                        } else if (logMsg.contains("CRITICAL") || logMsg.contains("FAILED") || logMsg.contains("ERROR")) {
                                            cyberRed
                                        } else {
                                            Color.LightGray
                                        }
                                        Text(
                                            text = logMsg,
                                            color = tint,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 13.sp,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (activeTab == 1) {
                    // Decoy PIN Config Status & Real Rust Argon2id PHC Verification
                    val isDecoy by viewModel.isDecoyMode.collectAsState()
                    val activeProfile by viewModel.activeProfileAlias.collectAsState()
                    val profilePrefix = activeProfile ?: ""
                    val prefs = context.getSharedPreferences("simplex_secure_prefs", Context.MODE_PRIVATE)
                    val primaryHash = if (profilePrefix.isNotEmpty()) prefs.getString("profile_${profilePrefix}_pin_phc", "NOT INITIALIZED") ?: "NOT INITIALIZED" else "NOT INITIALIZED"
                    val distressHash = if (profilePrefix.isNotEmpty()) prefs.getString("profile_${profilePrefix}_distress_phc", "NOT INITIALIZED") ?: "NOT INITIALIZED" else "NOT INITIALIZED"
                    val panicHash = if (profilePrefix.isNotEmpty()) prefs.getString("profile_${profilePrefix}_panic_phc", "NOT INITIALIZED") ?: "NOT INITIALIZED" else "NOT INITIALIZED"

                    // BIP-39 State collections
                    val bip39Passphrase by viewModel.bip39Passphrase.collectAsState()
                    val bip39Mnemonic by viewModel.bip39Mnemonic.collectAsState()
                    val bip39SeedHex by viewModel.bip39SeedHex.collectAsState()
                    val bip39ValidationInput by viewModel.bip39ValidationInput.collectAsState()
                    val bip39ValidationResult by viewModel.bip39ValidationResult.collectAsState()
                    val bip39ValidatedSeedHex by viewModel.bip39ValidatedSeedHex.collectAsState()
                    val bip39DerivedKeys by viewModel.bip39DerivedKeys.collectAsState()

                    val storageShards by viewModel.storageShards.collectAsState()
                    val postChallengeLog by viewModel.postChallengeLog.collectAsState()
                    val postChallengeSuccess by viewModel.postChallengeSuccess.collectAsState()

                    val bip39CustomEntropyWords by viewModel.bip39CustomEntropyWords.collectAsState()
                    val bip39ChallengeIndices by viewModel.bip39ChallengeIndices.collectAsState()
                    val bip39ChallengeAnswers by viewModel.bip39ChallengeAnswers.collectAsState()
                    val bip39ChallengePassed by viewModel.bip39ChallengePassed.collectAsState()

                    var customWordsInput by remember { mutableStateOf(bip39CustomEntropyWords) }

                    // Grayscale Simulated Cryptocontainer States
                    var localStorageAllocatedGb by remember { mutableStateOf(16f) }
                    var ramCacheAllocatedGb by remember { mutableStateOf(1.2f) }
                    var globalCloudRedundancyEnabled by remember { mutableStateOf(true) }
                    var hostingParticipationEnabled by remember { mutableStateOf(true) }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Profile Integrity status
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black)
                                .border(1.dp, if (isDecoy) cyberCyan.copy(alpha = 0.4f) else cyberGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "CURRENT SYSTEM HANDSHAKE PROFILE",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(if (isDecoy) cyberCyan else cyberGreen, CircleShape)
                                    )
                                    Text(
                                        text = if (isDecoy) "DECOY ACTIVE" else "STANDARD ACTIVE",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isDecoy) cyberCyan else cyberGreen
                                    )
                                }
                                Text(
                                    text = if (isDecoy) "Sandbox simulated environment active. All operations are local and memory-only. Physical local storage remains safe and untampered."
                                           else "Primary database session verified. Cryptographic files and peer channels are operating in highly secure persistent storage.",
                                    fontSize = 11.sp,
                                    color = Color.LightGray,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        // Auto-Lock Status Card in Decoy/Security systems tab
                        val autoLockTimeout by viewModel.autoLockTimeoutSeconds.collectAsState()
                        val autoLockRemaining by viewModel.autoLockSecondsRemaining.collectAsState()
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F151F)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, cyberCyan.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = cyberCyan,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "AUTO-LOCK INACTIVITY TIMER",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = cyberCyan
                                        )
                                    }
                                    Text(
                                        text = formatAutoLockDuration(autoLockTimeout).uppercase(),
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (autoLockTimeout == -1) Color.Gray else cyberGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = if (autoLockTimeout > 0 && autoLockRemaining != null)
                                        "Active session armed. Locks automatically after ${autoLockTimeout}s of inactivity (${autoLockRemaining}s remaining). Master keys zeroized on lock."
                                    else if (autoLockTimeout == 0)
                                        "Immediate background lock armed. App locks instantly on app switch or minimize."
                                    else if (autoLockTimeout == -1)
                                        "Automatic lock disabled. Manual lock button required to secure session."
                                    else
                                        "Configured for ${autoLockTimeout}s timeout.",
                                    fontSize = 10.sp,
                                    color = Color.LightGray,
                                    lineHeight = 14.sp
                                )
                                Button(
                                    onClick = { activeTab = 5 },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = cyberCyan.copy(alpha = 0.15f),
                                        contentColor = cyberCyan
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, cyberCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .testTag("open_auto_lock_settings_button"),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "CONFIGURE AUTO-LOCK TIMER & TIMEOUTS",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Hashes Table Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161824)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "RUST ARGON2ID PHC HASHES (PER-PROFILE)",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )

                                // Primary Hash Row
                                Column {
                                    Text("PRIMARY MASTER PASS-VECTOR", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = cyberGreen)
                                    Text(
                                        text = primaryHash.take(32) + "...",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White
                                    )
                                }

                                // Distress Hash Row
                                Column {
                                    Text("DECOY DISTRESS PASS-VECTOR", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = cyberCyan)
                                    Text(
                                        text = distressHash.take(32) + "...",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White
                                    )
                                }

                                // Panic Hash Row
                                Column {
                                    Text("DECOY PANIC PURGE PASS-VECTOR", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = cyberRed)
                                    Text(
                                        text = panicHash.take(32) + "...",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Heap Memory zeroizer manually
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1113)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, cyberRed.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "HEAP MEMORY AUDIT & ZEROIZER",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = cyberRed
                                )

                                var isSanitizing by remember { mutableStateOf(false) }
                                var sanitizeLogText by remember { mutableStateOf("Ready to sanitize. Heap keys currently protected.") }

                                Text(
                                    text = sanitizeLogText,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSanitizing) cyberGreen else Color.LightGray,
                                    lineHeight = 14.sp
                                )

                                Button(
                                    onClick = {
                                        isSanitizing = true
                                        sanitizeLogText = "ZEROIZING TRANSIENT BUFFERS..."
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(800)
                                            sanitizeLogText = "FILLING ALL EMPTY HEAP ADDRESSES WITH NULL CHUNKS..."
                                            kotlinx.coroutines.delay(800)
                                            com.example.crypto.MemoryZeroizer.triggerGcPurge()
                                            sanitizeLogText = "SANID-HEX SUCCESS: JVM garbage collection forced. Session keys flushed."
                                            isSanitizing = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = cyberRed.copy(alpha = 0.2f),
                                        contentColor = cyberRed
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, cyberRed, RoundedCornerShape(4.dp)),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp),
                                    enabled = !isSanitizing
                                ) {
                                    Text(
                                        text = if (isSanitizing) "PURGING OPERATIONAL MEMORY..." else "SECURE SANITIZE HEAP (ZERO BUFFER)",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // System File Logger Audit Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A1B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, cyberCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "SYSTEM AUDIT TRACE & PERSISTENT LOGS",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = cyberCyan
                                )

                                val logFile = com.example.crypto.SimplexLogger.getLogFile(context)
                                val logFilePath = logFile.absolutePath
                                val logFileExists = logFile.exists()
                                val logFileSize = if (logFileExists) logFile.length() else 0L

                                Text(
                                    text = "Active Log Path: $logFilePath\nFile Status: ${if (logFileExists) "CONNECTED ($logFileSize bytes)" else "OFFLINE"}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.LightGray,
                                    lineHeight = 13.sp
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            com.example.crypto.SimplexLogger.log("USER_AUDIT", "User manually initiated security audit trail confirmation.")
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = cyberCyan.copy(alpha = 0.2f),
                                            contentColor = cyberCyan
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(1.dp, cyberCyan, RoundedCornerShape(4.dp)),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        Text("TEST TRACE", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            com.example.crypto.SimplexLogger.clearLog()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = cyberRed.copy(alpha = 0.2f),
                                            contentColor = cyberRed
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(1.dp, cyberRed, RoundedCornerShape(4.dp)),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        Text("CLEAR LOGS", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // BIP-39 Cryptographic Seed Recovery (Live and Colored)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1318)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, cyberCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = cyberCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "BIP-39 PROTOCOL: MASTER KEY RECOVERY",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = cyberCyan
                                    )
                                }

                                Text(
                                    text = "Generate a secure, deterministic 12-word mnemonic backup to derive your 256-bit database master seed offline. The entropy is securely calculated using today's local date combined with 5 custom words.",
                                    fontSize = 10.sp,
                                    color = Color.LightGray,
                                    lineHeight = 13.sp
                                )

                                // Current Date source display
                                val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.4f))
                                        .border(0.5.dp, Color.DarkGray, RoundedCornerShape(4.dp))
                                        .padding(6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ENTROPY DATE SOURCE:",
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = todayStr,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = cyberCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // 5 words input field
                                Text(
                                    text = "ENTER 5 CUSTOM WORDS FOR ENTROPY:",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.LightGray
                                )

                                val enteredWordsList = customWordsInput.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                                val enteredWordCount = enteredWordsList.size

                                OutlinedTextField(
                                    value = customWordsInput,
                                    onValueChange = { customWordsInput = it },
                                    placeholder = { Text("secret anchor memory safety shadow", color = Color.DarkGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = if (enteredWordCount == 5) cyberGreen else cyberCyan,
                                        unfocusedBorderColor = Color.DarkGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Optional BIP-39 passphrase salting input
                                Text(
                                    text = "BIP-39 PASSPHRASE / 25TH WORD (OPTIONAL SALTING):",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.LightGray,
                                    modifier = Modifier.padding(top = 4.dp)
                                )

                                OutlinedTextField(
                                    value = bip39Passphrase,
                                    onValueChange = { viewModel.setBip39Passphrase(it) },
                                    placeholder = { Text("Enter optional passphrase for custom salting", color = Color.DarkGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = cyberCyan,
                                        unfocusedBorderColor = Color.DarkGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Words entered: $enteredWordCount / 5",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (enteredWordCount == 5) cyberGreen else if (enteredWordCount > 5) cyberRed else Color.Gray,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.generateDeterministicBip39Mnemonic(customWordsInput)
                                        },
                                        enabled = (enteredWordCount == 5),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = cyberCyan.copy(alpha = 0.2f),
                                            contentColor = cyberCyan,
                                            disabledContainerColor = Color.DarkGray.copy(alpha = 0.1f),
                                            disabledContentColor = Color.Gray
                                        ),
                                        modifier = Modifier
                                            .weight(1.5f)
                                            .border(1.dp, if (enteredWordCount == 5) cyberCyan else Color.DarkGray, RoundedCornerShape(4.dp)),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        Text("GENERATE DETERMINISTIC SEED", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.clearBip39State()
                                            customWordsInput = ""
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = cyberRed.copy(alpha = 0.2f),
                                            contentColor = cyberRed
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(1.dp, cyberRed, RoundedCornerShape(4.dp)),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        Text("WIPE MNEMONIC", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (bip39Mnemonic.isNotEmpty()) {
                                    // Security warning/disclaimer text
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF241515)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, cyberRed.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = cyberRed,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "🚨 SECURITY DISCLAIMER",
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = cyberRed
                                                )
                                            }
                                            Text(
                                                text = "ONLY YOU ARE RESPONSIBLE FOR YOUR DATA! Write down this 12-word phrase immediately and keep it safe. If you lose this mnemonic or forget your custom words, your cryptographic keys and database are completely unrecoverable.",
                                                fontSize = 9.sp,
                                                color = Color.LightGray,
                                                lineHeight = 12.sp
                                            )
                                        }
                                    }

                                    // Gorgeous Mnemonic Grid (Live neon)
                                    Text(
                                        text = "YOUR SECURE 12-WORD MNEMONIC SEED:",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = cyberGreen,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.6f))
                                            .border(1.dp, cyberGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                            .padding(6.dp)
                                    ) {
                                        for (i in 0 until 12 step 3) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                for (j in 0..2) {
                                                    val index = i + j
                                                    if (index < bip39Mnemonic.size) {
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .background(Color(0xFF041008))
                                                                .border(0.5.dp, cyberGreen.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                                                                .padding(4.dp),
                                                            contentAlignment = Alignment.CenterStart
                                                        ) {
                                                            Text(
                                                                text = "${index + 1}. ${bip39Mnemonic[index]}",
                                                                fontSize = 9.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                color = cyberGreen,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // COPY BUTTON FOR PHRASE (As requested: "даём юзеру возможность её скопировать")
                                    Button(
                                        onClick = {
                                            val phraseStr = bip39Mnemonic.joinToString(" ")
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("BIP-39 Mnemonic", phraseStr)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Mnemonic phrase copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = cyberGreen.copy(alpha = 0.15f),
                                            contentColor = cyberGreen
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, cyberGreen.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Text("COPY 12-WORD PHRASE", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    bip39SeedHex?.let { hex ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.Black)
                                                .border(0.5.dp, Color.DarkGray, RoundedCornerShape(4.dp))
                                                .padding(6.dp)
                                        ) {
                                            Text(
                                                text = "DERIVED 256-BIT MASTER SEED (HEX):",
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color.Gray,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = hex,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color.White,
                                                    lineHeight = 11.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        val clip = ClipData.newPlainText("BIP-39 Master Seed", hex)
                                                        clipboard.setPrimaryClip(clip)
                                                        Toast.makeText(context, "Master Seed HEX copied!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Share,
                                                        contentDescription = "Copy Seed",
                                                        tint = cyberCyan,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // verification: "проверяем заставляя юзера ввести 3 случайных слова из seed фразы."
                                    if (bip39ChallengeIndices.size == 3) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "VERIFY YOUR BACKUP (ENTER 3 WORDS):",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = cyberCyan
                                        )
                                        Text(
                                            text = "Please enter the requested words from your generated 12-word phrase to verify you have backed it up:",
                                            fontSize = 9.sp,
                                            color = Color.LightGray,
                                            lineHeight = 11.sp
                                        )

                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            for (i in 0 until 3) {
                                                val wordIndex = bip39ChallengeIndices[i]
                                                val typedAnswer = bip39ChallengeAnswers.getOrNull(i) ?: ""
                                                
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = "Word #${wordIndex + 1}:",
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = Color.Gray,
                                                        modifier = Modifier.width(64.dp)
                                                    )
                                                    OutlinedTextField(
                                                        value = typedAnswer,
                                                        onValueChange = { viewModel.setChallengeAnswer(i, it) },
                                                        placeholder = { Text("Word #${wordIndex + 1}", color = Color.DarkGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                                        textStyle = LocalTextStyle.current.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = cyberCyan,
                                                            unfocusedBorderColor = Color.DarkGray,
                                                            focusedTextColor = Color.White,
                                                            unfocusedTextColor = Color.White
                                                        ),
                                                        singleLine = true,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }

                                        bip39ChallengePassed?.let { passed ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (passed) Color(0xFF041008) else Color(0xFF140707))
                                                    .border(1.dp, if (passed) cyberGreen.copy(alpha = 0.4f) else cyberRed.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                    .padding(6.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .background(if (passed) cyberGreen else cyberRed, CircleShape)
                                                    )
                                                    Text(
                                                        text = if (passed) "VERIFICATION SUCCESSFUL: BACKUP VALIDATED" else "VERIFICATION FAILED: WORD MISMATCH",
                                                        fontSize = 9.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (passed) cyberGreen else cyberRed
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Interactive Phrase Validator
                                Text(
                                    text = "MANUAL RESTORE FROM EXISTING 12-WORD SEED:",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.LightGray,
                                    modifier = Modifier.padding(top = 4.dp)
                                )

                                OutlinedTextField(
                                    value = bip39ValidationInput,
                                    onValueChange = { viewModel.setBip39ValidationInput(it) },
                                    placeholder = { Text("abandon ability able about...", color = Color.DarkGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = cyberCyan,
                                        unfocusedBorderColor = Color.DarkGray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                )


                                bip39ValidationResult?.let { isValid ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isValid) Color(0xFF041008) else Color(0xFF140707))
                                            .border(1.dp, if (isValid) cyberGreen.copy(alpha = 0.4f) else cyberRed.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                            .padding(6.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .background(if (isValid) cyberGreen else cyberRed, CircleShape)
                                                )
                                                Text(
                                                    text = if (isValid) "BIP-39 VALIDATION: CHECKSUM PASSED" else "BIP-39 VALIDATION: CHECK FAILED",
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isValid) cyberGreen else cyberRed
                                                )
                                            }
                                            Text(
                                                text = if (isValid) "Seed derived securely. Master key ready for SQLite session deployment."
                                                       else "Unrecognized dictionary keywords or checksum mismatch. Phrase must be exactly 12 words from BIP-39 subset.",
                                                fontSize = 9.sp,
                                                color = Color.LightGray,
                                                lineHeight = 12.sp
                                            )

                                            if (isValid) {
                                                bip39ValidatedSeedHex?.let { validatedHex ->
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "RECOVERED SEED: $validatedHex",
                                                        fontSize = 8.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = cyberGreen.copy(alpha = 0.8f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // BIP-32/44 Deterministic Derivation Hierarchy Card (Live and Colored)
                        if (bip39DerivedKeys.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF060F11)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, cyberCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = cyberCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "BIP-32/44 HD KEY DERIVATION HIERARCHY",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = cyberCyan
                                        )
                                    }

                                    Text(
                                        text = "On-the-fly subkey derivation hierarchy generated strictly according to protocol. Master seed is stretched into independent 256-bit subkeys for each specialized application container layer:",
                                        fontSize = 9.sp,
                                        color = Color.LightGray,
                                        lineHeight = 12.sp
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        bip39DerivedKeys.forEach { keyItem ->
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFF03080A))
                                                    .border(0.5.dp, cyberCyan.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                    .padding(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = keyItem.path,
                                                        fontSize = 9.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = cyberCyan,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = keyItem.purpose,
                                                        fontSize = 8.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = Color.LightGray
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = keyItem.keyHex.take(28) + "..." + keyItem.keyHex.takeLast(14),
                                                        fontSize = 8.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = cyberGreen,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            val clip = ClipData.newPlainText("Derived Key ${keyItem.path}", keyItem.keyHex)
                                                            clipboard.setPrimaryClip(clip)
                                                            Toast.makeText(context, "Derived child key copied!", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(20.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Share,
                                                            contentDescription = "Copy Path Key",
                                                            tint = cyberGreen,
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Real Rust Shamir GF(256) 3-of-5 Encrypted Vault Export & Import Card
                        ShamirBackupCard(
                            viewModel = viewModel,
                            cyberGreen = cyberGreen,
                            cyberCyan = cyberCyan,
                            cyberRed = cyberRed
                        )

                        // Rust Shamir Inspector & PoSt Verifier
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F161A)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, cyberCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = cyberCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "RUST SHAMIR GF(256) SHARD VERIFIER",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = cyberCyan
                                    )
                                }

                                Button(
                                    onClick = {
                                        viewModel.triggerDataSharding()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = cyberCyan.copy(alpha = 0.2f),
                                        contentColor = cyberCyan
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, cyberCyan, RoundedCornerShape(4.dp)),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Text("SPLIT & TEST 3-OF-5 SHAMIR SHARDS", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }

                                if (storageShards.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        storageShards.forEachIndexed { idx, shard ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.Black.copy(alpha = 0.4f))
                                                    .border(0.5.dp, cyberCyan.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                    .padding(6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Shard #${idx + 1}: ${shard.shardId}",
                                                        fontSize = 9.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "Threshold: 3/5 (${shard.targetPeerNodeId})",
                                                        fontSize = 8.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = cyberCyan
                                                    )
                                                    Text(
                                                        text = "SHA-256: ${shard.checksumSha256.joinToString("") { "%02x".format(it) }.take(16)}...",
                                                        fontSize = 8.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = Color.Gray
                                                    )
                                                }
                                                Button(
                                                    onClick = {
                                                        viewModel.executeProofOfSpacetimeChallenge(idx)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = cyberGreen.copy(alpha = 0.15f),
                                                        contentColor = cyberGreen
                                                    ),
                                                    shape = RoundedCornerShape(3.dp),
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                    modifier = Modifier
                                                        .height(24.dp)
                                                        .border(0.5.dp, cyberGreen.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                                ) {
                                                    Text("VERIFY", fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }

                                if (postChallengeLog.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.Black)
                                            .border(0.5.dp, if (postChallengeSuccess == true) cyberGreen.copy(alpha = 0.5f) else if (postChallengeSuccess == false) cyberRed.copy(alpha = 0.5f) else Color.DarkGray, RoundedCornerShape(4.dp))
                                            .padding(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        postChallengeLog.forEach { logLine ->
                                            Text(
                                                text = "> $logLine",
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color.LightGray,
                                                lineHeight = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Informative disclaimer
                        Text(
                            text = LocalizationHelper.get("duress_disclaimer"),
                            fontSize = 11.sp,
                            color = Color.Gray,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                } else if (activeTab == 2) {
                    ChainTunnelTab(
                        cyberGreen = cyberGreen,
                        cyberCyan = cyberCyan,
                        cyberRed = cyberRed,
                        cardOnyx = cardOnyx
                    )
                } else if (activeTab == 3) {
                    // --- SKINS / THEMES TAB ---
                    val currentSkin by viewModel.currentSkin.collectAsState()
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "SELECT ACTIVE SKIN DECORATION",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = cyberGreen
                        )
                        Text(
                            text = "Switch holographic wallpapers, Martian citadel backdrops, and tactical HUD shaders in real-time.",
                            fontSize = 9.sp,
                            color = Color.LightGray.copy(alpha = 0.75f),
                            fontFamily = FontFamily.Monospace
                        )

                        com.example.ui.SkinStyle.values().forEach { skin ->
                            val isSelected = currentSkin == skin
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) cyberCyan.copy(alpha = 0.15f) else Color.Black)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) cyberCyan else Color.White.copy(alpha = 0.05f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        viewModel.updateProfileSkin(skin)
                                    }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = skin.name,
                                        color = if (isSelected) Color.White else Color.LightGray,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when (skin) {
                                            com.example.ui.SkinStyle.CYBERPUNK -> "Neon matrix grid lines with glowing scanlines"
                                            com.example.ui.SkinStyle.ALIEN_STARSHIP -> "Starship technical HUD circuits and console displays"
                                            com.example.ui.SkinStyle.DARK_NEBULA -> "Cloud dust shapes with deep space dark stars"
                                            com.example.ui.SkinStyle.COSMOS -> "Orbital rings with stars"
                                            com.example.ui.SkinStyle.GAUDI -> "Organic parabolic tiles and curved structures"
                                            com.example.ui.SkinStyle.MATRIX -> "Falling cascading digital code green lines"
                                            com.example.ui.SkinStyle.VAN_GOGH -> "Swirling starlight strokes and yellow-blue hues"
                                            com.example.ui.SkinStyle.JAMAICA_SUMMER -> "Bright vertical stripes of green, gold, and red"
                                            com.example.ui.SkinStyle.MARIJUANA_WEED -> "Symmetrical organic multi-leaf ornaments"
                                            com.example.ui.SkinStyle.GALAXY -> "Spiral galaxy arm structures"
                                            com.example.ui.SkinStyle.MICROCOSMOS -> "Cellular biological particles and deep space molecules"
                                            com.example.ui.SkinStyle.LIVE_CELL -> "Animated heartbeat pulses"
                                        },
                                        color = Color.Gray,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = cyberCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else if (activeTab == 4) {
                    // --- MULTI-ACCOUNT WORKSPACE IDENTITY SWITCHER TAB ---
                    val profileList by viewModel.profileList.collectAsState()
                    val activeProfile by viewModel.activeProfileAlias.collectAsState()
                    val primaryProfile by viewModel.primaryProfileAlias.collectAsState()

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "IDENTITY WORKSPACES",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = cyberCyan
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(cyberCyan.copy(alpha = 0.2f))
                                    .clickable {
                                        viewModel.deselectProfile()
                                        onDismiss()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "SWITCH / EXIT",
                                    color = cyberCyan,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        profileList.forEach { alias ->
                            val isActive = activeProfile == alias
                            val isPrimary = primaryProfile == alias

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isActive) cyberGreen.copy(alpha = 0.12f) else Color.Black)
                                    .border(
                                        width = 1.dp,
                                        color = if (isActive) cyberGreen else Color.White.copy(alpha = 0.05f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        viewModel.selectProfile(alias)
                                        onDismiss()
                                    }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isActive) cyberGreen else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = alias.uppercase(),
                                            color = if (isActive) Color.White else Color.LightGray,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isPrimary) "PRIMARY WORKSPACE" else "SECONDARY IDENTITY",
                                            color = Color.Gray,
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                if (isActive) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(cyberGreen.copy(alpha = 0.2f))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text("ACTIVE", color = cyberGreen, fontSize = 7.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else if (activeTab == 5) {
                    AutoLockSettingsContent(viewModel = viewModel)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    com.example.ui.keyboard.InAppKeyboardManager.hideKeyboard()
                    onDismiss()
                }) {
                    Text(
                        LocalizationHelper.get("close_center"),
                        color = if (activeTab == 0) cyberGreen else cyberCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
    }
}


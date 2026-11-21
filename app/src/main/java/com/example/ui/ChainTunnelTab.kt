package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import com.example.net.ProxyChainManager
import com.example.net.ProxyConfig
import com.example.net.ProxyStatus
import com.example.net.ProxyType
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ChainTunnelTab(
    cyberGreen: Color,
    cyberCyan: Color,
    cyberRed: Color,
    cardOnyx: Color
) {
    val configs by ProxyChainManager.configs.collectAsState()
    val status by ProxyChainManager.chainStatus.collectAsState()
    val logs by ProxyChainManager.chainLogs.collectAsState()
    val activeLayerIndex by ProxyChainManager.activeLayerIndex.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showImportDialog by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        // Explanatory Title with dynamic language get
        Text(
            text = "SEQUENTIAL MULTI-BRIDGE ROUTING CHAIN",
            color = Color.Gray,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        // Proxy sequence column
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F111A)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.3f)
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
        ) {
            if (configs.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
                    Text(
                        text = "No proxy layers configured. Import custom configurations below.",
                        color = Color.DarkGray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(configs) { index, config ->
                        val isCurrentConnecting = activeLayerIndex == index
                        val borderCol = if (isCurrentConnecting) cyberCyan else if (config.isEnabled) cyberGreen.copy(alpha = 0.3f) else Color.DarkGray.copy(alpha = 0.2f)
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (config.isEnabled) Color(0xFF131622) else Color(0xFF0C0D14))
                                .border(1.dp, borderCol, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            // Order Indicator Tag
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(if (config.isEnabled) cyberGreen.copy(alpha = 0.15f) else Color.DarkGray.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, if (config.isEnabled) cyberGreen else Color.Gray, CircleShape)
                            ) {
                                Text(
                                    text = (index + 1).toString(),
                                    color = if (config.isEnabled) cyberGreen else Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Name and Type info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = config.alias,
                                    color = if (config.isEnabled) Color.White else Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "PROTO: ${config.type.name} | ${if (config.isEnabled) "ACTIVE LAYER" else "BYPASSED"}",
                                    color = if (config.isEnabled) cyberCyan else Color.DarkGray,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Order adjustment buttons
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val list = configs.toMutableList()
                                        val temp = list[index]
                                        list[index] = list[index - 1]
                                        list[index - 1] = temp
                                        ProxyChainManager.updateChainOrder(list.map { it.id })
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move Up",
                                    tint = if (index > 0) Color.White else Color.DarkGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (index < configs.size - 1) {
                                        val list = configs.toMutableList()
                                        val temp = list[index]
                                        list[index] = list[index + 1]
                                        list[index + 1] = temp
                                        ProxyChainManager.updateChainOrder(list.map { it.id })
                                    }
                                },
                                enabled = index < configs.size - 1,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move Down",
                                    tint = if (index < configs.size - 1) Color.White else Color.DarkGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Switch
                            Switch(
                                checked = config.isEnabled,
                                onCheckedChange = { isChecked ->
                                    ProxyChainManager.addOrUpdateConfig(config.copy(isEnabled = isChecked))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = cyberGreen,
                                    checkedTrackColor = cyberGreen.copy(alpha = 0.2f),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.Black
                                ),
                                modifier = Modifier.scale(0.7f)
                            )
                        }
                    }
                }
            }
        }

        // Action controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    if (status == ProxyStatus.CONNECTED || status == ProxyStatus.CONNECTING) {
                        ProxyChainManager.disconnectChain()
                    } else {
                        coroutineScope.launch {
                            ProxyChainManager.connectChain {}
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (status == ProxyStatus.CONNECTED || status == ProxyStatus.CONNECTING) cyberRed.copy(alpha = 0.2f) else cyberGreen.copy(alpha = 0.2f),
                    contentColor = if (status == ProxyStatus.CONNECTED || status == ProxyStatus.CONNECTING) cyberRed else cyberGreen
                ),
                modifier = Modifier
                    .weight(1.3f)
                    .border(1.dp, if (status == ProxyStatus.CONNECTED || status == ProxyStatus.CONNECTING) cyberRed else cyberGreen, RoundedCornerShape(4.dp)),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play icon",
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (status == ProxyStatus.CONNECTED || status == ProxyStatus.CONNECTING) 
                            LocalizationHelper.get("disconnect_chain") 
                            else LocalizationHelper.get("connect_chain"),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = { showImportDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = cyberCyan.copy(alpha = 0.15f),
                    contentColor = cyberCyan
                ),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, cyberCyan, RoundedCornerShape(4.dp)),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Text(
                    text = LocalizationHelper.get("import_config"),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Console logger
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
        ) {
            if (logs.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(10.dp)
                ) {
                    Text(
                        text = "Tunnel system ready. Tap 'CONNECT CHAIN' to compile security tunnels sequentially.",
                        color = Color.DarkGray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    reverseLayout = false
                ) {
                    items(logs.size) { idx ->
                        val logMsg = logs[idx]
                        val tint = if (logMsg.contains("SUCCESS") || logMsg.contains("SECURED")) {
                            cyberGreen
                        } else if (logMsg.contains("SYSTEM") || logMsg.contains("established")) {
                            cyberCyan
                        } else if (logMsg.contains("CRITICAL") || logMsg.contains("FAILED") || logMsg.contains("ERROR")) {
                            cyberRed
                        } else {
                            Color.LightGray
                        }
                        Text(
                            text = logMsg,
                            color = tint,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 11.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }

    // Inner dialog to add a new customized config node
    if (showImportDialog) {
        var inputAlias by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf(ProxyType.WIREGUARD) }
        var inputConfig by remember { mutableStateOf("") }

        com.example.ui.keyboard.SecureInAppDialogHost(
            hostId = "chain_tunnel_import_dialog",
            skin = SkinStyle.CYBERPUNK,
            onDismissRequest = { showImportDialog = false }
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardOnyx,
                border = androidx.compose.foundation.BorderStroke(1.dp, cyberCyan.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "IMPORT TUNNEL CONFIG",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    OutlinedTextField(
                        value = inputAlias,
                        onValueChange = { inputAlias = it },
                        label = { Text("Configuration Alias/Name", color = Color.Gray, fontSize = 11.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White, fontFamily = FontFamily.Monospace),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = cyberCyan,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Protocol Selector Row
                    Text("Select Tunnel Protocol:", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ProxyType.values().forEach { proto ->
                            val isSelected = selectedType == proto
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isSelected) cyberCyan.copy(alpha = 0.2f) else Color(0xFF131622))
                                    .border(1.dp, if (isSelected) cyberCyan else Color.DarkGray, RoundedCornerShape(3.dp))
                                    .clickable { selectedType = proto }
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = proto.name.take(4),
                                    color = if (isSelected) cyberCyan else Color.Gray,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Raw config text
                    OutlinedTextField(
                        value = inputConfig,
                        onValueChange = { inputConfig = it },
                        label = { Text("Config Contents (JSON / XML / INI)", color = Color.Gray, fontSize = 11.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Monospace),
                        minLines = 4,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = cyberCyan,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showImportDialog = false }) {
                            Text("CANCEL", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (inputAlias.isNotBlank() && inputConfig.isNotBlank()) {
                                    val newConf = ProxyConfig(
                                        id = UUID.randomUUID().toString(),
                                        alias = inputAlias,
                                        type = selectedType,
                                        rawConfig = inputConfig,
                                        isEnabled = true,
                                        orderIndex = configs.size
                                    )
                                    ProxyChainManager.addOrUpdateConfig(newConf)
                                    showImportDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = cyberCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("SAVE NODE", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.SimplexViewModel
import org.json.JSONArray

/**
 * Real Shamir GF(256) 3-of-5 encrypted vault backup & SAF `.marcianox` file export/import card.
 */
@Composable
fun ShamirBackupCard(
    viewModel: SimplexViewModel,
    cyberGreen: Color,
    cyberCyan: Color,
    cyberRed: Color
) {
    val context = LocalContext.current
    val shares by viewModel.lastShamirExportShares.collectAsState()
    val statusMsg by viewModel.backupStatusMessage.collectAsState()

    var passphrase by remember { mutableStateOf("") }

    val exportDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && shares.isNotEmpty()) {
            try {
                val bundle = JSONArray().apply {
                    shares.forEach { put(org.json.JSONObject(it)) }
                }.toString(2)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(bundle.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Shamir 3-of-5 vault bundle exported.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importDocsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val collectedShares = mutableListOf<String>()
            uris.forEach { uri ->
                runCatching {
                    val content = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8).trim()
                    } ?: ""
                    if (content.startsWith("[")) {
                        val arr = JSONArray(content)
                        for (i in 0 until arr.length()) {
                            collectedShares.add(arr.getJSONObject(i).toString())
                        }
                    } else if (content.isNotBlank()) {
                        collectedShares.add(content)
                    }
                }
            }
            val ok = viewModel.restoreVaultFromShamirShares(collectedShares, passphrase)
            Toast.makeText(
                context,
                if (ok) "Shamir 3-of-5 recovery succeeded!" else "Shamir recovery failed (need >=3 shares + valid passphrase).",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1418)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, cyberGreen.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "RUST SHAMIR 3-OF-5 VAULT BACKUP (.marcianox)",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = cyberGreen
            )
            Text(
                text = "Splits your sealed BIP-39 mnemonic & contacts into 5 GF(256) shares encrypted with PBKDF2-HMAC-SHA256 + AES-256-GCM. Any 3 shares restore the vault.",
                fontSize = 10.sp,
                color = Color.LightGray,
                lineHeight = 13.sp
            )

            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                placeholder = { Text("Backup encryption passphrase (>=6 chars)", color = Color.Gray, fontSize = 10.sp) },
                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = cyberGreen,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shamir_passphrase_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.generateShamirBackupExport(passphrase)
                    },
                    enabled = passphrase.length >= 6,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cyberGreen.copy(alpha = 0.2f),
                        contentColor = cyberGreen
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, if (passphrase.length >= 6) cyberGreen else Color.DarkGray, RoundedCornerShape(4.dp))
                        .testTag("shamir_generate_button"),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text("SPLIT 3-OF-5", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        importDocsLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    enabled = passphrase.length >= 6,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cyberCyan.copy(alpha = 0.2f),
                        contentColor = cyberCyan
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, if (passphrase.length >= 6) cyberCyan else Color.DarkGray, RoundedCornerShape(4.dp))
                        .testTag("shamir_restore_button"),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text("RESTORE FILES", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }

            if (shares.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            exportDocLauncher.launch("marcianox_shamir_bundle.marcianox.json")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cyberGreen,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text("SAVE .MARCIANOX FILE", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                    }

                    Button(
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("MarcianoX Shamir Share #1", shares.first()))
                            Toast.makeText(context, "Share #1 copied", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cyberCyan.copy(alpha = 0.2f),
                            contentColor = cyberCyan
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("COPY #1", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (!statusMsg.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .border(0.5.dp, if (statusMsg!!.contains("failed", true) || statusMsg!!.contains("error", true)) cyberRed else cyberGreen, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = statusMsg!!,
                        color = if (statusMsg!!.contains("failed", true) || statusMsg!!.contains("error", true)) cyberRed else cyberGreen,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

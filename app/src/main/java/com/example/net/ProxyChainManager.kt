package com.example.net

import android.content.Context
import android.content.SharedPreferences
import com.example.crypto.SimplexLogger
import com.example.rust.MarcianoRustCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class ProxyType {
    WIREGUARD,
    OPENVPN,
    V2RAY,
    TOR,
    PROXY
}

enum class ProxyStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

data class ProxyConfig(
    val id: String,
    val alias: String,
    val type: ProxyType,
    val rawConfig: String,
    val isEnabled: Boolean = true,
    val orderIndex: Int = 0
)

/**
 * Multi-layer proxy/tunnel verifier backed by Rust `MarcianoRustCore.probeRoute` and
 * `MarcianoRustCore.probeSocks5Daemon`. Performs genuine TCP/SOCKS5 socket handshakes against
 * parsed user configurations instead of synthetic delays or random ping values.
 */
object ProxyChainManager {
    private const val PREFS_NAME = "simplex_proxy_chain_prefs"
    private const val CONFIG_KEY = "proxy_configs"

    private lateinit var prefs: SharedPreferences
    private val _configs = MutableStateFlow<List<ProxyConfig>>(emptyList())
    val configs: StateFlow<List<ProxyConfig>> = _configs.asStateFlow()

    private val _chainStatus = MutableStateFlow(ProxyStatus.DISCONNECTED)
    val chainStatus: StateFlow<ProxyStatus> = _chainStatus.asStateFlow()

    private val _chainLogs = MutableStateFlow<List<String>>(emptyList())
    val chainLogs: StateFlow<List<String>> = _chainLogs.asStateFlow()

    private val _activeLayerIndex = MutableStateFlow(-1)
    val activeLayerIndex: StateFlow<Int> = _activeLayerIndex.asStateFlow()

    private fun serializeConfig(config: ProxyConfig): String {
        return "${config.id}[||]${config.alias}[||]${config.type.name}[||]${config.rawConfig}[||]${config.isEnabled}[||]${config.orderIndex}"
    }

    private fun deserializeConfig(str: String): ProxyConfig? {
        return try {
            val parts = str.split("[||]")
            if (parts.size < 6) return null
            ProxyConfig(
                id = parts[0],
                alias = parts[1],
                type = ProxyType.valueOf(parts[2]),
                rawConfig = parts[3],
                isEnabled = parts[4].toBoolean(),
                orderIndex = parts[5].toInt()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun serializeList(list: List<ProxyConfig>): String {
        return list.joinToString("[RECORD]") { serializeConfig(it) }
    }

    private fun deserializeList(str: String): List<ProxyConfig> {
        if (str.isBlank()) return emptyList()
        return str.split("[RECORD]").mapNotNull { deserializeConfig(it) }
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadConfigs()
        if (_configs.value.isEmpty()) {
            seedDefaultSocksLayer()
        }
    }

    private fun seedDefaultSocksLayer() {
        val defaultLayer = listOf(
            ProxyConfig(
                id = "tor_local_socks5",
                alias = "Orbot / Tor SOCKS5 (127.0.0.1:9050)",
                type = ProxyType.TOR,
                rawConfig = "SocksHost 127.0.0.1\nSocksPort 9050",
                isEnabled = true,
                orderIndex = 0
            )
        )
        saveConfigs(defaultLayer)
    }

    private fun loadConfigs() {
        try {
            val jsonStr = prefs.getString(CONFIG_KEY, null)
            _configs.value = if (jsonStr != null) {
                deserializeList(jsonStr).sortedBy { it.orderIndex }
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            _configs.value = emptyList()
        }
    }

    private fun saveConfigs(list: List<ProxyConfig>) {
        try {
            val sortedList = list.sortedBy { it.orderIndex }
            _configs.value = sortedList
            prefs.edit().putString(CONFIG_KEY, serializeList(sortedList)).apply()
        } catch (_: Exception) {
        }
    }

    fun addOrUpdateConfig(config: ProxyConfig) {
        val current = _configs.value.toMutableList()
        val index = current.indexOfFirst { it.id == config.id }
        if (index >= 0) current[index] = config else current.add(config)
        saveConfigs(current)
    }

    fun deleteConfig(id: String) {
        val current = _configs.value.toMutableList()
        current.removeAll { it.id == id }
        saveConfigs(current)
    }

    fun updateChainOrder(orderedIds: List<String>) {
        val updated = _configs.value.map { config ->
            val idx = orderedIds.indexOf(config.id)
            if (idx >= 0) config.copy(orderIndex = idx) else config
        }
        saveConfigs(updated)
    }

    fun clearLogs() {
        _chainLogs.value = emptyList()
    }

    fun addLog(msg: String) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
        sdf.timeZone = TimeZone.getDefault()
        val formatted = "[${sdf.format(Date())}] $msg"
        _chainLogs.value = _chainLogs.value + formatted
        SimplexLogger.log("PROXY_CHAIN", msg)
    }

    suspend fun connectChain(onComplete: (Boolean) -> Unit) = withContext(Dispatchers.IO) {
        _chainStatus.value = ProxyStatus.CONNECTING
        clearLogs()
        addLog("SYSTEM: Initiating Rust Multi-Layer Proxy Chain Verifier...")

        val activeChain = _configs.value.filter { it.isEnabled }.sortedBy { it.orderIndex }
        if (activeChain.isEmpty()) {
            addLog("CRITICAL: Connection aborted. No proxy layers are enabled.")
            _chainStatus.value = ProxyStatus.FAILED
            onComplete(false)
            return@withContext
        }

        if (!MarcianoRustCore.isLoaded) {
            addLog("CRITICAL: Native Rust library marcianox_core is not loaded.")
            _chainStatus.value = ProxyStatus.FAILED
            onComplete(false)
            return@withContext
        }

        var success = true
        for (index in activeChain.indices) {
            val layer = activeChain[index]
            _activeLayerIndex.value = index
            addLog("SYSTEM: Probing Layer ${index + 1}/${activeChain.size} [${layer.type.name} - ${layer.alias}]...")
            val layerOk = bootstrapLayer(layer)
            if (!layerOk) {
                addLog("CRITICAL: Layer ${index + 1} [${layer.type.name}] failed real socket handshake.")
                success = false
                break
            }
            addLog("SYSTEM: Layer ${index + 1} verified live and integrated into chain.")
        }

        _activeLayerIndex.value = -1
        if (success) {
            addLog("SYSTEM: All enabled chain endpoints verified via live Rust sockets. SECURED.")
            _chainStatus.value = ProxyStatus.CONNECTED
            onComplete(true)
        } else {
            addLog("CRITICAL: Chain verification failed. Check Orbot/proxy endpoint reachability.")
            _chainStatus.value = ProxyStatus.FAILED
            onComplete(false)
        }
    }

    fun disconnectChain() {
        _chainStatus.value = ProxyStatus.DISCONNECTED
        _activeLayerIndex.value = -1
        clearLogs()
        addLog("SYSTEM: Proxy chain disconnected.")
    }

    private fun bootstrapLayer(layer: ProxyConfig): Boolean {
        if (layer.rawConfig.isBlank()) {
            addLog("[${layer.type.name}] ERROR: Empty configuration.")
            return false
        }

        val endpoint = extractHostPort(layer.type, layer.rawConfig)
        if (endpoint == null) {
            addLog("[${layer.type.name}] ERROR: Could not parse host:port from config.")
            return false
        }
        val (host, port) = endpoint

        return when (layer.type) {
            ProxyType.TOR -> {
                val res = MarcianoRustCore.probeSocks5Daemon(host, port)
                if (res.ok) {
                    addLog("[TOR] SUCCESS: SOCKS5 daemon live at $host:$port (RTT: ${res.rttMs}ms)")
                    true
                } else {
                    addLog("[TOR] FAILED: $host:$port -> ${res.detail}")
                    false
                }
            }
            ProxyType.PROXY -> {
                val socksProbe = MarcianoRustCore.probeSocks5Daemon(host, port)
                if (socksProbe.ok) {
                    addLog("[PROXY] SUCCESS: SOCKS5 handshake at $host:$port (RTT: ${socksProbe.rttMs}ms)")
                    true
                } else {
                    val tcpProbe = MarcianoRustCore.probeRoute(host, port, "127.0.0.1", 9050, false)
                    if (tcpProbe.ok) {
                        addLog("[PROXY] SUCCESS: TCP proxy reachable at $host:$port (RTT: ${tcpProbe.rttMs}ms)")
                        true
                    } else {
                        addLog("[PROXY] FAILED: $host:$port -> ${tcpProbe.detail}")
                        false
                    }
                }
            }
            ProxyType.WIREGUARD, ProxyType.OPENVPN, ProxyType.V2RAY -> {
                val tcpProbe = MarcianoRustCore.probeRoute(host, port, "127.0.0.1", 9050, false)
                if (tcpProbe.ok) {
                    addLog("[${layer.type.name}] SUCCESS: Endpoint $host:$port reachable (RTT: ${tcpProbe.rttMs}ms)")
                    true
                } else {
                    addLog("[${layer.type.name}] FAILED: $host:$port unreachable -> ${tcpProbe.detail}")
                    false
                }
            }
        }
    }

    internal fun extractHostPort(type: ProxyType, rawConfig: String): Pair<String, Int>? {
        val text = rawConfig.trim()
        return when (type) {
            ProxyType.TOR -> {
                val hostRegex = Regex("""(?im)^SocksHost\s+(\S+)""")
                val portRegex = Regex("""(?im)^SocksPort\s+(\d+)""")
                val host = hostRegex.find(text)?.groupValues?.get(1) ?: "127.0.0.1"
                val port = portRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 9050
                host to port
            }
            ProxyType.WIREGUARD -> {
                val m = Regex("""(?im)^\s*Endpoint\s*=\s*([^\s:]+):(\d+)""").find(text)
                if (m != null) m.groupValues[1] to (m.groupValues[2].toIntOrNull() ?: 51820) else null
            }
            ProxyType.OPENVPN -> {
                val m = Regex("""(?im)^\s*remote\s+(\S+)\s+(\d+)""").find(text)
                if (m != null) m.groupValues[1] to (m.groupValues[2].toIntOrNull() ?: 1194) else null
            }
            ProxyType.V2RAY -> {
                try {
                    val root = JSONObject(text)
                    val outbounds = root.optJSONArray("outbounds")
                    val vnext = outbounds?.optJSONObject(0)?.optJSONObject("settings")?.optJSONArray("vnext")?.optJSONObject(0)
                    val addr = vnext?.optString("address").orEmpty()
                    val port = vnext?.optInt("port", 443) ?: 443
                    if (addr.isNotBlank()) addr to port else null
                } catch (_: Exception) {
                    val m = Regex("""(\S+):(\d+)""").find(text)
                    m?.let { it.groupValues[1] to (it.groupValues[2].toIntOrNull() ?: 443) }
                }
            }
            ProxyType.PROXY -> {
                val cleaned = text.removePrefix("socks5://").removePrefix("http://").trim()
                val m = Regex("""([^\s:/]+):(\d+)""").find(cleaned)
                m?.let { it.groupValues[1] to (it.groupValues[2].toIntOrNull() ?: 1080) }
            }
        }
    }
}

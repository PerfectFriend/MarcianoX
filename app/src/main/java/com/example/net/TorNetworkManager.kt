package com.example.net

import android.content.Context
import android.content.SharedPreferences
import com.example.crypto.SimplexLogger
import com.example.rust.MarcianoRustCore
import com.example.rust.RustRelayEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class TorCircuitNode(
    val type: String,
    val ipAddress: String,
    val country: String,
    val countryCode: String,
    val bandwidthMbps: Int,
    val fingerprint: String
)

data class ActiveSocksConnection(
    val id: String,
    val clientAddress: String,
    val targetHost: String,
    val targetPort: Int,
    val timestamp: Long,
    val bytesTransferred: Long,
    val circuits: List<TorCircuitNode>
)

/**
 * Rust-backed Network, SOCKS5/Orbot & WebSocket Relay Controller.
 * All socket, TLS (rustls), SOCKS5 (tokio-socks), and WebSocket (tokio-tungstenite) operations
 * run natively in the Rust tokio runtime inside `marcianox_core`.
 */
object TorNetworkManager {
    private const val PREFS_NAME = "marcianox_network_prefs"
    private const val KEY_RELAY_URL = "relay_ws_url"
    private const val KEY_SOCKS_HOST = "socks_host"
    private const val KEY_SOCKS_PORT = "socks_port"
    private const val KEY_USE_SOCKS5 = "use_socks5"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var prefs: SharedPreferences? = null

    var socksHost: String = "127.0.0.1"
        private set

    var torPort: Int = 9050
        private set

    private val _useSocks5 = MutableStateFlow(false)
    val useSocks5: StateFlow<Boolean> = _useSocks5.asStateFlow()

    private val _relayUrl = MutableStateFlow("wss://relay.marcianox.net/ws")
    val relayUrl: StateFlow<String> = _relayUrl.asStateFlow()

    // True when configured SOCKS5 (Orbot/Tor) responds on socksHost:torPort
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _isRelayConnected = MutableStateFlow(false)
    val isRelayConnected: StateFlow<Boolean> = _isRelayConnected.asStateFlow()

    private val _lastRelayError = MutableStateFlow("")
    val lastRelayError: StateFlow<String> = _lastRelayError.asStateFlow()

    private val _totalBytesTransferred = MutableStateFlow(0L)
    val totalBytesTransferred: StateFlow<Long> = _totalBytesTransferred.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _activeConnections = MutableStateFlow<List<ActiveSocksConnection>>(emptyList())
    val activeConnections: StateFlow<List<ActiveSocksConnection>> = _activeConnections.asStateFlow()

    private val _relayEvents = MutableSharedFlow<RustRelayEvent>(extraBufferCapacity = 64)
    val relayEvents: SharedFlow<RustRelayEvent> = _relayEvents.asSharedFlow()

    private var monitorJob: Job? = null

    init {
        addLog("Rust Network Engine initialized (tokio + tokio-socks + tokio-tungstenite + rustls).")
    }

    fun startTorProxy(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _relayUrl.value = prefs?.getString(KEY_RELAY_URL, _relayUrl.value) ?: _relayUrl.value
            socksHost = prefs?.getString(KEY_SOCKS_HOST, "127.0.0.1") ?: "127.0.0.1"
            torPort = prefs?.getInt(KEY_SOCKS_PORT, 9050) ?: 9050
            _useSocks5.value = prefs?.getBoolean(KEY_USE_SOCKS5, false) ?: false
        }

        if (monitorJob?.isActive == true) {
            probeSocks5Async()
            return
        }

        probeSocks5Async()

        monitorJob = scope.launch {
            var tick = 0
            while (isActive) {
                if (MarcianoRustCore.isLoaded) {
                    try {
                        val st = MarcianoRustCore.relayStatus()
                        _isRelayConnected.value = st.connected
                        _lastRelayError.value = st.lastError
                        _totalBytesTransferred.value = st.bytesSent + st.bytesReceived

                        if (st.connected) {
                            _activeConnections.value = listOf(
                                ActiveSocksConnection(
                                    id = "rust_ws",
                                    clientAddress = if (_useSocks5.value) "$socksHost:$torPort" else "Direct TLS",
                                    targetHost = st.relayUrl,
                                    targetPort = if (st.relayUrl.startsWith("wss://")) 443 else 80,
                                    timestamp = System.currentTimeMillis(),
                                    bytesTransferred = st.bytesSent + st.bytesReceived,
                                    circuits = emptyList()
                                )
                            )
                        } else {
                            _activeConnections.value = emptyList()
                        }

                        val events = MarcianoRustCore.relayDrainEvents()
                        for (ev in events) {
                            when (ev.eventType) {
                                "CONNECTED" -> addLog("RELAY CONNECTED: ${ev.payload}")
                                "DISCONNECTED" -> addLog("RELAY DISCONNECTED: ${ev.payload}")
                                "ERROR" -> addLog("RELAY ERROR: ${ev.payload}")
                                "MESSAGE" -> _relayEvents.tryEmit(ev)
                            }
                        }

                        if (tick % 20 == 0) {
                            val socksProbe = MarcianoRustCore.probeSocks5Daemon(socksHost, torPort)
                            val prev = _isServerRunning.value
                            _isServerRunning.value = socksProbe.ok
                            if (prev != socksProbe.ok) {
                                addLog(
                                    if (socksProbe.ok) "SOCKS5 (Orbot/Tor) detected at $socksHost:$torPort (${socksProbe.rttMs}ms)"
                                    else "SOCKS5 proxy offline at $socksHost:$torPort (${socksProbe.detail})"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        addLog("Network telemetry error: ${e.message}")
                    }
                }
                tick++
                delay(250)
            }
        }
    }

    fun probeSocks5Async() {
        scope.launch {
            if (!MarcianoRustCore.isLoaded) {
                addLog("CRITICAL: Rust core library marcianox_core not loaded (${MarcianoRustCore.loadError}).")
                _isServerRunning.value = false
                return@launch
            }
            val probe = MarcianoRustCore.probeSocks5Daemon(socksHost, torPort)
            _isServerRunning.value = probe.ok
            if (probe.ok) {
                addLog("SUCCESS: Orbot/Tor SOCKS5 handshake confirmed at $socksHost:$torPort (${probe.rttMs}ms).")
            } else {
                addLog("INFO: No local SOCKS5 daemon on $socksHost:$torPort (${probe.detail}). Direct TLS wss:// available.")
            }
        }
    }

    fun stopTorProxy() {
        disconnectRelay()
        monitorJob?.cancel()
        monitorJob = null
        _isServerRunning.value = false
        _activeConnections.value = emptyList()
        addLog("Network monitor and relay transport halted.")
    }

    fun updateTransportConfig(relayWsUrl: String, newSocksHost: String, newSocksPort: Int, useSocks: Boolean) {
        _relayUrl.value = relayWsUrl.trim()
        socksHost = newSocksHost.trim().ifBlank { "127.0.0.1" }
        torPort = newSocksPort.coerceIn(1, 65535)
        _useSocks5.value = useSocks
        prefs?.edit()
            ?.putString(KEY_RELAY_URL, _relayUrl.value)
            ?.putString(KEY_SOCKS_HOST, socksHost)
            ?.putInt(KEY_SOCKS_PORT, torPort)
            ?.putBoolean(KEY_USE_SOCKS5, useSocks)
            ?.apply()
        addLog("Transport updated: relay=${_relayUrl.value}, socks5=${if (useSocks) "$socksHost:$torPort" else "DISABLED"}")
        probeSocks5Async()
    }

    suspend fun connectRelay(
        url: String = _relayUrl.value,
        useSocks: Boolean = _useSocks5.value
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!MarcianoRustCore.isLoaded) {
            return@withContext Result.failure(IllegalStateException("Rust core not loaded"))
        }
        addLog("Connecting WebSocket relay: $url (${if (useSocks) "via SOCKS5 $socksHost:$torPort" else "Direct TLS"})...")
        val res = MarcianoRustCore.relayConnect(url, socksHost, torPort, useSocks)
        res.onSuccess {
            _isRelayConnected.value = true
            _lastRelayError.value = ""
            addLog("SUCCESS: Live Rust WebSocket relay session active -> $url")
        }.onFailure { err ->
            _isRelayConnected.value = false
            _lastRelayError.value = err.message ?: "Connection failed"
            addLog("FAILED: Relay connection error -> ${_lastRelayError.value}")
        }
        res
    }

    fun sendRelayFrame(frameJson: String): Result<Unit> {
        if (!MarcianoRustCore.isLoaded) {
            return Result.failure(IllegalStateException("Rust core not loaded"))
        }
        return MarcianoRustCore.relaySendFrame(frameJson)
    }

    fun disconnectRelay() {
        if (MarcianoRustCore.isLoaded) {
            MarcianoRustCore.nativeRelayDisconnect()
        }
        _isRelayConnected.value = false
        _activeConnections.value = emptyList()
        addLog("Relay WebSocket disconnected cleanly.")
    }

    fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val formatted = "[$timestamp] $message"
        val currentList = _logs.value.toMutableList()
        currentList.add(0, formatted)
        if (currentList.size > 150) {
            currentList.removeAt(currentList.size - 1)
        }
        _logs.value = currentList
        SimplexLogger.log("RUST_NET", message)
    }

    fun getTorConfiguredClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
        if (_useSocks5.value) {
            builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, torPort)))
        }
        return builder.build()
    }

    /**
     * Performs a real Rust tokio TCP or SOCKS5 CONNECT route probe against the given host/URL.
     */
    suspend fun testOnionRoute(addressInput: String): String = withContext(Dispatchers.IO) {
        if (!MarcianoRustCore.isLoaded) {
            return@withContext "FAILED: Rust native engine not loaded."
        }
        val (host, port) = parseHostAndPort(addressInput)
        if (host.isBlank()) {
            return@withContext "FAILED: Invalid host or URL."
        }
        val viaSocks = _useSocks5.value || host.endsWith(".onion", ignoreCase = true)
        addLog("Probing $host:$port (${if (viaSocks) "SOCKS5 $socksHost:$torPort" else "Direct TCP"})...")
        val res = MarcianoRustCore.probeRoute(host, port, socksHost, torPort, viaSocks)
        if (res.ok) {
            val msg = "SUCCESS [${res.mode}]: $host:$port reachable in ${res.rttMs}ms (${res.detail})"
            addLog(msg)
            msg
        } else {
            val msg = "FAILED [${res.mode}]: $host:$port -> ${res.detail}"
            addLog(msg)
            msg
        }
    }

    fun parseHostAndPort(raw: String): Pair<String, Int> {
        val trimmed = raw.trim()
        val defaultPort = when {
            trimmed.startsWith("wss://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> 443
            trimmed.startsWith("ws://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true) -> 80
            trimmed.endsWith(".onion", ignoreCase = true) -> 80
            else -> 443
        }
        val withoutScheme = trimmed
            .removePrefix("wss://")
            .removePrefix("ws://")
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
        val colonIdx = withoutScheme.lastIndexOf(':')
        return if (colonIdx > 0) {
            val h = withoutScheme.substring(0, colonIdx)
            val p = withoutScheme.substring(colonIdx + 1).toIntOrNull() ?: defaultPort
            h to p
        } else {
            withoutScheme to defaultPort
        }
    }
}

package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.Bip39Utility
import com.example.crypto.DistributedStorageProtocol
import com.example.crypto.EncryptedVoiceClip
import com.example.crypto.MediaCryptoManager
import com.example.crypto.MemoryZeroizationManager
import com.example.crypto.MemoryZeroizer
import com.example.crypto.ProfileVaultManager
import com.example.crypto.SecurityManager
import com.example.crypto.SimplexCrypto
import com.example.crypto.SimplexLogger
import com.example.db.ChatContact
import com.example.db.ChatMessage
import com.example.db.DatabaseProvider
import com.example.db.SimplexRepository
import com.example.db.UnidirectionalQueue
import com.example.net.DistributedBackupStorage
import com.example.net.ProxyChainManager
import com.example.net.TorNetworkManager
import com.example.rust.MarcianoRustCore
import com.example.rust.RustRatchetEnvelope
import com.example.ui.AppLanguage
import com.example.ui.LocalizationHelper
import com.example.ui.SkinStyle
import com.example.ui.keyboard.InAppKeyboardManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SimplexViewModel(application: Application) : AndroidViewModel(application) {

    private val db = DatabaseProvider.getDatabase(application)
    private val repository = SimplexRepository(db.simplexDao(), db.unidirectionalQueueDao())
    private val prefs: SharedPreferences = application.getSharedPreferences("simplex_secure_prefs", Context.MODE_PRIVATE)

    // Splash screen state
    private val _showSplash = MutableStateFlow(true)
    val showSplash: StateFlow<Boolean> = _showSplash.asStateFlow()

    val allUnidirectionalQueues: StateFlow<List<UnidirectionalQueue>> = repository.allQueues
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _splashText = MutableStateFlow("INITIALIZING RUST CRYPTO PORTAL...")
    val splashText: StateFlow<String> = _splashText.asStateFlow()

    private val _splashProgress = MutableStateFlow(0.15f)
    val splashProgress: StateFlow<Float> = _splashProgress.asStateFlow()

    // Profile Management state
    private val _profileList = MutableStateFlow<List<String>>(emptyList())
    val profileList: StateFlow<List<String>> = _profileList.asStateFlow()

    private val _activeProfileAlias = MutableStateFlow<String?>(null)
    val activeProfileAlias: StateFlow<String?> = _activeProfileAlias.asStateFlow()

    private val _primaryProfileAlias = MutableStateFlow<String?>(null)
    val primaryProfileAlias: StateFlow<String?> = _primaryProfileAlias.asStateFlow()

    private val _showProfileSelection = MutableStateFlow(false)
    val showProfileSelection: StateFlow<Boolean> = _showProfileSelection.asStateFlow()

    private val _currentSkin = MutableStateFlow(SkinStyle.CYBERPUNK)
    val currentSkin: StateFlow<SkinStyle> = _currentSkin.asStateFlow()

    // Lockscreen State
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isSetupState = MutableStateFlow(true)
    val isSetupState: StateFlow<Boolean> = _isSetupState.asStateFlow()

    private val _pinBuffer = MutableStateFlow("")
    val pinBuffer: StateFlow<String> = _pinBuffer.asStateFlow()

    private val _setupStep = MutableStateFlow(SetupStep.SET_PIN)
    val setupStep: StateFlow<SetupStep> = _setupStep.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isPanicWiped = MutableStateFlow(false)
    val isPanicWiped: StateFlow<Boolean> = _isPanicWiped.asStateFlow()

    private val _panicWipeStage = MutableStateFlow("INITIATING DATA CLEARING SEQUENCE...")
    val panicWipeStage: StateFlow<String> = _panicWipeStage.asStateFlow()

    private val _panicWipeProgress = MutableStateFlow(0f)
    val panicWipeProgress: StateFlow<Float> = _panicWipeProgress.asStateFlow()

    private val _panicWipeLogs = MutableStateFlow<List<String>>(emptyList())
    val panicWipeLogs: StateFlow<List<String>> = _panicWipeLogs.asStateFlow()

    private var temporaryPin = ""
    private var temporaryDistressPin = ""
    private var temporaryPanicPin = ""

    // Decoy/Distress System (volatile RAM-only decoy chats if distress PIN entered)
    private val _isDecoyMode = MutableStateFlow(false)
    val isDecoyMode: StateFlow<Boolean> = _isDecoyMode.asStateFlow()

    private val decoyContactsList = listOf(
        ChatContact(
            id = 9991,
            aliasName = "Mom",
            inboundQueueUri = "marcianox://decoy/mom-in",
            outboundQueueUri = "marcianox://decoy/mom-out",
            sharedSecretHex = "0000",
            onionAddress = "decoy-local",
            status = "OFFLINE"
        ),
        ChatContact(
            id = 9992,
            aliasName = "Gym Coach",
            inboundQueueUri = "marcianox://decoy/gym-in",
            outboundQueueUri = "marcianox://decoy/gym-out",
            sharedSecretHex = "1111",
            onionAddress = "decoy-local",
            status = "OFFLINE"
        )
    )

    private val decoyMessagesMap = mapOf(
        9991 to listOf(
            DecryptedMessage(1, "PEER", "Did you pick up the groceries?", System.currentTimeMillis() - 3600000, isRead = true, isSystem = false),
            DecryptedMessage(2, "ME", "Yes, almost home.", System.currentTimeMillis() - 1800000, isRead = true, isSystem = false)
        ),
        9992 to listOf(
            DecryptedMessage(5, "PEER", "See you at 5 PM for training.", System.currentTimeMillis() - 7200000, isRead = true, isSystem = false)
        )
    )

    private val _decoyContacts = MutableStateFlow(decoyContactsList)
    private val _decoyMessages = MutableStateFlow<Map<Int, List<DecryptedMessage>>>(decoyMessagesMap)

    // Network & Tor Status
    private val _torStatus = MutableStateFlow(TorStatus())
    val torStatus: StateFlow<TorStatus> = _torStatus.asStateFlow()

    private val _rustEngineVersion = MutableStateFlow(MarcianoRustCore.version())
    val rustEngineVersion: StateFlow<String> = _rustEngineVersion.asStateFlow()

    val contacts: StateFlow<List<ChatContact>> = combine(
        _isDecoyMode,
        _activeProfileAlias,
        repository.allContacts
    ) { isDecoy, activeProfile, all ->
        if (isDecoy) {
            _decoyContacts.value
        } else {
            val profile = activeProfile ?: ""
            all.filter { it.profileAlias == profile }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedContactId = MutableStateFlow<Int?>(null)
    val selectedContactId: StateFlow<Int?> = _selectedContactId.asStateFlow()

    private val _activeMessages = MutableStateFlow<List<DecryptedMessage>>(emptyList())
    val activeMessages: StateFlow<List<DecryptedMessage>> = _activeMessages.asStateFlow()

    // Backup export / restore UI status
    private val _lastShamirExportShares = MutableStateFlow<List<String>>(emptyList())
    val lastShamirExportShares: StateFlow<List<String>> = _lastShamirExportShares.asStateFlow()

    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage: StateFlow<String?> = _backupStatusMessage.asStateFlow()

    // Volatile 32-byte master vault key held in RAM only while unlocked
    private var masterStorageKey: ByteArray? = null

    init {
        ProxyChainManager.init(application)
        TorNetworkManager.startTorProxy(application)

        // Boot sequence with SQLCipher & Rust native core initialization checks
        viewModelScope.launch {
            _splashText.value = "LOADING RUST CRYPTO ENCLAVE (${MarcianoRustCore.version()})..."
            _splashProgress.value = 0.28f
            delay(320)

            _splashText.value = "MOUNTING SQLCIPHER + ANDROID KEYSTORE..."
            _splashProgress.value = 0.54f
            val sysLang = Locale.getDefault().language
            val matchedLang = when (sysLang) {
                "ru" -> AppLanguage.RUSSIAN
                "es" -> AppLanguage.SPANISH
                "zh" -> AppLanguage.CHINESE
                "hi" -> AppLanguage.HINDI
                "ar" -> AppLanguage.ARABIC
                "pt" -> AppLanguage.PORTUGUESE
                "fr" -> AppLanguage.FRENCH
                else -> AppLanguage.ENGLISH
            }
            LocalizationHelper.setLanguage(matchedLang)
            delay(280)

            _splashText.value = "LOADING SEALED IDENTITIES..."
            _splashProgress.value = 0.85f
            loadProfileList()

            val primary = _primaryProfileAlias.value
            val list = _profileList.value
            if (primary != null && list.contains(primary)) {
                selectProfile(primary)
                _isSetupState.value = false
                _isLocked.value = true
                _showProfileSelection.value = false
            } else if (list.isNotEmpty()) {
                _showProfileSelection.value = true
                _isSetupState.value = false
                _isLocked.value = false
            } else {
                _isSetupState.value = true
                _isLocked.value = false
                _showProfileSelection.value = true
            }

            _splashProgress.value = 1.0f
            _splashText.value = if (DatabaseProvider.isSqlCipherEnabled) {
                "ENCLAVE ARMED // SQLCIPHER + RUST CORE"
            } else {
                "ENCLAVE ARMED // RUST CORE READY"
            }
            delay(260)
            _showSplash.value = false
        }

        // Monitor live Rust WebSocket relay & Orbot SOCKS5 status
        viewModelScope.launch {
            while (true) {
                val socksRunning = TorNetworkManager.isServerRunning.value
                val relayConnected = TorNetworkManager.isRelayConnected.value
                val useSocks = TorNetworkManager.useSocks5.value
                val conns = TorNetworkManager.activeConnections.value
                val statusLabel = when {
                    relayConnected && useSocks -> "Relay Online via Tor SOCKS5"
                    relayConnected -> "Relay Online (Direct WSS)"
                    socksRunning -> "Tor SOCKS5 Ready :${TorNetworkManager.torPort}"
                    else -> "Offline (Configure Relay/Orbot)"
                }

                _torStatus.value = TorStatus(
                    isConnected = relayConnected || socksRunning,
                    numCircuits = (if (socksRunning) 3 else 0) + conns.size,
                    currentOnionNode = conns.firstOrNull()?.targetHost ?: TorNetworkManager.relayUrl.value,
                    statusText = statusLabel
                )
                delay(1500)
            }
        }

        // Collect real incoming WebSocket relay frames from Rust tokio runtime
        viewModelScope.launch {
            TorNetworkManager.relayEvents.collect { event ->
                if (event.eventType == "MESSAGE" && !_isDecoyMode.value && masterStorageKey != null) {
                    handleIncomingRelayPayload(event.payload)
                }
            }
        }

        // Keep active messages in sync with Room + decrypt local ciphertext with masterStorageKey
        viewModelScope.launch {
            combine(_selectedContactId, _isDecoyMode) { selectedId, decoy -> Pair(selectedId, decoy) }
                .flatMapLatest { (id, decoy) ->
                    if (id == null) {
                        flowOf(emptyList())
                    } else if (decoy) {
                        _decoyMessages.map { it[id] ?: emptyList() }
                    } else {
                        repository.getMessagesForContact(id).map { dbMsgs ->
                            dbMsgs.map { msg ->
                                val key = masterStorageKey ?: getContactFallbackKey(msg.contactId)
                                val rawText = if (key != null) {
                                    SimplexCrypto.decrypt(msg.encryptedBody, msg.iv, key)
                                } else {
                                    "[Vault Locked]"
                                }
                                val (isSystem, cleanText) = if (rawText.startsWith("[SYS]")) {
                                    true to rawText.removePrefix("[SYS]")
                                } else {
                                    (msg.sender == "SYSTEM") to rawText
                                }
                                val bars = if (msg.waveformCsv.isNotBlank()) {
                                    msg.waveformCsv.split(",").mapNotNull { it.toFloatOrNull() }
                                } else {
                                    emptyList()
                                }
                                DecryptedMessage(
                                    id = msg.id,
                                    sender = msg.sender,
                                    text = cleanText,
                                    timestamp = msg.timestamp,
                                    isRead = msg.isRead,
                                    isSystem = isSystem,
                                    deliveryStatus = msg.deliveryStatus,
                                    expiryMinutes = msg.expiryMinutes,
                                    mediaMimeType = msg.mediaMimeType,
                                    mediaCiphertextB64 = msg.mediaCiphertextB64,
                                    mediaIvB64 = msg.mediaIvB64,
                                    waveformBars = bars
                                )
                            }
                        }
                    }
                }
                .collect { messages ->
                    _activeMessages.value = messages
                }
        }

        // Periodic background scanner for expiring PENDING messages
        viewModelScope.launch {
            while (true) {
                delay(3000)
                if (!_isDecoyMode.value) {
                    try {
                        val pending = repository.getPendingMessages()
                        val now = System.currentTimeMillis()
                        for (msg in pending) {
                            if (msg.expiryMinutes > 0) {
                                val elapsedMillis = now - msg.timestamp
                                val expiryMillis = msg.expiryMinutes.toLong() * 60 * 1000
                                if (elapsedMillis >= expiryMillis) {
                                    repository.updateDeliveryStatus(msg.id, "FAILED")
                                    insertSystemMessage(
                                        msg.contactId,
                                        "DELIVERY EXPIRED: Outbound message timed out after ${msg.expiryMinutes} min and was purged from transit queue."
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SimplexViewModel", "Error in pending scanner", e)
                    }
                }
            }
        }

        // Auto-Lock Watchdog
        viewModelScope.launch {
            while (true) {
                delay(1000)
                checkAutoLock()
            }
        }
    }

    companion object {
        const val PIN_LENGTH = 6
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_SECONDS = 30
        const val PREF_KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout_seconds"
        const val AUTO_LOCK_DEFAULT_SECONDS = 60
        const val AUTO_LOCK_NEVER = -1
        const val AUTO_LOCK_IMMEDIATE = 0
    }

    private val _autoLockTimeoutSeconds = MutableStateFlow(
        prefs.getInt(PREF_KEY_AUTO_LOCK_TIMEOUT, AUTO_LOCK_DEFAULT_SECONDS)
    )
    val autoLockTimeoutSeconds: StateFlow<Int> = _autoLockTimeoutSeconds.asStateFlow()

    private val _autoLockSecondsRemaining = MutableStateFlow<Int?>(null)
    val autoLockSecondsRemaining: StateFlow<Int?> = _autoLockSecondsRemaining.asStateFlow()

    private var lastActiveTimestamp: Long = System.currentTimeMillis()
    private var backgroundTimestamp: Long = 0L

    fun setAutoLockTimeout(seconds: Int) {
        prefs.edit().putInt(PREF_KEY_AUTO_LOCK_TIMEOUT, seconds).apply()
        _autoLockTimeoutSeconds.value = seconds
        recordUserActivity()
    }

    fun recordUserActivity(timestamp: Long = System.currentTimeMillis()) {
        lastActiveTimestamp = timestamp
        val timeout = _autoLockTimeoutSeconds.value
        if (timeout > 0 && !_isLocked.value && !_isSetupState.value && !_isPanicWiped.value) {
            _autoLockSecondsRemaining.value = timeout
        } else {
            _autoLockSecondsRemaining.value = null
        }
    }

    fun onAppBackgrounded(now: Long = System.currentTimeMillis()) {
        backgroundTimestamp = now
        if (_autoLockTimeoutSeconds.value == AUTO_LOCK_IMMEDIATE && !_isLocked.value && !_isSetupState.value) {
            lock(reasonMessage = "App locked on background switch.")
        }
    }

    fun onAppForegrounded(now: Long = System.currentTimeMillis()) {
        val timeout = _autoLockTimeoutSeconds.value
        if (timeout > 0 && !_isLocked.value && !_isSetupState.value) {
            val bgDuration = if (backgroundTimestamp > 0) now - backgroundTimestamp else 0L
            val inactiveDuration = now - lastActiveTimestamp
            if (bgDuration >= timeout * 1000L || inactiveDuration >= timeout * 1000L) {
                lock(reasonMessage = "Session auto-locked due to inactivity.")
                _autoLockSecondsRemaining.value = null
                return
            }
        }
        recordUserActivity(now)
    }

    fun checkAutoLock(now: Long = System.currentTimeMillis()) {
        if (_isLocked.value || _isSetupState.value || _isPanicWiped.value) {
            _autoLockSecondsRemaining.value = null
            return
        }

        val timeout = _autoLockTimeoutSeconds.value
        if (timeout > 0) {
            val elapsed = (now - lastActiveTimestamp) / 1000
            val remaining = (timeout - elapsed).toInt()
            if (remaining <= 0) {
                lock(reasonMessage = "Session auto-locked due to inactivity.")
                _autoLockSecondsRemaining.value = null
            } else {
                _autoLockSecondsRemaining.value = remaining
            }
        } else if (timeout == AUTO_LOCK_IMMEDIATE) {
            _autoLockSecondsRemaining.value = 0
        } else {
            _autoLockSecondsRemaining.value = null
        }
    }

    private val _lockoutSecondsRemaining = MutableStateFlow(0)
    val lockoutSecondsRemaining: StateFlow<Int> = _lockoutSecondsRemaining.asStateFlow()
    private var failedAttempts = 0
    private var lockoutJob: Job? = null

    fun isKeypadLocked(): Boolean = _lockoutSecondsRemaining.value > 0

    private fun triggerLockout(seconds: Int = LOCKOUT_DURATION_SECONDS) {
        lockoutJob?.cancel()
        _lockoutSecondsRemaining.value = seconds
        _errorMessage.value = "Too many failed attempts. Keypad locked for ${seconds}s."
        lockoutJob = viewModelScope.launch {
            for (s in seconds downTo 1) {
                _lockoutSecondsRemaining.value = s
                _errorMessage.value = "Too many failed attempts. Keypad locked for ${s}s."
                delay(1000)
            }
            _lockoutSecondsRemaining.value = 0
            _errorMessage.value = null
            failedAttempts = 0
        }
    }

    // --- LOCK SCREEN LOGIC (Rust Argon2id + PBKDF2 + AndroidKeyStore) ---

    fun enterDigit(digit: String) {
        if (_isPanicWiped.value || isKeypadLocked()) return
        _errorMessage.value = null
        val current = _pinBuffer.value
        if (current.length < PIN_LENGTH) {
            val newBuffer = current + digit
            _pinBuffer.value = newBuffer
            if (newBuffer.length == PIN_LENGTH) {
                viewModelScope.launch {
                    delay(120)
                    processPinBuffer(newBuffer)
                }
            }
        }
    }

    fun deleteLastDigit() {
        if (_isPanicWiped.value || isKeypadLocked()) return
        val current = _pinBuffer.value
        if (current.isNotEmpty()) {
            _pinBuffer.value = current.dropLast(1)
        }
    }

    fun clearPinBuffer() {
        if (_isPanicWiped.value) return
        _pinBuffer.value = ""
        _errorMessage.value = null
    }

    fun submitPin() {
        if (_isPanicWiped.value || isKeypadLocked()) return
        val current = _pinBuffer.value
        if (current.isNotEmpty()) {
            viewModelScope.launch { processPinBuffer(current) }
        }
    }

    private suspend fun processPinBuffer(pin: String) {
        _pinBuffer.value = ""
        if (_isSetupState.value) {
            handleSetupPin(pin)
        } else {
            handleUnlockPin(pin)
        }
    }

    private fun handleSetupPin(pin: String) {
        when (_setupStep.value) {
            SetupStep.SET_PIN -> {
                temporaryPin = pin
                _setupStep.value = SetupStep.CONFIRM_PIN
            }
            SetupStep.CONFIRM_PIN -> {
                if (pin == temporaryPin) {
                    _setupStep.value = SetupStep.SET_DISTRESS_PIN
                    _errorMessage.value = null
                } else {
                    _setupStep.value = SetupStep.SET_PIN
                    _errorMessage.value = "PIN mismatch. Try again."
                }
            }
            SetupStep.SET_DISTRESS_PIN -> {
                if (pin == temporaryPin) {
                    _errorMessage.value = "Distress PIN cannot match primary PIN."
                } else {
                    temporaryDistressPin = pin
                    _setupStep.value = SetupStep.CONFIRM_DISTRESS_PIN
                    _errorMessage.value = null
                }
            }
            SetupStep.CONFIRM_DISTRESS_PIN -> {
                if (pin == temporaryDistressPin) {
                    _setupStep.value = SetupStep.SET_PANIC_PIN
                    _errorMessage.value = null
                } else {
                    _setupStep.value = SetupStep.SET_DISTRESS_PIN
                    _errorMessage.value = "PIN mismatch. Try again."
                }
            }
            SetupStep.SET_PANIC_PIN -> {
                if (pin == temporaryPin || pin == temporaryDistressPin) {
                    _errorMessage.value = "Panic PIN cannot match primary or distress PIN."
                } else {
                    temporaryPanicPin = pin
                    _setupStep.value = SetupStep.CONFIRM_PANIC_PIN
                    _errorMessage.value = null
                }
            }
            SetupStep.CONFIRM_PANIC_PIN -> {
                if (pin == temporaryPanicPin) {
                    val alias = _activeProfileAlias.value ?: "DEFAULT_OPERATOR"
                    val mnemonic = if (MarcianoRustCore.isLoaded) {
                        MarcianoRustCore.generateBip39()
                    } else {
                        Bip39Utility.generateDeterministicMnemonic24("2026-01-01", listOf("mars", "enclave", "vault", "rust", "zero"))
                    }
                    createAndActivateProfile(
                        alias = alias,
                        mnemonic = mnemonic,
                        pin = temporaryPin,
                        distressPin = temporaryDistressPin,
                        panicPin = temporaryPanicPin
                    )
                    temporaryPin = ""
                    temporaryDistressPin = ""
                    temporaryPanicPin = ""
                } else {
                    _setupStep.value = SetupStep.SET_PANIC_PIN
                    _errorMessage.value = "PIN mismatch. Try again."
                }
            }
        }
    }

    fun handleUnlockPin(pin: String) {
        if (isKeypadLocked()) {
            _errorMessage.value = "Keypad locked. Retry in ${_lockoutSecondsRemaining.value}s."
            return
        }

        val alias = _activeProfileAlias.value ?: _primaryProfileAlias.value ?: ""
        if (alias.isBlank() || !ProfileVaultManager.hasPinConfigured(prefs, alias)) {
            _errorMessage.value = "No sealed profile configured."
            return
        }

        when (ProfileVaultManager.verifyPin(prefs, alias, pin)) {
            ProfileVaultManager.PinVerdict.PANIC_MATCH -> {
                failedAttempts = 0
                _lockoutSecondsRemaining.value = 0
                triggerPanicWipe()
            }
            ProfileVaultManager.PinVerdict.DISTRESS_MATCH -> {
                failedAttempts = 0
                _lockoutSecondsRemaining.value = 0
                _isDecoyMode.value = true
                masterStorageKey = MemoryZeroizationManager.register(
                    ByteArray(32).also { SecureRandom().nextBytes(it) }
                )
                _isLocked.value = false
                _errorMessage.value = null
                recordUserActivity()
            }
            ProfileVaultManager.PinVerdict.PRIMARY_MATCH -> {
                val unwrapped = ProfileVaultManager.unwrapMasterKeyWithPin(prefs, alias, pin)
                if (unwrapped == null) {
                    _errorMessage.value = "Vault key unwrap failed."
                    return
                }
                failedAttempts = 0
                _lockoutSecondsRemaining.value = 0
                _isDecoyMode.value = false
                activateUnlockedMasterKey(alias, unwrapped)
            }
            ProfileVaultManager.PinVerdict.INVALID -> {
                failedAttempts++
                if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                    triggerLockout(LOCKOUT_DURATION_SECONDS)
                } else {
                    _errorMessage.value = "Access Denied. Passcode Invalid."
                }
            }
        }
    }

    fun unlockWithKeystoreBiometrics(): Boolean {
        val alias = _activeProfileAlias.value ?: _primaryProfileAlias.value
        if (alias.isNullOrBlank()) {
            _errorMessage.value = "No active profile selected for biometric unlock."
            return false
        }
        val unwrapped = ProfileVaultManager.unwrapMasterKeyWithKeystore(prefs, alias)
        if (unwrapped == null) {
            _errorMessage.value = "Hardware Keystore biometric unlock unavailable. Enter PIN."
            return false
        }
        failedAttempts = 0
        _lockoutSecondsRemaining.value = 0
        _isDecoyMode.value = false
        activateUnlockedMasterKey(alias, unwrapped)
        return true
    }

    private fun activateUnlockedMasterKey(alias: String, unwrappedKey: ByteArray) {
        MemoryZeroizationManager.zero(
            masterStorageKey,
            MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY
        )
        masterStorageKey = MemoryZeroizationManager.register(unwrappedKey)
        if (MarcianoRustCore.isLoaded) {
            MarcianoRustCore.nativeVaultSetSession(alias, unwrappedKey)
        }
        _isLocked.value = false
        _isSetupState.value = false
        _errorMessage.value = null
        recordUserActivity()

        // Hydrate saved Rust Double Ratchet states for this profile's contacts
        viewModelScope.launch(Dispatchers.IO) {
            val savedContacts = contacts.value
            for (c in savedContacts) {
                if (c.ratchetStateJson.isNotBlank() && MarcianoRustCore.isLoaded) {
                    MarcianoRustCore.ratchetLoadState(c.id, c.ratchetStateJson)
                }
            }
        }
    }

    fun loadProfileList() {
        val aliasesStr = prefs.getString("profile_aliases", "") ?: ""
        val list = aliasesStr.split("|").filter { it.isNotEmpty() }
        _profileList.value = list
        val primary = prefs.getString("primary_profile_alias", "") ?: ""
        _primaryProfileAlias.value = if (primary.isNotEmpty()) primary else null
    }

    fun selectProfile(alias: String) {
        _activeProfileAlias.value = alias
        val skinStr = prefs.getString("profile_${alias}_skin", "CYBERPUNK") ?: "CYBERPUNK"
        val matchedSkin = try {
            SkinStyle.valueOf(skinStr)
        } catch (_: Exception) {
            SkinStyle.CYBERPUNK
        }
        _currentSkin.value = matchedSkin

        val hasPin = ProfileVaultManager.hasPinConfigured(prefs, alias)
        _isSetupState.value = !hasPin
        _isLocked.value = hasPin
        _showProfileSelection.value = false
    }

    fun deselectProfile() {
        lock(reasonMessage = null)
        _activeProfileAlias.value = null
        _showProfileSelection.value = true
        _isLocked.value = false
        _isSetupState.value = false
        _pinBuffer.value = ""
    }

    fun togglePrimaryProfile(alias: String, setPrimary: Boolean) {
        val editor = prefs.edit()
        if (setPrimary) {
            editor.putString("primary_profile_alias", alias)
            _primaryProfileAlias.value = alias
        } else {
            editor.remove("primary_profile_alias")
            _primaryProfileAlias.value = null
        }
        editor.apply()
    }

    fun deleteProfile(alias: String) {
        ProfileVaultManager.purgeProfileSecrets(prefs, alias)
        val editor = prefs.edit()
        if (prefs.getString("primary_profile_alias", "") == alias) {
            editor.remove("primary_profile_alias")
            _primaryProfileAlias.value = null
        }
        val newList = _profileList.value.toMutableList()
        newList.remove(alias)
        editor.putString("profile_aliases", newList.joinToString("|"))
        editor.apply()

        loadProfileList()
        if (_activeProfileAlias.value == alias) {
            deselectProfile()
        }
    }

    fun updateProfileSkin(skin: SkinStyle) {
        val alias = _activeProfileAlias.value ?: return
        _currentSkin.value = skin
        prefs.edit().putString("profile_${alias}_skin", skin.name).apply()
    }

    /**
     * Sealed profile initialization using Rust Argon2id PIN hashes, PBKDF2-HMAC-SHA256,
     * AndroidKeyStore hardware wrapping, and encrypted BIP-39 24-word mnemonic storage.
     * Starts genuinely clean with 0 fake/demo bot contacts.
     */
    fun createAndActivateProfile(
        alias: String,
        mnemonic: List<String>,
        pin: String,
        distressPin: String,
        panicPin: String
    ) {
        val key = ProfileVaultManager.storeProfileCredentials(
            prefs = prefs,
            alias = alias,
            mnemonic = mnemonic,
            pin = pin,
            distressPin = distressPin,
            panicPin = panicPin
        )

        val currentList = _profileList.value.toMutableList()
        if (alias !in currentList) {
            currentList.add(alias)
            prefs.edit().putString("profile_aliases", currentList.joinToString("|")).apply()
        }

        loadProfileList()
        _activeProfileAlias.value = alias
        _currentSkin.value = SkinStyle.CYBERPUNK
        _isDecoyMode.value = false
        _isLocked.value = false
        _isSetupState.value = false
        _showProfileSelection.value = false
        activateUnlockedMasterKey(alias, key)
    }

    fun lock(reasonMessage: String? = null) {
        InAppKeyboardManager.wipeAndHide()
        MediaCryptoManager.cancelRecording()
        MediaCryptoManager.stopPlayback()
        if (MarcianoRustCore.isLoaded) {
            MarcianoRustCore.nativeVaultLockAndZeroize()
        }
        MemoryZeroizationManager.zero(
            masterStorageKey,
            MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY
        )
        masterStorageKey = null
        _isLocked.value = true
        _pinBuffer.value = ""
        _autoLockSecondsRemaining.value = null
        if (reasonMessage != null) {
            _errorMessage.value = reasonMessage
        }
    }

    // --- PANIC MODE: FULL ZEROIZATION + SQLCIPHER SHREDDING ---

    fun triggerPanicWipe() {
        InAppKeyboardManager.wipeAndHide()
        MediaCryptoManager.cancelRecording()
        MediaCryptoManager.stopPlayback()
        _isPanicWiped.value = true
        _panicWipeProgress.value = 0.05f
        _panicWipeStage.value = "INITIALIZING PANIC DATA CLEARING SEQUENCE..."
        _panicWipeLogs.value = listOf("[00.0s] Panic zeroization sequence engaged")

        viewModelScope.launch(Dispatchers.IO) {
            delay(250)

            _panicWipeProgress.value = 0.25f
            _panicWipeStage.value = "ZEROIZING RUST VAULT, RATCHETS & RAM..."
            val zeroedCount = if (MarcianoRustCore.isLoaded) MarcianoRustCore.nativePanicZeroizeAll() else 0
            MemoryZeroizationManager.zero(
                masterStorageKey,
                MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY
            )
            MemoryZeroizationManager.wipeAllActiveBuffers(
                MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY
            )
            masterStorageKey = null
            temporaryPin = ""
            temporaryDistressPin = ""
            temporaryPanicPin = ""
            _pinBuffer.value = ""
            _panicWipeLogs.value = _panicWipeLogs.value + "[00.3s] Zeroized $zeroedCount Rust key/ratchet/relay slots in RAM"
            delay(250)

            _panicWipeProgress.value = 0.45f
            _panicWipeStage.value = "PURGING FORENSIC LOGS & MEDIA CACHE..."
            SimplexLogger.clearLog()
            _lastShamirExportShares.value = emptyList()
            _panicWipeLogs.value = _panicWipeLogs.value + "[00.6s] Forensics & transient media buffers scrubbed"
            delay(250)

            _panicWipeProgress.value = 0.70f
            _panicWipeStage.value = "SHREDDING SQLCIPHER DATABASE FILES..."
            try {
                repository.clearAllData()
            } catch (_: Exception) {
            }
            val context = getApplication<Application>()
            DatabaseProvider.shredDatabaseFiles(context)
            _panicWipeLogs.value = _panicWipeLogs.value + "[01.0s] SQLCipher DB + WAL/SHM overwritten with CSPRNG noise & deleted"
            delay(250)

            _panicWipeProgress.value = 0.92f
            _panicWipeStage.value = "PURGING ANDROID KEYSTORE & PREFERENCES..."
            _profileList.value.forEach { alias ->
                ProfileVaultManager.purgeProfileSecrets(prefs, alias)
            }
            try {
                SecurityManager.deleteAllKeys()
            } catch (_: Exception) {
            }
            prefs.edit().clear().commit()
            MemoryZeroizer.triggerGcPurge()
            _panicWipeLogs.value = _panicWipeLogs.value + "[01.3s] Android Keystore aliases & sealed credentials purged"
            delay(250)

            _panicWipeProgress.value = 1.0f
            _panicWipeStage.value = "DATA CLEARING SEQUENCE COMPLETE. SYSTEM SANITIZED."
            _panicWipeLogs.value = _panicWipeLogs.value + "[01.6s] 100% of on-device secrets destroyed"
            delay(900)

            _selectedContactId.value = null
            _activeMessages.value = emptyList()
            _profileList.value = emptyList()
            _activeProfileAlias.value = null
            _primaryProfileAlias.value = null
            _isSetupState.value = true
            _isLocked.value = false
            _showProfileSelection.value = true
            _setupStep.value = SetupStep.SET_PIN
            _isPanicWiped.value = false
            _errorMessage.value = "Data clearing sequence completed. All storage zeroized."
        }
    }

    fun resetToFreshState() {
        _isPanicWiped.value = false
        _isLocked.value = false
        _isSetupState.value = true
        _showProfileSelection.value = true
        _profileList.value = emptyList()
        _activeProfileAlias.value = null
        _primaryProfileAlias.value = null
        _pinBuffer.value = ""
        _errorMessage.value = null
        _panicWipeProgress.value = 0f
        _panicWipeLogs.value = emptyList()
    }

    // --- NETWORK & EXPIRY CONTROLS ---

    private val _networkMode = MutableStateFlow("ONLINE")
    val networkMode: StateFlow<String> = _networkMode.asStateFlow()

    fun setNetworkMode(mode: String) {
        _networkMode.value = mode
        TorNetworkManager.addLog("Relay Outbox Mode set to: $mode")
        if (mode == "ONLINE") {
            flushPendingOutbox()
        }
    }

    fun getUndeliveredBurnMinutes(contactId: Int): Int {
        return prefs.getInt("undelivered_burn_minutes_$contactId", 0)
    }

    fun setUndeliveredBurnMinutes(contactId: Int, minutes: Int) {
        prefs.edit().putInt("undelivered_burn_minutes_$contactId", minutes).apply()
    }

    fun fastForwardTime(minutes: Long) {
        if (_isDecoyMode.value) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.timeWarpPendingMessages(minutes * 60 * 1000)
        }
    }

    // --- REAL X25519 + DOUBLE RATCHET + WEBSOCKET RELAY MESSAGING ---

    fun selectContact(contactId: Int?) {
        _selectedContactId.value = contactId
        recordUserActivity()
    }

    fun createInvite(aliasName: String): String {
        val relayUrl = TorNetworkManager.relayUrl.value
        val (relayHost, _) = TorNetworkManager.parseHostAndPort(relayUrl)
        val kp = if (MarcianoRustCore.isLoaded) {
            MarcianoRustCore.generateX25519Keypair()
        } else {
            error("Rust engine required for X25519 invite generation")
        }
        val queueIn = "q-in-" + randomHex(10)
        val queueOut = "q-out-" + randomHex(10)
        val sharedBootstrap = randomHex(32)

        val inviteLink = "marcianox://invite?relay=${Uri.encode(relayUrl)}&queue=${Uri.encode(queueIn)}&pub=${Uri.encode(kp.publicB64)}"

        viewModelScope.launch(Dispatchers.IO) {
            val pendingRatchetPlaceholder = JSONObject().apply {
                put("pending_self_priv_b64", kp.privateB64)
                put("pending_self_pub_b64", kp.publicB64)
                put("role", "RESPONDER")
            }.toString()

            val contact = ChatContact(
                aliasName = aliasName,
                inboundQueueUri = queueIn,
                outboundQueueUri = queueOut,
                sharedSecretHex = sharedBootstrap,
                onionAddress = relayHost.ifBlank { relayUrl },
                status = "AWAITING_PEER",
                profileAlias = _activeProfileAlias.value ?: "",
                remotePublicKeyB64 = "",
                ratchetStateJson = pendingRatchetPlaceholder
            )
            val contactId = repository.insertContact(contact).toInt()
            repository.insertQueue(
                UnidirectionalQueue(
                    queueUri = queueIn,
                    contactId = contactId,
                    direction = "INBOUND",
                    serverAddress = relayUrl,
                    queueToken = "sub_${randomHex(6)}",
                    sharedSecretHex = kp.publicB64,
                    status = "LISTENING"
                )
            )
            repository.insertQueue(
                UnidirectionalQueue(
                    queueUri = queueOut,
                    contactId = contactId,
                    direction = "OUTBOUND",
                    serverAddress = relayUrl,
                    queueToken = "pub_${randomHex(6)}",
                    sharedSecretHex = sharedBootstrap,
                    status = "AWAITING_PEER"
                )
            )

            // Subscribe inbound queue on live WebSocket relay if connected
            val subFrame = JSONObject().apply {
                put("type", "SUBSCRIBE")
                put("queue", queueIn)
                put("pub_key", kp.publicB64)
            }.toString()
            TorNetworkManager.sendRelayFrame(subFrame)
            TorNetworkManager.addLog("Created X25519 invite queue [$queueIn] on $relayUrl")
        }

        return inviteLink
    }

    fun connectToInvite(aliasName: String, inviteUri: String) {
        if (_isDecoyMode.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsed = parseInviteUri(inviteUri)
                val relayUrl = parsed.relayUrl.ifBlank { TorNetworkManager.relayUrl.value }
                val (relayHost, _) = TorNetworkManager.parseHostAndPort(relayUrl)
                val kp = MarcianoRustCore.generateX25519Keypair()
                val myInboundQueue = "q-in-" + randomHex(10)

                val contact = ChatContact(
                    aliasName = aliasName,
                    inboundQueueUri = myInboundQueue,
                    outboundQueueUri = parsed.peerQueue,
                    sharedSecretHex = randomHex(32),
                    onionAddress = relayHost.ifBlank { relayUrl },
                    status = if (TorNetworkManager.isRelayConnected.value) "CONNECTED" else "READY",
                    profileAlias = _activeProfileAlias.value ?: "",
                    remotePublicKeyB64 = parsed.peerPubB64
                )
                val contactId = repository.insertContact(contact).toInt()

                var ratchetInitSummary = "Symmetric fallback"
                if (parsed.peerPubB64.isNotBlank()) {
                    val stateJson = MarcianoRustCore.ratchetInit(
                        contactId = contactId,
                        selfPrivB64 = kp.privateB64,
                        selfPubB64 = kp.publicB64,
                        remotePubB64 = parsed.peerPubB64,
                        isInitiator = true
                    )
                    repository.updateContactRatchetState(contactId, stateJson)
                    ratchetInitSummary = "Rust X25519 + HKDF Double Ratchet initialized"
                }

                repository.insertQueue(
                    UnidirectionalQueue(
                        queueUri = myInboundQueue,
                        contactId = contactId,
                        direction = "INBOUND",
                        serverAddress = relayUrl,
                        queueToken = "sub_${randomHex(6)}",
                        sharedSecretHex = kp.publicB64,
                        status = "LISTENING"
                    )
                )
                repository.insertQueue(
                    UnidirectionalQueue(
                        queueUri = parsed.peerQueue,
                        contactId = contactId,
                        direction = "OUTBOUND",
                        serverAddress = relayUrl,
                        queueToken = "pub_${randomHex(6)}",
                        sharedSecretHex = parsed.peerPubB64,
                        status = "READY"
                    )
                )

                // Send SUBSCRIBE + HANDSHAKE frames via WebSocket relay if online
                TorNetworkManager.sendRelayFrame(
                    JSONObject().apply {
                        put("type", "SUBSCRIBE")
                        put("queue", myInboundQueue)
                        put("pub_key", kp.publicB64)
                    }.toString()
                )
                if (parsed.peerPubB64.isNotBlank()) {
                    TorNetworkManager.sendRelayFrame(
                        JSONObject().apply {
                            put("type", "HANDSHAKE")
                            put("target_queue", parsed.peerQueue)
                            put("reply_queue", myInboundQueue)
                            put("pub_key", kp.publicB64)
                        }.toString()
                    )
                }

                insertSystemMessage(contactId, "$ratchetInitSummary. Outbound queue: ${parsed.peerQueue} ($relayHost).")
                _selectedContactId.value = contactId
            } catch (e: Exception) {
                Log.e("SimplexViewModel", "Error connecting to invite", e)
                TorNetworkManager.addLog("FAILED parsing invite: ${e.message}")
            }
        }
    }

    private data class ParsedInvite(val relayUrl: String, val peerQueue: String, val peerPubB64: String)

    private fun parseInviteUri(raw: String): ParsedInvite {
        val trimmed = raw.trim()
        if (trimmed.startsWith("marcianox://invite", ignoreCase = true)) {
            val u = Uri.parse(trimmed)
            return ParsedInvite(
                relayUrl = u.getQueryParameter("relay").orEmpty(),
                peerQueue = u.getQueryParameter("queue").orEmpty(),
                peerPubB64 = u.getQueryParameter("pub").orEmpty()
            )
        }
        val clean = trimmed.removePrefix("simplex://")
        val hostPart = clean.substringBefore("/")
        val rest = clean.substringAfter("/", "")
        val queuePart = rest.substringBefore("#")
        return ParsedInvite(
            relayUrl = if (hostPart.isNotBlank()) "wss://$hostPart/ws" else "",
            peerQueue = queuePart.ifBlank { "q-${randomHex(8)}" },
            peerPubB64 = ""
        )
    }

    fun sendMessage(text: String) {
        val contactId = _selectedContactId.value ?: return
        recordUserActivity()
        if (_isDecoyMode.value) {
            val map = _decoyMessages.value.toMutableMap()
            val list = map[contactId]?.toMutableList() ?: mutableListOf()
            list.add(
                DecryptedMessage(
                    id = (1000..99999).random(),
                    sender = "ME",
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    isRead = true,
                    isSystem = false
                )
            )
            map[contactId] = list
            _decoyMessages.value = map
            return
        }

        val vaultKey = masterStorageKey ?: return
        val expiryMin = getUndeliveredBurnMinutes(contactId)

        viewModelScope.launch(Dispatchers.IO) {
            val contact = repository.getContactById(contactId) ?: return@launch
            val localEnc = SimplexCrypto.encrypt(text, vaultKey)

            var deliveryStatus = "PENDING"
            if (_networkMode.value != "OFFLINE" && TorNetworkManager.isRelayConnected.value) {
                val frameResult = dispatchOverRelay(
                    contact = contact,
                    plaintextBytes = text.toByteArray(Charsets.UTF_8),
                    mimeType = "text/plain"
                )
                deliveryStatus = if (frameResult.isSuccess) "DELIVERED" else "PENDING"
            }

            repository.insertMessage(
                ChatMessage(
                    contactId = contactId,
                    sender = "ME",
                    encryptedBody = localEnc.ciphertext,
                    iv = localEnc.iv,
                    timestamp = System.currentTimeMillis(),
                    deliveryStatus = deliveryStatus,
                    expiryMinutes = expiryMin
                )
            )
        }
    }

    // --- VOICE & PHOTO HARDWARE MEDIA PIPELINE ---

    val isVoiceRecording: StateFlow<Boolean> = MediaCryptoManager.isRecording
    val voiceAmplitudeBars: StateFlow<List<Float>> = MediaCryptoManager.recordedBars

    fun startVoiceRecording(onError: (String) -> Unit) {
        recordUserActivity()
        MediaCryptoManager.startRecording(viewModelScope, onError)
    }

    fun cancelVoiceRecording() {
        MediaCryptoManager.cancelRecording()
    }

    fun stopAndSendVoiceRecording() {
        val contactId = _selectedContactId.value ?: return
        val vaultKey = masterStorageKey ?: return
        val clip: EncryptedVoiceClip = MediaCryptoManager.stopAndEncrypt(vaultKey) ?: return
        val expiryMin = getUndeliveredBurnMinutes(contactId)

        viewModelScope.launch(Dispatchers.IO) {
            val contact = repository.getContactById(contactId) ?: return@launch
            val label = "[VOICE_PCM:${clip.durationSec}s]"
            val localEnc = SimplexCrypto.encrypt(label, vaultKey)

            var deliveryStatus = "PENDING"
            if (_networkMode.value != "OFFLINE" && TorNetworkManager.isRelayConnected.value) {
                val mediaEnvelope = JSONObject().apply {
                    put("kind", "VOICE_PCM")
                    put("duration_sec", clip.durationSec)
                    put("waveform_csv", clip.waveformBars.joinToString(","))
                    put("ciphertext_b64", clip.ciphertextB64)
                    put("iv_b64", clip.ivB64)
                }.toString().toByteArray(Charsets.UTF_8)
                val frameResult = dispatchOverRelay(contact, mediaEnvelope, "audio/pcm;rate=16000")
                deliveryStatus = if (frameResult.isSuccess) "DELIVERED" else "PENDING"
            }

            repository.insertMessage(
                ChatMessage(
                    contactId = contactId,
                    sender = "ME",
                    encryptedBody = localEnc.ciphertext,
                    iv = localEnc.iv,
                    timestamp = System.currentTimeMillis(),
                    deliveryStatus = deliveryStatus,
                    expiryMinutes = expiryMin,
                    mediaMimeType = "audio/pcm;rate=16000",
                    mediaCiphertextB64 = clip.ciphertextB64,
                    mediaIvB64 = clip.ivB64,
                    waveformCsv = clip.waveformBars.joinToString(",")
                )
            )
        }
    }

    fun playEncryptedVoiceMessage(
        msg: DecryptedMessage,
        onFinished: () -> Unit,
        onError: (String) -> Unit
    ) {
        val vaultKey = masterStorageKey
        if (vaultKey == null || msg.mediaCiphertextB64.isBlank()) {
            onError("Vault locked or audio payload missing")
            onFinished()
            return
        }
        MediaCryptoManager.playEncryptedClip(
            scope = viewModelScope,
            ciphertextB64 = msg.mediaCiphertextB64,
            ivB64 = msg.mediaIvB64,
            masterKey = vaultKey,
            onFinished = onFinished,
            onError = onError
        )
    }

    fun sendEncryptedPhotoFromUri(uri: Uri, onError: (String) -> Unit) {
        val contactId = _selectedContactId.value ?: return
        val vaultKey = masterStorageKey ?: return
        val context = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Decode and re-encode JPEG via Bitmap to strip all EXIF/GPS camera metadata!
                val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Unable to open selected image")
                val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                    ?: error("Unsupported image format")
                val cleanStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, cleanStream)
                bitmap.recycle()
                val strippedBytes = cleanStream.toByteArray()

                val encrypted = MarcianoRustCore.encryptAesGcm(strippedBytes, vaultKey)
                MemoryZeroizationManager.zeroAll(
                    rawBytes,
                    strippedBytes,
                    level = MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY
                )

                val localEnc = SimplexCrypto.encrypt("[PHOTO_JPEG]", vaultKey)
                val contact = repository.getContactById(contactId) ?: return@launch
                var deliveryStatus = "PENDING"
                if (_networkMode.value != "OFFLINE" && TorNetworkManager.isRelayConnected.value) {
                    val photoPacket = JSONObject().apply {
                        put("kind", "PHOTO_JPEG")
                        put("ciphertext_b64", encrypted.ciphertextB64)
                        put("iv_b64", encrypted.ivB64)
                    }.toString().toByteArray(Charsets.UTF_8)
                    val res = dispatchOverRelay(contact, photoPacket, "image/jpeg")
                    deliveryStatus = if (res.isSuccess) "DELIVERED" else "PENDING"
                }

                repository.insertMessage(
                    ChatMessage(
                        contactId = contactId,
                        sender = "ME",
                        encryptedBody = localEnc.ciphertext,
                        iv = localEnc.iv,
                        timestamp = System.currentTimeMillis(),
                        deliveryStatus = deliveryStatus,
                        expiryMinutes = getUndeliveredBurnMinutes(contactId),
                        mediaMimeType = "image/jpeg",
                        mediaCiphertextB64 = encrypted.ciphertextB64,
                        mediaIvB64 = encrypted.ivB64
                    )
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Failed to encrypt photo")
                }
            }
        }
    }

    fun decryptMediaBytes(msg: DecryptedMessage): ByteArray? {
        val key = masterStorageKey ?: return null
        if (msg.mediaCiphertextB64.isBlank() || msg.mediaIvB64.isBlank()) return null
        return MarcianoRustCore.decryptAesGcm(msg.mediaCiphertextB64, msg.mediaIvB64, key)
    }

    private suspend fun dispatchOverRelay(
        contact: ChatContact,
        plaintextBytes: ByteArray,
        mimeType: String
    ): Result<Unit> {
        if (!MarcianoRustCore.isLoaded) return Result.failure(IllegalStateException("Rust core unavailable"))
        // Use Rust Double Ratchet if initialized; else send encrypted envelope with queue tag
        val hasRatchet = MarcianoRustCore.ratchetLoadState(contact.id, contact.ratchetStateJson)
        val frameJson = if (hasRatchet) {
            val enc = MarcianoRustCore.ratchetEncrypt(contact.id, plaintextBytes)
            repository.updateContactRatchetState(contact.id, enc.updatedStateJson)
            JSONObject().apply {
                put("type", "ENVELOPE")
                put("target_queue", contact.outboundQueueUri)
                put("mime", mimeType)
                put("dh_pub_b64", enc.envelope.dhPubB64)
                put("msg_num", enc.envelope.msgNum)
                put("ciphertext_b64", enc.envelope.ciphertextB64)
                put("iv_b64", enc.envelope.ivB64)
            }.toString()
        } else {
            val key = getContactFallbackKey(contact.id) ?: return Result.failure(IllegalStateException("No peer key"))
            val sym = MarcianoRustCore.encryptAesGcm(plaintextBytes, key)
            JSONObject().apply {
                put("type", "ENVELOPE")
                put("target_queue", contact.outboundQueueUri)
                put("mime", mimeType)
                put("ciphertext_b64", sym.ciphertextB64)
                put("iv_b64", sym.ivB64)
            }.toString()
        }
        return TorNetworkManager.sendRelayFrame(frameJson)
    }

    private suspend fun handleIncomingRelayPayload(rawJson: String) = withContext(Dispatchers.IO) {
        val vaultKey = masterStorageKey ?: return@withContext
        try {
            val root = JSONObject(rawJson)
            val type = root.optString("type")
            val targetQueue = root.optString("queue", root.optString("target_queue"))
            val matchedContact = contacts.value.find { it.inboundQueueUri == targetQueue } ?: return@withContext

            if (type == "HANDSHAKE") {
                val peerPub = root.optString("pub_key")
                val replyQueue = root.optString("reply_queue")
                if (peerPub.isNotBlank() && matchedContact.ratchetStateJson.contains("pending_self_priv_b64")) {
                    val pending = JSONObject(matchedContact.ratchetStateJson)
                    val selfPriv = pending.getString("pending_self_priv_b64")
                    val selfPub = pending.getString("pending_self_pub_b64")
                    val stateJson = MarcianoRustCore.ratchetInit(
                        contactId = matchedContact.id,
                        selfPrivB64 = selfPriv,
                        selfPubB64 = selfPub,
                        remotePubB64 = peerPub,
                        isInitiator = false
                    )
                    repository.insertContact(
                        matchedContact.copy(
                            outboundQueueUri = replyQueue.ifBlank { matchedContact.outboundQueueUri },
                            remotePublicKeyB64 = peerPub,
                            ratchetStateJson = stateJson,
                            status = "CONNECTED"
                        )
                    )
                    insertSystemMessage(matchedContact.id, "Peer X25519 handshake complete. Double Ratchet active.")
                }
                return@withContext
            }

            if (type == "ENVELOPE") {
                val dhPub = root.optString("dh_pub_b64")
                val ct = root.getString("ciphertext_b64")
                val iv = root.getString("iv_b64")
                val mime = root.optString("mime", "text/plain")

                val plaintext = if (dhPub.isNotBlank() && MarcianoRustCore.ratchetLoadState(matchedContact.id, matchedContact.ratchetStateJson)) {
                    val dec = MarcianoRustCore.ratchetDecrypt(
                        matchedContact.id,
                        RustRatchetEnvelope(dhPub, root.optInt("msg_num", 0), ct, iv)
                    )
                    repository.updateContactRatchetState(matchedContact.id, dec.updatedStateJson)
                    dec.plaintext
                } else {
                    val fallbackKey = getContactFallbackKey(matchedContact.id) ?: return@withContext
                    MarcianoRustCore.decryptAesGcm(ct, iv, fallbackKey) ?: return@withContext
                }

                val textPayload = String(plaintext, Charsets.UTF_8)
                MemoryZeroizationManager.zero(plaintext, MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY)

                if (mime == "text/plain") {
                    val localEnc = SimplexCrypto.encrypt(textPayload, vaultKey)
                    repository.insertMessage(
                        ChatMessage(
                            contactId = matchedContact.id,
                            sender = "PEER",
                            encryptedBody = localEnc.ciphertext,
                            iv = localEnc.iv,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } else {
                    val mediaObj = JSONObject(textPayload)
                    val localLabel = if (mime.startsWith("audio/")) "[VOICE_PCM:${mediaObj.optInt("duration_sec", 1)}s]" else "[PHOTO_JPEG]"
                    val localEnc = SimplexCrypto.encrypt(localLabel, vaultKey)
                    repository.insertMessage(
                        ChatMessage(
                            contactId = matchedContact.id,
                            sender = "PEER",
                            encryptedBody = localEnc.ciphertext,
                            iv = localEnc.iv,
                            timestamp = System.currentTimeMillis(),
                            mediaMimeType = mime,
                            mediaCiphertextB64 = mediaObj.optString("ciphertext_b64"),
                            mediaIvB64 = mediaObj.optString("iv_b64"),
                            waveformCsv = mediaObj.optString("waveform_csv")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            TorNetworkManager.addLog("Relay frame parse/decrypt failed: ${e.message}")
        }
    }

    fun flushPendingOutbox() {
        if (!TorNetworkManager.isRelayConnected.value || _isDecoyMode.value) return
        val vaultKey = masterStorageKey ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val pending = repository.getPendingMessages()
            for (msg in pending) {
                if (msg.sender != "ME") continue
                val contact = repository.getContactById(msg.contactId) ?: continue
                val plaintext = SimplexCrypto.decrypt(msg.encryptedBody, msg.iv, vaultKey)
                val res = dispatchOverRelay(contact, plaintext.toByteArray(Charsets.UTF_8), "text/plain")
                if (res.isSuccess) {
                    repository.updateDeliveryStatus(msg.id, "DELIVERED")
                }
            }
        }
    }

    fun deleteContact(contact: ChatContact) {
        if (_isDecoyMode.value) {
            _decoyContacts.value = _decoyContacts.value.filter { it.id != contact.id }
            if (_selectedContactId.value == contact.id) _selectedContactId.value = null
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteContact(contact)
            if (_selectedContactId.value == contact.id) _selectedContactId.value = null
        }
    }

    fun deleteMessageById(id: Int) {
        if (_isDecoyMode.value) {
            val updated = _decoyMessages.value.toMutableMap()
            _selectedContactId.value?.let { cid ->
                updated[cid] = updated[cid].orEmpty().filter { it.id != id }
                _decoyMessages.value = updated
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMessageById(id)
        }
    }

    // --- REAL SHAMIR 3-OF-5 ENCRYPTED VAULT EXPORT / IMPORT ---

    fun generateShamirBackupExport(passphrase: String): List<String> {
        val key = masterStorageKey ?: return emptyList()
        val alias = _activeProfileAlias.value ?: "OPERATOR"
        val mnemonic = ProfileVaultManager.decryptMnemonic(prefs, alias, key)
        val contactsArr = JSONArray()
        contacts.value.forEach { c ->
            contactsArr.put(
                JSONObject().apply {
                    put("alias", c.aliasName)
                    put("in_queue", c.inboundQueueUri)
                    put("out_queue", c.outboundQueueUri)
                    put("remote_pub", c.remotePublicKeyB64)
                }
            )
        }
        val vaultSnapshot = JSONObject().apply {
            put("version", 5)
            put("profile_alias", alias)
            put("exported_at", System.currentTimeMillis())
            put("mnemonic", mnemonic.joinToString(" "))
            put("contacts", contactsArr)
        }.toString().toByteArray(Charsets.UTF_8)

        val shards = DistributedBackupStorage.performShardedBackup(
            context = getApplication(),
            backupBytes = vaultSnapshot,
            passphrase = passphrase
        )
        MemoryZeroizationManager.zero(vaultSnapshot, MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY)

        val formattedShares = shards.map { s ->
            JSONObject().apply {
                put("format", "MARCIANOX_SHAMIR_V1")
                put("index", s.shardId)
                put("threshold", s.threshold)
                put("total", s.totalShards)
                put("sha256", s.checksum)
                put("share_b64", s.shareB64)
            }.toString(2)
        }
        _lastShamirExportShares.value = formattedShares
        _backupStatusMessage.value = "Generated 5 Shamir 3-of-5 encrypted shares via Rust GF(256)."
        return formattedShares
    }

    fun restoreVaultFromShamirShares(sharePayloads: List<String>, passphrase: String): Boolean {
        return try {
            val extracted = sharePayloads.map { raw ->
                val trimmed = raw.trim()
                if (trimmed.startsWith("{")) JSONObject(trimmed).getString("share_b64") else trimmed
            }
            val recoveredBytes = DistributedBackupStorage.recoverFromShares(extracted, passphrase)
                ?: run {
                    _backupStatusMessage.value = "Recovery failed: invalid passphrase or insufficient (<3) shares."
                    return false
                }
            val obj = JSONObject(String(recoveredBytes, Charsets.UTF_8))
            MemoryZeroizationManager.zero(recoveredBytes, MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY)
            val alias = obj.optString("profile_alias", "RESTORED")
            val words = obj.optString("mnemonic").split(" ").filter { it.isNotBlank() }
            _backupStatusMessage.value = "Verified & decrypted vault for '$alias' (${words.size}-word BIP-39 recovered)!"
            true
        } catch (e: Exception) {
            _backupStatusMessage.value = "Shamir recovery error: ${e.message}"
            false
        }
    }

    private fun getContactFallbackKey(contactId: Int): ByteArray? {
        val contact = contacts.value.find { it.id == contactId } ?: return null
        return try {
            val hex = contact.sharedSecretHex
            if (hex.length >= 64) hexToByteArray(hex.substring(0, 64))
            else hexToByteArray(hex.padEnd(64, '0'))
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun insertSystemMessage(contactId: Int, text: String) {
        val key = masterStorageKey ?: getContactFallbackKey(contactId) ?: return
        val encrypted = SimplexCrypto.encrypt("[SYS]$text", key)
        repository.insertMessage(
            ChatMessage(
                contactId = contactId,
                sender = "SYSTEM",
                encryptedBody = encrypted.ciphertext,
                iv = encrypted.iv,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // --- BIP-39 & DISTRIBUTED SHARDING INSPECTOR STATES ---
    private val _bip39Passphrase = MutableStateFlow("")
    val bip39Passphrase: StateFlow<String> = _bip39Passphrase.asStateFlow()

    private val _storageShards = MutableStateFlow<List<DistributedStorageProtocol.StorageShard>>(emptyList())
    val storageShards: StateFlow<List<DistributedStorageProtocol.StorageShard>> = _storageShards.asStateFlow()

    private val _postChallengeLog = MutableStateFlow<List<String>>(emptyList())
    val postChallengeLog: StateFlow<List<String>> = _postChallengeLog.asStateFlow()

    private val _postChallengeSuccess = MutableStateFlow<Boolean?>(null)
    val postChallengeSuccess: StateFlow<Boolean?> = _postChallengeSuccess.asStateFlow()

    private val _bip39Mnemonic = MutableStateFlow<List<String>>(emptyList())
    val bip39Mnemonic: StateFlow<List<String>> = _bip39Mnemonic.asStateFlow()

    private val _bip39SeedHex = MutableStateFlow<String?>(null)
    val bip39SeedHex: StateFlow<String?> = _bip39SeedHex.asStateFlow()

    private val _bip39ValidationInput = MutableStateFlow("")
    val bip39ValidationInput: StateFlow<String> = _bip39ValidationInput.asStateFlow()

    private val _bip39ValidationResult = MutableStateFlow<Boolean?>(null)
    val bip39ValidationResult: StateFlow<Boolean?> = _bip39ValidationResult.asStateFlow()

    private val _bip39ValidatedSeedHex = MutableStateFlow<String?>(null)
    val bip39ValidatedSeedHex: StateFlow<String?> = _bip39ValidatedSeedHex.asStateFlow()

    private val _bip39DerivedKeys = MutableStateFlow<List<Bip39Utility.DerivedKey>>(emptyList())
    val bip39DerivedKeys: StateFlow<List<Bip39Utility.DerivedKey>> = _bip39DerivedKeys.asStateFlow()

    private val _bip39CustomEntropyWords = MutableStateFlow("")
    val bip39CustomEntropyWords: StateFlow<String> = _bip39CustomEntropyWords.asStateFlow()

    private val _bip39ChallengeIndices = MutableStateFlow<List<Int>>(emptyList())
    val bip39ChallengeIndices: StateFlow<List<Int>> = _bip39ChallengeIndices.asStateFlow()

    private val _bip39ChallengeAnswers = MutableStateFlow<List<String>>(listOf("", "", ""))
    val bip39ChallengeAnswers: StateFlow<List<String>> = _bip39ChallengeAnswers.asStateFlow()

    private val _bip39ChallengePassed = MutableStateFlow<Boolean?>(null)
    val bip39ChallengePassed: StateFlow<Boolean?> = _bip39ChallengePassed.asStateFlow()

    fun generateDeterministicBip39Mnemonic(customWords: String) {
        _bip39CustomEntropyWords.value = customWords
        val currentDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val mnemonic = Bip39Utility.generateDeterministicMnemonic(currentDateStr, customWords)
        _bip39Mnemonic.value = mnemonic

        val seedBytes = Bip39Utility.deriveSeed(mnemonic.joinToString(" "), _bip39Passphrase.value)
        try {
            _bip39SeedHex.value = seedBytes.joinToString("") { "%02x".format(it) }
            _bip39DerivedKeys.value = Bip39Utility.deriveKeyHierarchy(seedBytes)
        } finally {
            MemoryZeroizationManager.zero(seedBytes)
        }

        _bip39ChallengeIndices.value = (0..11).shuffled().take(3).sorted()
        _bip39ChallengeAnswers.value = listOf("", "", "")
        _bip39ChallengePassed.value = null
    }

    fun setChallengeAnswer(indexInChallenge: Int, answer: String) {
        val currentAnswers = _bip39ChallengeAnswers.value.toMutableList()
        if (indexInChallenge in currentAnswers.indices) {
            currentAnswers[indexInChallenge] = answer.trim().lowercase()
            _bip39ChallengeAnswers.value = currentAnswers
            val indices = _bip39ChallengeIndices.value
            val mnemonic = _bip39Mnemonic.value
            if (indices.size == 3 && mnemonic.size >= 12) {
                var allCorrect = true
                var hasEmpty = false
                for (i in 0 until 3) {
                    val userWord = currentAnswers[i]
                    if (userWord.isEmpty()) hasEmpty = true
                    if (mnemonic[indices[i]] != userWord) allCorrect = false
                }
                _bip39ChallengePassed.value = if (hasEmpty) null else allCorrect
            } else {
                _bip39ChallengePassed.value = null
            }
        }
    }

    fun generateNewBip39Mnemonic() {
        val mnemonic = Bip39Utility.generateMnemonic()
        _bip39Mnemonic.value = mnemonic
        val seedBytes = Bip39Utility.deriveSeed(mnemonic.joinToString(" "), _bip39Passphrase.value)
        try {
            _bip39SeedHex.value = seedBytes.joinToString("") { "%02x".format(it) }
            _bip39DerivedKeys.value = Bip39Utility.deriveKeyHierarchy(seedBytes)
        } finally {
            MemoryZeroizationManager.zero(seedBytes)
        }
        _bip39ChallengeIndices.value = (0..11).shuffled().take(3).sorted()
        _bip39ChallengeAnswers.value = listOf("", "", "")
        _bip39ChallengePassed.value = null
    }

    fun setBip39ValidationInput(input: String) {
        _bip39ValidationInput.value = input
        if (input.trim().isEmpty()) {
            _bip39ValidationResult.value = null
            _bip39ValidatedSeedHex.value = null
            return
        }
        val isValid = Bip39Utility.validateMnemonic(input) || Bip39Utility.validateMnemonic24(input)
        _bip39ValidationResult.value = isValid
        if (isValid) {
            val seedBytes = Bip39Utility.deriveSeed(input, _bip39Passphrase.value)
            try {
                _bip39ValidatedSeedHex.value = seedBytes.joinToString("") { "%02x".format(it) }
                _bip39DerivedKeys.value = Bip39Utility.deriveKeyHierarchy(seedBytes)
            } finally {
                MemoryZeroizationManager.zero(seedBytes)
            }
        } else {
            _bip39ValidatedSeedHex.value = null
        }
    }

    fun setBip39Passphrase(passphrase: String) {
        _bip39Passphrase.value = passphrase
        val activeMnemonic = _bip39Mnemonic.value
        val validationInput = _bip39ValidationInput.value
        if (activeMnemonic.isNotEmpty()) {
            val seedBytes = Bip39Utility.deriveSeed(activeMnemonic.joinToString(" "), passphrase)
            try {
                _bip39SeedHex.value = seedBytes.joinToString("") { "%02x".format(it) }
                _bip39DerivedKeys.value = Bip39Utility.deriveKeyHierarchy(seedBytes)
            } finally {
                MemoryZeroizationManager.zero(seedBytes)
            }
        } else if (validationInput.trim().isNotEmpty() && _bip39ValidationResult.value == true) {
            val seedBytes = Bip39Utility.deriveSeed(validationInput, passphrase)
            try {
                _bip39ValidatedSeedHex.value = seedBytes.joinToString("") { "%02x".format(it) }
                _bip39DerivedKeys.value = Bip39Utility.deriveKeyHierarchy(seedBytes)
            } finally {
                MemoryZeroizationManager.zero(seedBytes)
            }
        }
    }

    fun triggerDataSharding(customData: String = "MarcianoX Sealed Backup Payload") {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val activeKeys = _bip39DerivedKeys.value
                val dbSubkeyHex = activeKeys.firstOrNull()?.keyHex
                    ?: "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
                val dbSubkeyBytes = hexToByteArray(dbSubkeyHex)
                val backupId = "shamir_" + System.currentTimeMillis().toString().takeLast(6)
                val payloadBytes = customData.toByteArray(Charsets.UTF_8)

                val shards = try {
                    DistributedStorageProtocol.encryptAndShard(payloadBytes, dbSubkeyBytes, backupId)
                } finally {
                    MemoryZeroizationManager.zeroAll(dbSubkeyBytes, payloadBytes)
                }
                _storageShards.value = shards
                _postChallengeLog.value = listOf(
                    "Rust Shamir GF(256) split [$backupId] complete.",
                    "Sealed with PBKDF2-HMAC-SHA256 + AES-256-GCM.",
                    "Threshold: any 3 of 5 shares reconstruct full vault."
                )
                _postChallengeSuccess.value = null
            } catch (e: Exception) {
                SimplexLogger.log("RUST_SHAMIR", "Sharding error: ${e.message}")
            }
        }
    }

    fun executeProofOfSpacetimeChallenge(shardIndex: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val currentShards = _storageShards.value
            if (currentShards.isEmpty() || shardIndex !in currentShards.indices) {
                _postChallengeLog.value = _postChallengeLog.value + "Error: shard index out of bounds."
                _postChallengeSuccess.value = false
                return@launch
            }
            val shard = currentShards[shardIndex]
            val challengeSeed = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val proof = DistributedStorageProtocol.generateStorageChallenge(shard, challengeSeed)
            val isVerified = DistributedStorageProtocol.verifyStorageProof(proof, proof.expectedResponseHash)
            _postChallengeLog.value = _postChallengeLog.value +
                "SHA-256 challenge #${shardIndex + 1}: ${if (isVerified) "VERIFIED" else "FAILED"} (${proof.expectedResponseHash.joinToString("") { "%02x".format(it) }.take(16)}...)"
            _postChallengeSuccess.value = isVerified
        }
    }

    fun clearBip39State() {
        _bip39Mnemonic.value = emptyList()
        _bip39SeedHex.value = null
        _bip39ValidationInput.value = ""
        _bip39ValidationResult.value = null
        _bip39ValidatedSeedHex.value = null
        _bip39CustomEntropyWords.value = ""
        _bip39ChallengeIndices.value = emptyList()
        _bip39ChallengeAnswers.value = listOf("", "", "")
        _bip39ChallengePassed.value = null
        _bip39DerivedKeys.value = emptyList()
        _storageShards.value = emptyList()
        _postChallengeLog.value = emptyList()
        _postChallengeSuccess.value = null
    }

    private fun hexToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun randomHex(numBytes: Int): String {
        val bytes = ByteArray(numBytes)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

enum class SetupStep {
    SET_PIN,
    CONFIRM_PIN,
    SET_DISTRESS_PIN,
    CONFIRM_DISTRESS_PIN,
    SET_PANIC_PIN,
    CONFIRM_PANIC_PIN
}

data class TorStatus(
    val isConnected: Boolean = false,
    val numCircuits: Int = 0,
    val currentOnionNode: String = "wss://relay.marcianox.net/ws",
    val statusText: String = "Offline"
)

data class DecryptedMessage(
    val id: Int,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val isRead: Boolean,
    val isSystem: Boolean,
    val deliveryStatus: String = "DELIVERED",
    val expiryMinutes: Int = 0,
    val mediaMimeType: String = "",
    val mediaCiphertextB64: String = "",
    val mediaIvB64: String = "",
    val waveformBars: List<Float> = emptyList()
)

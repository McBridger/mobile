package expo.modules.connector.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import expo.modules.connector.crypto.EncryptionService
import expo.modules.connector.interfaces.*
import expo.modules.connector.models.Message
import expo.modules.connector.models.BleDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

class Broker(
    private val context: Context,
    private val encryptionService: IEncryptionService,
    private val scanner: IBleScanner,
    private val history: History,
    private val bleFactory: () -> IBleTransport
) {
    private val TAG = "Broker"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _state = MutableStateFlow(State.IDLE)
    val state = _state.asStateFlow()

    private val _isForeground = MutableStateFlow(true)
    val isForeground = _isForeground.asStateFlow()

    private val _messages = MutableSharedFlow<Message>()
    val messages = _messages.asSharedFlow()

    private var bleTransport: IBleTransport? = null
    
    private var discoveryJob: Job? = null
    private var setupJob: Job? = null
    private var transportCollectionJob: Job? = null

    init {
        Log.i(TAG, "Initializing Broker instance.")
        if (encryptionService.load()) {
            Log.i(TAG, "Credentials loaded automatically.")
            setupTransport()
        } else {
            Log.w(TAG, "No credentials found during init.")
        }
        startDiscoveryLoop()
    }

    fun setForeground(isForeground: Boolean) {
        Log.i(TAG, "Lifecycle: App focus changed. isForeground=$isForeground")
        _isForeground.value = isForeground
    }

    private fun setupTransport() {
        Log.d(TAG, "setupTransport: Initializing transport component.")
        try {
            _state.value = State.TRANSPORT_INITIALIZING
            val transport = bleFactory()
            registerBle(transport)
            _state.value = State.READY
            Log.i(TAG, "setupTransport: Transport ready, state changed to READY.")
        } catch (e: Exception) {
            Log.e(TAG, "setupTransport: Failed to setup transport: ${e.message}", e)
            _state.value = State.ERROR
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun startDiscoveryLoop() {
        discoveryJob?.cancel()

        discoveryJob = scope.launch {
            combine(state, isForeground) { currentState, isFg ->
                val shouldScan = currentState == State.READY || 
                                currentState == State.DISCOVERING ||
                                currentState == State.DISCONNECTED
                
                if (!shouldScan) return@combine null
                if (isFg) ScanConfig.Aggressive else ScanConfig.Passive
            }
            .debounce(500L)
            .distinctUntilChanged()
            .flatMapLatest { config ->
                if (config == null) {
                    Log.i(TAG, "Discovery: Stopped (App state changed)")
                    return@flatMapLatest flowOf()
                }

                val advertiseUuid = encryptionService.deriveUuid("McBridge_Advertise_UUID") 
                if (advertiseUuid == null) {
                    Log.e(TAG, "Discovery: Error deriving UUID")
                    return@flatMapLatest flowOf()
                }
                
                val modeLabel = if (config is ScanConfig.Aggressive) "Aggressive" else "Passive"
                Log.i(TAG, "Discovery: Starting $modeLabel session for $advertiseUuid")
                
                flow {
                    emitAll(scanWithWatchdog(advertiseUuid, config))
                }.flowOn(Dispatchers.Main)
            }
            .collect { device ->
                Log.i(TAG, "Discovery: Hit! ${device.address} (RSSI: ${device.rssi})")
                connect(device.address)
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun scanWithWatchdog(uuid: UUID, config: ScanConfig): Flow<BleDevice> {
        return scanner.scan(uuid, config)
            .onStart { 
                Log.v(TAG, "Watchdog: Internal session started (${config.javaClass.simpleName})")
                _state.value = State.DISCOVERING 
            }
            .filter { it.rssi > -85 }
            .let { flow ->
                if (config.timeoutMs < Long.MAX_VALUE) {
                    flow.timeout(config.timeoutMs.milliseconds)
                        .retry { e ->
                            if (e is TimeoutCancellationException) {
                                Log.v(TAG, "Watchdog: Refreshing scan session...")
                                true
                            } else {
                                Log.e(TAG, "Watchdog: Fatal error: ${e.message}")
                                _state.value = State.ERROR
                                false
                            }
                        }
                } else flow
            }
            .catch { Log.e(TAG, "Watchdog: Flow error: ${it.message}") }
    }

    fun setup(mnemonic: String, salt: String) {
        Log.i(TAG, "setup: Initiating Magic Sync setup.")
        setupJob?.cancel()
        setupJob = scope.launch {
            try {
                Log.d(TAG, "setup: Phase 1 - State set to ENCRYPTING")
                _state.value = State.ENCRYPTING
                
                Log.d(TAG, "setup: Phase 2 - Deriving and persisting keys.")
                encryptionService.setup(mnemonic, salt)
                
                Log.d(TAG, "setup: Phase 3 - Setting up transport.")
                setupTransport()
                Log.i(TAG, "setup: Magic Sync is now fully READY.")
            } catch (e: Exception) {
                Log.e(TAG, "setup: ERROR during setup: ${e.message}", e)
                _state.value = State.ERROR
            }
        }
    }

    fun reset() {
        Log.i(TAG, "reset: Full Broker reset initiated.")
        setupJob?.cancel()
        transportCollectionJob?.cancel()
        
        scope.launch {
            try {
                bleTransport?.let {
                    Log.d(TAG, "reset: Disconnecting and stopping active transport.")
                    it.disconnect()
                    it.stop()
                }
                Log.d(TAG, "reset: Clearing encryption keys.")
                encryptionService.clear()
                history.clear()
                _state.value = State.IDLE
                Log.i(TAG, "reset: Broker is now IDLE.")
            } catch (e: Exception) {
                Log.e(TAG, "reset: Error during reset: ${e.message}")
            }
        }
    }

    fun registerBle(bleTransport: IBleTransport) {
        Log.d(TAG, "registerBle: Registering new BLE transport.")
        transportCollectionJob?.cancel()
        
        // Kill the old transport's scope before replacing it
        this.bleTransport?.stop()
        
        this.bleTransport = bleTransport
        transportCollectionJob = scope.launch {
            launch { 
                Log.d(TAG, "registerBle: Starting message collection.")
                bleTransport.incomingMessages.collect { onIncomingMessage(it) } 
            }
            launch { 
                Log.d(TAG, "registerBle: Starting state collection.")
                bleTransport.connectionState.collect { onStateChange(it) } 
            }
        }
    }

    suspend fun connect(address: String) {
        if (state.value == State.CONNECTING || state.value == State.CONNECTED) {
            Log.d(TAG, "connect: Already connected or connecting to $address, ignoring.")
            return
        }
        Log.i(TAG, "connect: Requesting connection to $address")
        _state.value = State.CONNECTING
        bleTransport?.connect(address)
    }

    suspend fun disconnect() {
        Log.d(TAG, "disconnect: Manual disconnect requested.")
        bleTransport?.disconnect()
    }

    fun getHistory(): History = history
    fun getMnemonic(): String? = encryptionService.getMnemonic()

    fun clipboardUpdate(content: String) {
        Log.d(TAG, "clipboardUpdate: Sending clipboard update (${content.length} chars)")
        val message = Message(type = Message.Type.CLIPBOARD, value = content)
        history.add(message)
        scope.launch {
            bleTransport?.send(message)
            _messages.emit(message)
        }
    } 

    fun onIncomingMessage(message: Message) {
        Log.i(TAG, "onIncomingMessage: Received message Type: ${message.getType()}")
        updateSystemClipboard(message.value)
        history.add(message)
        scope.launch { _messages.emit(message) }
    }

    private fun updateSystemClipboard(text: String) {
        scope.launch(Dispatchers.Main) {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Bridger Data", text))
                Log.d(TAG, "updateSystemClipboard: Clipboard updated on Main thread.")
            } catch (e: Exception) {
                Log.e(TAG, "updateSystemClipboard: Failed: ${e.message}")
            }
        }
    }

    private fun onStateChange(state: IBleTransport.ConnectionState) {
        Log.d(TAG, "onStateChange: Transport state changed to $state")
        when (state) {
            IBleTransport.ConnectionState.READY -> {
                Log.i(TAG, "onStateChange: Connection is READY. Sending device info.")
                _state.value = State.CONNECTED
                scope.launch { 
                    bleTransport?.send(Message(type = Message.Type.DEVICE_NAME, value = Build.MODEL)) 
                }
            }
            IBleTransport.ConnectionState.DISCONNECTED -> {
                Log.i(TAG, "onStateChange: State changed to DISCONNECTED.")
                _state.value = State.DISCONNECTED
            }
            IBleTransport.ConnectionState.CONNECTING -> _state.value = State.CONNECTING
            IBleTransport.ConnectionState.POWERED_OFF -> {
                Log.e(TAG, "onStateChange: Bluetooth is OFF.")
                _state.value = State.ERROR
            }
            else -> {}
        }
    }

    enum class State { IDLE, ENCRYPTING, KEYS_READY, TRANSPORT_INITIALIZING, READY, DISCOVERING, CONNECTING, CONNECTED, DISCONNECTED, ERROR }
}

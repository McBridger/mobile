package expo.modules.connector.core     
    
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import expo.modules.connector.crypto.EncryptionService
import expo.modules.connector.interfaces.IBleTransport
import expo.modules.connector.interfaces.IBleScanner
import expo.modules.connector.models.Message
import expo.modules.connector.models.BleDevice
import expo.modules.connector.transports.ble.BleScanner
import expo.modules.connector.transports.ble.BleTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

object Broker {
    private const val TAG = "Broker"
    private val scope = CoroutineScope(Dispatchers.Default)
    private val isInitialized = AtomicBoolean(false)

    private val _state = MutableStateFlow(State.IDLE)
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<Message>()
    val messages = _messages.asSharedFlow()

    private lateinit var bleTransport: IBleTransport
    private lateinit var history: History
    private lateinit var appContext: Context
    
    private var scanner: IBleScanner = BleScanner()
    private var discoveryJob: Job? = null
    private var setupJob: Job? = null
    private var transportCollectionJob: Job? = null

    fun setScanner(scanner: IBleScanner) {
        Log.i(TAG, "setScanner: Injecting new scanner implementation: ${scanner.javaClass.simpleName}")
        this.scanner = scanner
    }

    fun init(context: Context): Broker {
        if (isInitialized.getAndSet(true)) {
            Log.d(TAG, "init: Broker already initialized, skipping.")
            return this
        }

        this.appContext = context.applicationContext
        this.history = History.getInstance(this.appContext)
        
        // Start the reactive discovery loop once
        startDiscoveryLoop()
        
        Log.i(TAG, "init: Broker initialized successfully.")
        return this
    }

    /**
     * The heart of Magic Sync: A reactive loop that monitors the state
     * and automatically manages the scanning lifecycle.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startDiscoveryLoop() {
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            state
                .map({ currentState ->
                    // Scan in any of these states
                    currentState == State.READY || 
                    currentState == State.DISCOVERING ||
                    currentState == State.DISCONNECTED || 
                    currentState == State.ERROR
                })
                .distinctUntilChanged()
                .flatMapLatest({ shouldScan ->
                    if (!shouldScan) {
                        Log.i(TAG, "Discovery: DEACTIVATED (State: ${state.value})")
                        return@flatMapLatest flowOf()
                    }

                    val advertiseUuid = EncryptionService.deriveUuid("McBridge_Advertise_UUID")
                    if (advertiseUuid == null) {
                        Log.e(TAG, "Discovery: UUID null, cannot scan")
                        return@flatMapLatest flowOf()
                    }

                    Log.i(TAG, "Discovery: STARTING reactive loop for $advertiseUuid")
                    
                    // Use the watchdog to keep the scanner fresh
                    scanWithWatchdog(advertiseUuid)
                })
                .collect({ device ->
                    Log.i(TAG, "Discovery: Bingo! Device found: ${device.address}")
                    // connect() will set state to CONNECTING, which triggers flatMapLatest 
                    // to cancel this scan flow automatically.
                    connect(device.address)
                })
        }
    }

    /**
     * Creates a self-healing scan flow.
     */
    @OptIn(FlowPreview::class)
    private fun scanWithWatchdog(uuid: UUID): Flow<BleDevice> {
        return scanner.scan(uuid)
            .onStart { 
                Log.d(TAG, "Watchdog: Scan started/restarted")
                _state.value = State.DISCOVERING 
            }
            .filter { it.rssi > -85 }
            .timeout(15000.milliseconds) // Throw TimeoutCancellationException if quiet for 15s
            .retry { e ->
                if (e is TimeoutCancellationException) {
                    Log.d(TAG, "Watchdog: No devices found in 15s. Restarting to refresh BT stack...")
                    true // true = restart the flow
                } else {
                    Log.e(TAG, "Watchdog: Fatal scan error", e)
                    _state.value = State.ERROR
                    false // false = stop and propagate error
                }
            }
            .catch { e ->
                Log.e(TAG, "Watchdog: Flow caught exception: ${e.message}")
            }
    }

    /**
     * Orchestrates the entire setup process:
     * IDLE -> ENCRYPTING -> KEYS_READY -> TRANSPORT_INITIALIZING -> READY
     */
    fun setup(mnemonic: String, salt: String) {
        Log.i(TAG, "setup: Initiating Magic Sync setup.")
        setupJob?.cancel()
        setupJob = scope.launch {
            try {
                _state.value = State.ENCRYPTING
                Log.d(TAG, "setup: Phase 1 - Computing master keys (PBKDF2).")
                
                // This is a heavy blocking call, run on Default dispatcher
                EncryptionService.setup(appContext, mnemonic, salt)
                
                _state.value = State.KEYS_READY
                Log.d(TAG, "setup: Phase 2 - Keys ready, initializing transport.")
                
                _state.value = State.TRANSPORT_INITIALIZING
                
                val transport = BleTransport(appContext)
                registerBle(transport)
                
                _state.value = State.READY
                Log.i(TAG, "setup: Magic Sync is READY. The discovery loop will pick this up.")
            } catch (e: Exception) {
                Log.e(TAG, "setup: Failed during initialization", e)
                _state.value = State.ERROR
            }
        }
    }

    /**
     * Resets the broker to its initial state.
     * Clears keys, disconnects transport, and stops discovery.
     */
    fun reset() {
        Log.i(TAG, "reset: Full Broker reset initiated.")
        setupJob?.cancel()
        transportCollectionJob?.cancel()
        
        // 1. Disconnect BLE if active
        scope.launch {
            try {
                if (::bleTransport.isInitialized) {
                    bleTransport.disconnect()
                    Log.d(TAG, "reset: BLE disconnected.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "reset: Error during disconnect", e)
            }
        }

        // 2. Clear secure storage
        try {
            EncryptionService.clear(appContext)
            Log.d(TAG, "reset: Encryption keys cleared.")
        } catch (e: Exception) {
            Log.e(TAG, "reset: Error clearing keys", e)
        }

        // 3. Go to IDLE state (discovery loop will stop automatically)
        _state.value = State.IDLE
    }

    fun registerBle(bleTransport: IBleTransport): Broker {
        Log.d(TAG, "registerBle: Registering BLE transport. Transport hash: ${bleTransport.hashCode()}")
        
        // Cancel previous collection to avoid leaks and multiple state updates
        transportCollectionJob?.cancel()
        
        this.bleTransport = bleTransport

        transportCollectionJob = scope.launch {
            launch {
                Log.d(TAG, "registerBle: Collecting incomingMessages from bleTransport")
                bleTransport.incomingMessages.collect { msg -> onIncomingMessage(msg) }
            }
            launch {
                Log.d(TAG, "registerBle: Collecting connectionState from bleTransport")
                bleTransport.connectionState.collect { state -> onStateChange(state) }
            }
        }

        Log.i(TAG, "registerBle: BLE Transport registered. Discovery loop will handle connection.")
        return this
    }

    suspend fun connect(address: String) {
        val currentState = state.value
        if (currentState == State.CONNECTING || currentState == State.CONNECTED) {
            Log.d(TAG, "connect: Already $currentState, ignoring connection request to $address")
            return
        }

        Log.d(TAG, "connect: Attempting to connect to $address")
        // Trigger state change to stop the scanner via flatMapLatest
        _state.value = State.CONNECTING
        bleTransport.connect(address)
        Log.d(TAG, "connect: bleTransport.connect called for $address")
    }

    suspend fun disconnect() {
        Log.d(TAG, "disconnect: Attempting to disconnect")
        bleTransport.disconnect()
        Log.d(TAG, "disconnect: bleTransport.disconnect called")
    }

    // Accessor for History to expose it to ConnectorModule
    fun getHistory(): History {
        if (!::history.isInitialized) {
            Log.e(TAG, "getHistory: Broker not initialized, history is null.")
            throw IllegalStateException("Broker not initialized. Call init() first.")
        }
        Log.d(TAG, "getHistory: Returning history instance.")
        return history
    } 
    
    fun clipboardUpdate(content: String) {
        Log.d(TAG, "clipboardUpdate: Creating clipboard message with content length: ${content.length}")
        val message = Message(type = Message.Type.CLIPBOARD, value = content)
        history.add(message)
        Log.d(TAG, "clipboardUpdate: Message added to history: ${message.id}")

        scope.launch {
            Log.d(TAG, "clipboardUpdate: Sending message via bleTransport: ${message.id}")
            bleTransport.send(message)
            Log.d(TAG, "clipboardUpdate: Emitting message to _messages flow: ${message.id}")
            _messages.emit(message)
        }
    } 

    fun onIncomingMessage(message: Message) {
        Log.d(TAG, "onIncomingMessage: Received message Type: ${message.getType()}, Value: ${message.value}, ID: ${message.id}")
        updateSystemClipboard(message.value)
        history.add(message)
        Log.d(TAG, "onIncomingMessage: Message added to history: ${message.id}")
        scope.launch { 
            _messages.emit(message)
            Log.d(TAG, "onIncomingMessage: Emitted message to _messages flow: ${message.id}")
        }
    }

    private fun updateSystemClipboard(text: String) {
        Log.d(TAG, "updateSystemClipboard: Attempting to update system clipboard with text length: ${text.length}")
        try {
            val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Bridger Data", text)
            clipboard.setPrimaryClip(clip)
            Log.d(TAG, "updateSystemClipboard: System clipboard updated via Broker.")
        } catch (e: Exception) {
            Log.e(TAG, "updateSystemClipboard: Failed to update system clipboard: ${e.message}")
        }
    }

    private fun onStateChange(state: IBleTransport.ConnectionState) {
        Log.d(TAG, "onStateChange: Received new BLE connection state: $state")

        when (state) {
            IBleTransport.ConnectionState.CONNECTED -> {
                Log.i(TAG, "onStateChange: Broker state changed to CONNECTED (physical)")
            }
            IBleTransport.ConnectionState.READY -> {
                Log.i(TAG, "onStateChange: Broker state changed to READY_CONNECTED (internal)")
                _state.value = State.CONNECTED

                val message = Message(type = Message.Type.DEVICE_NAME, value = Build.MODEL)
                scope.launch { bleTransport.send(message) }
                Log.i(TAG, "Welcome message sent")
            }
            IBleTransport.ConnectionState.DISCONNECTED -> {
                Log.i(TAG, "onStateChange: Broker state changed to DISCONNECTED.")
                _state.value = State.DISCONNECTED
            }
            IBleTransport.ConnectionState.CONNECTING -> {
                Log.i(TAG, "onStateChange: Broker state changed to CONNECTING")
                _state.value = State.CONNECTING
            }
            IBleTransport.ConnectionState.POWERED_OFF -> {
                Log.e(TAG, "onStateChange: Broker state changed to ERROR (Bluetooth POWERED_OFF)")
                _state.value = State.ERROR
            }
            else -> {
                Log.w(TAG, "onStateChange: Unhandled BLE connection state: $state")
            }
        }
    }

    enum class State {
        IDLE,
        ENCRYPTING,
        KEYS_READY,
        TRANSPORT_INITIALIZING,
        READY,
        DISCOVERING,
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        ERROR
    }
}

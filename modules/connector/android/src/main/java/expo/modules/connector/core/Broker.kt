package expo.modules.connector.core     
    
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import expo.modules.connector.crypto.EncryptionService
import expo.modules.connector.interfaces.IBleTransport
import expo.modules.connector.models.Message
import expo.modules.connector.transports.ble.BleScanner
import expo.modules.connector.transports.ble.BleTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.nio.ByteBuffer
import java.util.UUID

object Broker {
    private const val TAG = "Broker"
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _state = MutableStateFlow(State.IDLE)
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<Message>()
    val messages = _messages.asSharedFlow()

    private lateinit var bleTransport: IBleTransport
    private lateinit var history: History
    private lateinit var appContext: Context
    
    private val scanner = BleScanner()
    private var discoveryJob: Job? = null
    private var setupJob: Job? = null
    private var transportCollectionJob: Job? = null
    private var isAutoDiscoveryEnabled = true

    fun init(context: Context): Broker {
        val applicationContext = context.applicationContext
        Log.d(TAG, "init: Called with context hash: ${context.hashCode()}, applicationContext hash: ${applicationContext.hashCode()}")

        if (!::appContext.isInitialized) {
            this.appContext = applicationContext
            this.history = History.getInstance(this.appContext)
            this.isAutoDiscoveryEnabled = true
            Log.i(TAG, "init: Broker initialized successfully for the first time.")
            return this
        }

        if (this.appContext != applicationContext) {
            Log.e(TAG, "init: Attempt to re-initialize Broker with a different ApplicationContext! Existing hash: ${this.appContext.hashCode()}, New hash: ${applicationContext.hashCode()}")
            throw IllegalStateException("Broker has already been initialized with a different ApplicationContext. Only one ApplicationContext is supported per process.")
        }

        Log.d(TAG, "init: Broker already initialized with the same ApplicationContext. Doing nothing.")
        return this
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
                
                // Ensure auto-discovery is enabled for the new setup
                isAutoDiscoveryEnabled = true
                
                val transport = BleTransport(appContext)
                registerBle(transport)
                
                _state.value = State.READY
                Log.i(TAG, "setup: Magic Sync is READY. Starting discovery.")
                tryDiscovery()
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
        discoveryJob?.cancel()
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

        // 3. Go to IDLE state
        _state.value = State.IDLE
    }

    /**
     * Internal logic to trigger discovery if conditions are met.
     */
    private fun tryDiscovery() {
        if (!isAutoDiscoveryEnabled) return
        
        // Allow scanning if we are READY, IDLE, or DISCONNECTED
        val currentState = state.value
        if (currentState != State.IDLE && currentState != State.READY && currentState != State.DISCONNECTED) {
            Log.d(TAG, "tryDiscovery: Skip scanning, state is $currentState")
            return
        }

        if (discoveryJob?.isActive == true) return

        val advertiseUuid = EncryptionService.deriveUuid("McBridge_Advertise_UUID") ?: run {
            Log.w(TAG, "tryDiscovery: Encryption not ready, cannot derive UUID.")
            return
        }

        Log.i(TAG, "tryDiscovery: Starting Magic Sync discovery for $advertiseUuid")
        _state.value = State.DISCOVERING
        discoveryJob = scope.launch {
            try {
                // Use first() to take only ONE device and immediately stop scanning
                val device = scanner.scan(advertiseUuid).first()
                Log.i(TAG, "Magic Sync: Found bridge at ${device.address}. Attempting connection.")
                connect(device.address)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "discoveryJob: Error during scan", e)
                }
            }
        }
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

        Log.i(TAG, "registerBle: BLE Transport registered. Trying initial discovery.")
        tryDiscovery()
        return this
    }

    suspend fun connect(address: String) {
        val currentState = state.value
        if (currentState == State.CONNECTING || currentState == State.CONNECTED) {
            Log.d(TAG, "connect: Already $currentState, ignoring connection request to $address")
            return
        }

        Log.d(TAG, "connect: Attempting to connect to $address")
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
                discoveryJob?.cancel() // Connection established, stop any active discovery
            }
            IBleTransport.ConnectionState.READY -> {
                Log.i(TAG, "onStateChange: Broker state changed to READY_CONNECTED (internal)")
                _state.value = State.CONNECTED

                val message = Message(type = Message.Type.DEVICE_NAME, value = Build.MODEL)
                scope.launch { bleTransport.send(message) }
                Log.i(TAG, "Welcome message sent")
            }
            IBleTransport.ConnectionState.DISCONNECTED -> {
                _state.value = State.DISCONNECTED
                Log.i(TAG, "onStateChange: Broker state changed to DISCONNECTED. Checking rescan...")
                if (isAutoDiscoveryEnabled) tryDiscovery()
            }
            IBleTransport.ConnectionState.CONNECTING -> {
                _state.value = State.CONNECTING
                Log.i(TAG, "onStateChange: Broker state changed to CONNECTING")
                discoveryJob?.cancel()
            }
            IBleTransport.ConnectionState.POWERED_OFF -> {
                _state.value = State.ERROR
                Log.e(TAG, "onStateChange: Broker state changed to ERROR (Bluetooth POWERED_OFF)")
                discoveryJob?.cancel()
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
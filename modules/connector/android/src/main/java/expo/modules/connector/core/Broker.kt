package expo.modules.connector.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import expo.modules.connector.core.BlobStorageManager
import expo.modules.connector.interfaces.*
import expo.modules.connector.models.*
import expo.modules.connector.services.NotificationService
import expo.modules.connector.utils.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

class Broker(
    context: Context,
    private val encryptionService: IEncryptionService,
    private val scanner: IBleScanner,
    private val history: History,
    private val wakeManager: IWakeManager,
    private val notificationService: NotificationService,
    private val blobStorageManager: BlobStorageManager,
    private val fileStreamProvider: IFileStreamProvider,
    private val tcpFactory: () -> ITcpTransport,
    private val bleFactory: () -> IBleTransport
) {
    private val TAG = "Broker"
    private val context = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _state = MutableStateFlow(State.IDLE)
    val state = _state.asStateFlow()

    private val _isForeground = MutableStateFlow(true)
    val isForeground = _isForeground.asStateFlow()

    private val _messages = MutableSharedFlow<Message>()
    val messages = _messages.asSharedFlow()

    private var bleTransport: IBleTransport? = null
    private var tcpTransport: ITcpTransport? = null
    private var partnerTcpTarget: Pair<String, Int>? = null

    private var discoveryJob: Job? = null
    private var setupJob: Job? = null

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
            
            val ble = bleFactory()
            registerBle(ble)
            
            tcpTransport?.stop()
            val tcp = tcpFactory()
            registerTcp(tcp)

            _state.value = State.READY
            Log.i(TAG, "setupTransport: Transports ready, state changed to READY.")
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
                
                scanWithWatchdog(advertiseUuid, config)
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
                if (config.timeoutMs == Long.MAX_VALUE) return@let flow

                flow.timeout(config.timeoutMs.milliseconds)
                    .retry { e ->
                        if (e is TimeoutCancellationException) return@retry true
                        Log.e(TAG, "Watchdog: Fatal error: ${e.message}")
                        _state.value = State.ERROR
                        false
                    }
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
        scope.launch {
            try {
                bleTransport?.let {
                    Log.d(TAG, "reset: Disconnecting and stopping active BLE transport.")
                    it.disconnect()
                    it.stop()
                }
                tcpTransport?.let {
                    Log.d(TAG, "reset: Stopping active TCP transport.")
                    it.stop()
                }
                Log.d(TAG, "reset: Clearing encryption keys.")
                encryptionService.clear()
                history.clear()
                blobStorageManager.cleanup()
                _state.value = State.IDLE
                Log.i(TAG, "reset: Broker is now IDLE.")
            } catch (e: Exception) {
                Log.e(TAG, "reset: Error during reset: ${e.message}")
            }
        }
    }

    fun registerBle(transport: IBleTransport) {
        Log.d(TAG, "registerBle: Registering BLE transport.")
        this.bleTransport?.stop()
        this.bleTransport = transport

        scope.launch {
            launch { transport.incomingMessages.collect { onIncomingMessage(it) } }
            launch { transport.connectionState.collect { onBleStateChange(it) } }
        }
    }

    fun registerTcp(transport: ITcpTransport) {
        Log.d(TAG, "registerTcp: Registering TCP transport.")
        this.tcpTransport?.stop()
        this.tcpTransport = transport

        scope.launch {
            launch { transport.incomingMessages.collect { onIncomingMessage(it) } }
        }
    }

    fun connect(address: String) {
        if (state.value == State.CONNECTING || state.value == State.CONNECTED) {
            Log.d(TAG, "connect: Already connected or connecting to $address, ignoring.")
            return
        }
        val ble = bleTransport ?: run {
            Log.e(TAG, "connect: No transport available.")
            return
        }
        
        Log.i(TAG, "connect: Requesting connection to $address")
        wakeManager.acquire(15000L)
        _state.value = State.CONNECTING
        ble.connect(address)
    }

    fun disconnect() {
        Log.d(TAG, "disconnect: Manual disconnect requested.")
        bleTransport?.disconnect()
    }

    fun getHistory(): History = history
    fun getMnemonic(): String? = encryptionService.getMnemonic()

    fun tinyUpdate(content: String) {
        Log.d(TAG, "tinyUpdate: Sending tiny update (${content.length} chars)")
        val message = TinyMessage(value = content)
        history.add(message)
        scope.launch {
            bleTransport?.send(message)
            _messages.emit(message)
        }
    }

    fun blobUpdate(metadata: FileMetadata, type: BlobType = BlobType.FILE) {
        Log.d(TAG, "blobUpdate: Preparing to share blob ${metadata.name} (${metadata.size})")
        
        val blobId = UUID.randomUUID().toString()
        val totalSize = metadata.size.toLongOrNull() ?: 0L

        scope.launch {
            val announcement = BlobMessage(
                id = blobId,
                name = metadata.name,
                size = totalSize,
                blobType = type
            )
            
            history.add(announcement)
            
            // Primary announcement via BLE (signal channel)
            Log.d(TAG, "blobUpdate: Sending announcement via BLE")
            val bleSuccess = bleTransport?.send(announcement) ?: false
            if (!bleSuccess) {
                Log.w(TAG, "blobUpdate: BLE announcement might have failed")
            }

            // Also send via TCP if it happens to be alive
            tcpTransport?.send(announcement)
            
            _messages.emit(announcement)

            // Streaming requires TCP
            withContext(Dispatchers.IO) {
                val tcp = tcpTransport ?: run {
                    Log.e(TAG, "blobUpdate: No TCP transport available for streaming")
                    return@withContext
                }

                val (host, port) = partnerTcpTarget ?: run {
                    Log.e(TAG, "blobUpdate: No partner TCP target known")
                    return@withContext
                }

                try {
                    fileStreamProvider.openStream(metadata.uri.toString())?.use { input ->
                        Log.d(TAG, "blobUpdate: Starting TCP stream for $blobId to $host:$port")
                        val success = tcp.sendBlob(announcement, input, host, port)
                        Log.i(TAG, "blobUpdate: TCP stream finished. Success: $success")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "blobUpdate: Stream error: ${e.message}")
                }
            }
        }
    } 

    private suspend fun onIncomingMessage(message: Message) {
        Log.i(TAG, "onIncomingMessage: Processing message Type: ${message.getType()}")

        history.add(message)
        when (message) {
            is TinyMessage -> {
                Log.d(TAG, "Incoming tiny message: ${message.value.length} chars")
                updateSystemClipboard(message.value)
            }
            is BlobMessage -> {
                Log.d(TAG, "Incoming blob announcement: ${message.name} (${message.size} bytes)")
                blobStorageManager.prepare(message)
            }
            is ChunkMessage -> {
                blobStorageManager.writeChunk(message)
            }
            is IntroMessage -> {
                Log.i(TAG, "Partner Intro: ${message.name} at ${message.ip}:${message.port}")
                partnerTcpTarget = message.ip to message.port
            }
        }

        scope.launch { _messages.emit(message) }
    }

    private suspend fun updateSystemClipboard(text: String) {
        withContext(Dispatchers.Main) {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("McBridger Data", text))
                Log.d(TAG, "updateSystemClipboard: Clipboard updated on Main thread.")
            } catch (e: Exception) {
                Log.e(TAG, "updateSystemClipboard: Failed: ${e.message}")
            }
        }
    }

    private fun onBleStateChange(state: IBleTransport.ConnectionState) {
        Log.d(TAG, "onBleStateChange: BLE state changed to $state")
        when (state) {
            IBleTransport.ConnectionState.READY -> {
                Log.i(TAG, "onBleStateChange: BLE is READY. Sending business card.")
                _state.value = State.CONNECTED
                wakeManager.release()
                
                val localIp = NetworkUtils.getLocalIpAddress() ?: "0.0.0.0"
                Log.d(TAG, "My IP: $localIp, Port: 49152")
                
                // Send our "Business Card" via BLE
                scope.launch { 
                    bleTransport?.send(IntroMessage(
                        name = Build.MODEL,
                        ip = localIp,
                        port = 49152
                    ))
                }
            }
            IBleTransport.ConnectionState.DISCONNECTED -> {
                _state.value = State.DISCONNECTED
                wakeManager.release()
            }
            IBleTransport.ConnectionState.POWERED_OFF -> {
                _state.value = State.ERROR
                wakeManager.release()
            }
            else -> {}
        }
    }

    enum class State { IDLE, ENCRYPTING, KEYS_READY, TRANSPORT_INITIALIZING, READY, DISCOVERING, CONNECTING, CONNECTED, DISCONNECTED, ERROR }
}

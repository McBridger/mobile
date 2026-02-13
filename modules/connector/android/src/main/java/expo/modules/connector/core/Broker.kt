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
import kotlinx.coroutines.channels.BufferOverflow
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

    private val _state = MutableStateFlow(BrokerState())
    val state = _state.asStateFlow()

    private val _isForeground = MutableStateFlow(true)
    val isForeground = _isForeground.asStateFlow()

    private val _messages = MutableSharedFlow<Message>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages = _messages.asSharedFlow()

    private var bleTransport: IBleTransport? = null
    private var tcpTransport: ITcpTransport? = null
    private var partnerTcpTarget: Pair<String, Int>? = null

    private var discoveryJob: Job? = null
    private var setupJob: Job? = null

    init {
        Log.i(TAG, "Initializing Broker instance.")
        observeBlobManager()
        if (encryptionService.load()) {
            Log.i(TAG, "Credentials loaded automatically.")
            setState(EncryptionState.KEYS_READY)
            setupTransport()
        } else {
            Log.w(TAG, "No credentials found during init.")
            setState(EncryptionState.IDLE)
        }
        startDiscoveryLoop()
    }

    private fun observeBlobManager() {
        scope.launch {
            blobStorageManager.events.collect { event ->
                when (event) {
                    is BlobStorageManager.TransferEvent.Progress -> {
                        // TODO: Update Porter when implemented
                        notificationService.showProgress(
                            blobId = event.blobId,
                            name = event.name,
                            progress = event.progress,
                            currentSize = event.currentSize,
                            totalSize = event.totalSize,
                            speedBps = event.speedBps
                        )
                    }
                    is BlobStorageManager.TransferEvent.Finished -> {
                        // TODO: Finalize Porter when implemented
                        notificationService.showFinished(event.blobId, event.name, event.success)
                    }
                }
            }
        }
    }

    fun setForeground(isForeground: Boolean) {
        Log.i(TAG, "Lifecycle: App focus changed. isForeground=$isForeground")
        _isForeground.value = isForeground
    }

    private fun setupTransport() {
        Log.d(TAG, "setupTransport: Initializing transport component.")
        try {
            val ble = bleFactory()
            registerBle(ble)
            
            tcpTransport?.stop()
            val tcp = tcpFactory()
            registerTcp(tcp)

            Log.i(TAG, "setupTransport: Transports registered.")
        } catch (e: Exception) {
            Log.e(TAG, "setupTransport: Failed to setup transport: ${e.message}", e)
            setState(BleState.ERROR, e.message)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun startDiscoveryLoop() {
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            combine(state, isForeground) { currentState, isFg ->
                val shouldScan = currentState.encryption.current == EncryptionState.KEYS_READY && 
                                currentState.ble.current != BleState.CONNECTED &&
                                currentState.ble.current != BleState.CONNECTING
                
                if (!shouldScan) {
                    if (currentState.ble.current == BleState.SCANNING) {
                        Log.i(TAG, "Discovery: Stopped (Scan condition no longer met)")
                        setState(BleState.IDLE)
                    }
                    return@combine null
                }
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
                    setState(EncryptionState.ERROR, "Failed to derive UUID")
                    return@flatMapLatest flowOf()
                }
                
                val modeLabel = if (config is ScanConfig.Aggressive) "Aggressive" else "Passive"
                Log.i(TAG, "Discovery: Starting $modeLabel session for $advertiseUuid")
                setState(BleState.SCANNING)
                scanWithWatchdog(advertiseUuid, config)
            }
            .collect { device ->
                Log.i(TAG, "Discovery: Hit! ${device.address}")
                connect(device.address)
            }
        }
    }
    
    @OptIn(FlowPreview::class)
    private fun scanWithWatchdog(uuid: UUID, config: ScanConfig): Flow<BleDevice> {
        return scanner.scan(uuid, config)
            .onStart { 
                Log.v(TAG, "Watchdog: Internal session started (${config.javaClass.simpleName})")
                setState(BleState.SCANNING)
            }
            .filter { it.rssi > -85 }
            .let { flow ->
                if (config.timeoutMs == Long.MAX_VALUE) return@let flow
                flow.timeout(config.timeoutMs.milliseconds)
                    .retry { e ->
                        if (e is TimeoutCancellationException) return@retry true
                        Log.e(TAG, "Watchdog: Fatal error: ${e.message}")
                        setState(BleState.ERROR, e.message)
                        false
                    }
            }
            .catch { Log.e(TAG, "Watchdog error: ${it.message}") }
    }

    fun setup(mnemonic: String, salt: String) {
        Log.i(TAG, "setup: Initiating Magic Sync setup.")
        setupJob?.cancel()
        setupJob = scope.launch {
            try {
                Log.d(TAG, "setup: Phase 1 - State set to ENCRYPTING")
                setState(EncryptionState.ENCRYPTING)
                Log.d(TAG, "setup: Phase 2 - Deriving and persisting keys.")
                encryptionService.setup(mnemonic, salt)
                setState(EncryptionState.KEYS_READY)
                Log.d(TAG, "setup: Phase 3 - Setting up transport.")
                setupTransport()
                Log.i(TAG, "setup: Magic Sync is now fully READY.")
            } catch (e: Exception) {
                Log.e(TAG, "setup: ERROR during setup: ${e.message}", e)
                setState(EncryptionState.ERROR, e.message)
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
                    it.disconnect(); it.stop() 
                }
                tcpTransport?.let {
                    Log.d(TAG, "reset: Stopping active TCP transport.")
                    it.stop() 
                }
                Log.d(TAG, "reset: Clearing encryption keys.")
                encryptionService.clear()
                history.clear()
                blobStorageManager.cleanup()
                _state.update { BrokerState() }
                Log.i(TAG, "reset: Broker is now IDLE.")
            } catch (e: Exception) {
                Log.e(TAG, "Reset error: ${e.message}")
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
        if (state.value.ble.current == BleState.CONNECTED) return
        if (state.value.ble.current == BleState.CONNECTING) return
        val ble = bleTransport ?: run {
            Log.e(TAG, "connect: No transport available.")
            return
        }
        
        Log.i(TAG, "connect: Requesting connection to $address")
        wakeManager.acquire(15000L)
        setState(BleState.CONNECTING)
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
        val id = UUID.randomUUID().toString()

        scope.launch {
            val announcement = BlobMessage(
                id = id,
                name = metadata.name,
                size = metadata.size,
                blobType = type
            )
            
            history.add(announcement)
            
            Log.d(TAG, "blobUpdate: Sending announcement via BLE")
            bleTransport?.send(announcement)
            tcpTransport?.send(announcement)
            
            _messages.emit(announcement)

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
                        Log.d(TAG, "blobUpdate: Starting TCP stream for $id to $host:$port")
                        val success = tcp.sendBlob(announcement, input, host, port)
                        Log.i(TAG, "blobUpdate: TCP stream finished. Success: $success")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Stream error: ${e.message}")
                }
            }
        }
    }

    // --- State Update Helpers ---

    private fun setState(current: BleState, error: String? = null) {
        _state.update { it.copy(ble = Status(current, error)) }
    }

    private fun setState(current: TcpState, error: String? = null) {
        _state.update { it.copy(tcp = Status(current, error)) }
    }

    private fun setState(current: EncryptionState, error: String? = null) {
        _state.update { it.copy(encryption = Status(current, error)) }
    }

    private suspend fun onIncomingMessage(message: Message) {
        Log.i(TAG, "onIncomingMessage: Processing message Type: ${message.getType()}")
        if (message !is ChunkMessage) history.add(message)
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
                setState(TcpState.TRANSFERRING)
                blobStorageManager.writeChunk(message)
            }
            is IntroMessage -> {
                Log.i(TAG, "Partner Intro: ${message.name} at ${message.ip}:${message.port}")
                partnerTcpTarget = message.ip to message.port
                setState(TcpState.PINGING)
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
                setState(BleState.CONNECTED)
                wakeManager.release()
                
                val localIp = NetworkUtils.getLocalIpAddress() ?: "0.0.0.0"
                Log.d(TAG, "My IP: $localIp, Port: 49152")
                scope.launch { 
                    bleTransport?.send(IntroMessage(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis() / 1000.0,
                        name = Build.MODEL,
                        ip = localIp,
                        port = 49152,
                        address = null
                    ))
                }
            }
            IBleTransport.ConnectionState.DISCONNECTED -> {
                Log.i(TAG, "onBleStateChange: BLE Disconnected")
                setState(BleState.IDLE)
                wakeManager.release()
            }
            IBleTransport.ConnectionState.POWERED_OFF -> {
                Log.e(TAG, "onBleStateChange: Bluetooth Powered Off")
                setState(BleState.ERROR, "Bluetooth Powered Off")
                wakeManager.release()
            }
            else -> {}
        }
    }
}

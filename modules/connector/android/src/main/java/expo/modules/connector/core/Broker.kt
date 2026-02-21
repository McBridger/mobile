package expo.modules.connector.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import expo.modules.connector.interfaces.*
import expo.modules.connector.models.*
import expo.modules.connector.services.NotificationService
import expo.modules.connector.utils.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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

    private val _porterUpdates = MutableSharedFlow<Bundle>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val porterUpdates = _porterUpdates.asSharedFlow()

    // --- New Infrastructure ---
    private val _activePorters = MutableStateFlow<Map<String, Porter>>(emptyMap())
    val activePorters = _activePorters.asStateFlow()
    private val incomingChannels = ConcurrentHashMap<String, Channel<ChunkMessage>>()

    private var bleTransport: IBleTransport? = null
    private var tcpTransport: ITcpTransport? = null
    private var partnerTcpTarget: Pair<String, Int>? = null

    private var discoveryJob: Job? = null
    private var setupJob: Job? = null

    init {
        Log.i(TAG, "Initializing Broker instance.")
        
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
            setState(EncryptionState.ERROR, e.message)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun startDiscoveryLoop() {
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            combine(state, isForeground) { currentState, isFg ->
                val shouldScan = currentState.encryption.current == EncryptionState.KEYS_READY && 
                                currentState.ble.current != IBleTransport.State.CONNECTED &&
                                currentState.ble.current != IBleTransport.State.CONNECTING
                
                if (!shouldScan) return@combine null
                if (isFg) ScanConfig.Aggressive else ScanConfig.Passive
            }
            .debounce(500L)
            .distinctUntilChanged()
            .flatMapLatest { config ->
                if (config == null) return@flatMapLatest flowOf()

                val advertiseUuid = encryptionService.deriveUuid("McBridge_Advertise_UUID") 
                if (advertiseUuid == null) {
                    Log.e(TAG, "Discovery: Error deriving UUID")
                    setState(EncryptionState.ERROR, "Failed to derive UUID")
                    return@flatMapLatest flowOf()
                }
                
                val modeLabel = if (config is ScanConfig.Aggressive) "Aggressive" else "Passive"
                Log.i(TAG, "Discovery: Starting $modeLabel session for $advertiseUuid")
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
            }
            .filter { it.rssi > -85 }
            .let { flow ->
                if (config.timeoutMs == Long.MAX_VALUE) return@let flow
                flow.timeout(config.timeoutMs.milliseconds)
                    .retry { e ->
                        if (e is TimeoutCancellationException) return@retry true
                        Log.e(TAG, "Watchdog: Fatal error: ${e.message}")
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
                _activePorters.value = emptyMap()
                incomingChannels.values.forEach { it.close() }
                incomingChannels.clear()
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
            launch { 
                transport.state.collect { newState ->
                    setState(newState)
                    handleBleStateSideEffects(newState)
                } 
            }
        }
    }

    private fun handleBleStateSideEffects(state: IBleTransport.State) {
        when (state) {
            IBleTransport.State.CONNECTED -> {
                Log.i(TAG, "BLE is CONNECTED. Sending business card.")
                wakeManager.release()
                val localIp = NetworkUtils.getLocalIpAddress() ?: "0.0.0.0"
                scope.launch {
                    try {
                        bleTransport?.send(IntroMessage(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis() / 1000.0,
                            name = Build.MODEL,
                            ip = localIp,
                            port = 49152,
                            address = null
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send Intro: ${e.message}")
                    }
                }
            }
            IBleTransport.State.IDLE, 
            IBleTransport.State.POWERED_OFF, 
            IBleTransport.State.ERROR -> {
                wakeManager.release()
            }
            else -> {}
        }
    }

    fun registerTcp(transport: ITcpTransport) {
        Log.d(TAG, "registerTcp: Registering TCP transport.")
        this.tcpTransport?.stop()
        this.tcpTransport = transport
        scope.launch {
            launch { transport.incomingMessages.collect { onIncomingMessage(it) } }
            launch { transport.state.collect { setState(it) } }
        }
    }

    fun connect(address: String) {
        if (state.value.ble.current == IBleTransport.State.CONNECTED) return
        if (state.value.ble.current == IBleTransport.State.CONNECTING) return
        val ble = bleTransport ?: run {
            Log.e(TAG, "connect: No transport available.")
            return
        }
        
        Log.i(TAG, "connect: Requesting connection to $address")
        wakeManager.acquire(15000L)
        ble.connect(address)
    }

    fun disconnect() {
        Log.d(TAG, "disconnect: Manual disconnect requested.")
        bleTransport?.disconnect()
    }

    fun getHistory(): History = history
    fun getMnemonic(): String? = encryptionService.getMnemonic()

    // --- Outgoing Engine ---

    fun handleOutgoing(porter: Porter) {
        Log.d(TAG, "handleOutgoing: ${porter.name} (${porter.totalSize} bytes)")
        
        // 1. Persist task intent immediately
        history.add(porter)

        // 2. Decide strategy
        // Threshold reduced to 500 bytes to fit into a single BLE MTU packet safely.
        if (porter.type == BridgerType.TEXT && porter.totalSize < 500) {
            sendTiny(porter)
        } else {
            sendBlob(porter)
        }
    }

    private fun sendTiny(porter: Porter) {
        Log.d(TAG, "sendTiny: Instant sync for ${porter.id}")
        val message = TinyMessage(id = porter.id, value = porter.data ?: "")

        scope.launch {
            try {
                bleTransport?.send(message) ?: throw IllegalStateException("BLE transport not initialized")
                history.updatePorter(porter.id) { it.copy(status = Porter.Status.COMPLETED) }
            } catch (e: Exception) {
                Log.e(TAG, "sendTiny: Failed to send message ID: ${porter.id}, Error: ${e.message}")
                handleTransferError(porter.id, e.message ?: "Unknown transport failure")
            }
        }
    }

    private fun sendBlob(porter: Porter) {
        Log.d(TAG, "sendBlob: Starting stream for ${porter.id}")
        val id = porter.id
        
        // Add to active registry for progress tracking
        _activePorters.update { it + (id to porter.copy(status = Porter.Status.ACTIVE)) }
        history.updatePorter(id) { it.copy(status = Porter.Status.ACTIVE) }
        
        val announcement = BlobMessage(
            id = id,
            name = porter.name,
            size = porter.totalSize,
            dataType = porter.type
        )

        scope.launch {
            try {
                Log.d(TAG, "sendBlob: Sending announcement")
                bleTransport?.send(announcement) ?: throw IllegalStateException("BLE transport not initialized")
                
                // TCP announcement is optional if already in session, but protocol requires it here
                try { tcpTransport?.send(announcement) } catch (_: Exception) {}

                withContext(Dispatchers.IO) {
                    val tcp = tcpTransport ?: throw IllegalStateException("No TCP transport")
                    val (host, port) = partnerTcpTarget ?: throw IllegalStateException("No partner target")
                    
                    val streamUri = porter.data ?: ""
                    val stream = if (porter.type == BridgerType.TEXT) {
                        porter.data?.byteInputStream()
                    } else {
                        fileStreamProvider.openStream(streamUri)
                    }

                    stream?.use { input ->
                        Log.d(TAG, "sendBlob: Starting TCP stream")
                        tcp.sendBlob(announcement, input, host, port)
                        finishPorter(id, true, data = streamUri)
                    } ?: throw java.io.IOException("Could not open stream for $streamUri")
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendBlob failure: ${e.message}")
                handleTransferError(id, e.message ?: "Stream failure")
            }
        }
    }

    // --- State Update Helpers ---

    private fun setState(current: IBleTransport.State, error: String? = null) {
        _state.update { it.copy(ble = Status(current, error)) }
    }

    private fun setState(current: ITcpTransport.State, error: String? = null) {
        _state.update { it.copy(tcp = Status(current, error)) }
    }

    private fun setState(current: EncryptionState, error: String? = null) {
        _state.update { it.copy(encryption = Status(current, error)) }
    }

    private suspend fun onIncomingMessage(message: Message) {
        Log.v(TAG, "onIncomingMessage: ${message.getType()}")
        when (message) {
            is TinyMessage -> handleTinyMessage(message)
            is BlobMessage -> handleBlobAnnouncement(message)
            is ChunkMessage -> handleChunk(message)
            is IntroMessage -> handleIntro(message)
            else -> {}
        }
    }

    // --- Private Ingestor Logic ---

    private fun handleTinyMessage(msg: TinyMessage) {
        Log.d(TAG, "Incoming tiny message: ${msg.value.length} chars")
        val porter = Porter(
            id = msg.id,
            timestamp = msg.timestamp,
            isOutgoing = false,
            status = Porter.Status.COMPLETED,
            name = "Clipboard Sync",
            type = BridgerType.TEXT,
            totalSize = msg.value.length.toLong(),
            data = msg.value
        )
        history.add(porter)
        scope.launch { updateSystemClipboard(porter) }
    }

    private fun handleBlobAnnouncement(msg: BlobMessage) {
        Log.d(TAG, "Incoming blob announcement: ${msg.name} (${msg.size} bytes)")
        val porter = Porter(
            id = msg.id,
            timestamp = msg.timestamp,
            isOutgoing = false,
            status = Porter.Status.ACTIVE,
            name = msg.name,
            totalSize = msg.size,
            type = msg.dataType
        )
        _activePorters.update { it + (msg.id to porter) }
        
        val channel = Channel<ChunkMessage>(capacity = 64)
        incomingChannels[msg.id] = channel
        
        scope.launch {
            if (msg.dataType == BridgerType.TEXT) {
                executeTextWorker(msg.id, channel)
            } else {
                executeFileWorker(msg, channel)
            }
        }
    }

    private fun handleChunk(chunk: ChunkMessage) {
        incomingChannels[chunk.id]?.trySend(chunk)
    }

    private fun handleIntro(msg: IntroMessage) {
        Log.i(TAG, "Partner Intro: ${msg.name} at ${msg.ip}:${msg.port}")
        partnerTcpTarget = msg.ip to msg.port
        tcpTransport?.connect(msg.ip, msg.port)
    }

    // --- Workers ---

    private suspend fun executeTextWorker(id: String, channel: Channel<ChunkMessage>) {
        val buffer = StringBuilder()
        var received = 0L
        try {
            for (chunk in channel) {
                buffer.append(String(chunk.data))
                received += chunk.data.size
                notifyProgress(id, received)
                if (received >= (_activePorters.value[id]?.totalSize ?: 0)) break
            }
            finishPorter(id, true, data = buffer.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Text worker error: ${e.message}")
            finishPorter(id, false, error = e.message)
        }
    }

    private suspend fun executeFileWorker(msg: BlobMessage, channel: Channel<ChunkMessage>) {
        val session = blobStorageManager.openSession(msg) ?: run {
            finishPorter(msg.id, false, error = "Failed to open storage session")
            return
        }
        
        var received = 0L
        try {
            for (chunk in channel) {
                session.write(chunk.offset, chunk.data)
                received += chunk.data.size
                notifyProgress(msg.id, received)
                if (received >= msg.size) break
            }
            val uri = session.finalize()
            finishPorter(msg.id, uri != null, data = uri)
        } catch (e: Exception) {
            Log.e(TAG, "File worker error: ${e.message}")
            session.close()
            finishPorter(msg.id, false, error = e.message)
        }
    }

    // --- Progress & Finalization Helpers ---

    private suspend fun notifyProgress(id: String, currentSize: Long) {
        val porter = _activePorters.value[id] ?: return
        
        updateActivePorter(id) { 
            val progress = if (it.totalSize > 0) ((currentSize * 100) / it.totalSize).toInt() else 0
            it.copy(status = Porter.Status.ACTIVE, currentSize = currentSize, progress = progress)
        }

        val bundle = Bundle().apply {
            putString("id", id)
            putInt("progress", if (porter.totalSize > 0) (currentSize * 100 / porter.totalSize).toInt() else 0)
            putDouble("currentSize", currentSize.toDouble())
        }
        _porterUpdates.emit(bundle)
        
        notificationService.showProgress(
            blobId = id,
            name = porter.name,
            progress = if (porter.totalSize > 0) (currentSize * 100 / porter.totalSize).toInt() else 0,
            currentSize = currentSize,
            totalSize = porter.totalSize,
            speedBps = 0 
        )
    }

    private suspend fun finishPorter(id: String, success: Boolean, data: String? = null, error: String? = null) {
        val active = _activePorters.value[id] ?: return
        val finalPorter = active.copy(
            status = if (success) Porter.Status.COMPLETED else Porter.Status.ERROR,
            data = data,
            error = error
        )
        
        history.add(finalPorter)
        _activePorters.update { it - id }
        incomingChannels.remove(id)?.close()
        
        notificationService.showFinished(id, active.name, success)
        
        // Fix: Update clipboard for ALL incoming types (Text, File, Image)
        if (success && !finalPorter.isOutgoing && data != null) {
            updateSystemClipboard(finalPorter)
        }
    }

    private suspend fun handleTransferError(id: String, error: String) {
        val active = _activePorters.value[id] ?: return
        val finalPorter = active.copy(status = Porter.Status.ERROR, error = error)
        history.add(finalPorter)
        _activePorters.update { it - id }
    }

    private fun updateActivePorter(id: String, transform: (Porter) -> Porter) {
        _activePorters.update { current ->
            val porter = current[id] ?: return@update current
            current + (id to transform(porter))
        }
    }

    private suspend fun updateSystemClipboard(porter: Porter) {
        val content = porter.data ?: return
        withContext(Dispatchers.Main) {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = if (porter.type == BridgerType.TEXT) {
                    ClipData.newPlainText("McBridger Text", content)
                } else {
                    ClipData.newUri(context.contentResolver, porter.name, android.net.Uri.parse(content))
                }
                clipboard.setPrimaryClip(clip)
                Log.d(TAG, "updateSystemClipboard: Clipboard updated for type ${porter.type}")
            } catch (e: Exception) {
                Log.e(TAG, "updateSystemClipboard: Failed: ${e.message}")
            }
        }
    }
}

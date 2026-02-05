package expo.modules.connector.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import expo.modules.connector.interfaces.*
import expo.modules.connector.models.Message
import expo.modules.connector.models.BleDevice
import expo.modules.connector.models.ClipboardMessage
import expo.modules.connector.models.FileMessage
import expo.modules.connector.models.IntroMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import expo.modules.connector.models.*
import expo.modules.connector.services.FileTransferService
import expo.modules.connector.transports.tcp.TcpTransport
import expo.modules.connector.transports.tcp.TcpFileProvider
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import java.io.RandomAccessFile

class Broker(
    context: Context,
    private val encryptionService: IEncryptionService,
    private val scanner: IBleScanner,
    private val history: History,
    private val wakeManager: IWakeManager,
    private val fileTransferService: FileTransferService,
    private val fileProvider: TcpFileProvider,
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
    
    private val incomingFiles = mutableMapOf<String, java.io.File>()

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
                if (config.timeoutMs == Long.MAX_VALUE) { return@let flow }

                flow.timeout(config.timeoutMs.milliseconds)
                    .retry { e ->
                        if (e is TimeoutCancellationException) { return@retry true }

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
        transportCollectionJob?.cancel()
        
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
            launch { 
                transport.incomingMessages.collect { onIncomingMessage(it) } 
            }
            launch { 
                transport.connectionState.collect { onBleStateChange(it) } 
            }
        }
    }

    fun registerTcp(transport: ITcpTransport) {
        Log.d(TAG, "registerTcp: Registering TCP transport.")
        this.tcpTransport?.stop()
        this.tcpTransport = transport

        scope.launch {
            launch { 
                transport.incomingMessages.collect { onIncomingMessage(it) } 
            }
            launch { 
                transport.connectionState.collect { 
                    Log.d(TAG, "TCP Transport state: $it")
                } 
            }
        }
    }

    fun connect(address: String) {
        if (state.value == State.CONNECTING || state.value == State.CONNECTED) {
            Log.d(TAG, "connect: Already connected or connecting to $address, ignoring.")
            return
        }

        if (bleTransport == null) {
            Log.e(TAG, "connect: No transport available.")
            return
        }
        
        // Lock for the duration of the connection attempt (released via state changes)
        wakeManager.acquire(15000L)
        
        Log.i(TAG, "connect: Requesting connection to $address")
        _state.value = State.CONNECTING
        
        bleTransport!!.connect(address)
    }

    fun disconnect() {
        Log.d(TAG, "disconnect: Manual disconnect requested.")
        bleTransport?.disconnect()
    }

    fun getHistory(): History = history
    fun getMnemonic(): String? = encryptionService.getMnemonic()

    fun clipboardUpdate(content: String) {
        Log.d(TAG, "clipboardUpdate: Sending clipboard update (${content.length} chars)")
        val message = ClipboardMessage(value = content)
        history.add(message)
        scope.launch {
            bleTransport?.send(message)
            _messages.emit(message)
        }
    }

    fun fileUpdate(metadata: FileMetadata) {
        Log.d(TAG, "fileUpdate: Preparing to share file ${metadata.name} (${metadata.size})")
        
        val tcp = tcpTransport ?: run {
            Log.e(TAG, "fileUpdate: TCP transport is not ready")
            return
        }

        val fileId = UUID.randomUUID().toString()
        val totalSize = metadata.size.toLongOrNull() ?: 0L

        scope.launch {
            // 1. Send announcement
            val announcement = FileMessage(
                id = fileId,
                url = "", // No URL needed for push protocol
                name = metadata.name,
                size = metadata.size
            )
            
            history.add(announcement)
            tcp.send(announcement)
            bleTransport?.send(announcement)
            _messages.emit(announcement)

            // 2. Stream parts
            withContext(Dispatchers.IO) {
                try {
                    fileProvider.openStream(metadata.uri.toString())?.use { input ->
                        val buffer = ByteArray(64 * 1024) // 64KB chunks
                        var currentOffset = 0L
                        
                        while (isActive) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            
                            val part = FilePart(
                                fileId = fileId,
                                data = if (read == buffer.size) buffer else buffer.copyOf(read),
                                offset = currentOffset,
                                total = totalSize
                            )
                            
                            if (!tcp.send(part)) {
                                Log.e(TAG, "Stream interrupted: Send failed")
                                break
                            }
                            
                            currentOffset += read
                        }
                        Log.i(TAG, "Streaming finished: $currentOffset bytes sent for $fileId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Streaming error: ${e.message}")
                }
            }
        }
    } 

    private suspend fun onIncomingMessage(message: Message) {
        Log.i(TAG, "onIncomingMessage: Processing message Type: ${message.getType()}")

        history.add(message)
        when (message) {
            is ClipboardMessage -> {
                wakeManager.withLock(10000L) {
                    updateSystemClipboard(message.value)
                }
            }
            is FileMessage -> {
                Log.d(TAG, "Incoming file: ${message.name} (${message.size})")
                // Prepare local file in cache
                val tempFile = java.io.File(context.cacheDir, message.id)
                incomingFiles[message.id] = tempFile
                fileTransferService.showOffer(message.name, message.id)
            }
            is FilePart -> {
                handleFilePart(message)
            }
            else -> {}
        }

        scope.launch { _messages.emit(message) }
    }

    private suspend fun handleFilePart(part: FilePart) = withContext(Dispatchers.IO) {
        val file = incomingFiles[part.fileId] ?: run {
            val f = java.io.File(context.cacheDir, part.fileId)
            incomingFiles[part.fileId] = f
            f
        }

        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(part.offset)
                raf.write(part.data)
            }
            
            // Auto-finalize when last byte arrives
            if (part.offset + part.data.size >= part.total && part.total > 0) {
                Log.i(TAG, "File ${part.fileId} fully received. Finalizing...")
                // We need the original filename. Let's get it from history or store it.
                val historyItem = history.getHistory().find { it is FileMessage && it.id == part.fileId } as? FileMessage
                val filename = historyItem?.name ?: "received_${System.currentTimeMillis()}"
                
                finalizeFile(part.fileId, filename)
                
                // Update UI or notification
                withContext(Dispatchers.Main) {
                    fileTransferService.showFinished(filename)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write file part: ${e.message}")
        }
    }

    fun finalizeFile(fileId: String, filename: String): Boolean {
        val tempFile = incomingFiles[fileId] ?: return false
        if (!tempFile.exists()) return false

        return try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val McBridgerDir = java.io.File(downloadsDir, "McBridger")
            if (!McBridgerDir.exists()) McBridgerDir.mkdirs()

            val destFile = java.io.File(McBridgerDir, filename)
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()
            incomingFiles.remove(fileId)
            Log.i(TAG, "File finalized and moved to: ${destFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize file: ${e.message}")
            false
        }
    }

    private suspend fun updateSystemClipboard(text: String) {
        withContext(Dispatchers.Main) {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Bridger Data", text))
                Log.d(TAG, "updateSystemClipboard: Clipboard updated on Main thread.")
            } catch (e: Exception) {
                Log.e(TAG, "updateSystemClipboard: Failed: ${e.message}")
            }
        }
    }

    private fun onBleStateChange(state: IBleTransport.ConnectionState) {
        Log.d(TAG, "onBleStateChange: BLE state changed to $state")
        when (state) {
            IBleTransport.ConnectionState.CONNECTING -> { _state.value = State.CONNECTING }
            // Must record CONNECTING here because we're not READY yet.
            IBleTransport.ConnectionState.CONNECTED -> { _state.value = State.CONNECTING }
            
            IBleTransport.ConnectionState.READY -> {
                Log.i(TAG, "onBleStateChange: BLE is READY. Sending device info.")
                _state.value = State.CONNECTED
                wakeManager.release()
                scope.launch { 
                    bleTransport?.send(IntroMessage(value = Build.MODEL))
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

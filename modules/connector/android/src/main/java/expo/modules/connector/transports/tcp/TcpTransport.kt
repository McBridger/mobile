package expo.modules.connector.transports.tcp

import android.util.Log
import expo.modules.connector.interfaces.IEncryptionService
import expo.modules.connector.interfaces.ITcpTransport
import expo.modules.connector.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Socket

/**
 * SRP: Implementation of ITcpTransport using Unified Binary Framing.
 */
class TcpTransport(
    private val encryptionService: IEncryptionService,
    private val port: Int = 49152,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : ITcpTransport {

    private val TAG = "TcpTransport"
    private var controlSocket: Socket? = null
    private var maintenanceJob: Job? = null
    private val activityPulse = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _state = MutableStateFlow(ITcpTransport.State.IDLE)
    override val state: StateFlow<ITcpTransport.State> = _state.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Message>()
    override val incomingMessages = _incomingMessages

    private val manager = TcpServerManager(
        port = port,
        onMessage = ::handleIncomingFrame,
        scope = scope
    )

    init {
        manager.start()
        _state.value = ITcpTransport.State.READY

        scope.launch {
            activityPulse.collectLatest {
                delay(5000L)
                if (_state.value == ITcpTransport.State.CONNECTED || _state.value == ITcpTransport.State.TRANSFERRING) {
                    Log.w(TAG, "Watchdog: Peer silence detected. Reverting to PINGING.")
                    _state.value = ITcpTransport.State.PINGING
                }
            }
        }
    }

    override fun connect(host: String, port: Int) {
        Log.i(TAG, "connect: Initiating persistent connection to $host:$port")
        
        maintenanceJob?.cancel()
        maintenanceJob = scope.launch {
            _state.value = ITcpTransport.State.PINGING
            
            while (isActive) {
                try {
                    val socket = controlSocket ?: manager.connect(host, port)?.also {
                        it.setKeepAlive(true)
                        it.tcpNoDelay = true
                        controlSocket = it
                        _state.value = ITcpTransport.State.CONNECTED
                        activityPulse.emit(Unit)
                        Log.i(TAG, "Maintenance: Control socket established.")
                    }

                    if (socket != null) {
                        Log.v(TAG, "Maintenance: Sending heartbeat...")
                        manager.sendFrame(socket, PingMessage().toBytes())
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Maintenance: Connection lost or failed: ${e.message}")
                    disconnectInternal()
                    // Stay in PINGING while we retry
                }
                delay(3000L)
            }
        }
    }

    private fun disconnectInternal() {
        Log.i(TAG, "Internal disconnect triggered.")
        try { controlSocket?.close() } catch (_: Exception) {}
        controlSocket = null
        if (_state.value != ITcpTransport.State.IDLE) {
            _state.value = ITcpTransport.State.PINGING
        }
    }

    private suspend fun handleIncomingFrame(payload: ByteArray, address: String) {
        activityPulse.emit(Unit)
        try {
            // In a future step, payload will be decrypted here
            val message = Message.fromBytes(payload) ?: return
            
            if (message is PingMessage) {
                Log.v(TAG, "Heartbeat: Ping received from $address")
                // Incoming ping confirms we are CONNECTED (if not currently transferring)
                if (_state.value != ITcpTransport.State.TRANSFERRING) {
                    _state.value = ITcpTransport.State.CONNECTED
                }
                return
            }
            
            // Any other message means traffic is flowing
            _state.value = ITcpTransport.State.TRANSFERRING
            _incomingMessages.emit(message.withAddress(address))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process frame: ${e.message}")
        }
    }

    override suspend fun send(message: Message) {
        withContext(Dispatchers.IO) {
            val socket = controlSocket ?: throw java.io.IOException("No active control socket")
            try {
                manager.sendFrame(socket, message.toBytes())
            } catch (e: Exception) {
                disconnectInternal()
                throw e
            }
        }
    }

    override suspend fun sendBlob(
        message: BlobMessage, 
        inputStream: java.io.InputStream, 
        host: String, 
        port: Int
    ) {
        withContext(Dispatchers.IO) {
            val socket = controlSocket ?: throw java.io.IOException("TCP control channel not ready. Call connect() first.")
            
            val previousState = _state.value
            _state.value = ITcpTransport.State.TRANSFERRING
            
            try {
                // 1. Send Blob Announcement Frame
                manager.sendFrame(socket, message.toBytes())
                
                // 2. Send Data in Chunks
                val buffer = ByteArray(64 * 1024)
                var offset = 0L
                while (isActive) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    
                    val chunk = ChunkMessage(offset, buffer.copyOf(read), message.id)
                    chunk.address = socket.inetAddress.hostAddress
                    manager.sendFrame(socket, chunk.toBytes())
                    
                    offset += read
                }
                
                Log.i(TAG, "Blob send finished: $offset bytes for ${message.name}")
            } catch (e: Exception) {
                Log.e(TAG, "sendBlob failed: ${e.message}")
                disconnectInternal()
                throw java.io.IOException("TCP Stream error: ${e.message}", e)
            } finally {
                if (_state.value == ITcpTransport.State.TRANSFERRING) {
                    _state.value = previousState
                }
            }
        }
    }

    override fun stop() {
        manager.stop()
        maintenanceJob?.cancel()
        maintenanceJob = null
        try { controlSocket?.close() } catch (_: Exception) {}
        controlSocket = null
        _state.value = ITcpTransport.State.IDLE
        scope.cancel()
    }

    companion object {
        private const val TAG = "TcpTransport"
    }
}

package expo.modules.connector.transports.tcp

import android.util.Log
import expo.modules.connector.interfaces.*
import expo.modules.connector.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.net.Socket
import kotlin.coroutines.coroutineContext

/**
 * SRP: Implementation of ITcpTransport using Unified Binary Framing.
 * Architecture: Single Writer Loop (Actor Pattern) with strict State Machine.
 */
class TcpTransport(
    private val encryptionService: IEncryptionService,
    private val systemObserver: ISystemObserver,
    private val port: Int = 49152,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : ITcpTransport {

    private val TAG = "TcpTransport"
    private var controlSocket: Socket? = null
    private var maintenanceJob: Job? = null

    private val _state = MutableStateFlow(ITcpTransport.State.IDLE)
    override val state: StateFlow<ITcpTransport.State> = _state.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Message>()
    override val incomingMessages = _incomingMessages

    // Outbound queue with backpressure support (Capacity 64)
    private val outboundQueue = Channel<Message>(capacity = 64)
    
    // Last time we heard from the partner (Ping or Data)
    @Volatile private var lastPeerActivityAt = 0L

    private val manager = TcpServerManager(port, ::handleIncomingFrame, scope)

    init {
        manager.start()
        _state.value = ITcpTransport.State.READY

        // Global Watchdog: disconnect on Wi-Fi loss
        scope.launch {
            systemObserver.isNetworkHighSpeed.collect { isHighSpeed ->
                if (!isHighSpeed && _state.value != ITcpTransport.State.IDLE) {
                    Log.w(TAG, "Watchdog: Wi-Fi lost. Forcing disconnect.")
                    disconnectInternal()
                }
            }
        }
    }

    override fun connect(host: String, port: Int) {
        Log.i(TAG, "connect: Starting connection management for $host:$port")
        maintenanceJob?.cancel()
        
        maintenanceJob = scope.launch {
            var currentRetryDelay = 2000L

            while (isActive) {
                _state.value = ITcpTransport.State.PINGING

                try {
                    val socket = manager.connect(host, port) ?: throw IOException("Connect returned null")
                    socket.keepAlive = true
                    socket.tcpNoDelay = true
                    socket.sendBufferSize = 65536 // OS-level buffer bloat protection
                    
                    controlSocket = socket
                    _state.value = ITcpTransport.State.CONNECTED
                    lastPeerActivityAt = System.currentTimeMillis()
                    currentRetryDelay = 2000L // Reset backoff on success
                    Log.i(TAG, "Socket established with $host:$port")

                    // Run writer and watchdog concurrently for the current socket
                    coroutineScope {
                        launch { writeLoop(socket) }
                        launch { watchdogLoop() }
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "Connection failed or lost: ${e.message}. Retrying in ${currentRetryDelay}ms")
                    delay(currentRetryDelay)
                    currentRetryDelay = (currentRetryDelay * 2).coerceAtMost(30000L)
                } finally {
                    disconnectInternal()
                }
            }
        }
    }

    /**
     * Single Writer Loop: The only coroutine allowed to write to the socket.
     */
    private suspend fun writeLoop(socket: Socket) {
        for (message in outboundQueue) {
            if (!socket.isConnected || socket.isClosed) throw IOException("Socket closed during write")
            
            manager.sendFrame(socket, message.toBytes())
        }
    }

    /**
     * Watchdog Loop: Monitors connection pulse and sends heartbeats.
     */
    private suspend fun watchdogLoop() {
        while (coroutineContext.isActive) {
            delay(3000L)
            
            if (!systemObserver.isForeground.value) continue

            val idleTime = System.currentTimeMillis() - lastPeerActivityAt

            if (idleTime > 12000L) {
                // 12s of total silence from peer - socket is dead.
                throw IOException("Heartbeat timeout. Peer is silent for 12s.")
            } else if (idleTime > 4000L && _state.value != ITcpTransport.State.TRANSFERRING) {
                // Send ping if peer is idle for 4s and not transferring data
                outboundQueue.trySend(PingMessage())
            }
        }
    }

    private suspend fun handleIncomingFrame(payload: ByteArray, address: String) {
        // ANY incoming frame is a sign of life. Reset peer activity timer.
        lastPeerActivityAt = System.currentTimeMillis()

        try {
            val message = Message.fromBytes(payload) ?: return
            if (message is PingMessage) return // Internal ping handled by updating pulse above

            // Handle data flow with respect to State API
            val prevState = _state.value
            if (prevState != ITcpTransport.State.TRANSFERRING) _state.value = ITcpTransport.State.TRANSFERRING
            
            _incomingMessages.emit(message.withAddress(address))
            
            if (prevState != ITcpTransport.State.TRANSFERRING) _state.value = ITcpTransport.State.CONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process frame: ${e.message}")
        }
    }

    override suspend fun sendBlob(
        message: BlobMessage, 
        inputStream: java.io.InputStream, 
        host: String, 
        port: Int
    ) {
        withContext(Dispatchers.IO) {
            val prevState = _state.value
            _state.value = ITcpTransport.State.TRANSFERRING // Watchdog will suppress pings

            try {
                outboundQueue.send(message) // Announcement

                val buffer = ByteArray(64 * 1024)
                var offset = 0L
                
                inputStream.use { stream ->
                    while (coroutineContext.isActive) {
                        val read = stream.read(buffer)
                        if (read == -1) break
                        
                        val chunk = ChunkMessage(offset, buffer.copyOf(read), message.id).apply { address = host }
                        
                        // Backpressure: suspends if queue (64) is full
                        outboundQueue.send(chunk) 
                        offset += read
                    }
                }
                Log.i(TAG, "Blob send finished: $offset bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Blob send failed: ${e.message}")
                throw IOException("TCP Stream error: ${e.message}", e)
            } finally {
                if (_state.value == ITcpTransport.State.TRANSFERRING) {
                    _state.value = prevState
                }
            }
        }
    }

    private fun disconnectInternal() {
        try { controlSocket?.close() } catch (_: Exception) {}
        controlSocket = null
        
        // Drain the queue to prevent stale frames in new connections
        while (outboundQueue.tryReceive().isSuccess) { /* drain */ }
        
        if (_state.value != ITcpTransport.State.IDLE) {
            _state.value = ITcpTransport.State.PINGING
        }
    }

    override fun stop() {
        manager.stop()
        maintenanceJob?.cancel()
        disconnectInternal()
        _state.value = ITcpTransport.State.IDLE
        scope.cancel()
    }

    companion object {
        private const val TAG = "TcpTransport"
    }
}

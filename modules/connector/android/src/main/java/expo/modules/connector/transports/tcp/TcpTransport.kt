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

    // Tracks the last ping ID we sent to avoid infinite echo loops
    @Volatile private var lastSentPingId: String? = null

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
        Log.i(TAG, "connect: Starting connection for $host:$port")
        maintenanceJob?.cancel()
        
        maintenanceJob = scope.launch {
            try {
                val socket = manager.connect(host, port) ?: throw IOException("Connect returned null")
                socket.keepAlive = true
                socket.tcpNoDelay = true
                socket.sendBufferSize = 65536 // OS-level buffer bloat protection
                
                controlSocket = socket
                _state.value = ITcpTransport.State.CONNECTED
                Log.i(TAG, "Socket established with $host:$port")

                // Run writer and reader loops concurrently for the current socket
                coroutineScope {
                    launch { writeLoop(socket) }
                    launch { manager.readIncomingFromClientSocket(socket, host) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Connection failed or lost: ${e.message}")
            } finally {
                disconnectInternal()
            }
        }
    }

    override fun forcePing() {
        if (_state.value != ITcpTransport.State.CONNECTED) return
        
        scope.launch {
            val ping = PingMessage()
            lastSentPingId = ping.id
            Log.d(TAG, "forcePing: Sent Ping(${ping.id}), waiting for any pulse...")

            try {
                outboundQueue.send(ping)
                withTimeout(2000L) {
                    // Success if we receive ANY PingMessage within 2s
                    incomingMessages.first { it is PingMessage }
                }
                Log.i(TAG, "forcePing: Success! Pipe is alive.")
            } catch (e: Exception) {
                Log.w(TAG, "forcePing: FAILED! No response within 2s. Scuttling.")
                stop()
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

    private suspend fun handleIncomingFrame(payload: ByteArray, address: String) {
        try {
            val message = Message.fromBytes(payload) ?: return

            if (message is PingMessage) {
                // Emit so forcePing and other observers can see it
                _incomingMessages.emit(message.withAddress(address))
                
                // Echo Pattern: only echo if it's NOT our own last ping returning to us
                if (message.id != lastSentPingId) {
                    Log.v(TAG, "Echoing incoming Ping(${message.id})")
                    outboundQueue.send(PingMessage(id = message.id))
                }
                return
            }
            
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
            _state.value = ITcpTransport.State.TRANSFERRING

            try {
                outboundQueue.send(message) // Announcement

                val buffer = ByteArray(64 * 1024)
                var offset = 0L
                
                inputStream.use { stream ->
                    while (coroutineContext.isActive) {
                        val read = stream.read(buffer)
                        if (read == -1) break
                        
                        val chunk = ChunkMessage(offset, buffer.copyOf(read), message.id).apply { address = host }
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
            _state.value = ITcpTransport.State.READY
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

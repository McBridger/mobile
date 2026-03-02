package expo.modules.connector.transports.tcp

import android.util.Log
import expo.modules.connector.interfaces.*
import expo.modules.connector.models.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class TcpTransport(
        private val encryptionService: IEncryptionService,
        private val systemObserver: ISystemObserver,
        private val port: Int = 49152,
        private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : ITcpTransport {

    private val TAG = "TcpTransport"
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private var serverJob: Job? = null

    private val _isStarted = MutableStateFlow(false)
    private val _isTransferring = MutableStateFlow(false)
    private val _activeSessionsFlow = MutableStateFlow<List<SessionContext>>(emptyList())

    override val state: StateFlow<ITcpTransport.State> =
            combine(_isStarted, _isTransferring, _activeSessionsFlow) {
                            isStarted,
                            isTransferring,
                            sessions ->
                        if (!isStarted) ITcpTransport.State.IDLE
                        else if (isTransferring) ITcpTransport.State.TRANSFERRING
                        else if (sessions.isNotEmpty()) ITcpTransport.State.CONNECTED
                        else ITcpTransport.State.READY
                    }
                    .stateIn(scope, SharingStarted.Eagerly, ITcpTransport.State.IDLE)

    private val _incomingMessages =
            MutableSharedFlow<Message>(
                    extraBufferCapacity = 64,
                    onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.SUSPEND
            )
    override val incomingMessages: SharedFlow<Message> = _incomingMessages.asSharedFlow()

    private inner class SessionContext(val session: ITcpSession, val supervisor: Job)
    private val activeSessions = CopyOnWriteArrayList<SessionContext>()

    init {
        start()
        // Watchdog: disconnect on Wi-Fi loss
        scope.launch {
            systemObserver.isNetworkHighSpeed.collect { isHighSpeed ->
                if (!isHighSpeed && _isStarted.value) {
                    Log.w(TAG, "Watchdog: Wi-Fi lost. Stopping transport.")
                    stop()
                }
            }
        }
    }

    private fun start() {
        if (_isStarted.value) return
        _isStarted.value = true

        serverJob =
                scope.launch {
                    try {
                        val serverSocket =
                                aSocket(selectorManager).tcp().bind(port = port) {
                                    reuseAddress = true
                                }
                        Log.i(TAG, "Server bound to port $port")
                        while (isActive) {
                            val socket = serverSocket.accept()
                            val host = socket.remoteAddress.toString()
                            Log.i(TAG, "Accepted connection from $host")
                            val session =
                                    TcpSession(
                                            id = java.util.UUID.randomUUID().toString(),
                                            host = host,
                                            port = port,
                                            socket = socket
                                    )
                            setupNewSession(session)
                        }
                    } catch (e: Exception) {
                        if (isActive) Log.e(TAG, "Server error: ${e.message}")
                    }
                }
    }

    override fun connect(host: String, port: Int) {
        scope.launch {
            try {
                Log.i(TAG, "Connecting to $host:$port")
                val socket =
                        aSocket(selectorManager).tcp().connect(host, port) {
                            keepAlive = true
                            noDelay = true
                        }
                val session =
                        TcpSession(
                                id = java.util.UUID.randomUUID().toString(),
                                host = host,
                                port = port,
                                socket = socket
                        )
                setupNewSession(session)
                Log.i(TAG, "Connected to $host:$port")
            } catch (e: Exception) {
                Log.w(TAG, "Connect failed: ${e.message}")
            }
        }
    }

    private fun setupNewSession(session: ITcpSession) {
        val supervisor =
                scope.launch {
                    try {
                        coroutineScope {
                            launch { collectMessages(session) }
                            launch { collectState(session, this@coroutineScope) }
                        }
                    } finally {
                        handleSessionDeath(session)
                    }
                }
        activeSessions.add(SessionContext(session, supervisor))
        _activeSessionsFlow.value = activeSessions.toList()
    }

    private suspend fun collectMessages(session: ITcpSession) {
        session.incomingMessages.collect { msgBytes ->
            try {
                val msg = Message.fromBytes(msgBytes).withAddress(session.host)
                _incomingMessages.emit(msg)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse incoming message: ${e.message}")
            }
        }
    }

    private suspend fun collectState(session: ITcpSession, scopeToCancel: CoroutineScope) {
        session.state.collect { sessionState ->
            if (sessionState == SessionState.DISCONNECTED || sessionState == SessionState.ERROR) {
                scopeToCancel.cancel("Session terminated with state: $sessionState")
            }
        }
    }

    private fun handleSessionDeath(deadSession: ITcpSession) {
        val ctx = activeSessions.find { it.session === deadSession } ?: return
        Log.i(TAG, "handleSessionDeath for ${ctx.session.id}")
        ctx.session.disconnect()
        ctx.supervisor.cancel()
        activeSessions.remove(ctx)
        _activeSessionsFlow.value = activeSessions.toList()
    }

    override fun disconnect() {
        Log.i(TAG, "Explicit disconnect requested (stopping all sessions)")
        activeSessions.forEach { it.session.disconnect() }
    }

    override fun forcePing() {
        if (activeSessions.isEmpty()) return
        scope.launch {
            activeSessions.forEach { ctx ->
                launch {
                    try {
                        ctx.session.forcePing()
                    } catch (e: Exception) {
                        Log.w(TAG, "Ping failed for session ${ctx.session.id}")
                    }
                }
            }
        }
    }

    private suspend fun broadcastBytes(
            data: ByteArray,
            targets: List<SessionContext> = activeSessions
    ) {
        if (data.isEmpty() || targets.isEmpty()) return

        coroutineScope {
            targets.forEach { ctx ->
                launch {
                    try {
                        ctx.session.send(data)
                    } catch (e: Exception) {
                        Log.w(TAG, "Send failed for session ${ctx.session.id}: ${e.message}")
                    }
                }
            }
        }
    }

    override suspend fun sendBlob(
            message: BlobMessage,
            inputStream: java.io.InputStream,
            host: String,
            port: Int
    ) {
        val transferTargets = activeSessions.toList()
        if (transferTargets.isEmpty()) throw java.io.IOException("No active sessions to send blob")

        _isTransferring.value = true
        try {
            broadcastBytes(message.toBytes(), transferTargets)

            val buffer = ByteArray(64 * 1024)
            var offset = 0L
            inputStream.use { stream ->
                while (currentCoroutineContext().isActive) {
                    val read = stream.read(buffer)
                    if (read == -1) break

                    val chunk = ChunkMessage(offset, buffer.copyOf(read), message.id)
                    broadcastBytes(chunk.toBytes(), transferTargets)
                    offset += read
                }
            }
            // Send EOF
            val eof = ChunkMessage(offset, ByteArray(0), message.id)
            broadcastBytes(eof.toBytes(), transferTargets)
            Log.i(TAG, "Blob send finished: $offset bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Blob send failed: ${e.message}")
            throw java.io.IOException("TCP Stream error: ${e.message}", e)
        } finally {
            _isTransferring.value = false
        }
    }

    override fun stop() {
        Log.i(TAG, "Stopping TcpTransport")
        serverJob?.cancel()
        activeSessions.forEach { ctx ->
            ctx.supervisor.cancel()
            ctx.session.disconnect()
        }
        activeSessions.clear()
        _activeSessionsFlow.value = emptyList()
        _isStarted.value = false
        _isTransferring.value = false
    }
}

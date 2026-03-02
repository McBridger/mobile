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

    private val _isTransferring = MutableStateFlow(false)
    private val _activeSessionsFlow = MutableStateFlow<List<SessionContext>>(emptyList())

    override val state: StateFlow<ITcpTransport.State> =
            combine(_isTransferring, _activeSessionsFlow) {
                            isTransferring,
                            sessions ->
                        if (isTransferring) ITcpTransport.State.TRANSFERRING
                        else if (sessions.isNotEmpty()) ITcpTransport.State.CONNECTED
                        else ITcpTransport.State.READY
                    }
                    .stateIn(scope, SharingStarted.Eagerly, ITcpTransport.State.READY)

    private val _incomingMessages =
            MutableSharedFlow<Message>(
                    extraBufferCapacity = 64,
                    onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.SUSPEND
            )
    override val incomingMessages: SharedFlow<Message> = _incomingMessages.asSharedFlow()

    private inner class SessionContext(val session: ITcpSession, val supervisor: Job)
    private val activeSessions = CopyOnWriteArrayList<SessionContext>()

    init {
        // Watchdog: disconnect on Wi-Fi loss
        scope.launch {
            systemObserver.isNetworkHighSpeed.collect { isHighSpeed ->
                if (!isHighSpeed && activeSessions.isNotEmpty()) {
                    Log.w(TAG, "Watchdog: Wi-Fi lost. Stopping transport.")
                    stop()
                }
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
            } catch (e: Exception) {
                Log.w(TAG, "Connect failed: ${e.message}")
            }
        }
    }

    private fun setupNewSession(session: ITcpSession) {
        scope.launch {
            val supervisorJob = this.coroutineContext.job
            try {
                coroutineScope {
                    launch { collectState(session, this@coroutineScope) }

                    // Client Quarantine Handshake
                    val handshakeSuccess = performQuarantineHandshake(session)
                    if (!handshakeSuccess) {
                        Log.w(TAG, "Handshake failed, disconnecting session ${session.id}")
                        session.disconnect()
                        return@coroutineScope
                    }

                    Log.i(TAG, "Handshake SUCCESS for ${session.id}")
                    activeSessions.add(SessionContext(session, supervisorJob))
                    _activeSessionsFlow.value = activeSessions.toList()

                    collectMessages(session)
                }
            } finally {
                handleSessionDeath(session)
            }
        }
    }

    private suspend fun performQuarantineHandshake(session: ITcpSession): Boolean {
        return try {
            withTimeout(3000L) {
                executeClientHandshake(session)
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Quarantine timeout")
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Quarantine exception: ${e.message}")
            false
        }
    }

    private suspend fun executeClientHandshake(session: ITcpSession): Boolean {
        return coroutineScope {
            val responseDeferred = async { session.incomingMessages.first() }

            val clientIntro = IntroMessage(name = "KotlinClient") // Name doesn't matter for transport verification
            val encryptedMsg = encryptionService.encrypt(clientIntro.toBytes(), ByteArray(0))
            if (encryptedMsg == null) {
                responseDeferred.cancel()
                return@coroutineScope false
            }

            try {
                session.send(encryptedMsg)
            } catch (e: Exception) {
                responseDeferred.cancel()
                return@coroutineScope false
            }

            val firstMsgBytes = responseDeferred.await()
            val decrypted =
                    encryptionService.decrypt(firstMsgBytes, ByteArray(0))
                            ?: return@coroutineScope false

            val msg =
                    try {
                        Message.fromBytes(decrypted)
                    } catch (e: Exception) {
                        return@coroutineScope false
                    }

            msg is IntroMessage
        }
    }

    private suspend fun collectMessages(session: ITcpSession) {
        session.incomingMessages.collect { msgBytes ->
            try {
                val decryptedBytes =
                        encryptionService.decrypt(msgBytes, ByteArray(0)) ?: return@collect
                val msg = Message.fromBytes(decryptedBytes).withAddress(session.host)
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

        val anySuccess = java.util.concurrent.atomic.AtomicBoolean(false)
        coroutineScope {
            targets.forEach { ctx ->
                launch {
                    try {
                        ctx.session.send(data)
                        anySuccess.set(true)
                    } catch (e: Exception) {
                        Log.w(TAG, "Send failed for session ${ctx.session.id}: ${e.message}")
                    }
                }
            }
        }

        if (!anySuccess.get() && activeSessions.isNotEmpty()) {
            throw java.io.IOException("All active sessions failed to send")
        }
    }

    private suspend fun sendEncryptedData(data: ByteArray, targets: List<SessionContext> = activeSessions) {
        val encrypted = encryptionService.encrypt(data, ByteArray(0)) ?: return
        broadcastBytes(encrypted, targets)
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
            sendEncryptedData(message.toBytes(), transferTargets)

            val buffer = ByteArray(1024 * 1024)
            var offset = 0L
            inputStream.use { stream ->
                while (currentCoroutineContext().isActive) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    
                    if (transferTargets.all { ctx -> activeSessions.none { it.session === ctx.session } }) {
                        Log.w(TAG, "sendBlob aborted: All transfer targets disconnected")
                        break
                    }

                    val chunk = ChunkMessage(offset, buffer.copyOf(read), message.id)
                    sendEncryptedData(chunk.toBytes(), transferTargets)
                    offset += read
                }
            }
            // Send EOF
            val eof = ChunkMessage(offset, ByteArray(0), message.id)
            try {
                sendEncryptedData(eof.toBytes(), transferTargets)
            } catch (e: Exception) {
                // Ignore final EOF send failure if connections died at the very end
            }
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
        activeSessions.forEach { ctx ->
            ctx.supervisor.cancel()
            ctx.session.disconnect()
        }
        activeSessions.clear()
        _activeSessionsFlow.value = emptyList()
        _isTransferring.value = false
    }
}

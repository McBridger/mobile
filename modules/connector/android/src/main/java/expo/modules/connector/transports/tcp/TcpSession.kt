package expo.modules.connector.transports.tcp

import android.util.Log
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TcpSession(
        override val id: String,
        override val host: String,
        override val port: Int,
        private val socket: Socket,
        private val sessionScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : ITcpSession {

    companion object {
        private const val TAG = "TcpSession"
        const val FRAME_PING = 0
        const val FRAME_PONG = -1
        const val MAX_PAYLOAD_SIZE = 100 * 1024 * 1024 // 100MB as in existing project
    }

    private val _state = MutableStateFlow(SessionState.CONNECTED)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _incomingMessages =
            MutableSharedFlow<ByteArray>(
                    extraBufferCapacity = 64,
                    onBufferOverflow = BufferOverflow.SUSPEND
            )
    override val incomingMessages: SharedFlow<ByteArray> = _incomingMessages.asSharedFlow()

    private val writeMutex = Mutex()
    private val readChannel: ByteReadChannel = socket.openReadChannel()
    private val writeChannel: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)

    @Volatile private var isPingEnabled = false

    private val pendingPings = Channel<CompletableDeferred<Unit>>(Channel.UNLIMITED)

    init {
        sessionScope.launch { readLoop() }
        sessionScope.launch { pingLoop() }
    }

    private suspend fun readLoop() {
        try {
            while (sessionScope.isActive && !readChannel.isClosedForRead) {
                val length = readChannel.readInt()
                handleFrame(length)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Read loop disconnected: ${e.message}")
        } finally {
            terminate(SessionState.ERROR)
        }
    }

    private suspend fun handleFrame(length: Int) {
        if (length == FRAME_PING) return sendPong()

        if (length == FRAME_PONG) {
            pendingPings.tryReceive().getOrNull()?.complete(Unit)
            return
        }

        if (length < FRAME_PONG || length > MAX_PAYLOAD_SIZE) {
            throw IllegalArgumentException("Invalid frame size: $length")
        }

        val payload = ByteArray(length)
        readChannel.readFully(payload)
        _incomingMessages.emit(payload)
    }

    private suspend fun sendPong() {
        writeMutex.withLock {
            if (writeChannel.isClosedForWrite) return@withLock
            writeChannel.writeInt(FRAME_PONG)
            writeChannel.flush()
        }
    }

    private suspend fun pingLoop() {
        while (sessionScope.isActive) {
            delay(5000L)
            if (!isPingEnabled) continue
            if (_state.value != SessionState.CONNECTED) break

            forcePing()
        }
    }

    override suspend fun forcePing() {
        val deferred = CompletableDeferred<Unit>()
        pendingPings.trySend(deferred)

        try {
            writeMutex.withLock {
                if (writeChannel.isClosedForWrite) throw IllegalStateException("Channel closed")
                writeChannel.writeInt(FRAME_PING)
                writeChannel.flush()
            }

            withTimeout(2000L) { deferred.await() }
        } catch (e: Exception) {
            if (deferred.isCompleted) return
            Log.w(TAG, "forcePing FAILED -> Scuttling connection")
            terminate(SessionState.ERROR)
        }
    }

    override suspend fun send(data: ByteArray) {
        val length = data.size
        if (length > MAX_PAYLOAD_SIZE) {
            throw IllegalStateException("Payload size exceeds maximum allowed")
        }

        writeMutex.withLock {
            if (writeChannel.isClosedForWrite) throw IllegalStateException("Channel closed")
            writeChannel.writeInt(length)
            writeChannel.writeFully(data)
            writeChannel.flush()
        }
    }

    private fun terminate(finalState: SessionState) {
        if (_state.value == SessionState.DISCONNECTED || _state.value == SessionState.ERROR) return

        Log.i(TAG, "terminating session $id to $host with state $finalState")
        _state.value = finalState

        val err = Throwable("Session terminated")
        generateSequence { pendingPings.tryReceive().getOrNull() }.forEach {
            it.completeExceptionally(err)
        }

        sessionScope.cancel("Session terminated")

        try {
            writeChannel.close(Throwable("Session terminated"))
        } catch (_: Throwable) {}

        try {
            socket.close()
        } catch (_: Throwable) {}
    }

    override fun setPingEnabled(enabled: Boolean) {
        isPingEnabled = enabled
    }

    override fun disconnect() {
        terminate(SessionState.DISCONNECTED)
    }
}

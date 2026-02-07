package expo.modules.connector.transports.tcp

import android.util.Log
import okio.buffer
import okio.sink
import okio.source
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.*

/**
 * SRP: Minimalistic TCP Server that accepts on-demand connections for streaming.
 */
class TcpServerManager(
    private val port: Int,
    private val onIncomingStream: suspend (Socket) -> Unit,
    private val scope: CoroutineScope
) {
    private var serverSocket: ServerSocket? = null
    private var job: Job? = null

    fun start() {
        Log.i(TAG, "Starting on-demand TCP server on port $port")
        job = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    Log.d(TAG, "Connection accepted from ${socket.inetAddress.hostAddress}")
                    
                    // We handle each connection in a separate coroutine
                    // In our on-demand model, it's usually one at a time
                    launch { 
                        try {
                            onIncomingStream(socket)
                        } catch (e: Exception) {
                            Log.e(TAG, "Stream handling failed: ${e.message}")
                        } finally {
                            try { socket.close() } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) Log.e(TAG, "Server error: ${e.message}")
            }
        }
    }

    suspend fun connect(address: String, port: Int): Socket? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Connecting to $address:$port")
            Socket(address, port).apply {
                tcpNoDelay = true // Disable Nagle's algorithm for better latency
                soTimeout = 30000  // 30s timeout
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}")
            null
        }
    }

    fun stop() {
        job?.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        Log.i(TAG, "TCP Server stopped")
    }

    companion object {
        private const val TAG = "TcpServerManager"
    }
}
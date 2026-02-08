package expo.modules.connector.transports.tcp

import android.util.Log
import okio.buffer
import okio.source
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * SRP: TCP Server with Framing ([Length: 4b][Payload]).
 */
class TcpServerManager(
    private val port: Int,
    private val onMessage: suspend (ByteArray, String) -> Unit,
    private val scope: CoroutineScope
) {
    private var serverSocket: ServerSocket? = null
    private var job: Job? = null

    fun start() {
        Log.i(TAG, "Starting Framed TCP server on port $port")
        job = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    launch { handleClient(socket) }
                }
            } catch (e: Exception) {
                if (isActive) Log.e(TAG, "Server error: ${e.message}")
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        val address = socket.inetAddress.hostAddress ?: "unknown"
        Log.d(TAG, "New connection from $address")
        
        try {
            val input = DataInputStream(socket.getInputStream())
            while (currentCoroutineContext().isActive) {
                // 1. Read Length (Framing)
                val length = try { input.readInt() } catch (e: Exception) { break }
                if (length <= 0 || length > 100 * 1024 * 1024) {
                    Log.w(TAG, "Invalid frame length: $length from $address")
                    break
                }
                
                // 2. Read Payload
                val payload = ByteArray(length)
                input.readFully(payload)
                
                // 3. Dispatch
                onMessage(payload, address)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Client disconnected: $address (${e.message})")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    suspend fun connect(address: String, port: Int): Socket? = withContext(Dispatchers.IO) {
        try {
            Socket(address, port).apply {
                tcpNoDelay = true
                soTimeout = 30000
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed to $address:$port: ${e.message}")
            null
        }
    }

    suspend fun sendFrame(socket: Socket, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val output = DataOutputStream(socket.getOutputStream())
            output.writeInt(data.size)
            output.write(data)
            output.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Send frame failed: ${e.message}")
            false
        }
    }

    fun stop() {
        job?.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    companion object {
        private const val TAG = "TcpServerManager"
    }
}
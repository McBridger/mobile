package expo.modules.connector.transports.tcp

import android.util.Log
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json

/**
 * SRP: Manages the Ktor server instance and low-level routing.
 */
class TcpServerManager(
    private val port: Int,
    private val fileProvider: TcpFileProvider,
    private val onWebSocketMessage: suspend (ByteArray, String) -> Unit,
    private val onSessionEvent: (DefaultWebSocketServerSession?) -> Unit
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    fun start() {
        Log.i(TAG, "Starting server on port $port")
        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
            install(ContentNegotiation)

            routing {
                // File route
                get("/files/{id}/{name}") {
                    val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    Log.d(TAG, "Incoming file request for ID: $id")
                    
                    val metadata = fileProvider.getFile(id) ?: run {
                        Log.w(TAG, "File ID not found: $id")
                        return@get call.respond(HttpStatusCode.NotFound)
                    }
                    
                    try {
                        Log.d(TAG, "Opening stream for file: ${metadata.name} (${metadata.uri})")
                        val stream = fileProvider.openStream(metadata) ?: run {
                            Log.e(TAG, "Could not open stream for ${metadata.uri}")
                            return@get call.respond(HttpStatusCode.InternalServerError, "Could not open stream")
                        }
                        
                        call.respondOutputStream(ContentType.Application.OctetStream) {
                            val bytesCopied = stream.use { it.copyTo(this) }
                            Log.d(TAG, "Served $bytesCopied bytes for $id")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "File serve failed for $id: ${e.message}")
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }

                // Messaging route
                webSocket("/bridge") {
                    Log.i(TAG, "New WebSocket connection")
                    onSessionEvent(this)
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Binary) {
                                onWebSocketMessage(frame.readBytes(), call.request.local.remoteHost)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "WebSocket session error: ${e.message}")
                    } finally {
                        onSessionEvent(null)
                        Log.i(TAG, "WebSocket session closed")
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(500, 1000)
        server = null
    }

    companion object {
        private const val TAG = "TcpServerManager"
    }
}

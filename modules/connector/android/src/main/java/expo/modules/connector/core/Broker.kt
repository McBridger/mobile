package expo.modules.connector.core     
    
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import expo.modules.connector.interfaces.IBleTransport
import expo.modules.connector.models.Message
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object Broker {
    private const val TAG = "Broker"
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _state = MutableStateFlow(State.IDLE)
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<Message>()
    val messages = _messages.asSharedFlow()

    private lateinit var bleTransport: IBleTransport
    private lateinit var history: History
    private lateinit var appContext: Context
    
    fun init(context: Context): Broker {
        val applicationContext = context.applicationContext

        if (!::appContext.isInitialized) {
            this.appContext = applicationContext
            this.history = History.getInstance(this.appContext)
            return this
        }

        if (this.appContext != applicationContext) 
            throw IllegalStateException("Broker has already been initialized with a different ApplicationContext. Only one ApplicationContext is supported per process.")

        return this
    }

    @RequiresApi(Build.VERSION_CODES.HONEYCOMB)
    fun registerBle(bleTransport: IBleTransport): Broker {
        this.bleTransport = bleTransport

        scope.launch {
            bleTransport.incomingMessages.collect { msg -> onIncomingMessage(msg) }
            bleTransport.connectionState.collect { state -> onStateChange(state) }
        }

        return this
    }

    suspend fun connect(address: String) {
        bleTransport.connect(address)
    }

    suspend fun disconnect() {
        bleTransport.disconnect()
    }

    // Accessor for History to expose it to ConnectorModule
    fun getHistory(): History {
        if (!::history.isInitialized) throw IllegalStateException("Broker not initialized. Call init() first.")

        return history
    } 
    
    fun clipboardUpdate(content: String) {
        val message = Message(type = Message.Type.CLIPBOARD, value = content)
        history.add(message)

        scope.launch {
            bleTransport.send(message)
            _messages.emit(message)
        }
    } 
    
    @RequiresApi(Build.VERSION_CODES.HONEYCOMB)
    fun onIncomingMessage(message: Message) {
        updateSystemClipboard(message.value)
        history.add(message)
        scope.launch { _messages.emit(message) }
    }
    
    @RequiresApi(Build.VERSION_CODES.HONEYCOMB)
    private fun updateSystemClipboard(text: String) {
        try {
            val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Bridger Data", text)
            clipboard.setPrimaryClip(clip)
            Log.d(TAG, "System clipboard updated via Broker.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update system clipboard: ${e.message}")
        }
    }

    private fun onStateChange(state: IBleTransport.ConnectionState) {
        if (state == IBleTransport.ConnectionState.CONNECTED) {
            _state.value = State.CONNECTED
        }

        if (state == IBleTransport.ConnectionState.DISCONNECTED) {
            _state.value = State.IDLE
        }
    }

    enum class State {
        IDLE,
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        ERROR
    }
}


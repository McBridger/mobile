package expo.modules.connector.transports.ble
     
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import expo.modules.connector.interfaces.IBleTransport
import expo.modules.connector.models.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.util.UUID

@RequiresApi(Build.VERSION_CODES.ECLAIR)
class BleTransport(
    private val context: Context,
    serviceUuid: String,
    characteristicUuid: String
) : IBleTransport {

    private val bleManager = BleManager(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _connectionState = MutableStateFlow(IBleTransport.ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<IBleTransport.ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Message>()
    override val incomingMessages = _incomingMessages

    init {
        bleManager.setConfiguration(UUID.fromString(serviceUuid), UUID.fromString(characteristicUuid))
        setupCallbacks()
    }

    @RequiresApi(Build.VERSION_CODES.ECLAIR)
    private fun setupCallbacks() {
        bleManager.onDataReceived = onData@{ device, data ->
            val jsonString = data.getStringValue(0)
            if (jsonString == null) {
                Log.w(TAG, "Received data, but it could not be parsed as a String (JSON).")
                return@onData
            }

            val message = Message.fromJson(jsonString)?.copy(address = device.address)
            if (message == null) {
                Log.w(TAG, "Failed to parse incoming message JSON $jsonString")
                return@onData
            }
            
            scope.launch { _incomingMessages.emit(message) }
        }

        bleManager.connectionObserver = object : ConnectionObserver {
            override fun onDeviceConnecting(device: android.bluetooth.BluetoothDevice) {
                _connectionState.value = IBleTransport.ConnectionState.CONNECTING
            }

            override fun onDeviceConnected(device: android.bluetooth.BluetoothDevice) {
                _connectionState.value = IBleTransport.ConnectionState.CONNECTED
            }

            override fun onDeviceFailedToConnect(device: android.bluetooth.BluetoothDevice, reason: Int) {
                _connectionState.value = IBleTransport.ConnectionState.DISCONNECTED
            }

            override fun onDeviceReady(device: android.bluetooth.BluetoothDevice) { }
            override fun onDeviceDisconnecting(device: android.bluetooth.BluetoothDevice) { }
            override fun onDeviceDisconnected(device: android.bluetooth.BluetoothDevice, reason: Int) {
                _connectionState.value = IBleTransport.ConnectionState.DISCONNECTED
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    override suspend fun connect(address: String) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = IBleTransport.ConnectionState.POWERED_OFF
            return
        }
        try {
            bleManager
                .connect(adapter.getRemoteDevice(address))
                .retry(3, 100)
                .useAutoConnect(false)
                .timeout(10000)
                .enqueue()
        } catch (e: Exception) {
            _connectionState.value = IBleTransport.ConnectionState.DISCONNECTED
        }
    }

    override suspend fun disconnect() {
        bleManager.disconnect().enqueue()
    }

    override suspend fun send(message: Message): Boolean {
        if (_connectionState.value != IBleTransport.ConnectionState.CONNECTED) return false

        bleManager.performWrite(Data.from(message.toJson()))

        return true
    }

    companion object {
        private const val TAG = "BleTransport"
    }
}
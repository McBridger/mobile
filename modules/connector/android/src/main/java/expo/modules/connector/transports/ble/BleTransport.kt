package expo.modules.connector.transports.ble
     
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
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

class BleTransport(
    private val context: Context,
    serviceUuid: String,
    characteristicUuid: String,
    private val bleManager: BleManager = BleManager(context),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : IBleTransport {

    private val _connectionState = MutableStateFlow(IBleTransport.ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<IBleTransport.ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Message>()
    override val incomingMessages = _incomingMessages

    init {
        Log.d(TAG, "init: BleTransport initialized with serviceUuid: $serviceUuid, characteristicUuid: $characteristicUuid")
        bleManager.setConfiguration(UUID.fromString(serviceUuid), UUID.fromString(characteristicUuid))
        setupCallbacks()
    }

    private fun setupCallbacks() {
        Log.d(TAG, "setupCallbacks: Setting up data received and connection observers.")
        bleManager.onDataReceived = onData@{ device, data ->
            Log.d(TAG, "onDataReceived: Raw data received from ${device.address}")
            
            val jsonString = data.getStringValue(0)
            if (jsonString == null) {
                Log.w(TAG, "onDataReceived: Received data, but it could not be parsed as a String (JSON).")
                return@onData
            }
            
            Log.d(TAG, "onDataReceived: Raw JSON string: $jsonString")
            val message = Message.fromJson(jsonString, device.address)
            if (message == null) {
                Log.w(TAG, "onDataReceived: Failed to parse incoming message JSON $jsonString")
                return@onData
            }

            Log.i(TAG, "onDataReceived: Parsed message Type: ${message.getType()}, Value: ${message.value}, ID: ${message.id}")
            scope.launch { _incomingMessages.emit(message) }
        }

        bleManager.connectionObserver = object : ConnectionObserver {
            override fun onDeviceConnecting(device: android.bluetooth.BluetoothDevice) {
                Log.d(TAG, "onDeviceConnecting: ${device.address}")
                _connectionState.value = IBleTransport.ConnectionState.CONNECTING
            }

            override fun onDeviceConnected(device: android.bluetooth.BluetoothDevice) {
                Log.i(TAG, "onDeviceConnected: ${device.address}")
                _connectionState.value = IBleTransport.ConnectionState.CONNECTED
            }

            override fun onDeviceFailedToConnect(device: android.bluetooth.BluetoothDevice, reason: Int) {
                Log.e(TAG, "onDeviceFailedToConnect: ${device.address}, reason: $reason")
                _connectionState.value = IBleTransport.ConnectionState.DISCONNECTED
            }

            override fun onDeviceReady(device: android.bluetooth.BluetoothDevice) {
                Log.i(TAG, "onDeviceReady: ${device.address} - Device is ready for communication.")
                _connectionState.value = IBleTransport.ConnectionState.READY
            }
            override fun onDeviceDisconnecting(device: android.bluetooth.BluetoothDevice) {
                Log.d(TAG, "onDeviceDisconnecting: ${device.address}")
            }
            override fun onDeviceDisconnected(device: android.bluetooth.BluetoothDevice, reason: Int) {
                Log.i(TAG, "onDeviceDisconnected: ${device.address}, reason: $reason")
                _connectionState.value = IBleTransport.ConnectionState.DISCONNECTED
            }
        }
    }

    override suspend fun connect(address: String) {
        Log.d(TAG, "connect: Attempting to connect to $address")
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "connect: Bluetooth adapter is null or not enabled. Setting state to POWERED_OFF.")
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
            Log.d(TAG, "connect: Enqueued connection request for $address.")
        } catch (e: Exception) {
            Log.e(TAG, "connect: Exception during connection attempt to $address: ${e.message}")
            _connectionState.value = IBleTransport.ConnectionState.DISCONNECTED
        }
    }

    override suspend fun disconnect() {
        Log.d(TAG, "disconnect: Attempting to disconnect.")
        bleManager.disconnect().enqueue()
        Log.d(TAG, "disconnect: Enqueued disconnection request.")
    }

    override suspend fun send(message: Message): Boolean {
        Log.d(TAG, "send: Attempting to send message ID: ${message.id}, Type: ${message.getType()}, Value length: ${message.value.length}")
        if (_connectionState.value != IBleTransport.ConnectionState.READY) {
            Log.e(TAG, "send: Not ready, cannot send message ID: ${message.id}. Current state: ${_connectionState.value}")
            return false
        }

        val dataToSend = Data.from(message.toJson())
        Log.d(TAG, "send: Sending JSON: ${message.toJson()}")
        bleManager.performWrite(dataToSend)
        Log.i(TAG, "send: Message ID: ${message.id} sent successfully.")

        return true
    }

    companion object {
        private const val TAG = "BleTransport"
    }
}
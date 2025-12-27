package expo.modules.connector.transports.ble
     
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import expo.modules.connector.crypto.EncryptionService
import expo.modules.connector.interfaces.IBleTransport
import expo.modules.connector.interfaces.IBleManager
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

class BleTransport(
    private val context: Context,
    private val bleManager: IBleManager = BleManager(context),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : IBleTransport {

    private val _connectionState = MutableStateFlow(IBleTransport.ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<IBleTransport.ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Message>()
    override val incomingMessages = _incomingMessages

    init {
        Log.d(TAG, "init: BleTransport initializing.")
        
        val serviceUuid = EncryptionService.deriveUuid("McBridge_Service_UUID")
        val charUuid = EncryptionService.deriveUuid("McBridge_Characteristic_UUID")
        
        if (serviceUuid != null && charUuid != null) {
            Log.d(TAG, "init: Configured with Service: $serviceUuid, Char: $charUuid")
            bleManager.setConfiguration(serviceUuid, charUuid)
        } else {
            Log.e(TAG, "init: Failed to derive UUIDs. EncryptionService might not be ready.")
        }
        
        setupCallbacks()
    }

    private fun setupCallbacks() {
        Log.d(TAG, "setupCallbacks: Setting up data received and connection observers.")
        bleManager.onDataReceived = onData@{ device: BluetoothDevice, data: Data ->
            val rawBytes = data.value
            if (rawBytes == null) {
                Log.w(TAG, "onDataReceived: Received data, but it was empty.")
                return@onData
            }

            Log.d(TAG, "onDataReceived: Raw bytes received: ${rawBytes.size} bytes from ${device.address}")
            try {
                val message = Message.fromEncryptedData(rawBytes, device.address)
                Log.i(TAG, "onDataReceived: Successfully decrypted message Type: ${message.getType()}, ID: ${message.id}")
                scope.launch { _incomingMessages.emit(message) }
            } catch (e: Exception) {
                Log.e(TAG, "onDataReceived: Failed to decrypt or parse message: ${e.message}")
            }
        }

        bleManager.observer = object : ConnectionObserver {
            override fun onDeviceConnecting(device: BluetoothDevice) {
                Log.d(TAG, "onDeviceConnecting: ${device.address}")
                _connectionState.value = IBleTransport.ConnectionState.CONNECTING
            }

            override fun onDeviceConnected(device: BluetoothDevice) {
                Log.i(TAG, "onDeviceConnected: ${device.address}")
                _connectionState.value = IBleTransport.ConnectionState.CONNECTED
            }

            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                Log.e(TAG, "onDeviceFailedToConnect: ${device.address}, reason: $reason")
                _connectionState.value = IBleTransport.ConnectionState.DISCONNECTED
            }

            override fun onDeviceReady(device: BluetoothDevice) {
                Log.i(TAG, "onDeviceReady: ${device.address} - Device is ready for communication.")
                _connectionState.value = IBleTransport.ConnectionState.READY
            }

            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                Log.i(TAG, "onDeviceDisconnected: ${device.address}, reason: $reason")
                _connectionState.value = IBleTransport.ConnectionState.DISCONNECTED
            }
            
            override fun onDeviceDisconnecting(device: BluetoothDevice) {
                Log.d(TAG, "onDeviceDisconnecting: ${device.address}")
            }
        }
    }

    override suspend fun connect(address: String) {
        Log.d(TAG, "connect: Attempting to connect to $address")
        try {
            bleManager.connect(address)
            Log.d(TAG, "connect: Connection request initiated for $address.")
        } catch (e: Exception) {
            Log.e(TAG, "connect: Exception during connection attempt: ${e.message}")
            _connectionState.value = IBleTransport.ConnectionState.DISCONNECTED
        }
    }

    override suspend fun disconnect() {
        Log.d(TAG, "disconnect: Attempting to disconnect.")
        bleManager.disconnect()
    }

    override suspend fun send(message: Message): Boolean {
        Log.d(TAG, "send: Attempting to send message ID: ${message.id}")
        if (_connectionState.value != IBleTransport.ConnectionState.READY) {
            Log.e(TAG, "send: Not ready. Current state: ${_connectionState.value}")
            return false
        }

        val encryptedData = message.toEncryptedData()
        if (encryptedData == null) {
            Log.e(TAG, "send: Encryption failed for message ID: ${message.id}")
            return false
        }

        Log.d(TAG, "send: Sending encrypted data (${encryptedData.size} bytes)")
        bleManager.performWrite(Data(encryptedData))
        Log.i(TAG, "send: Message ID: ${message.id} sent successfully.")

        return true
    }

    companion object {
        private const val TAG = "BleTransport"
    }
}

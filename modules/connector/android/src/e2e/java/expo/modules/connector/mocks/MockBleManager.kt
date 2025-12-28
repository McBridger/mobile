package expo.modules.connector.mocks

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.util.Log
import expo.modules.connector.interfaces.IBleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.io.File
import java.util.UUID

class MockBleManager(private val context: Context) : IBleManager {
    private val TAG = "MockBleManager"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val bluetoothAdapter by lazy {
        context.getSystemService(BluetoothManager::class.java)?.adapter
    }
    
    override var onDataReceived: ((BluetoothDevice, Data) -> Unit)? = null
    override var observer: ConnectionObserver? = null

    override fun setConfiguration(serviceUuid: UUID, characteristicUuid: UUID) {
        Log.d(TAG, "Configured with Service: $serviceUuid")
    }

    override fun connect(address: String) {
        Log.d(TAG, "Connecting to $address...")
        
        scope.launch {
            try {
                // Пытаемся создать девайс, если адрес валидный, иначе - фейковый
                val device = if (BluetoothAdapter.checkBluetoothAddress(address)) {
                    bluetoothAdapter?.getRemoteDevice(address)
                } else {
                    Log.w(TAG, "Invalid BT address format: $address. Using fallback mock.")
                    bluetoothAdapter?.getRemoteDevice("00:11:22:33:44:55")
                }

                if (device == null) {
                    Log.e(TAG, "Could not obtain BluetoothDevice")
                    return@launch
                }
                
                Log.d(TAG, "Simulating: onDeviceConnecting")
                observer?.onDeviceConnecting(device)
                delay(300)
                
                Log.d(TAG, "Simulating: onDeviceConnected")
                observer?.onDeviceConnected(device)
                delay(300)
                
                Log.d(TAG, "Simulating: onDeviceReady")
                observer?.onDeviceReady(device)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect: ${e.message}")
            }
        }
    }

    override fun disconnect(force: Boolean) {
        Log.d(TAG, "Disconnected.")
    }

    override fun performWrite(data: Data) {
        val bytes = data.value ?: byteArrayOf()
        val hexString = bytes.joinToString("") { "%02x".format(it) }
        Log.i(TAG, "MOCK SEND (HEX): $hexString")
        
        scope.launch(Dispatchers.IO) {
            try {
                val outFile = File(context.externalCacheDir, "mcbridge_outbox.txt")
                outFile.appendText("$hexString\n")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write outbox: ${e.message}")
            }
        }
    }

    fun simulateIncomingData(address: String, hexData: String) {
        try {
            val device = if (BluetoothAdapter.checkBluetoothAddress(address)) {
                bluetoothAdapter?.getRemoteDevice(address)
            } else {
                bluetoothAdapter?.getRemoteDevice("00:11:22:33:44:55")
            }
            val bytes = hexData.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            onDataReceived?.invoke(device!!, Data(bytes))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to simulate incoming data: ${e.message}")
        }
    }

    private fun createMockDevice(address: String): BluetoothDevice {
        return bluetoothAdapter?.getRemoteDevice(if (BluetoothAdapter.checkBluetoothAddress(address)) address else "00:11:22:33:44:55")!!
    }
}

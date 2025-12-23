 package expo.modules.connector.transports.ble  
 
 import android.bluetooth.BluetoothDevice
 import android.bluetooth.BluetoothGatt
 import android.bluetooth.BluetoothGattCharacteristic 
 import android.content.Context 
 import android.util.Log 
 import expo.modules.connector.models.Message 
 import no.nordicsemi.android.ble.BleManager as NordicBleManager 
 import no.nordicsemi.android.ble.data.Data

 import java.util.UUID
 
class BleManager(context: Context) : NordicBleManager(context) {

    private var characteristic: BluetoothGattCharacteristic? = null
    private var serviceUuid: UUID? = null
    private var characteristicUuid: UUID? = null
    
    // Callback function instead of Interface
    var onDataReceived: ((BluetoothDevice, Data) -> Unit)? = null

    fun setConfiguration(serviceUuid: UUID, characteristicUuid: UUID) {
        this.serviceUuid = serviceUuid
        this.characteristicUuid = characteristicUuid
    }

    fun performWrite(data: Data) {
        characteristic?.let { char ->
            writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                .fail { _, status ->
                    log(Log.ERROR, "Failed to send data with status: $status") 
                }
                .enqueue()
        } ?: log(Log.WARN, "Characteristic is not initialized.")
    }

    override fun log(priority: Int, message: String) {
        Log.println(priority, TAG, message)
    }

    override fun initialize() {
        setNotificationCallback(characteristic).with { device, data ->
            onDataReceived?.invoke(device, data)
        }

        beginAtomicRequestQueue()
            .add(requestMtu(512).fail { _, status -> log(Log.ERROR, "Could not request MTU: $status") })
            .add(enableNotifications(characteristic).fail { _, status -> log(Log.ERROR, "Could not enable  notifications: $status") })
            .enqueue()
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        if (serviceUuid == null || characteristicUuid == null) {
            log(Log.ERROR, "UUIDs not configured!")
            return false
        }

        val service = gatt.getService(serviceUuid)
        if (service == null) {
            log(Log.ERROR, "ERROR: Service with UUID $serviceUuid was not found!")
            return false
        }

        characteristic = service.getCharacteristic(characteristicUuid)
        if (characteristic == null) {
            log(Log.ERROR, "ERROR: Characteristic with UUID $characteristicUuid was not found!")
        }

        return characteristic != null
    }

    override fun onServicesInvalidated() {
        characteristic = null
    }

    companion object {
        private const val TAG = "BleManager"
    }
}
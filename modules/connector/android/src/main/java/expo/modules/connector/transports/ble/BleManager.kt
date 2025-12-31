package expo.modules.connector.transports.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import expo.modules.connector.interfaces.IBleManager
import no.nordicsemi.android.ble.BleManager as NordicBleManager
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.util.UUID

class BleManager(private val context: Context) : NordicBleManager(context), IBleManager {

    private var characteristic: BluetoothGattCharacteristic? = null
    private var serviceUuid: UUID? = null
    private var characteristicUuid: UUID? = null

    override var onDataReceived: ((BluetoothDevice, Data) -> Unit)? = null
    
    override var observer: ConnectionObserver? = null
        set(value) {
            field = value
            setConnectionObserver(value)
        }

    override fun setConfiguration(serviceUuid: UUID, characteristicUuid: UUID) {
        this.serviceUuid = serviceUuid
        this.characteristicUuid = characteristicUuid
    }

    override fun connect(address: String) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        val device = adapter?.getRemoteDevice(address)

        if (device == null) {
            Log.e(TAG, "connect: Could not find device with address $address")
            return
        }

        connect(device)
            .retry(3, 100)
            .useAutoConnect(false)
            .timeout(10000)
            .enqueue()
    }

    override fun disconnect(force: Boolean) {
        super.disconnect().enqueue()
    }

    override fun performWrite(data: Data) {
        characteristic?.let { char ->
            writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                .fail { _, status ->
                    Log.e(TAG, "Failed to send data with status: $status")
                }
                .enqueue()
        } ?: Log.w(TAG, "Characteristic is not initialized.")
    }

    override fun log(priority: Int, message: String) {
        Log.println(priority, TAG, message)
    }

    override fun initialize() {
        setNotificationCallback(characteristic).with { device, data ->
            onDataReceived?.invoke(device, data)
        }

        beginAtomicRequestQueue()
            .add(requestMtu(512).fail { _, status -> Log.e(TAG, "Could not request MTU: $status") })
            .add(enableNotifications(characteristic).fail { _, status -> Log.e(TAG, "Could not enable notifications: $status") })
            .enqueue()
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        val sUuid = serviceUuid ?: return false
        val cUuid = characteristicUuid ?: return false

        val service = gatt.getService(sUuid) ?: return false
        characteristic = service.getCharacteristic(cUuid)
        
        return characteristic != null
    }

    override fun onServicesInvalidated() {
        characteristic = null
    }

    companion object {
        private const val TAG = "BleManager"
    }
}

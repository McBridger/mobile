package expo.modules.connector.interfaces

import android.bluetooth.BluetoothDevice
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.util.UUID

interface IBleManager {
    var onDataReceived: ((BluetoothDevice, Data) -> Unit)?
    var observer: ConnectionObserver?
    
    fun setConfiguration(serviceUuid: UUID, characteristicUuid: UUID)
    fun connect(address: String)
    fun disconnect(force: Boolean = false)
    fun performWrite(data: Data)
}

package expo.modules.connector.mocks

import expo.modules.connector.interfaces.IBleScanner
import expo.modules.connector.interfaces.ScanConfig
import expo.modules.connector.models.BleDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

class MockBleScanner : IBleScanner {
    private val _scanResults = MutableSharedFlow<BleDevice>()
    
    override fun scan(serviceUuid: UUID, config: ScanConfig): Flow<BleDevice> {
        return _scanResults.asSharedFlow()
    }

    suspend fun simulateDeviceFound(address: String, name: String, rssi: Int = -60) {
        _scanResults.emit(BleDevice(name, address, rssi))
    }
}

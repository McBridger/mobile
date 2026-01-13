package expo.modules.connector.interfaces

import expo.modules.connector.models.BleDevice
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface IBleScanner {
    fun scan(serviceUuid: UUID, config: ScanConfig): Flow<BleDevice>
}
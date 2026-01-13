package expo.modules.connector.transports.ble

import android.os.ParcelUuid
import android.util.Log
import expo.modules.connector.interfaces.IBleScanner
import expo.modules.connector.interfaces.ScanConfig
import expo.modules.connector.models.BleDevice
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import no.nordicsemi.android.support.v18.scanner.*
import java.util.UUID

class BleScanner : IBleScanner {
    private val scanner = BluetoothLeScannerCompat.getScanner()

    override fun scan(serviceUuid: UUID, config: ScanConfig): Flow<BleDevice> = callbackFlow {
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // Keep this at verbose, we only care during heavy debugging
                Log.v(TAG, "Result: ${result.device.address} (RSSI: ${result.rssi})")
                val sendResult = trySend(result)
                if (sendResult.isFailure && sendResult.isClosed) {
                    close(sendResult.exceptionOrNull())
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Native scan failed: $errorCode")
                close(Exception("Scan failed: $errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(config.androidMode)
            .setReportDelay(0)
            .build()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid))
            .build()

        Log.d(TAG, "Starting native scan (Mode: ${config.androidMode})")
        scanner.startScan(mutableListOf(filter), settings, callback)

        awaitClose {
            Log.d(TAG, "Stopping native scan")
            scanner.stopScan(callback)
        }
    }.buffer(500) // Buffer results for slow JS consumption
    .map { result ->
        // Mapping from Android/Nordic ScanResult to our domain BleDevice
        BleDevice(
            name = result.device.name ?: result.scanRecord?.deviceName,
            address = result.device.address,
            rssi = result.rssi
        )
    }

    companion object {
        private const val TAG = "BleScanner"
    }
}
package expo.modules.connector.transports.ble

import android.os.ParcelUuid
import android.util.Log
import expo.modules.connector.interfaces.IBleScanner
import expo.modules.connector.models.BleDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import no.nordicsemi.android.support.v18.scanner.*
import java.util.UUID

class BleScanner : IBleScanner {
    private val scanner = BluetoothLeScannerCompat.getScanner()

    override fun scan(serviceUuid: UUID): Flow<BleDevice> = callbackFlow {
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "onScanFailed: Error code $errorCode")
                close(Exception("Scan failed with code $errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid))
            .build()

        Log.d(TAG, "scan: Starting BLE scan for service $serviceUuid")
        scanner.startScan(mutableListOf(filter), settings, callback)

        awaitClose {
            Log.d(TAG, "scan: Stopping BLE scan")
            scanner.stopScan(callback)
        }
    }.map { result ->
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
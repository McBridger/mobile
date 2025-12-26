package expo.modules.connector.transports.ble

import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import no.nordicsemi.android.support.v18.scanner.*
import java.util.UUID

class BleScanner {
    private val scanner = BluetoothLeScannerCompat.getScanner()

    /**
     * Converts the imperative Bluetooth LE scanner API into a reactive Flow.
     * Automatically manages the scanning lifecycle.
     */
    fun scan(serviceUuid: UUID): Flow<ScanResult> = callbackFlow {
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "onScanFailed: Scan failed with error code $errorCode")
                close(Exception("Scan failed with code $errorCode"))
            }
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUuid))
                .build()
        )
        
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        Log.d(TAG, "Starting reactive scan for service: $serviceUuid")
        try {
            scanner.startScan(filters, settings, callback)
        } catch (e: Exception) {
            close(e)
        }

        // Clean up: when the Flow is cancelled or the scope is closed,
        // stop the hardware scanner automatically.
        awaitClose {
            Log.d(TAG, "Stopping reactive scan (scope closed)")
            scanner.stopScan(callback)
        }
    }

    companion object {
        private const val TAG = "BleScanner"
    }
}
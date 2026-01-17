package expo.modules.connector.transports.ble

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import expo.modules.connector.interfaces.IBleScanner
import expo.modules.connector.interfaces.ScanConfig
import expo.modules.connector.models.BleDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import no.nordicsemi.android.support.v18.scanner.*
import java.util.UUID

// Use Compat scanner to handle vendor-specific bugs (Samsung, Huawei, etc.)
class BleScanner(private val context: Context) : IBleScanner {
    
    private val scanner = BluetoothLeScannerCompat.getScanner()

    @SuppressLint("MissingPermission") // Permissions are checked at a higher level
    override fun scan(serviceUuid: UUID, config: ScanConfig): Flow<BleDevice> = callbackFlow {
        val scanTag = "BleScannerSdk"
        
        // 1. Constant Action. The system needs a consistent Intent Action to match the PendingIntent.
        // Add packageName to avoid collisions with other apps.
        val scanAction = "${context.packageName}.BLE_SCAN_RESULT"
        
        var isReceiverRegistered = false

        // 2. BroadcastReceiver. Lives within the flow scope.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != scanAction) return

                val errorCode = intent.getIntExtra(BluetoothLeScannerCompat.EXTRA_ERROR_CODE, -1)
                if (errorCode != -1) {
                    Log.e(scanTag, "System scan failed with error code: $errorCode")
                    return
                }

                try {
                    // Nordic Library helper to extract results
                    val results = intent.getParcelableArrayListExtra<ScanResult>(BluetoothLeScannerCompat.EXTRA_LIST_SCAN_RESULT)
                    
                    results?.forEach { result ->
                        val device = BleDevice(
                            name = result.device.name ?: result.scanRecord?.deviceName,
                            address = result.device.address,
                            rssi = result.rssi
                        )
                        // trySend is thread-safe for callbackFlow
                        trySend(device)
                    }
                } catch (e: Exception) {
                    Log.e(scanTag, "Error parsing scan result: ${e.message}")
                }
            }
        }

        // 3. Register Receiver. Handling Android 13/14 (Tiramisu+)
        try {
            val filter = IntentFilter(scanAction)
            
            // ContextCompat handles EXPORTED/NOT_EXPORTED flags automatically for different SDK versions.
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.e(scanTag, "Failed to register receiver", e)
            close(e)
            return@callbackFlow
        }

        // 4. Create PendingIntent
        val scanIntent = Intent(scanAction).apply {
            setPackage(context.packageName) // Security: restrict to our package
        }

        // Flags: 
        // MUTABLE - required for BLE scanner (system must fill extras)
        // UPDATE_CURRENT - update data if intent exists
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SCAN,
            scanIntent,
            flags
        )

        // 5. Scan Settings
        val settings = ScanSettings.Builder()
            .setScanMode(config.androidMode)
            .setUseHardwareBatchingIfSupported(true)
            .setReportDelay(0) // 0 = instant, >0 = batching (saves battery)
            .build()

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(android.os.ParcelUuid(serviceUuid))
                .build()
        )

        Log.i(scanTag, "Starting scan for $serviceUuid with PendingIntent")
        
        try {
            scanner.startScan(filters, settings, context, pendingIntent, REQUEST_CODE_SCAN)
        } catch (e: Exception) {
            Log.e(scanTag, "Failed to start native scan", e)
            // Cleanup immediately
            if (isReceiverRegistered) {
                try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            }
            close(e)
            return@callbackFlow
        }

        // 6. Cleanup
        awaitClose {
            Log.d(scanTag, "Flow closed. Stopping scan.")
            try {
                scanner.stopScan(context, pendingIntent, REQUEST_CODE_SCAN)
                
                // Cancel PendingIntent so system stops waking us
                pendingIntent.cancel()
                
                if (isReceiverRegistered) {
                    context.unregisterReceiver(receiver)
                }
            } catch (e: Exception) {
                Log.w(scanTag, "Error during cleanup: ${e.message}")
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_SCAN = 42
    }
}
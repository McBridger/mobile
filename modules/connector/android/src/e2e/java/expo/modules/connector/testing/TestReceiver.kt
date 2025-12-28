package expo.modules.connector.testing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import expo.modules.connector.mocks.MockBleManager
import expo.modules.connector.mocks.MockBleScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * ADB Backdoor for Maestro E2E testing.
 *
 * Usage:
 * 1. Simulate finding a device:
 *    adb shell am broadcast -a expo.modules.connector.SCAN_DEVICE --es address "00:11:22:33:44:55" --es name "Maestro Mac"
 *
 * 2. Simulate incoming encrypted data:
 *    adb shell am broadcast -a expo.modules.connector.RECEIVE_DATA --es data "HEX_STRING"
 */
class TestReceiver : BroadcastReceiver(), KoinComponent {
    private val TAG = "TestReceiver"
    private val scanner: MockBleScanner by inject()
    private val manager: MockBleManager by inject()
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "expo.modules.connector.SCAN_DEVICE" -> {
                val address = intent.getStringExtra("address") ?: "00:11:22:33:44:55"
                val name = intent.getStringExtra("name") ?: "Mock Device"
                Log.i(TAG, "Simulating scan result: $name ($address)")
                scope.launch {
                    scanner.simulateDeviceFound(address, name)
                }
            }
            "expo.modules.connector.RECEIVE_DATA" -> {
                val data = intent.getStringExtra("data") ?: ""
                val address = intent.getStringExtra("address") ?: "00:11:22:33:44:55"
                
                // Validate HEX data
                if (data.length % 2 != 0 || !data.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                    Log.e(TAG, "Invalid HEX data received: $data")
                    return
                }

                Log.i(TAG, "Simulating incoming data from $address")
                manager.simulateIncomingData(address, data)
            }
        }
    }
}

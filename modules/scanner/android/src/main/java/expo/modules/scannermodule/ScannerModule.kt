package expo.modules.scannermodule

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import java.util.ArrayList
import java.util.HashMap

class ScannerModule : Module() {

    private inner class ScanSession {
        val callback: ScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val deviceMap = Bundle().apply {
                    putString("name", result.device.name)
                    putString("address", result.device.address)
                    putInt("rssi", result.rssi)
                    putSerializable("services", getServiceUuids(result))
                }
                sendEvent("onDeviceFound", deviceMap)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error code: $errorCode")
                stopScanInternal()

                val errorMap = Bundle().apply {
                    putInt("code", errorCode)
                    putString("message", "Native scan failed with code: $errorCode")
                }
                sendEvent("onScanFailed", errorMap)
            }

            private fun getServiceUuids(result: ScanResult): ArrayList<String> {
                val serviceUuidsArray = ArrayList<String>()
                val parcelUuids: List<ParcelUuid>? = result.scanRecord?.serviceUuids
                parcelUuids?.forEach { uuid ->
                    serviceUuidsArray.add(uuid.uuid.toString())
                }
                return serviceUuidsArray
            }
        }
    }

    private enum class Error(val code: Int) {
        SCAN_IN_PROGRESS(101),
        BLUETOOTH_UNAVAILABLE(102),
        BLUETOOTH_DISABLED(103),
        START_SCAN_FAILED(104);
    }

    private val TAG = "ScannerModule"
    private val SCAN_TIMEOUT_MS = 10000L

    private var scanner: BluetoothLeScannerCompat? = null
    private var scanSettings: ScanSettings? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var currentScanSession: ScanSession? = null

    override fun definition() = ModuleDefinition {
        Name("ScannerModule")

        Constants {
            val errorConstants = HashMap<String, Any>().apply {
                Error.entries.forEach { error ->
                    put(error.name, error.code)
                }
            }
            mapOf("ERRORS" to errorConstants)
        }

        Events("onDeviceFound", "onScanStopped", "onScanFailed")

        AsyncFunction("startScan") { promise: Promise ->
            if (currentScanSession != null) {
                reject(promise, Error.SCAN_IN_PROGRESS, null)
                return@AsyncFunction
            }
            if (bluetoothAdapter == null) {
                reject(promise, Error.BLUETOOTH_UNAVAILABLE, null)
                return@AsyncFunction
            }
            if (bluetoothAdapter?.isEnabled == false) {
                reject(promise, Error.BLUETOOTH_DISABLED, null)
                return@AsyncFunction
            }

            try {
                currentScanSession = ScanSession()
                val filters: List<ScanFilter> = emptyList()
                scanner?.startScan(filters, scanSettings, currentScanSession!!.callback)
                timeoutHandler.postDelayed({ stopScanInternal() }, SCAN_TIMEOUT_MS)
                promise.resolve(null)
                Log.d(TAG, "Scan started.")
            } catch (e: Exception) {
                currentScanSession = null
                reject(promise, Error.START_SCAN_FAILED, e)
            }
        }

        AsyncFunction("stopScan") { promise: Promise ->
            stopScanInternal()
            promise.resolve(null)
        }

        OnCreate {
            scanner = BluetoothLeScannerCompat.getScanner()
            bluetoothAdapter = initializeBluetoothAdapter(appContext.reactContext!!)
            scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build()
        }
    }

    private fun stopScanInternal() {
        if (currentScanSession == null) return

        timeoutHandler.removeCallbacksAndMessages(null)
        try {
            scanner?.stopScan(currentScanSession!!.callback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan: ${e.message}")
        }

        currentScanSession = null
        sendEvent("onScanStopped", Bundle())
        Log.d(TAG, "Scan stopped.")
    }

    private fun reject(promise: Promise, error: Error, throwable: Throwable?) {
        val debugMessage = throwable?.message ?: error.name

        val errorMap = Bundle().apply {
            putInt("code", error.code)
            putString("message", debugMessage)
        }
        sendEvent("onScanFailed", errorMap)

        promise.reject(error.code.toString(), debugMessage, throwable)
    }

    private fun initializeBluetoothAdapter(context: Context): BluetoothAdapter? {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Device does not support Bluetooth Low Energy.")
            return null
        }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }
}

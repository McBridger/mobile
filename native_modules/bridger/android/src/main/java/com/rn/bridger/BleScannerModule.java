package com.rn.bridger;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.os.ParcelUuid;
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

public class BleScannerModule extends NativeBleScannerSpec {

    public static final String NAME = "BleScanner";
    private static final String TAG = "BleScannerModule";
    private static final long SCAN_TIMEOUT_MS = 10000;

    private final BluetoothLeScannerCompat scanner;
    private final ScanSettings scanSettings;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private final BluetoothAdapter bluetoothAdapter;

    @Nullable // A single object to manage all scanning state. If null, we are not scanning.
    private ScanSession currentScanSession;

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

    public BleScannerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.scanner = BluetoothLeScannerCompat.getScanner();
        this.bluetoothAdapter = initializeBluetoothAdapter(reactContext);
        this.scanSettings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build();
    }

    @Override
    public void startScan(Promise promise) {
        // --- Guard Clauses: Fail-fast and flat ---
        if (currentScanSession != null) {
            reject(promise, Error.SCAN_IN_PROGRESS);
            return;
        }
        if (bluetoothAdapter == null) {
            reject(promise, Error.BLUETOOTH_UNAVAILABLE);
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            reject(promise, Error.BLUETOOTH_DISABLED);
            return;
        }

        // --- Happy Path ---
        try {
            currentScanSession = new ScanSession();
            // Using an empty filter list to scan for all devices.
            // For production, you could add ScanFilters here to be more efficient.
            List<ScanFilter> filters = Collections.emptyList();
            scanner.startScan(filters, scanSettings, currentScanSession.callback);
            timeoutHandler.postDelayed(this::stopScanInternal, SCAN_TIMEOUT_MS);
            promise.resolve(null); // Resolve promise immediately on successful start
            Log.d(TAG, "Scan started.");
        } catch (Exception e) {
            currentScanSession = null; // Clean up session object on failure
            reject(promise, Error.START_SCAN_FAILED, e);
        }
    }

    @Override
    public void stopScan(Promise promise) {
        stopScanInternal();
        promise.resolve(null);
    }

    @Override
    public Map<String, Object> getTypedExportedConstants() {
        final Map<String, Object> constants = new HashMap<>();
        final Map<String, Object> errorConstants = new HashMap<>();
        for (Error error : Error.values()) {
            errorConstants.put(error.toString(), error.code);
        }
        constants.put("ERRORS", errorConstants);
        return constants;
    }

    private void stopScanInternal() {
        if (currentScanSession == null) return;

        timeoutHandler.removeCallbacksAndMessages(null);
        try {
            scanner.stopScan(currentScanSession.callback);
        } catch (Exception e) {
            // Can happen if BT is turned off during a scan. Safe to ignore.
            Log.e(TAG, "Error stopping scan: " + e.getMessage());
        }

        currentScanSession = null;
        emitOnScanStopped();
        Log.d(TAG, "Scan stopped.");
    }

    /** A single, unified place for rejecting promises. */
    private void reject(Promise promise, Error error, @Nullable Throwable throwable) {
        // The enum's `toString()` name is the debug message. Direct and honest.
        String debugMessage = (throwable != null && throwable.getMessage() != null)
            ? throwable.getMessage()
            : error.toString();

        WritableMap errorMap = Arguments.createMap();
        errorMap.putInt("code", error.code);
        errorMap.putString("message", debugMessage);
        emitOnScanFailed(errorMap);
        promise.reject(error.toString(), debugMessage, throwable);
    }

    private void reject(Promise promise, Error error) { reject(promise, error, null); }

    /** Encapsulates the disposable state of an active scan session. */
    private class ScanSession {
        final ScanCallback callback;

        ScanSession() {
            this.callback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, @NonNull ScanResult result) {
                    WritableMap deviceMap = Arguments.createMap();
                    deviceMap.putString("name", result.getDevice().getName());
                    deviceMap.putString("address", result.getDevice().getAddress());
                    deviceMap.putInt("rssi", result.getRssi());
                    deviceMap.putArray("services", getServiceUuids(result));
                    emitOnDeviceFound(deviceMap);
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.e(TAG, "Scan failed during operation with error code: " + errorCode);
                    stopScanInternal(); // Stop everything on failure

                    WritableMap errorMap = Arguments.createMap();
                    errorMap.putInt("code", errorCode);
                    // Provide a generic message for these native-level failures
                    errorMap.putString("message", "Native scan failed with code: " + errorCode);
                    emitOnScanFailed(errorMap);
                }

                private ReadableArray getServiceUuids(ScanResult result) {
                    WritableArray serviceUuidsArray = Arguments.createArray();
                    var record = result.getScanRecord();
                    if (record == null) return serviceUuidsArray;

                    List<ParcelUuid> parcelUuids = record.getServiceUuids();
                    if (parcelUuids == null) return serviceUuidsArray;

                    for (ParcelUuid uuid : parcelUuids) {
                        serviceUuidsArray.pushString(uuid.getUuid().toString());
                    }

                    return serviceUuidsArray;
                }
            };
        }
    }

    /** Initializes the Bluetooth adapter, checking for hardware support. */
    @Nullable
    private BluetoothAdapter initializeBluetoothAdapter(ReactApplicationContext context) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Device does not support Bluetooth Low Energy.");
            return null;
        }
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return (manager != null) ? manager.getAdapter() : null;
    }

    /** Defines structured error types for consistency. This is the single source of truth for codes. */
    private enum Error {
        SCAN_IN_PROGRESS(101),
        BLUETOOTH_UNAVAILABLE(102),
        BLUETOOTH_DISABLED(103),
        START_SCAN_FAILED(104);

        final int code;
        Error(int code) { this.code = code; }
    }
}
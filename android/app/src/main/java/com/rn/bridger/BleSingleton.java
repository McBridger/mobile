package com.rn.bridger;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.data.Data;
import no.nordicsemi.android.ble.observer.ConnectionObserver;

import java.lang.ref.WeakReference; // <-- The key import
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class BleSingleton {
    private static final String TAG = "BleSingleton";
    private static final String PREFS_NAME = "BLE_APP_PREFS";
    private static final String LAST_MESSAGE_KEY = "LAST_RECEIVED_MESSAGE";

    private static volatile BleSingleton instance; // Use volatile for thread safety
    private final Context context;
    private final BridgerBleManager bleManager;

    // --- Configuration Variables ---
    private UUID bridgerServiceUuid;
    private UUID writeCharacteristicUuid;
    private UUID notifyCharacteristicUuid;
    private final List<WeakReference<BleConnectionListener>> connectionListeners = new CopyOnWriteArrayList<>();
    private final List<WeakReference<BleDataListener>> dataListeners = new CopyOnWriteArrayList<>();


    // Define a callback interface for connection events
    public interface BleConnectionListener {
        void onDeviceConnected();
        void onDeviceDisconnected();
        void onDeviceFailedToConnect(String deviceAddress, String reason);
    }

    // Define a callback interface for incoming data (for the Service)
    public interface BleDataListener {
        void onDataReceived(String data);
    }

    private BleSingleton(Context context) {
        this.context = context.getApplicationContext();
        this.bleManager = new BridgerBleManager(this.context);
    }

    public static synchronized BleSingleton getInstance(Context context) {
        if (instance == null)
            instance = new BleSingleton(context);

        return instance;
    }

    // --- LISTENER API  ---

    public void addConnectionListener(BleConnectionListener listener) {
        if (listener == null) return;
        // Prevent adding duplicates
        for (WeakReference<BleConnectionListener> ref : connectionListeners) {
            if (listener.equals(ref.get())) return;
        }
        // Add the listener wrapped in a WeakReference
        this.connectionListeners.add(new WeakReference<>(listener));
    }

    public void removeConnectionListener(BleConnectionListener listener) {
        if (listener == null) return;
        for (WeakReference<BleConnectionListener> ref : connectionListeners) {
            // If the reference points to our listener, remove it
            if (listener.equals(ref.get())) {
                connectionListeners.remove(ref);
                return;
            }
        }
    }

    public void addDataListener(BleDataListener listener) {
        if (listener == null) return;
        // Prevent adding duplicates
        for (WeakReference<BleDataListener> ref : dataListeners) {
            if (listener.equals(ref.get())) return;
        }
        // Add the listener wrapped in a WeakReference
        this.dataListeners.add(new WeakReference<>(listener));
    }

    public void removeDataListener(BleDataListener listener) {
        if (listener == null) return;
        for (WeakReference<BleDataListener> ref : dataListeners) {
            // If the reference points to our listener, remove it
            if (listener.equals(ref.get())) {
                dataListeners.remove(ref);
                return;
            }
        }
    }

    /**
     * Configures the Singleton with the necessary BLE UUIDs.
     * This MUST be called before attempting to connect.
     */
    public void setup(String serviceUuid, String writeUuid, String notifyUuid) throws ConnectionActiveException {
        if (bleManager.isConnected())
            throw new ConnectionActiveException("Cannot set UUIDs while a device is connected. Please disconnect first.");

        try {
            this.bridgerServiceUuid = UUID.fromString(serviceUuid);
            this.writeCharacteristicUuid = UUID.fromString(writeUuid);
            this.notifyCharacteristicUuid = UUID.fromString(notifyUuid);
            Log.d(TAG, "BleSingleton configured with UUIDs.");
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid UUID format provided during setup.", e);
        }
    }

    // --- PUBLIC BLE ACTIONS ---

    /**
     * Initiates a connection to a BLE device.
     * @param address The MAC address of the device.
     * @throws NotConfiguredException if the setup() method has not been called.
     * @throws BluetoothUnavailableException if Bluetooth is off or unavailable.
     * @throws ConnectionActiveException if already connected or connecting.
     */
    public void connect(String address) throws NotConfiguredException, BluetoothUnavailableException, ConnectionActiveException {
        // 1. Check if configured
        if (this.bridgerServiceUuid == null)
            throw new NotConfiguredException("BleSingleton has not been configured with UUIDs. Call setup() first.");

        // 2. Check if already connected
        if (bleManager.isConnected())
            throw new ConnectionActiveException("A device is already connected.");

        // 3. Check Bluetooth state
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = (bluetoothManager != null) ? bluetoothManager.getAdapter() : null;

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled())
            throw new BluetoothUnavailableException("Bluetooth is not available or is disabled.");


        // If all checks pass, proceed with the asynchronous connection attempt
        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        bleManager.connect(device)
            .retry(3, 100)
            .useAutoConnect(false)
            .timeout(20000)
            .enqueue();
    }

    public void disconnect() {
        if (!bleManager.isConnected()) return;

        bleManager.disconnect().enqueue();
    }

    /**
     * Sends data to the connected device.
     * @param data The string data to send.
     * @throws NotConnectedException if no device is currently connected.
     */
    public void send(String data) throws NotConnectedException {
        if (!isConnected())
            throw new NotConnectedException("Cannot send data, no device is connected.");

        bleManager.performWrite(Data.from(data));
    }

    public boolean isConnected() {
        return bleManager.isConnected();
    }


    // --- SharedPreferences Logic ---

    private void saveLastReceivedMessage(String message) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(LAST_MESSAGE_KEY, message);
        editor.apply();
        Log.d(TAG, "Saved to SharedPreferences: " + message);
    }

    public String getLastReceivedMessage() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(LAST_MESSAGE_KEY, null);
    }

    // --- CONNECTION OBSERVER (MODIFIED to notify ALL listeners) ---

    private final ConnectionObserver connectionObserver = new ConnectionObserver() {
        @Override
        public void onDeviceFailedToConnect(@NonNull BluetoothDevice device, int reason) {
            for (WeakReference<BleConnectionListener> ref : connectionListeners) {
                Optional.ofNullable(ref.get())
                    .ifPresentOrElse(
                        l -> l.onDeviceFailedToConnect(device.getAddress(), "Native reason code: " + reason),
                        () -> connectionListeners.remove(ref)
                    );
            }
        }

        @Override
        public void onDeviceReady(@NonNull BluetoothDevice device) {
            for (WeakReference<BleConnectionListener> ref : connectionListeners) {
                Optional.ofNullable(ref.get())
                    .ifPresentOrElse(
                        BleConnectionListener::onDeviceConnected,
                        () -> connectionListeners.remove(ref)
                    );
            }
        }

        @Override
        public void onDeviceDisconnected(@NonNull BluetoothDevice device, int reason) {
            for (WeakReference<BleConnectionListener> ref : connectionListeners) {
                Optional.ofNullable(ref.get())
                    .ifPresentOrElse(
                        BleConnectionListener::onDeviceDisconnected,
                        () -> connectionListeners.remove(ref)
                    );
            }
        }

        @Override
        public void onDeviceConnecting(@NonNull BluetoothDevice device) {
            // <-- DEBUG LOG 3: Add logs to ALL observer methods
            Log.i(TAG, "ConnectionObserver: onDeviceConnecting to " + device.getAddress());
        }

        @Override
        public void onDeviceConnected(@NonNull BluetoothDevice device) {
            Log.i(TAG, "ConnectionObserver: onDeviceConnected to " + device.getAddress());
        }
        
        // ... onDeviceReady() is already implemented ...

        @Override
        public void onDeviceDisconnecting(@NonNull BluetoothDevice device) {
            Log.i(TAG, "ConnectionObserver: onDeviceDisconnecting from " + device.getAddress());
        }
        
        // ... onDeviceDisconnected() and onDeviceFailedToConnect() are already implemented ...
    };

    // --- BridgerBleManager (MODIFIED to notify ALL data listeners) ---

    private class BridgerBleManager extends BleManager {
        @Nullable private BluetoothGattCharacteristic writeCharacteristic;
        @Nullable private BluetoothGattCharacteristic notifyCharacteristic;

        public BridgerBleManager(@NonNull Context context) {
            super(context);
            setConnectionObserver(connectionObserver);
        }

        @Override
        protected void initialize() {
            setNotificationCallback(notifyCharacteristic)
                .with((device, data) -> {
                    // Add your raw data logging here for debugging
                    Log.d(TAG, "Raw data received from peripheral: " + data.toString());

                    String value = data.getStringValue(0);
                    if (value == null) {
                        Log.w(TAG, "Received data, but it could not be parsed as a String.");
                        return;
                    }

                    Log.d(TAG, "Data received in Singleton (as string): " + value);
                    saveLastReceivedMessage(value);
                    for (WeakReference<BleDataListener> ref : dataListeners) {
                        Optional.ofNullable(ref.get())
                            .ifPresentOrElse(
                                l -> l.onDataReceived(value),
                                () -> connectionListeners.remove(ref)
                            );
                    }
                });

            beginAtomicRequestQueue()
                .add(enableNotifications(notifyCharacteristic)
                    .fail((device, status) -> log(Log.ERROR, "Could not enable notifications: " + status))
                )
                .enqueue();
        }

        public void performWrite(@NonNull Data data) {
            if (writeCharacteristic == null) {
                log(Log.WARN, "Write characteristic is not initialized.");
                return;
            }
            writeCharacteristic(writeCharacteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                .fail((device, status) -> log(Log.ERROR, "Failed to send data with status: " + status))
                .enqueue();
        }

        @Override
        public void log(int priority, @NonNull String message) {
            Log.println(priority, TAG, message);
        }

        @Override
        protected boolean isRequiredServiceSupported(@NonNull final BluetoothGatt gatt) {
            // <-- DEBUG LOG 1: See if we are even getting this far
            Log.d(TAG, "Checking for required services...");
            final BluetoothGattService service = gatt.getService(bridgerServiceUuid);
            if (service == null) {
                // <-- DEBUG LOG 2: This is a common failure point!
                Log.e(TAG, "ERROR: Service with UUID " + bridgerServiceUuid + " was not found!");
                return false;
            }

            writeCharacteristic = service.getCharacteristic(writeCharacteristicUuid);
            notifyCharacteristic = service.getCharacteristic(notifyCharacteristicUuid);

            if (writeCharacteristic == null) {
                Log.e(TAG, "ERROR: Write characteristic with UUID " + writeCharacteristicUuid + " was not found!");
            }
            if (notifyCharacteristic == null) {
                Log.e(TAG, "ERROR: Notify characteristic with UUID " + notifyCharacteristicUuid + " was not found!");
            }

            boolean success = writeCharacteristic != null && notifyCharacteristic != null;
            Log.d(TAG, "Service check complete. Are all characteristics found? " + success);
            return success;
        }

        @Override
        protected void onServicesInvalidated() {
            writeCharacteristic = null;
            notifyCharacteristic = null;
        }
    }

    public static class NotConfiguredException extends Exception {
        NotConfiguredException(String message) { super(message); }
    }
    public static class ConnectionActiveException extends Exception {
        ConnectionActiveException(String message) { super(message); }
    }
    public static class BluetoothUnavailableException extends Exception {
        BluetoothUnavailableException(String message) { super(message); }
    }
    public static class NotConnectedException extends Exception {
        NotConnectedException(String message) { super(message); }
    }
}
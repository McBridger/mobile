package expo.modules.connector;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;
import android.os.Build;

import androidx.annotation.NonNull;

import no.nordicsemi.android.ble.data.Data;
import no.nordicsemi.android.ble.observer.ConnectionObserver;

import java.util.UUID;

import expo.modules.connector.BridgerMessage.MessageType;

public class BleSingleton {

    // =============================================================================================
    // 1. CONTRACTS (Interfaces & Exceptions)
    // =============================================================================================

    public interface BleConnectionListener {
        void onDeviceConnected();
        void onDeviceDisconnected();
        void onDeviceFailedToConnect(String deviceAddress, String deviceName, String reason);
    }

    public interface BleDataListener {
        void onDataReceived(BridgerMessage msg);
    }

    public static class NotConfiguredException extends Exception { NotConfiguredException(String message) { super(message); } }
    public static class ConnectionActiveException extends Exception { ConnectionActiveException(String message) { super(message); }}
    public static class BluetoothUnavailableException extends Exception { BluetoothUnavailableException(String message) { super(message); }}
    public static class NotConnectedException extends Exception { NotConnectedException(String message) { super(message); } }

    // =============================================================================================
    // 2. SINGLETON & STATICS
    // =============================================================================================

    private static final String TAG = "BleSingleton";
    private static volatile BleSingleton instance;

    public static BleSingleton getInstance(Context context) {
        if (instance == null) {
            synchronized (BleSingleton.class) {
                if (instance == null) {
                    instance = new BleSingleton(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * Visible for testing only. Allows injecting a mock manager.
     */
    public static BleSingleton createInstanceForTests(Context context, BridgerBleManager manager) {
        return new BleSingleton(context, manager);
    }

    // =============================================================================================
    // 3. FIELDS
    // =============================================================================================

    private final Context context;
    private final BridgerBleManager bleManager;

    // Observables
    private final Observable<BleConnectionListener> connectionListeners = new Observable<>();
    private final Observable<BleDataListener> dataListeners = new Observable<>();

    // Configuration
    private UUID bridgerServiceUuid;
    private UUID characteristicUuid;

    // =============================================================================================
    // 4. CONSTRUCTORS
    // =============================================================================================

    private BleSingleton(Context context) {
        this(context, new BridgerBleManager(context));
    }

    // Constructor for DI
    private BleSingleton(Context context, BridgerBleManager manager) {
        this.context = context.getApplicationContext();
        this.bleManager = manager;
        this.bleManager.setConnectionObserver(connectionObserver);
        this.bleManager.setMessageListener(this::onMessageReceived);
    }

    // =============================================================================================
    // 5. PUBLIC API: CONFIGURATION & CONNECTION
    // =============================================================================================

    /**
     * Configures the Singleton with the necessary BLE UUIDs.
     * This MUST be called before attempting to connect.
     */
    public void setup(String serviceUuid, String characteristicUuid) throws ConnectionActiveException {
        if (bleManager.isConnected())
            throw new ConnectionActiveException("Cannot set UUIDs while a device is connected. Please disconnect first.");

        try {
            this.bridgerServiceUuid = UUID.fromString(serviceUuid);
            this.characteristicUuid = UUID.fromString(characteristicUuid);

            // Propagate config to manager
            this.bleManager.setConfiguration(this.bridgerServiceUuid, this.characteristicUuid);

            Log.d(TAG, "BleSingleton configured with UUIDs.");
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid UUID format provided during setup.", e);
        }
    }

    /**
     * Initiates a connection to a BLE device.
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

        // 4. Connect
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

    // =============================================================================================
    // 6. PUBLIC API: ACTIONS & STATE
    // =============================================================================================

    public void send(BridgerMessage msg) throws NotConnectedException {
        if (!isConnected())
            throw new NotConnectedException("Cannot send data, no device is connected.");

        bleManager.performWrite(msg.toData());
        Log.d(TAG, "Sent clipboard message: " + msg.getValue());
    }

    public boolean isConnected() {
        return bleManager.isConnected();
    }

    // =============================================================================================
    // 7. PUBLIC API: LISTENERS
    // =============================================================================================

    public void addConnectionListener(BleConnectionListener listener) {
        connectionListeners.add(listener);
    }

    public void removeConnectionListener(BleConnectionListener listener) {
        connectionListeners.remove(listener);
    }

    public void addDataListener(BleDataListener listener) {
        dataListeners.add(listener);
    }

    public void removeDataListener(BleDataListener listener) {
        dataListeners.remove(listener);
    }

    // =============================================================================================
    // 8. PRIVATE IMPLEMENTATION
    // =============================================================================================

    private void onMessageReceived(BridgerMessage receivedMessage) {
        dataListeners.notify(l -> l.onDataReceived(receivedMessage));
    }

    private final ConnectionObserver connectionObserver = new ConnectionObserver() {
        @Override
        public void onDeviceFailedToConnect(@NonNull BluetoothDevice device, int reason) {
            connectionListeners.notify(l -> l.onDeviceFailedToConnect(device.getAddress(), device.getName(), "Native reason code: " + reason));
        }

        @Override
        public void onDeviceReady(@NonNull BluetoothDevice device) {
            connectionListeners.notify(BleConnectionListener::onDeviceConnected);

            String message = new BridgerMessage(MessageType.DEVICE_NAME, Build.MODEL).toJson();
            bleManager.performWrite(Data.from(message));
            Log.d(TAG, "Sent device name: " + Build.MODEL);
        }

        @Override
        public void onDeviceDisconnected(@NonNull BluetoothDevice device, int reason) {
            connectionListeners.notify(BleConnectionListener::onDeviceDisconnected);
        }

        @Override
        public void onDeviceConnecting(@NonNull BluetoothDevice device) {
            Log.i(TAG, "ConnectionObserver: onDeviceConnecting to " + device.getAddress());
        }

        @Override
        public void onDeviceConnected(@NonNull BluetoothDevice device) {
            Log.i(TAG, "ConnectionObserver: onDeviceConnected to " + device.getAddress());
        }

        @Override
        public void onDeviceDisconnecting(@NonNull BluetoothDevice device) {
            Log.i(TAG, "ConnectionObserver: onDeviceDisconnecting from " + device.getAddress());
        }
    };
}


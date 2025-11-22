package expo.modules.connector;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.data.Data;

class BridgerBleManager extends BleManager {
    private static final String TAG = "BridgerBleManager";

    @Nullable
    private BluetoothGattCharacteristic characteristic;
    private UUID serviceUuid;
    private UUID characteristicUuid;
    private BridgerMessageListener messageListener;

    public interface BridgerMessageListener {
        void onMessageReceived(BridgerMessage message);
    }

    public BridgerBleManager(@NonNull Context context) {
        super(context);
    }

    public void setConfiguration(UUID serviceUuid, UUID characteristicUuid) {
        this.serviceUuid = serviceUuid;
        this.characteristicUuid = characteristicUuid;
    }

    public void setMessageListener(BridgerMessageListener listener) {
        this.messageListener = listener;
    }

    public void performWrite(@NonNull Data data) {
        if (characteristic == null) {
            log(Log.WARN, "Characteristic is not initialized.");
            return;
        }
        writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                .fail((device, status) -> log(Log.ERROR, "Failed to send data with status: " + status))
                .enqueue();
    }

    @Override
    public void log(int priority, @NonNull String message) {
        Log.println(priority, TAG, message);
    }

    @Override
    protected void initialize() {
        setNotificationCallback(characteristic)
                .with((device, data) -> {
                    BridgerMessage receivedMessage = BridgerMessage.fromData(data, device);
                    if (receivedMessage == null) return;

                    if (messageListener != null) {
                        messageListener.onMessageReceived(receivedMessage);
                    }
                });

        beginAtomicRequestQueue()
                .add(requestMtu(512)
                        .fail((device, status) -> log(Log.ERROR, "Could not request MTU: " + status))
                )
                .add(enableNotifications(characteristic)
                        .fail((device, status) -> log(Log.ERROR, "Could not enable notifications: " + status))
                )
                .enqueue();
    }

    @Override
    protected boolean isRequiredServiceSupported(@NonNull final BluetoothGatt gatt) {
        if (serviceUuid == null || characteristicUuid == null) {
            log(Log.ERROR, "UUIDs not configured!");
            return false;
        }

        // <-- DEBUG LOG 1: See if we are even getting this far
        Log.d(TAG, "Checking for required services...");
        final BluetoothGattService service = gatt.getService(serviceUuid);
        if (service == null) {
            // <-- DEBUG LOG 2: This is a common failure point!
            Log.e(TAG, "ERROR: Service with UUID " + serviceUuid + " was not found!");
            return false;
        }

        characteristic = service.getCharacteristic(characteristicUuid);

        if (characteristic == null) {
            Log.e(TAG, "ERROR: Characteristic with UUID " + characteristicUuid + " was not found!");
        }

        boolean success = characteristic != null;
        Log.d(TAG, "Service check complete. Is characteristic found? " + success);
        return success;
    }

    @Override
    protected void onServicesInvalidated() {
        characteristic = null;
    }
}

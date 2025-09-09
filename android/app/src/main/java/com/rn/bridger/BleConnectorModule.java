package com.rn.bridger; // Assuming the same package

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;

import java.util.UUID;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.data.Data;
import no.nordicsemi.android.ble.observer.ConnectionObserver;

public class BleConnectorModule extends NativeBleConnectorSpec {

  public static final String NAME = "BleConnector";
  private static final String TAG = "BleConnectorModule";

  private UUID BRIDGER_SERVICE_UUID;
  private UUID WRITE_CHARACTERISTIC_UUID;
  private UUID NOTIFY_CHARACTERISTIC_UUID;

  private final BluetoothAdapter bluetoothAdapter;
  private final BridgerBleManager bleManager;

  @NonNull
  @Override
  public String getName() {
    return NAME;
  }

  public BleConnectorModule(ReactApplicationContext context) {
    super(context);
    BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    this.bluetoothAdapter = (bluetoothManager != null) ? bluetoothManager.getAdapter() : null;
    this.bleManager = new BridgerBleManager(context);
  }

  @Override
  public void isConnected(Promise promise) {
    promise.resolve(bleManager.isConnected());
  }

  @Override
  public void setup(String serviceUuid, String writeUuid, String notifyUuid, Promise promise) {
    try {
      if (bleManager.isConnected())
        throw new ConnectionActiveException("Cannot set UUIDs while a device is connected. Please disconnect first.");

      // This code only runs if the check above passes.
      this.BRIDGER_SERVICE_UUID = UUID.fromString(serviceUuid);
      this.WRITE_CHARACTERISTIC_UUID = UUID.fromString(writeUuid);
      this.NOTIFY_CHARACTERISTIC_UUID = UUID.fromString(notifyUuid);
      Log.d(TAG, "UUIDs updated successfully.");
      promise.resolve(null);

    } catch (ConnectionActiveException e) {
      // --- MODIFICATION: Catch our new custom exception ---
      promise.reject("CONNECTION_ACTIVE", e.getMessage(), e);
    } catch (IllegalArgumentException e) {
      // This catches the UUID.fromString() error
      promise.reject("INVALID_UUID", "One or more of the provided UUID strings are invalid.", e);
    }
  }

  public void connect(String address, Promise promise) {
    try {
      if (bleManager.isConnected())
        throw new ConnectionBusyException("A device is already connected or a connection is in progress.");
      if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled())
        throw new BluetoothUnavailableException("Bluetooth is not available or is disabled.");


      final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
      bleManager.connect(device)
          .retry(3, 100)
          .useAutoConnect(false)
          .timeout(20000)
          .enqueue();
      promise.resolve(null);

    } catch (ConnectionBusyException e) {
      promise.reject("CONNECTION_BUSY", e.getMessage(), e);
    } catch (BluetoothUnavailableException e) {
      promise.reject("BLUETOOTH_DISABLED", e.getMessage(), e);
    } catch (IllegalArgumentException e) {
      promise.reject("INVALID_ADDRESS", "Invalid Bluetooth address provided.", e);
    }
  }

  @Override
  public void disconnect(Promise promise) {
    if (bleManager.isConnected())
      bleManager.disconnect().enqueue();

    promise.resolve(null);
  }

  @Override
  public void send(String data, Promise promise) {
    if (!bleManager.isConnected()) {
      promise.reject("NOT_CONNECTED", "No device is connected.");
      return;
    }
    // FIX: Call the new public method in our BleManager, instead of the protected one.
    bleManager.performWrite(Data.from(data), promise);
  }

  private final ConnectionObserver connectionObserver = new ConnectionObserver() {
    @Override
    public void onDeviceConnecting(@NonNull BluetoothDevice device) { }

    @Override
    public void onDeviceConnected(@NonNull BluetoothDevice device) { }

    @Override
    public void onDeviceFailedToConnect(@NonNull BluetoothDevice device, int reason) {
      Log.e(TAG, "Failed to connect to " + device.getAddress() + " with reason: " + reason);
      WritableMap map = Arguments.createMap();
      map.putString("address", device.getAddress());
      map.putString("reason", "Failed with native reason code: " + reason);
      emitOnConnectionFailed(map);
    }

    @Override
    public void onDeviceReady(@NonNull BluetoothDevice device) {
      Log.d(TAG, "Device ready: " + device.getAddress());
      // FIX: Manually trigger the initialization now that the device is ready.
      bleManager.initializeCharacteristics();
      emitOnConnected();
    }

    @Override
    public void onDeviceDisconnecting(@NonNull BluetoothDevice device) { }

    @Override
    public void onDeviceDisconnected(@NonNull BluetoothDevice device, int reason) {
      Log.d(TAG, "Device disconnected: " + device.getAddress() + " with reason: " + reason);
      emitOnDisconnected();
    }
  };

  /**
   * The inner class that extends BleManager and encapsulates the GATT logic.
   */
  private class BridgerBleManager extends BleManager {

    @Nullable private BluetoothGattCharacteristic writeCharacteristic;
    @Nullable private BluetoothGattCharacteristic notifyCharacteristic;

    public BridgerBleManager(@NonNull Context context) {
      super(context);
      setConnectionObserver(connectionObserver);
    }

    public void initializeCharacteristics() {
      setNotificationCallback(notifyCharacteristic)
          .with((device, data) -> {
            String value = data.getStringValue(0);
            if (value != null) {
              emitOnReceived(value);
            }
          });

      beginAtomicRequestQueue()
          .add(enableNotifications(notifyCharacteristic)
              .fail((device, status) -> log(Log.ERROR, "Could not enable notifications: " + status))
          )
          .enqueue();
    }

    // FIX: This new public method safely exposes the writing functionality.
    public void performWrite(@NonNull Data data, @NonNull Promise promise) {
      if (writeCharacteristic == null) {
        promise.reject("WRITE_CHARACTERISTIC_NOT_FOUND", "The write characteristic is not initialized.");
        return;
      }
      // FIX: Replaced deprecated method with the new one specifying the write type.
      writeCharacteristic(writeCharacteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
          .done(device -> promise.resolve(null))
          .fail((device, status) -> promise.reject("SEND_FAILED", "Failed to send data with status: " + status))
          .enqueue();
    }

    @Override
    public void log(int priority, @NonNull String message) {
      Log.println(priority, TAG, message);
    }

    @Override
    protected boolean isRequiredServiceSupported(@NonNull final BluetoothGatt gatt) {
      final BluetoothGattService service = gatt.getService(BRIDGER_SERVICE_UUID);
      if (service == null) {
        log(Log.ERROR, "Required service not found: " + BRIDGER_SERVICE_UUID);
        return false;
      }
      writeCharacteristic = service.getCharacteristic(WRITE_CHARACTERISTIC_UUID);
      notifyCharacteristic = service.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID);

      boolean allCharacteristicsFound = writeCharacteristic != null && notifyCharacteristic != null;
      if (!allCharacteristicsFound) {
        log(Log.ERROR, "One or more required characteristics were not found.");
      }
      return allCharacteristicsFound;
    }

    // FIX: onServicesInvalidated is now an override directly in this class.
    @Override
    protected void onServicesInvalidated() {
      log(Log.WARN, "Services Invalidated");
      writeCharacteristic = null;
      notifyCharacteristic = null;
    }
  }

  private static class ConnectionActiveException extends Exception {
    ConnectionActiveException(String message) { super(message); }
  }
  private static class ConnectionBusyException extends Exception {
    ConnectionBusyException(String message) { super(message); }
  }
  private static class BluetoothUnavailableException extends Exception {
    BluetoothUnavailableException(String message) { super(message); }
  }
}
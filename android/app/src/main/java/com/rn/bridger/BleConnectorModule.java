package com.rn.bridger;

import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;

public class BleConnectorModule extends NativeBleConnectorSpec {

  public static final String NAME = "BleConnector";
  public static final String TAG = "BleConnectorModule";

  private final BleSingleton bleSingleton;

  private final BleSingleton.BleConnectionListener connectionListener = new BleSingleton.BleConnectionListener() {
    public void onDeviceConnected() {
      emitOnConnected();
    }

    public void onDeviceDisconnected() {
      emitOnDisconnected();
    }

    public void onDeviceFailedToConnect(String deviceAddress, String reason) {
      WritableMap map = Arguments.createMap();
      map.putString("address", deviceAddress);
      map.putString("reason", reason);
      emitOnConnectionFailed(map);
    }
  };

  private final BleSingleton.BleDataListener dataListener = new BleSingleton.BleDataListener() {
    public void onDataReceived(String data) {
      emitOnReceived(data);
    }
  };

  public BleConnectorModule(ReactApplicationContext context) {
    super(context);
    this.bleSingleton = BleSingleton.getInstance(context.getApplicationContext());
    bleSingleton.addConnectionListener(connectionListener);
    bleSingleton.addDataListener(dataListener);
  }

  @NonNull
  @Override
  public String getName() {
    return NAME;
  }


  @Override
  public void isConnected(Promise promise) {
    promise.resolve(bleSingleton.isConnected());
  }

  @Override
  public void setup(String serviceUuid, String writeUuid, String notifyUuid, Promise promise) {
    try {
      bleSingleton.setup(serviceUuid, writeUuid, notifyUuid);
      promise.resolve(null);
    } catch (BleSingleton.ConnectionActiveException e) {
      promise.reject("CONNECTION_ACTIVE", e.getMessage(), e);
    }
  }

  @Override
  public void connect(String address, Promise promise) {
    try {
      // Step 1: Create an intent for your foreground service.
      Intent serviceIntent = new Intent(getReactApplicationContext(), BridgerForegroundService.class);

      // Step 2: Start the service. This ensures it's running and ready to receive
      // connection events from the Singleton, even if the app is backgrounded immediately.
      // For Android O (API 26) and above, you MUST use startForegroundService.
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        getReactApplicationContext().startForegroundService(serviceIntent);
      } else {
        getReactApplicationContext().startService(serviceIntent);
      }

      // Step 3: Tell the singleton to initiate the actual BLE connection.
      // The service is already listening for the outcome of this call.
      bleSingleton.connect(address);

      // Step 4: Resolve the promise immediately. This tells JavaScript that the
      // connect *command* was successfully issued. The asynchronous result will
      // be handled by your event listeners (onConnected, onConnectionFailed).
      promise.resolve(null);

    } catch (BleSingleton.NotConfiguredException e) {
      promise.reject("NOT_CONFIGURED", e.getMessage(), e);
    } catch (BleSingleton.BluetoothUnavailableException e) {
      promise.reject("BLUETOOTH_UNAVAILABLE", e.getMessage(), e);
    } catch (BleSingleton.ConnectionActiveException e) {
      promise.reject("CONNECTION_ACTIVE", e.getMessage(), e);
    } catch (IllegalArgumentException e) {
      promise.reject("INVALID_ADDRESS", "The provided Bluetooth address is invalid.", e);
    } catch (Exception e) { // General catch-all for any other unexpected issues.
      promise.reject("CONNECT_ERROR", "An unexpected error occurred while starting the connection.", e);
    }
  }

  @Override
  public void disconnect(Promise promise) {
    bleSingleton.disconnect();

    Intent serviceIntent = new Intent(getReactApplicationContext(), BridgerForegroundService.class);
    getReactApplicationContext().stopService(serviceIntent);

    promise.resolve(null);
  }

  @Override
  public void send(String data, Promise promise) {
    try { bleSingleton.send(data); }
    catch (BleSingleton.NotConnectedException ignored) {}

    promise.resolve(null);
  }
}
package expo.modules.connector

import android.content.Intent
import android.os.Build
import android.util.Log
import expo.modules.connector.core.Broker
import expo.modules.connector.crypto.EncryptionService
import expo.modules.connector.services.ForegroundService
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.UUID

class ConnectorModule : Module() {
  private val scope = CoroutineScope(Dispatchers.Default)

  companion object {
    private const val TAG = "ConnectorModule"
  }

  override fun definition() = ModuleDefinition {
    Name("Connector")

    Events("onConnected", "onDisconnected", "onReceived")

    OnCreate {
      Log.d(TAG, "OnCreate: Initializing EncryptionService (Hardcoded for testing).")
      // FIXME: Temporary hardcoded phrase and salt for testing - matching macOS
      EncryptionService.setup(
        "Correct-Horse-Battery-Staple",
        "7c4d5f2a9b1e8c3d0f4a6b2c8e1d3f5a7092841536b5c4d2e1f0a9b8c7d6e5f4"
      )

      Log.d(TAG, "OnCreate: Initializing Broker and collecting messages.")
      val context = appContext.reactContext?.applicationContext ?: run {
        Log.e(TAG, "OnCreate: Application context is null.")
        return@OnCreate
      }
      Broker.init(context)

      scope.launch {
        Broker.messages.collect { msg ->
          Log.d(TAG, "onReceived event: ${msg.id}, Type: ${msg.getType()}, Value: ${msg.value}")
          sendEvent("onReceived", msg.toBundle())
        }
      }

      scope.launch {
        Broker.state.collect { state ->
          Log.d(TAG, "onStateChange event: Broker state changed to $state")

          when (state) {
            Broker.State.CONNECTED -> sendEvent("onConnected")
            Broker.State.DISCONNECTED -> sendEvent("onDisconnected")
            Broker.State.IDLE -> sendEvent("onDisconnected")
            else -> {}
          }
        }
      }
    }

    OnDestroy {
      Log.d(TAG, "OnDestroy: Cancelling scope.")
      scope.cancel()
    }

    AsyncFunction("getAdvertiseUUID") {
      val bytes = EncryptionService.derive("McBridge_Advertise_UUID", 16) ?: return@AsyncFunction null
      return@AsyncFunction bytesToUuid(bytes).toString()
    }

    AsyncFunction("getServiceUUID") {
      val bytes = EncryptionService.derive("McBridge_Service_UUID", 16) ?: return@AsyncFunction null
      return@AsyncFunction bytesToUuid(bytes).toString()
    }

    AsyncFunction("getCharacteristicUUID") {
      val bytes = EncryptionService.derive("McBridge_Characteristic_UUID", 16) ?: return@AsyncFunction null
      return@AsyncFunction bytesToUuid(bytes).toString()
    }

    AsyncFunction("isConnected") {
      val isConnected = Broker.state.value == Broker.State.CONNECTED
      Log.d(TAG, "isConnected: Current state is ${Broker.state.value}, returning $isConnected")
      return@AsyncFunction isConnected
    }

    AsyncFunction("connect") { address: String ->
      Log.d(TAG, "connect: Attempting to connect to $address")
      scope.launch {
        Broker.connect(address)
        Log.d(TAG, "connect: Broker.connect called for $address")
      }
      return@AsyncFunction null
    }

    AsyncFunction("disconnect") {
      Log.d(TAG, "disconnect: Attempting to disconnect")
      scope.launch {
        Broker.disconnect()
        Log.d(TAG, "disconnect: Broker.disconnect called")
      }
      return@AsyncFunction null
    }

    AsyncFunction("send") { data: String ->
      Log.d(TAG, "send: Sending clipboard update with data: $data")
      Broker.clipboardUpdate(data)
    }

    AsyncFunction("start") {
      val serviceUuid = bytesToUuid(EncryptionService.derive("McBridge_Service_UUID", 16)!!).toString()
      val characteristicUuid = bytesToUuid(EncryptionService.derive("McBridge_Characteristic_UUID", 16)!!).toString()

      Log.d(TAG, "start: Starting ForegroundService with auto-derived UUIDs.")
      val context = appContext.reactContext?.applicationContext ?: run {
        Log.e(TAG, "start: Application context is null.")
        return@AsyncFunction null
      }
      val intent = Intent(context, ForegroundService::class.java)
      intent.putExtra("SERVICE_UUID", serviceUuid)
      intent.putExtra("CHARACTERISTIC_UUID", characteristicUuid)

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
          Log.d(TAG, "start: Called startForegroundService for Android O+")
      } else {
          context.startService(intent)
          Log.d(TAG, "start: Called startService for Android < O")
      }

      return@AsyncFunction null
    }

    AsyncFunction("stop") {
      Log.d(TAG, "stop: Stopping ForegroundService")
       val context = appContext.reactContext?.applicationContext ?: run {
         Log.e(TAG, "stop: Application context is null.")
         return@AsyncFunction null
       }
       val intent = Intent(context, ForegroundService::class.java)
       context.stopService(intent)
       Log.d(TAG, "stop: Called stopService")
       return@AsyncFunction null 
    }

    AsyncFunction("getHistory") {
      Log.d(TAG, "getHistory: Retrieving history")
      return@AsyncFunction Broker.getHistory().retrieve()
    }

    AsyncFunction("clearHistory") {
      Log.d(TAG, "clearHistory: Clearing history")
      Broker.getHistory().clear()
      return@AsyncFunction null
    }
  }

  private fun bytesToUuid(bytes: ByteArray): UUID {
    val buffer = ByteBuffer.wrap(bytes)
    return UUID(buffer.long, buffer.long)
  }
}

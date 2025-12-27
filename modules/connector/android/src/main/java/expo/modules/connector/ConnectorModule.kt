package expo.modules.connector

import android.content.Intent
import android.os.Build
import android.util.Log
import expo.modules.connector.core.Broker
import expo.modules.connector.crypto.EncryptionService
import expo.modules.connector.services.ForegroundService
import expo.modules.connector.transports.ble.BleTransport
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

    Events("onReceived", "onStateChanged")

    OnCreate {
      Log.d(TAG, "OnCreate: Initializing Broker and collecting messages.")
      val context = appContext.reactContext?.applicationContext ?: run {
        Log.e(TAG, "OnCreate: Application context is null.")
        return@OnCreate
      }
      
      // 1. Initialize Broker
      Broker.init(context)

      // 2. Attempt to load saved credentials from secure storage
      val isReady = EncryptionService.load(context)
      Log.d(TAG, "OnCreate: EncryptionService load result: $isReady")

      // 3. If ready, register the BLE transport immediately (Magic Sync starts)
      if (isReady) {
        Log.i(TAG, "OnCreate: System is ready, registering BleTransport.")
        Broker.registerBle(BleTransport(context))
      }

      scope.launch {
        Broker.messages.collect { msg ->
          Log.d(TAG, "onReceived event: ${msg.id}, Type: ${msg.getType()}, Value: ${msg.value}")
          sendEvent("onReceived", msg.toBundle())
        }
      }

      scope.launch {
        Broker.state.collect { state ->
          Log.d(TAG, "onStateChange event: Broker state changed to $state")
          
          // Send granular state to JS in UPPERCASE
          sendEvent("onStateChanged", mapOf("status" to state.name))
        }
      }
    }

    OnDestroy {
      Log.d(TAG, "OnDestroy: Cancelling scope.")
      scope.cancel()
    }

    AsyncFunction("setup") { mnemonic: String, salt: String ->
      Log.d(TAG, "setup: Delegating setup to Broker.")
      Broker.setup(mnemonic, salt)
    }

    Function("isReady") {
      val ready = EncryptionService.isReady()
      Log.d(TAG, "isReady: Returning $ready")
      return@Function ready
    }

    Function("getStatus") {
      val status = Broker.state.value.name
      Log.d(TAG, "getStatus: Returning $status")
      return@Function status
    }

    AsyncFunction("send") { data: String ->
      Log.d(TAG, "send: Sending clipboard update with data: $data")
      Broker.clipboardUpdate(data)
    }

    AsyncFunction("start") {
      Log.d(TAG, "start: Starting ForegroundService.")
      val context = appContext.reactContext?.applicationContext ?: run {
        Log.e(TAG, "start: Application context is null.")
        return@AsyncFunction null
      }
      
      val intent = Intent(context, ForegroundService::class.java)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
        Log.d(TAG, "start: Called startForegroundService for Android O+")
      } else {
        context.startService(intent)
        Log.d(TAG, "start: Called startService for Android < O")
      }

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

    Function("getMnemonic") {
      return@Function EncryptionService.getMnemonic()
    }

    AsyncFunction("reset") {
      Log.d(TAG, "reset: Full system reset initiated.")
      Broker.reset()
      Log.i(TAG, "reset: System reset command sent to Broker.")
      return@AsyncFunction null
    }
  }
}
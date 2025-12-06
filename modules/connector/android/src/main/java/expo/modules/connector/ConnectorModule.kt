package expo.modules.connector

import android.content.Intent
import android.os.Build
import expo.modules.connector.core.Broker
import expo.modules.connector.services.ForegroundService
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ConnectorModule : Module() {
  private val scope = CoroutineScope(Dispatchers.Default)

  override fun definition() = ModuleDefinition {
    Name("Connector")

    Events("onConnected", "onDisconnected", "onConnectionFailed", "onReceived")

    OnCreate {
      val context = appContext.reactContext?.applicationContext ?: return@OnCreate
      Broker.init(context)

      scope.launch {
        Broker.messages.collect { msg -> sendEvent("onReceived", msg.toBundle()) }
      }
    }

    OnDestroy { scope.cancel() }

    AsyncFunction("isConnected") {
      return@AsyncFunction Broker.state.value == Broker.State.CONNECTED
    }

    AsyncFunction("connect") { address: String ->
      scope.launch {
        Broker.connect(address)
      }
    }

    AsyncFunction("disconnect") {
      scope.launch {
        Broker.disconnect()
      }
    }

    AsyncFunction("send") { data: String ->
      Broker.clipboardUpdate(data)
    }

    AsyncFunction("start") { serviceUuid: String, characteristicUuid: String ->
      val context = appContext.reactContext?.applicationContext ?: return@AsyncFunction null
      val intent = Intent(context, ForegroundService::class.java)
      intent.putExtra("SERVICE_UUID", serviceUuid)
      intent.putExtra("CHARACTERISTIC_UUID", characteristicUuid)

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
      } else {
          context.startService(intent)
      }

      return@AsyncFunction null
    }

    AsyncFunction("stop") {
       val context = appContext.reactContext?.applicationContext ?: return@AsyncFunction null
       val intent = Intent(context, ForegroundService::class.java)
       context.stopService(intent)
       return@AsyncFunction null 
    }

    AsyncFunction("getHistory") {
      return@AsyncFunction Broker.getHistory().retrieve()
    }

    AsyncFunction("clearHistory") {
      Broker.getHistory().clear()
      return@AsyncFunction null
    }
  }
}
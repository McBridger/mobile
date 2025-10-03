package expo.modules.connector

import android.content.Intent
import android.os.Build
import android.os.Bundle
import expo.modules.connector.BridgerMessage.Companion.toSend
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class ConnectorModule : Module() {
  private var bleSingleton: BleSingleton? = null
  private var bridgerHistory: BridgerHistory? = null

  private val connectionListener = object : BleSingleton.BleConnectionListener {
    override fun onDeviceConnected() {
      sendEvent("onConnected")
    }

    override fun onDeviceDisconnected() {
      sendEvent("onDisconnected")
    }

    override fun onDeviceFailedToConnect(deviceAddress: String, deviceName: String, reason: String) {
      sendEvent("onConnectionFailed", mapOf(
        "device" to deviceAddress,
        "name" to deviceName,
        "reason" to reason
      ))
    }
  }

  private val dataListener = BleSingleton.BleDataListener { data ->
      sendEvent("onReceived", data.toBundle())
  }

  override fun definition() = ModuleDefinition {
    Name("Connector")

    Events("onConnected", "onDisconnected", "onConnectionFailed", "onReceived")

    OnCreate {
      val context = appContext.reactContext?.applicationContext ?: return@OnCreate
      bleSingleton = BleSingleton.getInstance(context)
      bleSingleton?.addConnectionListener(connectionListener)
      bleSingleton?.addDataListener(dataListener)
      bridgerHistory = BridgerHistory.getInstance(context)
    }

    OnDestroy {
        bleSingleton?.removeConnectionListener(connectionListener)
        bleSingleton?.removeDataListener(dataListener)
    }

    AsyncFunction("isConnected") {
      return@AsyncFunction bleSingleton?.isConnected ?: false
    }

    AsyncFunction("setup") { serviceUuid: String, characteristicUuid: String ->
      bleSingleton?.setup(serviceUuid, characteristicUuid)
    }

    AsyncFunction("connect") { address: String ->
      bleSingleton?.connect(address)
    }

    AsyncFunction("disconnect") {
      bleSingleton?.disconnect()
    }

    AsyncFunction("send") { data: String ->
      try {
        val msg = toSend(data)
        bleSingleton?.send(msg)
        bridgerHistory?.add(msg)
      } catch (e: BleSingleton.NotConnectedException) {
        // Ignoring error
      }
    }

    AsyncFunction("startBridgerService") {
       val context = appContext.reactContext?.applicationContext ?: return@AsyncFunction null
       val intent = Intent(context, BridgerForegroundService::class.java)
       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
           context.startForegroundService(intent)
       } else {
           context.startService(intent)
       }

       return@AsyncFunction null
    }

    AsyncFunction("stopBridgerService") {
       val context = appContext.reactContext?.applicationContext ?: return@AsyncFunction null
       val intent = Intent(context, BridgerForegroundService::class.java)
       context.stopService(intent)
       return@AsyncFunction null 
    }

    AsyncFunction("getHistory") {
       val context = appContext.reactContext?.applicationContext ?: return@AsyncFunction emptyList<Bundle>()
       return@AsyncFunction bridgerHistory?.retrieve() ?: emptyList<Bundle>()
    }

    AsyncFunction("clearHistory") {
       val context = appContext.reactContext?.applicationContext ?: return@AsyncFunction null
       bridgerHistory?.clear()
       return@AsyncFunction null
    }
  }
}
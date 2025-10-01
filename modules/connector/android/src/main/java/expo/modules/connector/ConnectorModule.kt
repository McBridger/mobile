package expo.modules.connector // Вы можете изменить это на ваше имя пакета

import android.content.Intent
import android.os.Build
import android.os.Bundle
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

import java.io.File

// Предполагается, что классы BleSingleton и BridgerForegroundService существуют
// и доступны в вашем проекте. Убедитесь, что импорты корректны.
import expo.modules.connector.BleSingleton
// import com.rn.bridger.BridgerForegroundService

class ConnectorModule : Module() {

  // Ссылка на синглтон для управления BLE
  private var bleSingleton: BleSingleton? = null

  // Определения слушателей событий. Они будут инициализированы в OnCreate.
  private val connectionListener = object : BleSingleton.BleConnectionListener {
    override fun onDeviceConnected() {
      // Отправляем событие в JS о том, что устройство подключено
      sendEvent("onConnected")
    }

    override fun onDeviceDisconnected() {
      // Отправляем событие в JS о том, что устройство отключено
      sendEvent("onDisconnected")
    }

    override fun onDeviceFailedToConnect(deviceAddress: String, deviceName: String, reason: String) {
      // Отправляем событие в JS с деталями ошибки подключения
      sendEvent("onConnectionFailed", mapOf(
        "device" to deviceAddress,
        "name" to deviceName,
        "reason" to reason
      ))
    }
  }

  private val dataListener = object : BleSingleton.BleDataListener {
    override fun onDataReceived(data: Bundle) {
      // Отправляем полученные данные в JS.
      // sendEvent может напрямую работать с Bundle.
      sendEvent("onReceived", data)
    }
  }

  override fun definition() = ModuleDefinition {
    Name("Connector")

    // Определяем имена событий, которые модуль может отправлять в JavaScript.
    Events("onConnected", "onDisconnected", "onConnectionFailed", "onReceived")

    // Блок OnCreate выполняется один раз при создании модуля.
    // Идеальное место для инициализации.
    OnCreate {
      val context = appContext.reactContext?.applicationContext ?: return@OnCreate
      bleSingleton = BleSingleton.getInstance(context)
      bleSingleton?.addConnectionListener(connectionListener)
      bleSingleton?.addDataListener(dataListener)
    }
    
    // Блок OnDestroy выполняется при уничтожении модуля.
    // Здесь можно очистить ресурсы, например, удалить слушателей.
    OnDestroy {
        bleSingleton?.removeConnectionListener(connectionListener)
        bleSingleton?.removeDataListener(dataListener)
    }

    // Асинхронная функция для проверки статуса подключения.
    // Возвращаемое значение будет передано в resolve промиса.
    AsyncFunction("isConnected") {
      return@AsyncFunction bleSingleton?.isConnected() ?: false
    }

    // Асинхронная функция для настройки UUID сервиса и характеристики.
    // Если bleSingleton.setup() выбросит исключение, промис будет автоматически отклонен.
    AsyncFunction("setup") { serviceUuid: String, characteristicUuid: String ->
      bleSingleton?.setup(serviceUuid, characteristicUuid)
    }

    // Асинхронная функция для подключения к устройству.
    AsyncFunction("connect") { address: String ->
      // Если bleSingleton.connect() выбросит исключение, промис будет автоматически отклонен.
      bleSingleton?.connect(address)
    }

    // Асинхронная функция для отключения от устройства.
    AsyncFunction("disconnect") {
      bleSingleton?.disconnect()
    }

    // Асинхронная функция для отправки данных.
    AsyncFunction("send") { data: String ->
      try {
        bleSingleton?.send(data)
      } catch (e: BleSingleton.NotConnectedException) {
        // Игнорируем ошибку, как в оригинальном коде.
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
       val context = appContext.reactContext?.applicationContext ?: return@AsyncFunction emptyList<String>()
       val file = File(context.filesDir, "bridger_history.txt")
       if (!file.exists()) return@AsyncFunction emptyList<String>()
       return@AsyncFunction file.readLines()
    }

    AsyncFunction("clearHistory") {
       val context = appContext.reactContext?.applicationContext ?: return@AsyncFunction null
       val file = File(context.filesDir, "bridger_history.txt")
       if (file.exists()) file.delete()
       return@AsyncFunction null 
    }
  }
}
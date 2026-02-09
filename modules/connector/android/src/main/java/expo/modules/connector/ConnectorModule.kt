package expo.modules.connector

import android.content.Intent
import android.os.Build
import android.util.Log
import expo.modules.connector.core.Broker
import expo.modules.connector.services.ForegroundService
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.error.KoinApplicationAlreadyStartedException
import expo.modules.connector.di.initKoin
import expo.modules.connector.models.ChunkMessage

class ConnectorModule : Module(), KoinComponent {
  private val scope = CoroutineScope(Dispatchers.Main)
  private val broker: Broker by lazy { get<Broker>() }

  companion object {
    private const val TAG = "ConnectorModule"
  }

  override fun definition() = ModuleDefinition {
    Name("Connector")

    Events("onStateChanged", "onReceived")

    OnCreate {
      val context = appContext.reactContext?.applicationContext ?: return@OnCreate
      initKoin(context)

      // Re-connect the "pipe" to JavaScript using the lazy broker
      scope.launch {
        broker.state.collect { state ->
          Log.d(TAG, "Emitting state change to JS: ${state.name}")
          sendEvent("onStateChanged", mapOf("status" to state.name))
        }
      }

      scope.launch {
        broker.messages.collect { message ->
          if (message is ChunkMessage) return@collect
          Log.d(TAG, "Emitting new message to JS: ${message.id}")
          sendEvent("onReceived", message.toBundle())
        }
      }
    }

    OnActivityEntersForeground {
      broker.setForeground(true)
    }

    OnActivityEntersBackground {
      broker.setForeground(false)
    }

    OnDestroy {
      scope.cancel()
    }

    AsyncFunction("setup") { mnemonic: String, salt: String ->
      broker.setup(mnemonic, salt)
    }

    Function("isReady") {
      return@Function broker.state.value != Broker.State.IDLE
    }

    Function("getStatus") {
      return@Function broker.state.value.name
    }

    AsyncFunction("send") { data: String ->
      // TODO: Handle more than tiny update
      broker.tinyUpdate(data)
    }

    AsyncFunction("start") {
      val context = appContext.reactContext?.applicationContext ?: return@AsyncFunction null
      val intent = Intent(context, ForegroundService::class.java)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
      return@AsyncFunction null
    }

    AsyncFunction("getHistory") { promise: Promise ->
      scope.launch {
        try {
          val history = broker.getHistory().retrieve()
          promise.resolve(history)
        } catch (e: Exception) {
          promise.reject("ERR_HISTORY", e.message, e)
        }
      }
    }

    AsyncFunction("clearHistory") {
      broker.getHistory().clear()
      return@AsyncFunction null
    }

    Function("getMnemonic") {
      return@Function broker.getMnemonic()
    }

    AsyncFunction("reset") {
      broker.reset()
      return@AsyncFunction null
    }
  }
}

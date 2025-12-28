package expo.modules.connector

import android.content.Intent
import android.os.Build
import android.util.Log
import expo.modules.connector.core.Broker
import expo.modules.connector.services.ForegroundService
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

      try {
        // Initialize Koin with auto-discovered modules (including mocks if WITH_MOCK=true)
        startKoin {
          androidContext(context)
          modules(expo.modules.connector.di.getAppModules())
          allowOverride(true)
        }
      } catch (e: KoinApplicationAlreadyStartedException) {
        Log.w(TAG, "Koin already started.")
      } catch (e: Exception) {
        Log.e(TAG, "Koin initialization error: ${e.message}")
      }

      // Re-connect the "pipe" to JavaScript using the lazy broker
      scope.launch {
        broker.state.collect { state ->
          Log.d(TAG, "Emitting state change to JS: ${state.name}")
          sendEvent("onStateChanged", mapOf("status" to state.name))
        }
      }

      scope.launch {
        broker.messages.collect { message ->
          Log.d(TAG, "Emitting new message to JS: ${message.id}")
          sendEvent("onReceived", message.toBundle())
        }
      }
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
      broker.clipboardUpdate(data)
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

    AsyncFunction("getHistory") {
      return@AsyncFunction broker.getHistory().retrieve()
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

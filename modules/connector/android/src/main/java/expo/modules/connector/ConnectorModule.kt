package expo.modules.connector

import android.content.Intent
import android.os.Build
import android.util.Log
import expo.modules.connector.core.Broker
import expo.modules.connector.interfaces.ISystemObserver
import expo.modules.connector.services.ForegroundService
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import expo.modules.connector.di.initKoin
import expo.modules.connector.models.*

class ConnectorModule : Module(), KoinComponent {
  private val scope = CoroutineScope(Dispatchers.Main)
  private val broker: Broker by lazy { get<Broker>() }
  private val systemObserver: ISystemObserver by lazy { get<ISystemObserver>() }

  companion object {
    private const val TAG = "ConnectorModule"
  }

  override fun definition() = ModuleDefinition {
    Name("Connector")

    Events("onStateChanged", "onHistoryChanged", "onPorterUpdated")

    OnCreate {
      val context = appContext.reactContext?.applicationContext ?: return@OnCreate
      initKoin(context)

      // Emit full state updates
      scope.launch {
        broker.state.collect { state ->
          sendEvent("onStateChanged", state.toBundle())
        }
      }

      // Emit history updates
      scope.launch {
        broker.getHistory().items.collect { items ->
          val bundles = items.map { it.toBundle() }
          sendEvent("onHistoryChanged", mapOf("items" to bundles))
        }
      }

      // Emit porter delta updates
      scope.launch {
        broker.porterUpdates.collect { update ->
          sendEvent("onPorterUpdated", update)
        }
      }
    }

    OnActivityEntersForeground {
      systemObserver.setForeground(true)
    }

    OnActivityEntersBackground {
      systemObserver.setForeground(false)
    }

    OnDestroy {
      scope.cancel()
    }

    AsyncFunction("setup") { mnemonic: String, salt: String ->
      broker.setup(mnemonic, salt)
    }

    Function("isReady") {
      return@Function broker.state.value.encryption.current == EncryptionState.KEYS_READY
    }

    Function("getBrokerState") {
      return@Function broker.state.value.toBundle()
    }

    AsyncFunction("send") { data: String ->
      val porter = Porter(
        isOutgoing = true,
        name = "Manual Send",
        type = BridgerType.TEXT,
        totalSize = data.length.toLong(),
        data = data
      )
      broker.handleOutgoing(porter)
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
          val history = broker.getHistory().retrieveBundles()
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

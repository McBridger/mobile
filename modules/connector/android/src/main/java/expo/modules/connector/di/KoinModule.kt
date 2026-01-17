package expo.modules.connector.di

import android.util.Log
import expo.modules.connector.core.Broker
import expo.modules.connector.core.History
import expo.modules.connector.core.WakeManager
import expo.modules.connector.crypto.EncryptionService
import expo.modules.connector.interfaces.*
import expo.modules.connector.transports.ble.BleManager
import expo.modules.connector.transports.ble.BleScanner
import expo.modules.connector.transports.ble.BleTransport
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val connectorModule = module {
  // 1. Scanner (Singleton)
  singleOf(::BleScanner) { bind<IBleScanner>() }

  // 2. EncryptionService (Singleton)
  singleOf(::EncryptionService) { bind<IEncryptionService>() }
  singleOf(::WakeManager) { bind<IWakeManager>() }

  // 3. History (Singleton)
  single(named("MAX_HISTORY_SIZE")) { 20 }
  single {
    History(
      context = get(),
      maxHistorySize = get(named("MAX_HISTORY_SIZE"))
    )
  }

  // 4. Broker (Singleton)
  single(createdAtStart = true) {
    Broker(
      context = get(),
      encryptionService = get(),
      scanner = get(),
      history = get(),
      wakeManager = get(),
      bleFactory = { get<IBleTransport>() }
    )
  }

  // 5. Managers and Transports (Factories)
  factoryOf(::BleManager) { bind<IBleManager>() }

  factory<IBleTransport> {
    BleTransport(
      context = get(),
      bleManager = get(),
      encryptionService = get()
    )
  }
}

/**
 * Safely initializes Koin if it hasn't been started yet.
 */
fun initKoin(context: android.content.Context) {
    if (GlobalContext.getOrNull() != null) {
        Log.d("KoinModule", "Koin already running, skipping init.")
        return
    }

    try {
        val modules = mutableListOf(connectorModule)

        // Reflection magic to pull in mocks if they exist in the classpath (e.g. in E2E builds)
        try {
            val clazz = Class.forName("expo.modules.connector.mocks.MockModuleKt")
            val method = clazz.getMethod("getMockModule")
            val mockModule = method.invoke(null) as org.koin.core.module.Module
            modules.add(mockModule)
            Log.i("KoinModule", "Successfully injected mockModule via reflection")
        } catch (ignored: ClassNotFoundException) {
            // Normal build, no mocks present
        }

        startKoin {
            androidContext(context.applicationContext)
            modules(modules)
            allowOverride(true)
        }
        Log.i("KoinModule", "Koin initialized successfully.")
    } catch (e: Exception) {
        Log.e("KoinModule", "Error starting Koin: ${e.message}")
    }
}

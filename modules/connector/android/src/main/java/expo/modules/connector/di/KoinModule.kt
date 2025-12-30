package expo.modules.connector.di

import android.util.Log
import expo.modules.connector.core.Broker
import expo.modules.connector.core.History
import expo.modules.connector.crypto.EncryptionService
import expo.modules.connector.interfaces.*
import expo.modules.connector.transports.ble.BleManager
import expo.modules.connector.transports.ble.BleScanner
import expo.modules.connector.transports.ble.BleTransport
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val connectorModule = module {
  // 1. Scanner (Singleton)
  singleOf(::BleScanner) { bind<IBleScanner>() }

  // 2. EncryptionService (Singleton)
  singleOf(::EncryptionService) { bind<IEncryptionService>() }

  // 3. History (Singleton)
  singleOf(::History)

  // 4. Broker (Singleton)
  single(createdAtStart = true) {
    Broker(
      context = get(),
      encryptionService = get(),
      scanner = get(),
      history = get(),
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
 * Collects all modules, including mocks via reflection if present.
 */
fun getAppModules(): List<org.koin.core.module.Module> {
  val modules = mutableListOf(connectorModule)

  try {
    val clazz = Class.forName("expo.modules.connector.mocks.MockModuleKt")
    val method = clazz.getMethod("getMockModule")
    val mockModule = method.invoke(null) as org.koin.core.module.Module
    modules.add(mockModule)
    Log.i("KoinModule", "Successfully injected mockModule via reflection")
  } catch (e: ClassNotFoundException) {
    // Normal build, no mocks present
  } catch (e: Exception) {
    Log.e("KoinModule", "Failed to inject mockModule: ${e.message}")
  }

  return modules
}

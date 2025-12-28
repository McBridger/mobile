package expo.modules.connector.di

import android.content.Context
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
    
    // 2. EncryptionService (Singleton, needs Context)
    singleOf(::EncryptionService) { bind<IEncryptionService>() }
    
    // 3. History (Singleton, needs Context)
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
            // scope uses default value
        )
    }
}

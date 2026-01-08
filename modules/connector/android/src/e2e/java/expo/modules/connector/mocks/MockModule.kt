package expo.modules.connector.mocks

import expo.modules.connector.interfaces.IBleManager
import expo.modules.connector.interfaces.IBleScanner
import expo.modules.connector.interfaces.IEncryptionService
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val mockModule = module {
    // Override history limit for E2E tests
    single(named("MAX_HISTORY_SIZE")) { 2 }

    singleOf(::MockBleScanner) { bind<IBleScanner>() }
    singleOf(::MockBleManager) { bind<IBleManager>() }
    singleOf(::MockEncryptionService) { bind<IEncryptionService>() }
}

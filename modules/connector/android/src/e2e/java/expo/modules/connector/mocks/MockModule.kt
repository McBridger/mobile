package expo.modules.connector.mocks

// MockBleManager
// MockBleScanner
// MockEncryptionService
import expo.modules.connector.interfaces.IBleManager
import expo.modules.connector.interfaces.IBleScanner
import expo.modules.connector.interfaces.IEncryptionService
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val mockModule = module {
    singleOf(::MockBleScanner) { bind<IBleScanner>() }
    singleOf(::MockBleManager) { bind<IBleManager>() }
    singleOf(::MockEncryptionService) { bind<IEncryptionService>() }
}

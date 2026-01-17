package expo.modules.connector.interfaces

import no.nordicsemi.android.support.v18.scanner.ScanSettings

sealed class ScanConfig(val androidMode: Int, val timeoutMs: Long) {
    object Aggressive : ScanConfig(ScanSettings.SCAN_MODE_LOW_LATENCY, 60_000L)
    object Passive : ScanConfig(ScanSettings.SCAN_MODE_LOW_POWER, Long.MAX_VALUE)
    
    // For cases when we want to stop scanning completely but keep the flow alive
    object Idle : ScanConfig(ScanSettings.SCAN_MODE_OPPORTUNISTIC, Long.MAX_VALUE)
}

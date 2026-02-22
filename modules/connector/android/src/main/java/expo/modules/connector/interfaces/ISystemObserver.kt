package expo.modules.connector.interfaces

import kotlinx.coroutines.flow.StateFlow

/**
 * Single Source of Truth for system-level states.
 * Centralizes monitoring of network, bluetooth, and app lifecycle.
 */
interface ISystemObserver {
    val isForeground: StateFlow<Boolean>
    val isNetworkHighSpeed: StateFlow<Boolean> // Wi-Fi or Ethernet
    val isBluetoothEnabled: StateFlow<Boolean>

    /**
     * Notify observer about lifecycle changes.
     */
    fun setForeground(isForeground: Boolean)
    
    fun stop()
}

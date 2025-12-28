package expo.modules.connector.models

data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int
)

package expo.modules.connector.core

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import expo.modules.connector.interfaces.ISystemObserver
import expo.modules.connector.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SystemObserver(private val context: Context) : ISystemObserver {
    private val TAG = "SystemObserver"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isForeground = MutableStateFlow(true)
    override val isForeground = _isForeground.asStateFlow()

    private val _isNetworkHighSpeed = MutableStateFlow(false)
    override val isNetworkHighSpeed = _isNetworkHighSpeed.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(false)
    override val isBluetoothEnabled = _isBluetoothEnabled.asStateFlow()

    private val _localIpAddress = MutableStateFlow<String?>(null)
    override val localIpAddress = _localIpAddress.asStateFlow()

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkState(network)
        }

        override fun onLost(network: Network) {
            // Force reset when network is explicitly lost
            updateNetworkState(null)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            updateNetworkState(network)
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                _isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
                Log.d(TAG, "Bluetooth state changed: ${_isBluetoothEnabled.value}")
            }
        }
    }

    init {
        // Initial state sync using the currently active network
        updateNetworkState(connectivityManager.activeNetwork)
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // Initial Bluetooth Check
        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled == true
        context.registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        
        Log.i(TAG, "SystemObserver initialized. Net: ${_isNetworkHighSpeed.value}, IP: ${_localIpAddress.value}, BT: ${_isBluetoothEnabled.value}")
    }

    private fun updateNetworkState(network: Network?) {
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        
        val isHighSpeed = capabilities?.let {
            it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
            it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } ?: false

        // If network is null, IP is definitely null. Otherwise, fetch it.
        val currentIp = if (network != null) NetworkUtils.getLocalIpAddress() else null
        
        if (_isNetworkHighSpeed.value != isHighSpeed) {
            _isNetworkHighSpeed.value = isHighSpeed
            Log.d(TAG, "High-speed network status: $isHighSpeed")
        }

        if (_localIpAddress.value != currentIp) {
            _localIpAddress.value = currentIp
            Log.d(TAG, "Local IP address changed: $currentIp")
        }
    }

    override fun setForeground(isForeground: Boolean) {
        _isForeground.value = isForeground
    }

    override fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error during stop: ${e.message}")
        }
        scope.cancel()
    }
}

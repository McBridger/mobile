package expo.modules.connector.mocks

import android.content.Context
import expo.modules.connector.interfaces.IEncryptionService
import java.util.UUID
import android.util.Log

class MockEncryptionService(context: Context) : IEncryptionService {
    private val TAG = "MockEncryptionService"
    private val prefs = context.getSharedPreferences("mock_crypto_prefs", Context.MODE_PRIVATE)

    override fun isReady(): Boolean = prefs.getBoolean("is_setup", false)
    
    override fun getMnemonic(): String? = prefs.getString("mnemonic", null)

    override fun setup(mnemonic: String, saltHex: String) {
        Log.d(TAG, "Mock setup called with mnemonic: $mnemonic")
        prefs.edit()
            .putBoolean("is_setup", true)
            .putString("mnemonic", mnemonic)
            .apply()
    }

    override fun load(): Boolean {
        val ready = isReady()
        Log.d(TAG, "Mock load called, returning: $ready")
        return ready
    }


    override fun clear() {
        Log.d(TAG, "Mock clear called")
        prefs.edit().clear().apply()
    }

    override fun derive(info: String, byteCount: Int): ByteArray? {
        // Return fixed set of bytes (e.g., all zeros)
        // This makes encryption deterministic
        return ByteArray(byteCount) { 0 }
    }

    override fun deriveUuid(info: String): UUID? {
        // Return predictable UUIDs for tests
        return when (info) {
            "McBridge_Advertise_UUID" -> UUID.fromString("00000000-0000-0000-0000-000000000001")
            "McBridge_Service_UUID"   -> UUID.fromString("00000000-0000-0000-0000-000000000002")
            else -> UUID.nameUUIDFromBytes(info.toByteArray())
        }
    }

    override fun encrypt(data: ByteArray, keyBytes: ByteArray): ByteArray? {
        // In mock, we can return raw data for simplicity
        Log.d(TAG, "Mock encrypt: returning raw data (size: ${data.size})")
        return data
    }

    override fun decrypt(combinedData: ByteArray, keyBytes: ByteArray): ByteArray? {
        // Return as is in mock
        Log.d(TAG, "Mock decrypt: returning raw data (size: ${combinedData.size})")
        return combinedData
    }
}

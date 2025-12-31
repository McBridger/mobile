package expo.modules.connector.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import expo.modules.connector.interfaces.IEncryptionService
import java.nio.ByteBuffer
import java.security.spec.KeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

class EncryptionService(private val context: Context) : IEncryptionService {
    private var masterKey: ByteArray? = null
    private var mnemonic: String? = null
    
    // Cache for derived results to avoid heavy re-computation
    private val derivedCache = mutableMapOf<String, ByteArray>()
    private val uuidCache = mutableMapOf<String, UUID>()

    override fun isReady(): Boolean = masterKey != null
    
    override fun getMnemonic(): String? = mnemonic

    override fun setup(mnemonic: String, saltHex: String) {
        Log.i(TAG, "setup: Performing key derivation.")
        clearCache()
        this.mnemonic = mnemonic
        val saltBytes = hexToBytes(saltHex)
        
        val iterations = 600_000
        val keyLength = 256
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(mnemonic.toCharArray(), saltBytes, iterations, keyLength)
        val derivedKey = factory.generateSecret(spec).encoded
        this.masterKey = derivedKey
        
        persist(mnemonic, bytesToHex(derivedKey))
    }

    override fun load(): Boolean {
        return try {
            Log.d(TAG, "load: Attempting to open EncryptedSharedPreferences...")
            val prefs = getSharedPrefs()
            val savedMasterKeyHex = prefs.getString(KEY_MASTER_KEY, null)
            val savedMnemonic = prefs.getString(KEY_MNEMONIC, null)

            if (savedMasterKeyHex == null) {
                Log.w(TAG, "load: No master key found in storage.")
                return false
            }

            Log.i(TAG, "load: Found saved master key. System ready.")
            this.masterKey = hexToBytes(savedMasterKeyHex)
            this.mnemonic = savedMnemonic
            true
        } catch (e: Exception) {
            Log.e(TAG, "load: Fatal error while reading encrypted storage: ${e.message}", e)
            false
        }
    }

    private fun persist(mnemonic: String, masterKeyHex: String) {
        try {
            getSharedPrefs().edit().apply {
                putString(KEY_MNEMONIC, mnemonic)
                putString(KEY_MASTER_KEY, masterKeyHex)
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "persist: Error saving: ${e.message}")
        }
    }

    override fun clear() {
        try {
            getSharedPrefs().edit().clear().apply()
            this.masterKey = null
            this.mnemonic = null
            clearCache()
        } catch (e: Exception) {
            Log.e(TAG, "clear: Error clearing: ${e.message}")
        }
    }

    private fun clearCache() {
        derivedCache.clear()
        uuidCache.clear()
    }

    private fun getSharedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun derive(info: String, byteCount: Int): ByteArray? {
        val cacheKey = "$info-$byteCount"
        derivedCache[cacheKey]?.let { return it }

        val ikm = masterKey ?: return null
        val hmac = Mac.getInstance("HmacSHA256")
        val salt = ByteArray(hmac.macLength) { 0 }
        hmac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = hmac.doFinal(ikm)

        return hkdfExpand(prk, info.toByteArray(Charsets.UTF_8), byteCount).also {
            derivedCache[cacheKey] = it
        }
    }

    override fun deriveUuid(info: String): UUID? {
        uuidCache[info]?.let { return it }
        
        val bytes = derive(info, 16) ?: return null
        val buffer = ByteBuffer.wrap(bytes)
        return UUID(buffer.long, buffer.long).also {
            uuidCache[info] = it
        }
    }

    override fun encrypt(data: ByteArray, keyBytes: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val nonce = ByteArray(GCM_NONCE_LENGTH).apply { java.security.SecureRandom().nextBytes(this) }
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), spec)
            val cipherText = cipher.doFinal(data)
            ByteBuffer.allocate(nonce.size + cipherText.size).put(nonce).put(cipherText).array()
        } catch (e: Exception) {
            Log.e(TAG, "encrypt: Failed to encrypt data: ${e.message}", e)
            null
        }
    }

    override fun decrypt(combinedData: ByteArray, keyBytes: ByteArray): ByteArray? {
        if (combinedData.size < GCM_NONCE_LENGTH + GCM_TAG_LENGTH) {
            Log.e(TAG, "decrypt: Data too short (size: ${combinedData.size})")
            return null
        }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val nonce = combinedData.sliceArray(0 until GCM_NONCE_LENGTH)
            val encrypted = combinedData.sliceArray(GCM_NONCE_LENGTH until combinedData.size)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), spec)
            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "decrypt: Failed to decrypt data: ${e.message}", e)
            null
        }
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, outLen: Int): ByteArray {
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(prk, "HmacSHA256"))
        val okm = ByteBuffer.allocate(ceil(outLen.toDouble() / hmac.macLength).toInt() * hmac.macLength)
        var t = byteArrayOf()
        for (i in 1..ceil(outLen.toDouble() / hmac.macLength).toInt()) {
            hmac.update(t); hmac.update(info); hmac.update(i.toByte())
            t = hmac.doFinal(); okm.put(t)
        }
        return ByteArray(outLen).also { okm.flip(); okm.get(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val s = hex.removePrefix("0x")
        check(s.length % 2 == 0) { "Must have an even length" }
        return s.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "EncryptionService"
        private const val PREFS_NAME = "mcbridge_secure_prefs"
        private const val KEY_MASTER_KEY = "master_key_hex"
        private const val KEY_MNEMONIC = "mnemonic"
        private const val GCM_NONCE_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }
}

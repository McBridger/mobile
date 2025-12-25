package expo.modules.connector.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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

object EncryptionService {
    private const val TAG = "EncryptionService"
    private const val PREFS_NAME = "mcbridge_secure_prefs"
    private const val KEY_MASTER_KEY = "master_key_hex"
    private const val KEY_MNEMONIC = "mnemonic"
    
    private const val GCM_NONCE_LENGTH = 12
    private const val GCM_TAG_LENGTH = 16
    
    private var masterKey: ByteArray? = null
    private var mnemonic: String? = null

    fun isReady(): Boolean = masterKey != null

    /**
     * One-time setup: derives master key via PBKDF2 and persists it along with the mnemonic.
     */
    fun setup(context: Context, mnemonic: String, saltHex: String) {
        Log.i(TAG, "setup: Performing one-time key derivation.")
        this.mnemonic = mnemonic
        val saltBytes = hexToBytes(saltHex)
        
        // Heavy derivation happens here
        val iterations = 600_000
        val keyLength = 256
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(mnemonic.toCharArray(), saltBytes, iterations, keyLength)
        val derivedKey = factory.generateSecret(spec).encoded
        this.masterKey = derivedKey
        
        persist(context, mnemonic, bytesToHex(derivedKey))
    }

    /**
     * Fast load: retrieves already derived master key from secure storage.
     */
    fun load(context: Context): Boolean {
        return try {
            val prefs = getSharedPrefs(context)
            val savedMasterKeyHex = prefs.getString(KEY_MASTER_KEY, null)
            val savedMnemonic = prefs.getString(KEY_MNEMONIC, null)

            if (savedMasterKeyHex != null) {
                Log.i(TAG, "load: Found saved master key. System ready.")
                this.masterKey = hexToBytes(savedMasterKeyHex)
                this.mnemonic = savedMnemonic
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "load: Failed to load credentials: ${e.message}")
            false
        }
    }

    private fun persist(context: Context, mnemonic: String, masterKeyHex: String) {
        try {
            val prefs = getSharedPrefs(context)
            prefs.edit().apply {
                putString(KEY_MNEMONIC, mnemonic)
                putString(KEY_MASTER_KEY, masterKeyHex)
                apply()
            }
            Log.d(TAG, "persist: Credentials saved securely.")
        } catch (e: Exception) {
            Log.e(TAG, "persist: Error saving: ${e.message}")
        }
    }

    private fun getSharedPrefs(context: Context): SharedPreferences {
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

    fun derive(info: String, byteCount: Int): ByteArray? {
        val ikm = masterKey ?: return null
        val prk = hkdfExtract(ikm)
        return hkdfExpand(prk, info.toByteArray(Charsets.UTF_8), byteCount)
    }

    fun deriveUuid(info: String): UUID? {
        val bytes = derive(info, 16) ?: return null
        val buffer = ByteBuffer.wrap(bytes)
        return UUID(buffer.long, buffer.long)
    }

    fun encrypt(data: ByteArray, keyBytes: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val nonce = ByteArray(GCM_NONCE_LENGTH).apply { java.security.SecureRandom().nextBytes(this) }
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), spec)
            val cipherText = cipher.doFinal(data)
            ByteBuffer.allocate(nonce.size + cipherText.size).put(nonce).put(cipherText).array()
        } catch (e: Exception) { null }
    }

    fun decrypt(combinedData: ByteArray, keyBytes: ByteArray): ByteArray? {
        if (combinedData.size < GCM_NONCE_LENGTH + GCM_TAG_LENGTH) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val nonce = combinedData.sliceArray(0 until GCM_NONCE_LENGTH)
            val encrypted = combinedData.sliceArray(GCM_NONCE_LENGTH until combinedData.size)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), spec)
            cipher.doFinal(encrypted)
        } catch (e: Exception) { null }
    }

    private fun hkdfExtract(ikm: ByteArray): ByteArray {
        val hmac = Mac.getInstance("HmacSHA256")
        val salt = ByteArray(hmac.macLength) { 0 }
        hmac.init(SecretKeySpec(salt, "HmacSHA256"))
        return hmac.doFinal(ikm)
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
        return ByteArray(s.length / 2) { i ->
            ((Character.digit(s[i * 2], 16) shl 4) + Character.digit(s[i * 2 + 1], 16)).toByte()
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
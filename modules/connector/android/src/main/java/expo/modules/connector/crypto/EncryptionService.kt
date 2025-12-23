package expo.modules.connector.crypto

import android.util.Log
import java.nio.ByteBuffer
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

object EncryptionService {
    private const val TAG = "EncryptionService"
    private const val PBKDF2_ITERATIONS = 600_000
    private const val KEY_LENGTH = 256
    private const val GCM_NONCE_LENGTH = 12
    private const val GCM_TAG_LENGTH = 16

    private var masterKey: SecretKey? = null
    private var salt: ByteArray = byteArrayOf()

    fun isReady(): Boolean = masterKey != null

    fun setup(passphrase: String, saltHex: String) {
        try {
            this.salt = hexToBytes(saltHex)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec: KeySpec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
            val tmp = factory.generateSecret(spec)
            this.masterKey = SecretKeySpec(tmp.encoded, "AES")
        } catch (e: Exception) {
            Log.e(TAG, "Setup failed: ${e.message}")
        }
    }

    fun derive(info: String, byteCount: Int): ByteArray? {
        val ikm = masterKey?.encoded ?: return null
        val prk = hkdfExtract(ikm)
        return hkdfExpand(prk, info.toByteArray(Charsets.UTF_8), byteCount)
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
        // CryptoKit HKDF uses a salt of HashLen zeros if not provided
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
}
package expo.modules.connector.interfaces

import android.content.Context
import java.util.UUID

interface IEncryptionService {
    fun isReady(): Boolean
    fun getMnemonic(): String?
    fun setup(mnemonic: String, saltHex: String)
    fun load(): Boolean
    fun clear()
    fun derive(info: String, byteCount: Int): ByteArray?
    fun deriveUuid(info: String): UUID?
    fun encrypt(data: ByteArray, keyBytes: ByteArray): ByteArray?
    fun decrypt(combinedData: ByteArray, keyBytes: ByteArray): ByteArray?
}

package expo.modules.connector.crypto

import expo.modules.connector.interfaces.IEncryptionService
import expo.modules.connector.models.Message
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.math.abs

private const val ENCRYPTION_DOMAIN = "McBridge_Encryption_Domain"

/**
 * Extension to encrypt Message using IEncryptionService
 */
fun IEncryptionService.encryptMessage(message: Message): ByteArray? {
    val key = derive(ENCRYPTION_DOMAIN, 32) ?: return null
    val data = Json.encodeToString(message).toByteArray(Charsets.UTF_8)
    return encrypt(data, key)
}

/**
 * Extension to decrypt Message using IEncryptionService
 */
fun IEncryptionService.decryptMessage(data: ByteArray, address: String? = null): Message? {
    return try {
        val key = derive(ENCRYPTION_DOMAIN, 32) ?: return null
        val decrypted = decrypt(data, key) ?: return null

        val message = Json.decodeFromString<Message>(String(decrypted, Charsets.UTF_8))

        // Security check: message should not be older than 60 seconds
        if (abs(System.currentTimeMillis() - message.timestamp) > 60_000L) {
            return null
        }

        return if (address != null) message.withAddress(address) else message
    } catch (e: Exception) {
        null
    }
}

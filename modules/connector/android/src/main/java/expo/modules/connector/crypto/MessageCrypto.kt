package expo.modules.connector.crypto

import com.google.gson.Gson
import expo.modules.connector.interfaces.IEncryptionService
import expo.modules.connector.models.Message
import kotlin.math.abs

private val gson = Gson()
private const val ENCRYPTION_DOMAIN = "McBridge_Encryption_Domain"

/**
 * Extension to encrypt Message using IEncryptionService
 */
fun IEncryptionService.encryptMessage(message: Message): ByteArray? {
    val key = derive(ENCRYPTION_DOMAIN, 32) ?: return null
    val transferMsg = Message.Transfer(
        type = message.typeId,
        payload = message.value,
        timestamp = message.timestamp / 1000.0
    )
    val data = gson.toJson(transferMsg).toByteArray(Charsets.UTF_8)
    return encrypt(data, key)
}

/**
 * Extension to decrypt Message using IEncryptionService
 */
fun IEncryptionService.decryptMessage(data: ByteArray, address: String? = null): Message? {
    return try {
        val key = derive(ENCRYPTION_DOMAIN, 32) ?: return null
        val decrypted = decrypt(data, key) ?: return null
        
        val transferMsg = gson.fromJson(
            String(decrypted, Charsets.UTF_8), 
            Message.Transfer::class.java
        )

        val msgTs = transferMsg.timestamp ?: (System.currentTimeMillis() / 1000.0)
        // Security check: message should not be older than 60 seconds
        if (abs((System.currentTimeMillis() / 1000.0) - msgTs) > 60) {
            return null
        }

        Message(
            typeId = transferMsg.type,
            value = transferMsg.payload,
            address = address,
            timestamp = (msgTs * 1000).toLong()
        )
    } catch (e: Exception) {
        null
    }
}

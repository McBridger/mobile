package expo.modules.connector.crypto

import android.util.Log
import expo.modules.connector.interfaces.IEncryptionService
import expo.modules.connector.models.Message
import kotlin.math.abs

private const val ENCRYPTION_DOMAIN = "McBridge_Encryption_Domain"

/** Extension to encrypt Message using IEncryptionService */
fun IEncryptionService.encryptMessage(message: Message): ByteArray? {
    val key = derive(ENCRYPTION_DOMAIN, 32) ?: return null
    val data = message.toMsgPack()
    return encrypt(data, key)
}

/** Extension to decrypt Message using IEncryptionService */
fun IEncryptionService.decryptMessage(data: ByteArray, address: String? = null): Message? {
    return try {
        val key = derive(ENCRYPTION_DOMAIN, 32) ?: return null
        val decrypted = decrypt(data, key)
        if (decrypted == null) {
            Log.e("MessageCrypto", "decryptMessage: Decryption failed (decrypted is null)")
            return null
        }

        Log.d(
                "MessageCrypto",
                "decryptMessage: Decrypted data size: ${decrypted.size}, hex: ${decrypted.joinToString("") { "%02x".format(it) }}"
        )

        val message =
                try {
                    Message.fromMsgPack(decrypted)
                } catch (e: Exception) {
                    Log.e(
                            "MessageCrypto",
                            "decryptMessage: MsgPack parsing failed: ${e.message}",
                            e
                    )
                    return null
                }

        // Security check: message should not be older than 60 seconds
        val now = System.currentTimeMillis() / 1000.0
        val diff = abs(now - message.timestamp)
        if (diff > 60.0) {
            Log.e(
                    "MessageCrypto",
                    "decryptMessage: Timestamp check failed. Diff: $diff, Msg TS: ${message.timestamp}, Now: $now"
            )
            return null
        }

        Log.d(
                "MessageCrypto",
                "decryptMessage: Successfully parsed ${message.getType()} message from $address"
        )
        return if (address != null) message.withAddress(address) else message
    } catch (e: Exception) {
        Log.e("MessageCrypto", "decryptMessage: Unexpected error: ${e.message}", e)
        null
    }
}

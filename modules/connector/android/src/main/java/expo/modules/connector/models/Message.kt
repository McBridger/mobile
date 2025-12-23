package expo.modules.connector.models
 
import android.os.Bundle
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import expo.modules.connector.crypto.EncryptionService
import java.util.UUID
import kotlin.math.abs
 
data class Message(
    @SerializedName("t") val typeId: Int, // JSON -> {"t": 0}
    @SerializedName("p") val value: String,
    @SerializedName("a") val address: String? = null,
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("ts") val timestamp: Long = System.currentTimeMillis()
) {
    constructor(
        type: Type,
        value: String,
        address: String? = null,
        id: String = UUID.randomUUID().toString(),
        timestamp: Long = System.currentTimeMillis()
    ) : this(type.ordinal, value, address, id, timestamp)

    enum class Type {
        CLIPBOARD,   // 0
        DEVICE_NAME, // 1
        FILE_URL     // 2
    }
    
    fun getType(): Type = Type.entries[typeId]

    fun toJson(): String = gson.toJson(this)

    // To JS
    fun toBundle(): Bundle = Bundle().apply {
        putString("type", getType().name)
        putString("value", value)
        putString("address", address)
        putString("id", id)
        putLong("timestamp", timestamp)
    }

    /**
     * Encrypts the message for secure transfer (Matches iOS)
     */
    fun toEncryptedData(): ByteArray? {
        val transferMsg = TransferMessage(typeId, value, timestamp / 1000.0)
        val data = gson.toJson(transferMsg).toByteArray(Charsets.UTF_8)
        
        val key = EncryptionService.derive("McBridge_Encryption_Domain", 32) ?: return null
        return EncryptionService.encrypt(data, key)
    }

    companion object {
        private const val TAG = "Message"
        private val gson = Gson()

        fun fromJson(json: String, address: String? = null): Message? {
            return try {
                val parsed = gson.fromJson(json, TransferMessage::class.java)
                Log.d(TAG, "Data parsed from JSON: Type=${parsed.type}, Payload=${parsed.payload}")

                val msg = Message(
                    type = Type.entries[parsed.type],
                    value = parsed.payload,
                    address = address
                )

                Log.d(TAG, "Data converted to Message: Type=${msg.getType()}, Payload=${msg.value}, Address=${msg.address}, ID=${msg.id}")

                return msg
            } catch (e: Exception) {
                Log.e(TAG, "fromJson: Error parsing JSON message: ${e.message}, JSON: $json")
                null
            }
        }

        /**
         * Decrypts data and creates a Message (Matches iOS)
         */
        @Throws(Exception::class)
        fun fromEncryptedData(data: ByteArray, address: String? = null): Message {
            val key = EncryptionService.derive("McBridge_Encryption_Domain", 32) 
                ?: throw Exception("Encryption not initialized")

            val decrypted = EncryptionService.decrypt(data, key) 
                ?: throw Exception("Decryption failed")

            val transferMsg = gson.fromJson(String(decrypted, Charsets.UTF_8), TransferMessage::class.java)

            // Replay Protection
            val msgTs = transferMsg.timestamp ?: (System.currentTimeMillis() / 1000.0)
            if (abs((System.currentTimeMillis() / 1000.0) - msgTs) > 60) {
                throw Exception("Message expired")
            }

            return Message(
                typeId = transferMsg.type,
                value = transferMsg.payload,
                address = address,
                timestamp = (msgTs * 1000).toLong()
            )
        }
    }

    private data class TransferMessage( 
        @SerializedName("t") val type: Int, 
        @SerializedName("p") val payload: String,
        @SerializedName("ts") val timestamp: Double? = null // Optional for backward compatibility
    )
}

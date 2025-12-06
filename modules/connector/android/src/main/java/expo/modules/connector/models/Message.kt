package expo.modules.connector.models
 
import android.os.Bundle
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.util.UUID
 
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
    }

    private data class TransferMessage(@SerializedName("t") val type: Int, @SerializedName("p") val payload: String)
}
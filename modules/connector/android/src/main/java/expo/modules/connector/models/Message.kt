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

        fun fromJson(json: String): Message? {
            return try {
                val msg = gson.fromJson(json, Message::class.java)
                Log.d(TAG, "Data received as JSON: Type=${msg.getType()}, Payload=${msg.value}, Address=${msg.address}, ID=${msg.id}")
                Type.entries[msg.typeId]
                msg
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing JSON message: ${e.message}")
                null
            }
        }
    }
}
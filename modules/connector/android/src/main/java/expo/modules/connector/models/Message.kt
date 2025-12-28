package expo.modules.connector.models

import android.os.Bundle
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.util.UUID

data class Message(
    @SerializedName("t") val typeId: Int,
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

    fun toBundle(): Bundle = Bundle().apply {
        putString("type", getType().name)
        putString("value", value)
        putString("address", address)
        putString("id", id)
        putLong("timestamp", timestamp)
    }

    companion object {
        private val gson = Gson()

        fun fromJson(json: String, address: String? = null): Message? {
            return try {
                gson.fromJson(json, Message::class.java).copy(address = address)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * DTO for transfer over BLE/Network to match iOS structure
     */
    data class Transfer(
        @SerializedName("t") val type: Int,
        @SerializedName("p") val payload: String,
        @SerializedName("ts") val timestamp: Double? = null
    )
}
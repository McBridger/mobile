package expo.modules.connector.models

import android.os.Bundle
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.UUID

@Serializable
data class Message(
    @SerialName("t") val typeId: Int,
    @SerialName("p") val value: String,
    @SerialName("a") val address: String? = null,
    @SerialName("id") val id: String = UUID.randomUUID().toString(),
    @SerialName("ts") val timestamp: Long = System.currentTimeMillis()
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

    fun toJson(): String = Json.encodeToString(this)

    fun toBundle(): Bundle = Bundle().apply {
        putString("type", getType().name)
        putString("value", value)
        putString("address", address)
        putString("id", id)
        putLong("timestamp", timestamp)
    }

    companion object {
        fun fromJson(json: String, address: String? = null): Message? = runCatching {
            Json.decodeFromString<Message>(json)
                .let { msg ->
                    address?.let { msg.copy(address = it) } ?: msg
                }
        }.getOrNull()
    }

    /**
     * DTO for transfer over BLE/Network to match iOS structure
     */
    @Serializable
    data class Transfer(
        @SerialName("t") val type: Int,
        @SerialName("p") val payload: String,
        @SerialName("ts") val timestamp: Double? = null
    )
}
package expo.modules.connector.models

import android.os.Bundle
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

enum class MessageType(val id: Int) {
    CLIPBOARD(0),
    DEVICE_NAME(1),
    FILE_URL(2);

    companion object {
        fun fromId(id: Int) = entries.find { it.id == id }
    }
}

object MessageSerializer : JsonContentPolymorphicSerializer<Message>(Message::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Message> {
        return when (val typeId = element.jsonObject["t"]?.jsonPrimitive?.intOrNull) {
            MessageType.CLIPBOARD.id -> ClipboardMessage.serializer()
            MessageType.DEVICE_NAME.id -> IntroMessage.serializer()
            MessageType.FILE_URL.id -> FileMessage.serializer()
            else -> throw SerializationException("Unknown message type: $typeId. Full JSON: $element")
        }
    }
}

@Serializable(with = MessageSerializer::class)
sealed class Message {
    abstract val typeId: Int
    abstract val id: String
    abstract val timestamp: Long
    abstract var address: String?

    open fun toBundle(): Bundle = Bundle().apply {
        putString("type", getType().name)
        putString("id", id)
        putString("address", address)
        putLong("timestamp", timestamp)
    }

    fun getType(): MessageType = MessageType.fromId(typeId)?: throw IllegalStateException("Invalid typeId: $typeId")
    fun toJson(): String = Json.encodeToString(this)

    fun withAddress(address: String): Message {
        this.address = address
        return this
    }
}

@Serializable
data class ClipboardMessage(
    @SerialName("p") val value: String,
    @SerialName("t") override val typeId: Int = MessageType.CLIPBOARD.id,

    // Base fields
    @SerialName("a") override var address: String? = null,
    @SerialName("id") override val id: String = UUID.randomUUID().toString(),
    @SerialName("ts") override val timestamp: Long = System.currentTimeMillis(),
) : Message() {

    override fun toBundle() = super.toBundle().apply {
        putString("value", value)
    }
}

@Serializable
data class IntroMessage(
    @SerialName("p") val value: String,
    @SerialName("t") override val typeId: Int = MessageType.DEVICE_NAME.id,

    // Base fields
    @SerialName("a") override var address: String? = null,
    @SerialName("id") override val id: String = UUID.randomUUID().toString(),
    @SerialName("ts") override val timestamp: Long = System.currentTimeMillis(),
) : Message() {

    override fun toBundle() = super.toBundle().apply {
        putString("value", value)
    }
}

@Serializable
data class FileMessage(
    @SerialName("u") val url: String,
    @SerialName("n") val name: String,
    @SerialName("s") val size: String,
    @SerialName("t") override val typeId: Int = MessageType.FILE_URL.id,

    // Base fields
    @SerialName("a") override var address: String? = null,
    @SerialName("id") override val id: String = UUID.randomUUID().toString(),
    @SerialName("ts") override val timestamp: Long = System.currentTimeMillis(),
) : Message() {

    override fun toBundle() = super.toBundle().apply {
        putString("url", url)
        putString("name", name)
        putString("size", size)
    }
}
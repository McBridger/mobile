package expo.modules.connector.models

import android.os.Bundle
import com.ensarsarajcic.kotlinx.serialization.msgpack.MsgPack
import com.ensarsarajcic.kotlinx.serialization.msgpack.MsgPackConfiguration
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

enum class MessageType(val id: String) {
    CLIPBOARD("0"),
    DEVICE_NAME("1"),
    FILE_URL("2");

    companion object {
        fun fromId(id: String?) = entries.find { it.id == id }
    }
}

fun nowSeconds(): Double = System.currentTimeMillis() / 1000.0

private val messageModule = SerializersModule {
    polymorphic(Message::class) {
        subclass(ClipboardMessage::class)
        subclass(IntroMessage::class)
        subclass(FileMessage::class)
    }
}

public val mJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    serializersModule = messageModule
    classDiscriminator = "t"
}

public val mMsgPack =
        MsgPack(MsgPackConfiguration(ignoreUnknownKeys = true), serializersModule = messageModule)

@Serializable
sealed class Message {
    abstract val typeId: String
    abstract val id: String
    abstract val timestamp: Double
    abstract var address: String?

    open fun toBundle(): Bundle =
            Bundle().apply {
                putString("type", getType().name)
                putString("id", id)
                putString("address", address)
                putDouble("timestamp", timestamp)
            }

    fun getType(): MessageType =
            MessageType.fromId(typeId) ?: throw IllegalStateException("Invalid typeId: $typeId")

    fun toJson(): String = mJson.encodeToString(this)
    fun toMsgPack(): ByteArray = mMsgPack.encodeToByteArray(this)

    fun withAddress(address: String): Message {
        this.address = address
        return this
    }

    companion object {
        fun fromJSON(data: String): Message = mJson.decodeFromString(data)
        fun fromMsgPack(data: ByteArray): Message = mMsgPack.decodeFromByteArray(data)

        // Generic helpers
        inline fun <reified T> toJSON(value: T): String = mJson.encodeToString(value)
        inline fun <reified T> fromJSONGeneric(data: String): T = mJson.decodeFromString(data)

        inline fun <reified T> toMsgPackGeneric(value: T): ByteArray =
                mMsgPack.encodeToByteArray(value)
        inline fun <reified T> fromMsgPackGeneric(data: ByteArray): T =
                mMsgPack.decodeFromByteArray(data)
    }
}

@Serializable
@SerialName("0")
data class ClipboardMessage(
        @SerialName("p") val value: String,
        @Transient override val typeId: String = MessageType.CLIPBOARD.id,

        // Base fields
        @SerialName("a") override var address: String? = null,
        @SerialName("id") override val id: String = UUID.randomUUID().toString(),
        @SerialName("ts") override val timestamp: Double = nowSeconds()
) : Message() {

    override fun toBundle() = super.toBundle().apply { putString("value", value) }
}

@Serializable
@SerialName("1")
data class IntroMessage(
        @SerialName("p") val value: String,
        @Transient override val typeId: String = MessageType.DEVICE_NAME.id,

        // Base fields
        @SerialName("a") override var address: String? = null,
        @SerialName("id") override val id: String = UUID.randomUUID().toString(),
        @SerialName("ts") override val timestamp: Double = nowSeconds()
) : Message() {

    override fun toBundle() = super.toBundle().apply { putString("value", value) }
}

@Serializable
@SerialName("2")
data class FileMessage(
        @SerialName("u") val url: String,
        @SerialName("n") val name: String,
        @SerialName("s") val size: String,
        @Transient override val typeId: String = MessageType.FILE_URL.id,

        // Base fields
        @SerialName("a") override var address: String? = null,
        @SerialName("id") override val id: String = UUID.randomUUID().toString(),
        @SerialName("ts") override val timestamp: Double = nowSeconds()
) : Message() {

    override fun toBundle() =
            super.toBundle().apply {
                putString("url", url)
                putString("name", name)
                putString("size", size)
            }
}

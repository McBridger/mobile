package expo.modules.connector.models

import android.os.Bundle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import java.util.UUID

@Serializable
enum class MessageType(val id: Int) {
    TINY(0), INTRO(1), BLOB(2), CHUNK(3);
    companion object {
        fun fromInt(id: Int) = values().find { it.id == id } ?: TINY
    }
}

@Serializable
enum class BlobType {
    FILE, TEXT, IMAGE;
    companion object {
        fun fromString(s: String) = values().find { it.name == s } ?: FILE
    }
}

val messageModule = SerializersModule {
    polymorphic(Message::class) {
        subclass(TinyMessage::class)
        subclass(IntroMessage::class)
        subclass(BlobMessage::class)
    }
}

val mJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    serializersModule = messageModule
}

@Serializable
sealed class Message {
    abstract val id: String
    abstract val timestamp: Double
    abstract var address: String?

    abstract fun getType(): MessageType
    abstract fun writePayload(sink: BufferedSink)

    open fun toBundle(): Bundle = Bundle().apply {
        putString("id", id)
        putString("type", getType().name)
        putDouble("timestamp", timestamp)
        putString("address", address)
    }

    fun toBytes(): ByteArray = Buffer().apply {
        writeByte(getType().id)
        val uuid = UUID.fromString(id)
        writeLong(uuid.mostSignificantBits)
        writeLong(uuid.leastSignificantBits)
        writeLong(java.lang.Double.doubleToLongBits(timestamp))
        writePayload(this)
    }.readByteArray()

    fun withAddress(addr: String): Message {
        this.address = addr
        return this
    }

    companion object {
        fun fromBytes(bytes: ByteArray): Message {
            val b = Buffer().write(bytes)
            val typeId = b.readByte().toInt()
            val id = UUID(b.readLong(), b.readLong()).toString()
            val ts = java.lang.Double.longBitsToDouble(b.readLong())
            
            return when (MessageType.fromInt(typeId)) {
                MessageType.TINY -> TinyMessage(id, ts, b.readStr())
                MessageType.INTRO -> IntroMessage(id, ts, b.readStr(), b.readStr(), b.readInt())
                MessageType.BLOB -> BlobMessage(id, ts, b.readStr(), b.readLong(), BlobType.fromString(b.readStr()))
                MessageType.CHUNK -> ChunkMessage(b.readLong(), b.readByteArray(), id)
            }
        }

        fun fromJSON(data: String): Message = mJson.decodeFromString(data)
        fun toJSONList(messages: List<Message>): String = mJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(Message.serializer()), messages)
        fun fromJSONList(json: String): List<Message> = mJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Message.serializer()), json)

        private fun BufferedSource.readStr(): String = readUtf8(readInt().toLong())
        
        internal fun writeS(sink: BufferedSink, s: String) {
            val bytes = s.toByteArray()
            sink.writeInt(bytes.size)
            sink.write(bytes)
        }
    }
}

@Serializable
class TinyMessage(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Double = System.currentTimeMillis() / 1000.0,
    val value: String,
    override var address: String? = null
) : Message() {
    override fun getType() = MessageType.TINY
    override fun writePayload(sink: BufferedSink) { writeS(sink, value) }
    override fun toBundle() = super.toBundle().apply { putString("value", value) }
}

@Serializable
class IntroMessage(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Double = System.currentTimeMillis() / 1000.0,
    val name: String,
    val ip: String,
    val port: Int,
    override var address: String? = null
) : Message() {
    override fun getType() = MessageType.INTRO
    override fun writePayload(sink: BufferedSink) {
        writeS(sink, name); writeS(sink, ip); sink.writeInt(port)
    }
    override fun toBundle() = super.toBundle().apply {
        putString("name", name); putString("ip", ip); putInt("port", port)
    }
}

@Serializable
class BlobMessage(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Double = System.currentTimeMillis() / 1000.0,
    val name: String,
    val size: Long,
    val blobType: BlobType,
    override var address: String? = null
) : Message() {
    override fun getType() = MessageType.BLOB
    override fun writePayload(sink: BufferedSink) {
        writeS(sink, name); sink.writeLong(size); writeS(sink, blobType.name)
    }
    override fun toBundle() = super.toBundle().apply {
        putString("name", name); putString("size", size.toString()); putString("blobType", blobType.name)
    }
}

class ChunkMessage(
    val offset: Long,
    val data: ByteArray,
    override val id: String, // Blob ID from header
    override val timestamp: Double = 0.0,
    override var address: String? = null
) : Message() {
    override fun getType() = MessageType.CHUNK
    override fun writePayload(sink: BufferedSink) {
        sink.writeLong(offset); sink.write(data)
    }
    override fun toBundle(): Bundle = Bundle().apply {
        putString("id", id)
        putString("type", "CHUNK")
    }
}

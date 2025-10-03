package expo.modules.connector

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import no.nordicsemi.android.ble.data.Data
import java.util.UUID

/**
 * A data class representing a message for communication.
 *
 * This class is designed in Kotlin for conciseness and safety, while being fully
 * interoperable with Java code thanks to the @JvmOverloads and @JvmStatic annotations.
 *
 * @param type The message type, corresponding to the ordinal of the MessageType enum.
 * @param value The main payload or value of the message.
 * @param address The Bluetooth address of the sender, optional.
 * @param id A unique identifier for the message, defaults to a random UUID.
 * @param timestamp The creation timestamp of the message, defaults to the current system time.
 */
data class BridgerMessage (
    @SerializedName("t") private val type: Int,
    @SerializedName("p") val value: String,
    @SerializedName("a") val address: String? = null,
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("ts") val timestamp: Long = System.currentTimeMillis()
) {

    /**
     * Secondary constructor for type-safe creation.
     * Developers should prefer using this constructor.
     * It accepts the MessageType enum and delegates to the primary constructor.
     * The @JvmOverloads annotation makes it convenient to call from Java.
     */
    @JvmOverloads
    constructor(
        type: MessageType, // Takes the enum for type safety
        value: String,
        address: String? = null,
        id: String = UUID.randomUUID().toString(),
        timestamp: Long = System.currentTimeMillis()
    ) : this( type = type.ordinal, value = value, address = address, id = id, timestamp = timestamp )

    /**
     * An enum representing the different types of messages that can be sent.
     */
    enum class MessageType {
        CLIPBOARD,
        DEVICE_NAME
    }

    /**
     * A computed property to get the MessageType enum from the raw `type` integer.
     */
    fun getType(): MessageType {
        return MessageType.entries[type]
    }

    /**
     * Converts the message to a Nordic Semi `Data` object for BLE transfer.
     */
    fun toData(): Data {
        val msg = TransferMessage(type, value)
        val json = gson.toJson(msg)
        return Data.from(json)
    }

    /**
     * Converts the message to an Android `Bundle` for passing between components.
     */
    fun toBundle(): Bundle {
        return Bundle().apply {
            putInt("type", type)
            putString("value", value)
            putString("address", address)
            putString("id", id)
            putLong("timestamp", timestamp)
        }
    }

    /**
     * Serializes the entire message object to a JSON string.
     */
    fun toJson(): String = gson.toJson(this)

    /**
     * Contains static factory methods and constants for the BridgerMessage class.
     */
    companion object {
        private const val TAG = "Message"
        private val gson = Gson()

        /**
         * Creates a BridgerMessage from raw BLE data and a device.
         * Annotated with @JvmStatic to be callable as a static method from Java.
         */
        @JvmStatic
        fun fromData(data: Data, device: BluetoothDevice): BridgerMessage? {
            Log.d(TAG, "Raw data received from peripheral: $data")

            val jsonString = data.getStringValue(0) ?: run {
                Log.w(TAG, "Received data, but it could not be parsed as a String (JSON).")
                return null
            }

            return try {
                val msg = gson.fromJson(jsonString, TransferMessage::class.java)
                val receivedMessage = BridgerMessage(
                    type = msg.type,
                    value = msg.payload,
                    address = device.address
                )
                Log.d(TAG, "Data received as JSON: Type=${receivedMessage.getType()}, Payload=${receivedMessage.value}, Address=${receivedMessage.address}, ID=${receivedMessage.id}")
                receivedMessage
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing JSON message: ${e.message}")
                null
            }
        }

        /**
         * Creates a standard clipboard message to be sent.
         * Annotated with @JvmStatic to be callable as a static method from Java.
         */
        @JvmStatic
        fun toSend(payload: String): BridgerMessage {
            return BridgerMessage(
                type = MessageType.CLIPBOARD.ordinal,
                value = payload
            )
        }
    }

    /**
     * A private data class used for serializing only the transferable parts of the message.
     */
    private data class TransferMessage(@SerializedName("t") val type: Int, @SerializedName("p") val payload: String)
}
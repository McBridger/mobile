package expo.modules.connector.models

import android.os.Bundle
import org.json.JSONObject
import java.util.UUID

/**
 * The Unified Porter: A single entity for all data movements.
 * IMMUTABLE data class. Use .copy() for updates.
 */
data class Porter(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Double = System.currentTimeMillis() / 1000.0,
    val isOutgoing: Boolean,
    val status: Status = Status.PENDING,
    val error: String? = null,
    
    // Metadata
    val name: String,
    val type: BridgerType,
    val totalSize: Long,
    
    // Progress
    val progress: Int = 0,
    val currentSize: Long = 0,
    val speedBps: Long = 0,
    
    // Payload (Populated when status is COMPLETED or during ACTIVE stream)
    // For TEXT: it's the actual content.
    // For FILE/IMAGE: it's the local URI string.
    val data: String? = null
) {
    enum class Status { PENDING, ACTIVE, COMPLETED, ERROR }

    /**
     * Prepares data for JavaScript. 
     * Truncates 'data' only if it's TEXT to prevent TransactionTooLargeException.
     */
    fun toBundle(): Bundle = Bundle().apply {
        putString("id", id)
        putDouble("timestamp", timestamp)
        putBoolean("isOutgoing", isOutgoing)
        putString("status", status.name)
        error?.let { putString("error", it) }
        putString("name", name)
        putString("type", type.name)
        putDouble("totalSize", totalSize.toDouble())
        putInt("progress", progress)
        putDouble("currentSize", currentSize.toDouble())
        putDouble("speedBps", speedBps.toDouble())
        
        data?.let { 
            if (type == BridgerType.TEXT) {
                val isTruncated = it.length > 1024
                val preview = if (isTruncated) it.take(1024) + "..." else it
                putString("data", preview)
                putBoolean("isTruncated", isTruncated)
            } else {
                putString("data", it) // URIs are small, no truncation needed
                putBoolean("isTruncated", false)
            }
        }
    }

    fun toJSON(): JSONObject = JSONObject().apply {
        put("id", id)
        put("timestamp", timestamp)
        put("isOutgoing", isOutgoing)
        put("status", status.name)
        put("error", error ?: JSONObject.NULL)
        put("name", name)
        put("type", type.name)
        put("totalSize", totalSize)
        put("progress", progress)
        put("currentSize", currentSize)
        put("speedBps", speedBps)
        put("data", data ?: JSONObject.NULL)
    }

    companion object {
        fun fromJSON(obj: JSONObject): Porter = Porter(
            id = obj.getString("id"),
            timestamp = obj.getDouble("timestamp"),
            isOutgoing = obj.getBoolean("isOutgoing"),
            status = Status.valueOf(obj.getString("status")),
            error = if (obj.isNull("error")) null else obj.getString("error"),
            name = obj.getString("name"),
            type = BridgerType.valueOf(obj.getString("type")),
            totalSize = obj.getLong("totalSize"),
            progress = obj.optInt("progress", 0),
            currentSize = obj.optLong("currentSize", 0),
            speedBps = obj.optLong("speedBps", 0),
            data = if (obj.isNull("data")) null else obj.getString("data")
        )
    }
}

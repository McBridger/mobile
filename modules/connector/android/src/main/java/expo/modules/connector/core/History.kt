package expo.modules.connector.core

import android.content.Context
import android.os.Bundle
import android.util.Log
import expo.modules.connector.database.dao.HistoryDao
import expo.modules.connector.database.entities.HistoryEntity
import expo.modules.connector.interfaces.IEncryptionService
import expo.modules.connector.models.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class History(
    context: Context,
    private val dao: HistoryDao,
    private val encryptionService: IEncryptionService,
    private val maxHistorySize: Int
) {
    // Scope for IO operations to avoid blocking UI thread
    private val scope = CoroutineScope(Dispatchers.IO)
    private val historyFile: File by lazy {
        val packageName = context.packageName
        File(context.filesDir, "bridger_history_$packageName.json")
    }

    init {
        // Cleanup old JSON history file if it exists
        scope.launch {
            if (historyFile.exists()) {
                if (historyFile.delete()) {
                    Log.i(TAG, "Legacy JSON history file deleted.")
                }
            }
        }
    }

    fun add(message: Message) {
        scope.launch {
            try {
                if (!encryptionService.isReady()) {
                    Log.w(TAG, "Encryption service not ready. Skipping history save.")
                    return@launch
                }

                val key = encryptionService.derive(HISTORY_DOMAIN, 32) ?: return@launch
                val encryptedBytes = encryptionService.encrypt(
                    message.value.toByteArray(Charsets.UTF_8),
                    key
                ) ?: return@launch

                val entity = HistoryEntity(
                    messageId = message.id,
                    type = message.typeId,
                    encryptedPayload = bytesToHex(encryptedBytes),
                    address = message.address,
                    timestamp = message.timestamp
                )

                dao.insert(entity)
                dao.trim(maxHistorySize)
                Log.d(TAG, "Added message to DB. Message ID: ${message.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save message to history DB: ${e.message}")
            }
        }
    }

    suspend fun retrieve(): List<Bundle> = withContext(Dispatchers.IO) {
        try {
            if (!encryptionService.isReady()) return@withContext emptyList<Bundle>()
            
            val key = encryptionService.derive(HISTORY_DOMAIN, 32) ?: return@withContext emptyList<Bundle>()
            val entities = dao.getAll()
            
            entities.mapNotNull { entity ->
                try {
                    val encryptedBytes = hexToBytes(entity.encryptedPayload)
                    val decryptedBytes = encryptionService.decrypt(encryptedBytes, key) ?: return@mapNotNull null
                    val value = String(decryptedBytes, Charsets.UTF_8)
                    
                    Message(
                        typeId = entity.type,
                        value = value,
                        address = entity.address,
                        id = entity.messageId,
                        timestamp = entity.timestamp
                    ).toBundle()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decrypt history entry, skipping.")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving history from DB: ${e.message}")
            emptyList()
        }
    }

    fun clear() {
        scope.launch {
            dao.deleteAll()
            Log.d(TAG, "History database cleared.")
        }
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        check(hex.length % 2 == 0) { "Must have an even length" }
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    companion object {
        private const val TAG = "History"
        private const val HISTORY_DOMAIN = "History_Encryption_Domain"
    }
}

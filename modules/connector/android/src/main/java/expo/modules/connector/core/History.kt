package expo.modules.connector.core
    
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import expo.modules.connector.models.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue

class History private constructor(private val historyFile: File) {
    private val gson = Gson()
    private val historyQueue = ConcurrentLinkedQueue<String>()
    
    // Scope for IO operations to avoid blocking UI thread
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        // Load history in background
        scope.launch {
            loadHistoryFromFile()
        }
    }

    fun add(message: Message) {
        historyQueue.add(message.toJson())
        // Save async
        scope.launch {
            saveHistoryToFile()
        }
        Log.d(TAG, "Added message to history: ${message.id}")
    }

    fun retrieve(): List<Bundle> {
        return historyQueue.mapNotNull { json ->
            Message.fromJson(json)?.toBundle()
        }
    }

    fun clear() {
        historyQueue.clear()
        scope.launch {
            if (!historyFile.exists()) return@launch
            if (!historyFile.delete()) return@launch

            Log.d(TAG, "Bridger history file cleared.")
        }
    }

    private fun loadHistoryFromFile() {
        if (!historyFile.exists()) return

        try {
            FileReader(historyFile).use { reader ->
                val type = object : TypeToken<ConcurrentLinkedQueue<String>>() {}.type
                val loadedQueue: ConcurrentLinkedQueue<String>? = gson.fromJson(reader, type)
                if (loadedQueue != null) {
                    historyQueue.addAll(loadedQueue)
                }
                Log.d(TAG, "Bridger history loaded from file. Total entries: ${historyQueue.size}")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error loading history from file: ${e.message}")
            historyFile.delete()
        }
    }

    private fun saveHistoryToFile() {
        try {
            FileWriter(historyFile).use { writer ->
                gson.toJson(historyQueue, writer)
                Log.d(TAG, "Bridger history saved to file.")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error saving history to file: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "History"
        @Volatile
        private var INSTANCE: History? = null

        fun getInstance(context: Context): History {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                val packageName = appContext.packageName
                val file = File(appContext.filesDir, "bridger_history_$packageName.json")

                INSTANCE ?: History(file).also { INSTANCE = it }
            }
        }
    }
}
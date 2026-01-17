package expo.modules.connector.core
    
import android.content.Context
import android.os.Bundle
import android.util.Log
import expo.modules.connector.models.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue

class History(context: Context, private val maxHistorySize: Int) {
    private val historyQueue = ConcurrentLinkedQueue<Message>()
    
    // Scope for IO operations to avoid blocking UI thread
    private val scope = CoroutineScope(Dispatchers.IO)
    private val initializationJob: Job
    
    private val historyFile: File by lazy {
        val packageName = context.packageName
        File(context.filesDir, "bridger_history_$packageName.json")
    }

    init {
        // Load history in background and keep track of the job
        initializationJob = scope.launch {
            loadHistoryFromFile()
        }
    }

    fun add(message: Message) {
        historyQueue.add(message)
        
        // Limit history size to prevent memory leaks and massive files
        while (historyQueue.size > maxHistorySize) {
            historyQueue.poll()
        }

        // Save async
        scope.launch {
            saveHistoryToFile()
        }
        Log.d(TAG, "Added message to history: ${message.id}. Current size: ${historyQueue.size}")
    }

    suspend fun retrieve(): List<Bundle> {
        // Wait for initialization to complete if it hasn't already (non-blocking!)
        initializationJob.join()
        return historyQueue.map { it.toBundle() }
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
                val content = reader.readText()
                val loadedList: List<Message> = Message.fromJSON(content)
                historyQueue.addAll(loadedList)
                Log.d(TAG, "Bridger history loaded from file. Total entries: ${historyQueue.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading history from file: ${e.message}")
            historyFile.delete()
        }
    }

    private fun saveHistoryToFile() {
        try {
            FileWriter(historyFile).use { writer ->
                val jsonContent = Message.toJSON(historyQueue.toList())
                writer.write(jsonContent)
                Log.d(TAG, "Bridger history saved to file.")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error saving history to file: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "History"
    }
}

package expo.modules.connector.core

import android.content.Context
import android.os.Bundle
import android.util.Log
import expo.modules.connector.models.Porter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

class History(context: Context, private val maxHistorySize: Int) {
    private val TAG = "History"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val initializationJob: Job

    private val _items = MutableStateFlow<List<Porter>>(emptyList())
    val items = _items.asStateFlow()

    private val historyFile: File by lazy {
        val packageName = context.packageName
        File(context.filesDir, "bridger_history_$packageName.json")
    }

    init {
        initializationJob = scope.launch { loadHistoryFromFile() }
    }

    fun add(porter: Porter) {
        _items.update { current ->
            (current + porter).takeLast(maxHistorySize)
        }
        scope.launch { saveHistoryToFile() }
        Log.d(TAG, "Added Porter to history: ${porter.id}. Total size: ${_items.value.size}")
    }

    fun get(id: String): Porter? = _items.value.find { it.id == id }

    /**
     * Updates a porter in the list using immutable copy and saves to disk.
     */
    fun updatePorter(id: String, transform: (Porter) -> Porter) {
        _items.update { current ->
            current.map { item ->
                if (item.id != id) return@map item
                transform(item)
            }
        }
        scope.launch { saveHistoryToFile() }
    }

    suspend fun retrieveBundles(): List<Bundle> {
        initializationJob.join() // Ensure we don't return empty list during startup
        return _items.value.map { it.toBundle() }
    }

    fun clear() {
        _items.value = emptyList()
        scope.launch {
            if (historyFile.exists()) {
                historyFile.delete()
                Log.d(TAG, "History file cleared.")
            }
        }
    }

    private fun loadHistoryFromFile() {
        if (!historyFile.exists()) return

        try {
            FileReader(historyFile).use { reader ->
                val json = JSONArray(reader.readText())
                val loaded = mutableListOf<Porter>()
                for (i in 0 until json.length()) {
                    val obj = json.getJSONObject(i)
                    loaded.add(Porter.fromJSON(obj))
                }
                _items.value = loaded
                Log.d(TAG, "History loaded from file. Total entries: ${loaded.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading history: ${e.message}")
        }
    }

    private fun saveHistoryToFile() {
        try {
            val jsonArray = JSONArray()
            _items.value.forEach { porter ->
                jsonArray.put(porter.toJSON())
            }
            FileWriter(historyFile).use { it.write(jsonArray.toString()) }
            Log.v(TAG, "History saved to disk.")
        } catch (e: IOException) {
            Log.e(TAG, "Error saving history: ${e.message}")
        }
    }
}
